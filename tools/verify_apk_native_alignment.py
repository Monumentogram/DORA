#!/usr/bin/env python3
"""Inventory allowlisted APK native libraries and verify 16 KiB ELF alignment."""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path


PAGE_SIZE = 16 * 1024
PT_LOAD = 1


def load_allowlist(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line_number, raw_line in enumerate(
        path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("|")
        if len(fields) != 5 or not all(fields):
            raise ValueError(f"Malformed allowlist line {line_number}: {raw_line}")
        basename = fields[0]
        if basename in entries:
            raise ValueError(f"Duplicate native allowlist entry: {basename}")
        entries[basename] = line
    return entries


def load_segments(data: bytes, entry_name: str) -> list[tuple[int, int, int]]:
    if data[:4] != b"\x7fELF":
        raise ValueError(f"Not an ELF binary: {entry_name}")

    elf_class = data[4]
    data_encoding = data[5]
    if data_encoding == 1:
        endian = "<"
    elif data_encoding == 2:
        endian = ">"
    else:
        raise ValueError(f"Unsupported ELF byte order in {entry_name}")

    if elf_class == 2:
        program_offset = struct.unpack_from(f"{endian}Q", data, 32)[0]
        entry_size = struct.unpack_from(f"{endian}H", data, 54)[0]
        entry_count = struct.unpack_from(f"{endian}H", data, 56)[0]
        program_format = f"{endian}IIQQQQQQ"
        offset_index, virtual_index, align_index = 2, 3, 7
    elif elf_class == 1:
        program_offset = struct.unpack_from(f"{endian}I", data, 28)[0]
        entry_size = struct.unpack_from(f"{endian}H", data, 42)[0]
        entry_count = struct.unpack_from(f"{endian}H", data, 44)[0]
        program_format = f"{endian}IIIIIIII"
        offset_index, virtual_index, align_index = 1, 2, 7
    else:
        raise ValueError(f"Unsupported ELF class in {entry_name}: {elf_class}")

    expected_size = struct.calcsize(program_format)
    if entry_size < expected_size:
        raise ValueError(f"Invalid ELF program header size in {entry_name}")

    segments: list[tuple[int, int, int]] = []
    for index in range(entry_count):
        header_offset = program_offset + index * entry_size
        values = struct.unpack_from(program_format, data, header_offset)
        if values[0] == PT_LOAD:
            segments.append(
                (
                    values[offset_index],
                    values[virtual_index],
                    values[align_index],
                )
            )
    if not segments:
        raise ValueError(f"ELF has no PT_LOAD segments: {entry_name}")
    return segments


def verify(apk_path: Path, allowlist_path: Path) -> None:
    allowlist = load_allowlist(allowlist_path)
    with zipfile.ZipFile(apk_path) as apk:
        native_entries = sorted(
            name for name in apk.namelist() if name.startswith("lib/") and name.endswith(".so")
        )
        packaged_basenames = {Path(name).name for name in native_entries}
        unexpected = packaged_basenames.difference(allowlist)
        stale = set(allowlist).difference(packaged_basenames)
        if unexpected:
            raise ValueError(f"Unapproved native libraries: {sorted(unexpected)}")
        if stale:
            raise ValueError(f"Stale native allowlist entries: {sorted(stale)}")

        for entry_name in native_entries:
            for file_offset, virtual_address, alignment in load_segments(
                apk.read(entry_name), entry_name
            ):
                if alignment < PAGE_SIZE:
                    raise ValueError(
                        f"{entry_name} PT_LOAD alignment {alignment:#x} is below {PAGE_SIZE:#x}"
                    )
                if (virtual_address - file_offset) % PAGE_SIZE != 0:
                    raise ValueError(
                        f"{entry_name} PT_LOAD offset/vaddr are not 16 KiB congruent"
                    )
            print(f"PASS {entry_name}: 16 KiB ELF alignment")

    print(
        f"Native inventory passed: {len(native_entries)} ABI entries, "
        f"{len(packaged_basenames)} allowlisted library basename(s)"
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument(
        "--allowlist",
        type=Path,
        default=Path("android/native-libs-allowlist.txt"),
    )
    arguments = parser.parse_args()
    verify(arguments.apk, arguments.allowlist)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, zipfile.BadZipFile, struct.error) as error:
        print(f"FAIL {error}", file=sys.stderr)
        sys.exit(1)
