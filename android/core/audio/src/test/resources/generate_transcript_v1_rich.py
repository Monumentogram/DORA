"""Regenerate the synthetic transcript-v1-rich.b64 golden fixture (Python 3 stdlib).

Wire-layout source inspected: TranscriptCodec.kt at installed Preview baseline
98a975f3aff268db605573e1cd666dbc237f3123, under
android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/transcript/.
This independent serializer never calls the current or old Kotlin encoder.
DTR1 uses big-endian ints/longs, int-length UTF-8, and no Cloud presence booleans.

History: finish RU (revision 2), edit RU (3), finish EN (5), edit EN (6),
explicitly select the older RU version resolving the EN edit (7), then begin
and cancel a third job (9). All identifiers and text are synthetic.
Run this script explicitly to regenerate; JVM tests consume only the fixed b64.
"""

import base64
import hashlib
import struct
from pathlib import Path


def identifier(number):
    return f"00000000-0000-0000-0000-{number:012d}"


payload = bytearray()


def integer(value):
    payload.extend(struct.pack(">i", value))


def long(value):
    payload.extend(struct.pack(">q", value))


def text(value):
    encoded = value.encode("utf-8")
    integer(len(encoded))
    payload.extend(encoded)


integer(0x44545231)
integer(1)
for number in (1, 2, 3):
    text(identifier(number))
text("a" * 64)
long(16000)
long(9)
text(identifier(4))
integer(2)
for number, raw, time in ((4, "первый оригинал", 2), (5, "second original", 5)):
    text(identifier(number))
    text(raw)
    long(time)
    for field in ("small-q8", "LOCAL", "UNAVAILABLE", "NOT_EVALUABLE"):
        text(field)
integer(2)
for number, version, before, after, time in (
    (7, 4, "первый оригинал", "исправленный первый", 3),
    (8, 5, "second original", "corrected second", 6),
):
    for field in (identifier(number), identifier(version), before, after):
        text(field)
    long(time)
integer(3)
for number, status, language, generation, time in (
    (4, "SUCCEEDED", "ru", 1, 1),
    (5, "SUCCEEDED", "en", 2, 4),
    (6, "CANCELLED", "ru", 3, 8),
):
    for field in (identifier(number), status, language):
        text(field)
    long(generation)
    long(time)
integer(1)
text(identifier(4))
long(6)
long(7)
integer(1)
text(identifier(8))

Path(__file__).with_name("transcript-v1-rich.b64").write_text(
    base64.b64encode(payload).decode("ascii") + "\n", encoding="ascii"
)
print(f"DTR1 bytes={len(payload)} sha256={hashlib.sha256(payload).hexdigest()}")
