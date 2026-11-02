"""Bounded HTTP ingress. Proxy must terminate TLS and trust only loopback backend."""
import json
import re
from typing import Literal

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from starlette.concurrency import run_in_threadpool

from .auth import canonical_uuid
from .errors import ServiceError

UUID_PATTERN = r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Empty(Strict):
    pass


class Recording(Strict):
    recording_id: str = Field(pattern=f"^{UUID_PATTERN}$")
    source_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    source_version: int = Field(ge=1, le=1)
    frames: int = Field(ge=1, le=28800000)


class Job(Strict):
    action_id: str = Field(pattern=f"^{UUID_PATTERN}$")
    recording_id: str = Field(pattern=f"^{UUID_PATTERN}$")
    language: Literal["ru-RU", "en-US"]
    consent_version: str = Field(max_length=64)
    consent_source: str = Field(max_length=64)
    consent_granted_at: int


class Audio(Strict):
    offset: int = Field(ge=0, le=57600044)
    data_base64: str = Field(max_length=87384)
    final: bool


class Run(Job):
    source_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    source_version: int = Field(ge=1, le=1)
    frames: int = Field(ge=8000, le=1_382_400_000)
    window_policy: Literal["cloud-balanced-1800-v1"]


def create_app(service, auth):
    app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)

    @app.api_route("/{path:path}", methods=["POST", "GET", "PUT", "DELETE", "PATCH"])
    async def ingress(request: Request, path: str):
        try:
            if request.url.scheme != "https":
                raise ServiceError("HTTPS_REQUIRED")
            if request.scope.get("query_string"):
                raise ServiceError("QUERY_FORBIDDEN")
            raw_path = request.scope.get("raw_path", b"")
            path = "/" + path
            if raw_path != path.encode("ascii", "ignore"):
                raise ServiceError("PATH_INVALID")
            method = request.method
            match = re.fullmatch(rf"/v1/jobs/({UUID_PATTERN})(?:/(audio|retry|cancel))?", path)
            run_match = re.fullmatch(rf"/v1/runs/({UUID_PATTERN})(?:/(cancel|parts/([0-9]|[1-3][0-9]|4[0-7])))?", path)
            part_index = None
            if (method, path) == ("POST", "/v1/session"):
                model, operation, job_id = Empty, "session", None
            elif (method, path) == ("POST", "/v1/recordings"):
                model, operation, job_id = Recording, "register", None
            elif (method, path) == ("POST", "/v1/jobs"):
                model, operation, job_id = Job, "create", None
            elif method == "POST" and path in ("/v1/runs", "/v1/runs/quote"):
                model, operation, job_id = Run, "run_create" if path == "/v1/runs" else "run_quote", None
            elif run_match and canonical_uuid(run_match[1]):
                job_id, suffix, raw_index = run_match.groups()
                if method == "GET" and suffix is None:
                    model, operation = None, "run_get"
                elif method == "POST" and suffix == "cancel":
                    model, operation = Empty, "run_cancel"
                elif method == "POST" and raw_index is not None:
                    model, operation, part_index = Empty, "run_part", int(raw_index)
                else:
                    raise ServiceError("NOT_FOUND", 404)
            elif match and canonical_uuid(match[1]):
                job_id, suffix = match.groups()
                if method == "GET" and not suffix:
                    model, operation = None, "get"
                elif method == "POST" and suffix:
                    model, operation = Audio if suffix == "audio" else Empty, suffix
                else:
                    raise ServiceError("NOT_FOUND", 404)
            else:
                raise ServiceError("NOT_FOUND", 404)
            for key in (b"x-dora-installation", b"x-dora-time", b"x-dora-nonce", b"x-dora-signature", b"authorization"):
                if sum(1 for k, _ in request.scope["headers"] if k == key) > 1:
                    raise ServiceError("AUTH_INVALID", 401)
            # JSONObject on Android escapes Base64 '/': a valid 64 KiB chunk can
            # occupy almost 175 KiB of JSON. Keep metadata routes at 100 KiB and
            # the decoded audio bound at 65536 bytes in Service.audio.
            body_limit = 192 * 1024 if operation == "audio" else 100 * 1024
            lengths = [value for key, value in request.scope["headers"] if key == b"content-length"]
            if len(lengths) > 1 or (lengths and (not lengths[0].isdigit() or len(lengths[0]) > 12)):
                raise ServiceError("CONTENT_LENGTH_INVALID", 422)
            declared = int(lengths[0]) if lengths else None
            if declared is not None and declared > body_limit:
                raise ServiceError("BODY_TOO_LARGE", 413)
            raw = bytearray()
            async for chunk in request.stream():
                if len(raw) + len(chunk) > body_limit:
                    raise ServiceError("BODY_TOO_LARGE", 413)
                raw.extend(chunk)
            if declared is not None and declared != len(raw):
                raise ServiceError("CONTENT_LENGTH_MISMATCH", 422)
            if model is None:
                if raw:
                    raise ServiceError("BODY_FORBIDDEN")
                body = None
            else:
                # Reject duplicate keys instead of accepting ambiguous proof-bound JSON.
                def pairs(items):
                    result = {}
                    for key, value in items:
                        if key in result:
                            raise ValueError()
                        result[key] = value
                    return result
                body = model.model_validate(json.loads(raw, object_pairs_hook=pairs)).model_dump()

            def process():
                with service.store.serialized():
                    principal = auth.verify(method, path, bytes(raw), request.headers, operation != "session")
                    if operation == "session":
                        return auth.session(principal)
                    args = [principal]
                    if job_id:
                        args.append(job_id)
                    if part_index is not None:
                        args.append(part_index)
                    if operation in ("register", "create", "audio", "run_quote", "run_create"):
                        args.append(body)
                    if operation.startswith("run_"):
                        return getattr(service.runs, operation[4:])(*args)
                    return getattr(service, operation)(*args)
            return JSONResponse(await run_in_threadpool(process))
        except ServiceError as exc:
            return JSONResponse({"error_code": exc.code}, status_code=exc.status)
        except (ValidationError, ValueError, TypeError, UnicodeError):
            return JSONResponse({"error_code": "INVALID_REQUEST"}, status_code=422)
        except Exception:
            # No exception text: providers may embed URLs/content/credentials.
            return JSONResponse({"error_code": "SERVICE_UNAVAILABLE"}, status_code=503)

    return app
