"""Authenticated seekable WAV backed by encrypted SQLite chunks, never plaintext disk."""
import hashlib
import io
import struct

from .errors import ServiceError


class EncryptedAudioReader:
    MAX_READ = 1024 * 1024

    def __init__(self, store, job, sha256):
        self.store, self.job = store, job
        self.length = job["binding"]["frames"] * 2 + 44
        self.sha256 = sha256
        self.position = 0
        self._offset = -1
        self._plain = bytearray()
        self.closed = False

    @classmethod
    def validated(cls, store, job):
        frames = job["binding"]["frames"]
        if not 8000 <= frames <= 28_800_000:
            raise ServiceError("INVALID_MEDIA")
        expected = struct.pack("<4sI4s4sIHHIIHH4sI", b"RIFF", frames*2+36, b"WAVE", b"fmt ",
                               16, 1, 1, 16000, 32000, 2, 16, b"data", frames*2)
        header, digest, offset, terminal = bytearray(), hashlib.sha256(), 0, False
        for row in store.iter_chunks(job["id"]):
            plain = bytearray(store.decrypt(f"audio:{job['id']}:{row['offset']}", row["ciphertext"]))
            try:
                if (terminal or row["offset"] != offset or not 0 < len(plain) <= 65536 or
                        len(plain) != row["size"] or hashlib.sha256(plain).hexdigest() != row["digest"]):
                    raise ServiceError("INVALID_MEDIA")
                header.extend(plain[:max(0, 44-len(header))])
                digest.update(plain)
                offset += len(plain)
                terminal = bool(row["final"])
            finally:
                plain[:] = b"\0" * len(plain)
        if offset != frames*2+44 or header != expected or not terminal:
            raise ServiceError("INVALID_MEDIA")
        return cls(store, job, digest.digest())

    def tell(self):
        return self.position

    def seek(self, offset, whence=io.SEEK_SET):
        if self.closed:
            raise ValueError("closed encrypted reader")
        value = offset + (0 if whence == io.SEEK_SET else self.position if whence == io.SEEK_CUR else self.length)
        if whence not in (io.SEEK_SET, io.SEEK_CUR, io.SEEK_END) or not 0 <= value <= self.length:
            raise ValueError("invalid encrypted reader offset")
        self.position = value
        return value

    def read(self, size=-1):
        # Refuse an SDK regression that tries to materialize the entire WAV.
        if self.closed or not 0 <= size <= self.MAX_READ:
            raise ValueError("bounded encrypted reader size required")
        wanted = min(size, self.length-self.position)
        output = bytearray()
        while len(output) < wanted:
            if not self._offset <= self.position < self._offset+len(self._plain):
                self._wipe()
                row = self.store.db.execute("SELECT * FROM chunks WHERE job=? AND offset<=? "
                    "AND length(ciphertext)>0 ORDER BY offset DESC LIMIT 1", (self.job["id"], self.position)).fetchone()
                if not row:
                    raise ServiceError("INVALID_MEDIA")
                self._plain = bytearray(self.store.decrypt(f"audio:{self.job['id']}:{row['offset']}", row["ciphertext"]))
                self._offset = row["offset"]
                if (not 0 < len(self._plain) <= 65536 or len(self._plain) != row["size"] or
                        hashlib.sha256(self._plain).hexdigest() != row["digest"] or
                        not self._offset <= self.position < self._offset+len(self._plain)):
                    self._wipe()
                    raise ServiceError("INVALID_MEDIA")
            first = self.position-self._offset
            amount = min(wanted-len(output), len(self._plain)-first)
            output.extend(self._plain[first:first+amount])
            self.position += amount
        return bytes(output)

    def readable(self):
        return not self.closed

    def seekable(self):
        return not self.closed

    def _wipe(self):
        self._plain[:] = b"\0" * len(self._plain)
        self._plain = bytearray()
        self._offset = -1

    def close(self):
        self._wipe()
        self.closed = True

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()
