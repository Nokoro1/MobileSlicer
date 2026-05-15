#!/usr/bin/env python3
"""Compare Orca/MobileSlicer export metadata without comparing toolpaths blindly.

The audit intentionally separates thumbnail payloads from the rest of the
G-code. This lets us verify that adding thumbnails does not alter non-thumbnail
toolpath output while still reporting thumbnail tags, dimensions, and counts.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import re
import sys
import zipfile
import struct
import zlib
from dataclasses import asdict, dataclass
from pathlib import Path
from xml.etree import ElementTree


THUMBNAIL_BLOCK_RE = re.compile(
    r"; THUMBNAIL_BLOCK_START\n.*?; THUMBNAIL_BLOCK_END\n*",
    re.DOTALL,
)
THUMBNAIL_BEGIN_RE = re.compile(
    r"; (?P<tag>thumbnail(?:_[A-Za-z0-9]+)?) begin (?P<width>\d+)x(?P<height>\d+) (?P<size>\d+)"
)
COLPIC_RE = re.compile(r";(?P<tag>[gs]image):(?P<payload>[^\n\r]*)")
BTT_HEADER_RE = re.compile(r";(?P<width>[0-9a-fA-F]{4})(?P<height>[0-9a-fA-F]{4})\r?\n")


@dataclass(frozen=True)
class ThumbnailBlock:
    tag: str
    width: int | None
    height: int | None
    encoded_size: int


@dataclass(frozen=True)
class GcodeMetadata:
    path: str
    bytes: int
    lines: int
    stripped_sha256: str
    thumbnail_blocks: list[ThumbnailBlock]
    thumbnail_image_metrics: dict[str, object]
    print_time_lines: list[str]
    filament_lines: list[str]
    layer_lines: list[str]
    toolchange_lines: list[str]


@dataclass(frozen=True)
class AuditFailure:
    check: str
    message: str


@dataclass(frozen=True)
class PngImageMetrics:
    width: int
    height: int
    color_type: int
    bit_depth: int
    pixels: int
    nontransparent_pixels: int
    transparent_pixels: int
    alpha_coverage: float
    average_luma: float
    unique_rgba: int
    bbox: list[int] | None


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def strip_thumbnail_blocks(gcode: str) -> str:
    without_standard = THUMBNAIL_BLOCK_RE.sub("", gcode)
    without_colpic = COLPIC_RE.sub("", without_standard)
    return re.sub(r";[0-9a-fA-F]{8}\r?\n.*?; bigtree thumbnail end\r?\n?", "", without_colpic, flags=re.DOTALL)


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8", errors="replace")).hexdigest()


def extract_standard_thumbnail_blocks(gcode: str) -> list[ThumbnailBlock]:
    blocks: list[ThumbnailBlock] = []
    for match in THUMBNAIL_BEGIN_RE.finditer(gcode):
        blocks.append(
            ThumbnailBlock(
                tag=match.group("tag"),
                width=int(match.group("width")),
                height=int(match.group("height")),
                encoded_size=int(match.group("size")),
            )
        )
    for match in COLPIC_RE.finditer(gcode):
        blocks.append(
            ThumbnailBlock(
                tag=match.group("tag"),
                width=None,
                height=None,
                encoded_size=len(match.group("payload")),
            )
        )
    btt_end = gcode.find("; bigtree thumbnail end")
    if btt_end >= 0:
        match = BTT_HEADER_RE.search(gcode[:btt_end])
        if match:
            payload_start = match.end()
            blocks.append(
                ThumbnailBlock(
                    tag="thumbnail_BTT",
                    width=int(match.group("width"), 16),
                    height=int(match.group("height"), 16),
                    encoded_size=max(0, btt_end - payload_start),
                )
            )
    return blocks


def extract_standard_thumbnail_payloads(gcode: str) -> dict[str, bytes]:
    payloads: dict[str, bytes] = {}
    lines = gcode.splitlines()
    index = 0
    while index < len(lines):
        match = THUMBNAIL_BEGIN_RE.match(lines[index])
        if not match:
            index += 1
            continue
        tag = match.group("tag")
        width = int(match.group("width"))
        height = int(match.group("height"))
        key = f"{tag}:{width}x{height}"
        index += 1
        chunks: list[str] = []
        end_marker = f"; {tag} end"
        while index < len(lines):
            line = lines[index]
            if line.strip() == end_marker:
                break
            if line.startswith(";"):
                payload = line[1:].strip()
                if payload:
                    chunks.append(payload)
            index += 1
        encoded = "".join(chunks)
        if encoded:
            try:
                payloads[key] = base64.b64decode(encoded, validate=True)
            except ValueError:
                payloads[key] = b""
        index += 1
    return payloads


def inspect_gcode_thumbnail_payloads(gcode: str) -> dict[str, object]:
    metrics: dict[str, object] = {}
    for key, payload in extract_standard_thumbnail_payloads(gcode).items():
        if not payload:
            metrics[key] = {"parse_error": "empty or invalid base64 payload"}
            continue
        if key.startswith("thumbnail:"):
            try:
                metrics[key] = asdict(png_image_metrics(payload))
            except ValueError as exc:
                metrics[key] = {"parse_error": str(exc)}
        elif key.startswith("thumbnail_JPG:"):
            is_jpeg = len(payload) >= 4 and payload.startswith(b"\xff\xd8") and payload.endswith(b"\xff\xd9")
            metrics[key] = {
                "bytes": len(payload),
                "format": "JPG" if is_jpeg else "unknown",
                "sha256": hashlib.sha256(payload).hexdigest(),
                **({} if is_jpeg else {"parse_error": "payload is not a JPEG stream"}),
            }
        elif key.startswith("thumbnail_QOI:"):
            is_qoi = len(payload) >= 14 and payload.startswith(b"qoif")
            metrics[key] = {
                "bytes": len(payload),
                "format": "QOI" if is_qoi else "unknown",
                "sha256": hashlib.sha256(payload).hexdigest(),
                **({} if is_qoi else {"parse_error": "payload is not a QOI stream"}),
            }
        else:
            metrics[key] = {
                "bytes": len(payload),
                "sha256": hashlib.sha256(payload).hexdigest(),
            }
    return metrics


def interesting_lines(gcode: str, patterns: tuple[str, ...], limit: int = 80) -> list[str]:
    lowered_patterns = tuple(pattern.lower() for pattern in patterns)
    lines: list[str] = []
    for line in gcode.splitlines():
        lowered = line.lower()
        if any(pattern in lowered for pattern in lowered_patterns):
            lines.append(line.strip())
            if len(lines) >= limit:
                break
    return lines


def toolchange_lines(gcode: str, limit: int = 80) -> list[str]:
    lines: list[str] = []
    for line in gcode.splitlines():
        stripped = line.strip()
        lowered = stripped.lower()
        if re.match(r"^T\d+(?:\s|;|$)", stripped) or "total toolchange" in lowered or "tool changes" in lowered:
            lines.append(stripped)
            if len(lines) >= limit:
                break
    return lines


def inspect_gcode(path: Path) -> GcodeMetadata:
    gcode = read_text(path)
    stripped = strip_thumbnail_blocks(gcode)
    return GcodeMetadata(
        path=str(path),
        bytes=path.stat().st_size,
        lines=gcode.count("\n") + (1 if gcode and not gcode.endswith("\n") else 0),
        stripped_sha256=sha256_text(stripped),
        thumbnail_blocks=extract_standard_thumbnail_blocks(gcode),
        thumbnail_image_metrics=inspect_gcode_thumbnail_payloads(gcode),
        print_time_lines=interesting_lines(gcode, ("print_time", "estimated printing time", "estimated print time")),
        filament_lines=interesting_lines(gcode, ("filament used", "used_filament", "total filament", "extruded_weight")),
        layer_lines=interesting_lines(gcode, ("total_layer_count", "layer_count", "layer change")),
        toolchange_lines=toolchange_lines(gcode),
    )


def paeth_predictor(left: int, up: int, upper_left: int) -> int:
    estimate = left + up - upper_left
    distance_left = abs(estimate - left)
    distance_up = abs(estimate - up)
    distance_upper_left = abs(estimate - upper_left)
    if distance_left <= distance_up and distance_left <= distance_upper_left:
        return left
    if distance_up <= distance_upper_left:
        return up
    return upper_left


def decode_png_rgba(data: bytes) -> tuple[int, int, int, int, list[tuple[int, int, int, int]]]:
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("not a PNG")
    offset = 8
    width = height = bit_depth = color_type = None
    idat_chunks: list[bytes] = []
    while offset < len(data):
        if offset + 8 > len(data):
            raise ValueError("truncated PNG chunk header")
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        offset += length + 12
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, png_filter, interlace = struct.unpack(
                ">IIBBBBB",
                chunk_data,
            )
            if compression != 0 or png_filter != 0 or interlace != 0:
                raise ValueError("unsupported PNG compression/filter/interlace mode")
        elif chunk_type == b"IDAT":
            idat_chunks.append(chunk_data)
        elif chunk_type == b"IEND":
            break
    if width is None or height is None or bit_depth is None or color_type is None:
        raise ValueError("missing PNG IHDR")
    if bit_depth != 8:
        raise ValueError(f"unsupported PNG bit depth: {bit_depth}")
    channels_by_color_type = {
        0: 1,
        2: 3,
        4: 2,
        6: 4,
    }
    channels = channels_by_color_type.get(color_type)
    if channels is None:
        raise ValueError(f"unsupported PNG color type: {color_type}")
    decompressed = zlib.decompress(b"".join(idat_chunks))
    row_stride = width * channels
    expected_minimum = height * (row_stride + 1)
    if len(decompressed) < expected_minimum:
        raise ValueError("truncated PNG image data")

    pixels: list[tuple[int, int, int, int]] = []
    previous = bytearray(row_stride)
    index = 0
    for _ in range(height):
        filter_type = decompressed[index]
        index += 1
        row = bytearray(decompressed[index:index + row_stride])
        index += row_stride
        if filter_type > 4:
            raise ValueError(f"unsupported PNG row filter: {filter_type}")
        for pos in range(row_stride):
            left = row[pos - channels] if pos >= channels else 0
            up = previous[pos]
            upper_left = previous[pos - channels] if pos >= channels else 0
            if filter_type == 1:
                row[pos] = (row[pos] + left) & 0xff
            elif filter_type == 2:
                row[pos] = (row[pos] + up) & 0xff
            elif filter_type == 3:
                row[pos] = (row[pos] + ((left + up) // 2)) & 0xff
            elif filter_type == 4:
                row[pos] = (row[pos] + paeth_predictor(left, up, upper_left)) & 0xff
        previous = row
        for x in range(width):
            sample = row[x * channels:(x + 1) * channels]
            if color_type == 0:
                pixels.append((sample[0], sample[0], sample[0], 255))
            elif color_type == 2:
                pixels.append((sample[0], sample[1], sample[2], 255))
            elif color_type == 4:
                pixels.append((sample[0], sample[0], sample[0], sample[1]))
            else:
                pixels.append((sample[0], sample[1], sample[2], sample[3]))
    return width, height, bit_depth, color_type, pixels


def png_image_metrics(data: bytes) -> PngImageMetrics:
    width, height, bit_depth, color_type, pixels = decode_png_rgba(data)
    nontransparent: list[tuple[int, int, int, int]] = []
    min_x = width
    min_y = height
    max_x = -1
    max_y = -1
    weighted_luma = 0.0
    for index, (red, green, blue, alpha) in enumerate(pixels):
        if alpha <= 0:
            continue
        x = index % width
        y = index // width
        min_x = min(min_x, x)
        min_y = min(min_y, y)
        max_x = max(max_x, x)
        max_y = max(max_y, y)
        nontransparent.append((red, green, blue, alpha))
        weighted_luma += 0.2126 * red + 0.7152 * green + 0.0722 * blue
    nontransparent_pixels = len(nontransparent)
    pixels_count = width * height
    return PngImageMetrics(
        width=width,
        height=height,
        color_type=color_type,
        bit_depth=bit_depth,
        pixels=pixels_count,
        nontransparent_pixels=nontransparent_pixels,
        transparent_pixels=pixels_count - nontransparent_pixels,
        alpha_coverage=nontransparent_pixels / pixels_count if pixels_count else 0.0,
        average_luma=weighted_luma / nontransparent_pixels if nontransparent_pixels else 0.0,
        unique_rgba=len(set(nontransparent)),
        bbox=[min_x, min_y, max_x, max_y] if nontransparent_pixels else None,
    )


def inspect_3mf(path: Path) -> dict[str, object]:
    with zipfile.ZipFile(path) as archive:
        names = sorted(archive.namelist())
        relationship_targets: list[str] = []
        json_entries: dict[str, object] = {}
        entry_sha256: dict[str, str] = {}
        thumbnail_metrics: dict[str, object] = {}
        for name in names:
            if not name.endswith(".rels"):
                continue
            try:
                root = ElementTree.fromstring(archive.read(name))
            except ElementTree.ParseError:
                continue
            for element in root.iter():
                target = element.attrib.get("Target")
                if target:
                    relationship_targets.append(target)
        for name in names:
            if not (name.startswith("Metadata/") and name.endswith(".json")):
                continue
            try:
                json_entries[name] = json.loads(archive.read(name).decode("utf-8"))
            except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                json_entries[name] = {"__parse_error__": str(exc)}
    thumbnail_entries = [
        name for name in names
        if name.startswith("Metadata/")
        and (
            name.endswith(".png")
            or name.endswith(".jpg")
            or name.endswith(".jpeg")
            or "thumbnail" in name.lower()
        )
    ]
    with zipfile.ZipFile(path) as archive:
        for name in names:
            if name.endswith("/"):
                continue
            payload = archive.read(name)
            entry_sha256[name] = hashlib.sha256(payload).hexdigest()
        for name in thumbnail_entries:
            payload = archive.read(name)
            if name.lower().endswith(".png"):
                try:
                    thumbnail_metrics[name] = asdict(png_image_metrics(payload))
                except ValueError as exc:
                    thumbnail_metrics[name] = {"parse_error": str(exc)}
    relationship_entries = [name for name in names if name.endswith(".rels")]
    return {
        "path": str(path),
        "entries": len(names),
        "entry_names": names,
        "thumbnail_entries": thumbnail_entries,
        "relationship_entries": relationship_entries,
        "relationship_targets": sorted(relationship_targets),
        "entry_sha256": entry_sha256,
        "thumbnail_image_metrics": thumbnail_metrics,
        "json_entries": sorted(json_entries),
        "json_fields": {
            name: sorted(value.keys()) if isinstance(value, dict) else []
            for name, value in json_entries.items()
        },
        "json_payloads": json_entries,
    }


def compare_gcode(mobile: GcodeMetadata, orca: GcodeMetadata | None) -> dict[str, object]:
    result: dict[str, object] = {"mobile": asdict(mobile)}
    if orca is not None:
        result["orca"] = asdict(orca)
        result["comparison"] = {
            "stripped_body_hash_match": mobile.stripped_sha256 == orca.stripped_sha256,
            "thumbnail_count_match": len(mobile.thumbnail_blocks) == len(orca.thumbnail_blocks),
            "mobile_thumbnail_count": len(mobile.thumbnail_blocks),
            "orca_thumbnail_count": len(orca.thumbnail_blocks),
        }
    return result


def thumbnail_signature(block: ThumbnailBlock) -> str:
    if block.width is None or block.height is None:
        return block.tag
    return f"{block.tag}:{block.width}x{block.height}"


def parse_expected_thumbnail(value: str) -> ThumbnailBlock:
    raw = value.strip()
    if raw in {"gimage", "simage"}:
        return ThumbnailBlock(tag=raw, width=None, height=None, encoded_size=0)
    match = re.fullmatch(r"(?P<tag>thumbnail(?:_[A-Za-z0-9]+)?):(?P<width>\d+)x(?P<height>\d+)", raw)
    if not match:
        raise argparse.ArgumentTypeError(
            f"invalid thumbnail expectation {value!r}; use thumbnail:150x150, thumbnail_QOI:96x96, gimage, or simage"
        )
    return ThumbnailBlock(
        tag=match.group("tag"),
        width=int(match.group("width")),
        height=int(match.group("height")),
        encoded_size=0,
    )


def validate_gcode_expectations(
    metadata: GcodeMetadata,
    expected_thumbnails: list[ThumbnailBlock],
    forbidden_tags: set[str],
    require_print_time: bool,
    require_filament: bool,
    require_toolchange: bool,
    require_thumbnail_visuals: bool,
    require_thumbnail_antialias: bool,
    require_thumbnail_payload_signatures: bool,
) -> list[AuditFailure]:
    failures: list[AuditFailure] = []
    actual_signatures = [thumbnail_signature(block) for block in metadata.thumbnail_blocks]
    if expected_thumbnails:
        expected_signatures = [thumbnail_signature(block) for block in expected_thumbnails]
        if actual_signatures != expected_signatures:
            failures.append(
                AuditFailure(
                    check="thumbnail-signature",
                    message=f"expected thumbnails {expected_signatures}, got {actual_signatures}",
                )
            )
        else:
            for block in metadata.thumbnail_blocks:
                if block.encoded_size <= 0:
                    failures.append(
                        AuditFailure(
                            check="thumbnail-payload",
                            message=f"{thumbnail_signature(block)} has an empty encoded payload",
                        )
                    )
    for block in metadata.thumbnail_blocks:
        if block.tag in forbidden_tags:
            failures.append(
                AuditFailure(
                    check="forbidden-thumbnail-tag",
                    message=f"forbidden thumbnail tag present: {block.tag}",
                )
            )
    if require_print_time and not metadata.print_time_lines:
        failures.append(AuditFailure(check="print-time", message="missing print time metadata"))
    if require_filament and not metadata.filament_lines:
        failures.append(AuditFailure(check="filament", message="missing filament usage metadata"))
    if require_toolchange and not metadata.toolchange_lines:
        failures.append(AuditFailure(check="toolchange", message="missing toolchange metadata or commands"))
    if require_thumbnail_payload_signatures:
        for block in expected_thumbnails:
            if block.width is None or block.height is None:
                continue
            if block.tag not in {"thumbnail_JPG", "thumbnail_QOI"}:
                continue
            key = thumbnail_signature(block)
            raw_metrics = metadata.thumbnail_image_metrics.get(key)
            if not isinstance(raw_metrics, dict):
                failures.append(
                    AuditFailure(
                        check="thumbnail-payload-signature",
                        message=f"{key} is missing decoded payload metrics",
                    )
                )
                continue
            if raw_metrics.get("parse_error"):
                failures.append(
                    AuditFailure(
                        check="thumbnail-payload-signature",
                        message=f"{key} parse failed: {raw_metrics['parse_error']}",
                    )
                )
                continue
            expected_format = block.tag.removeprefix("thumbnail_")
            if raw_metrics.get("format") != expected_format:
                failures.append(
                    AuditFailure(
                        check="thumbnail-payload-signature",
                        message=f"{key} expected {expected_format} payload, got {raw_metrics.get('format')}",
                    )
                )
    if require_thumbnail_visuals:
        if not metadata.thumbnail_image_metrics:
            failures.append(AuditFailure(check="gcode-thumbnail-visuals", message="missing decoded thumbnail metrics"))
        for key, raw_metrics in metadata.thumbnail_image_metrics.items():
            if not key.startswith("thumbnail:"):
                continue
            if not isinstance(raw_metrics, dict):
                failures.append(AuditFailure(check="gcode-thumbnail-visuals", message=f"{key} metrics are invalid"))
                continue
            if raw_metrics.get("parse_error"):
                failures.append(
                    AuditFailure(check="gcode-thumbnail-visuals", message=f"{key} parse failed: {raw_metrics['parse_error']}")
                )
                continue
            nontransparent = int(raw_metrics.get("nontransparent_pixels", 0))
            transparent = int(raw_metrics.get("transparent_pixels", 0))
            unique_rgba = int(raw_metrics.get("unique_rgba", 0))
            if nontransparent <= 0:
                failures.append(AuditFailure(check="gcode-thumbnail-visuals", message=f"{key} is blank"))
            if transparent <= 0:
                failures.append(
                    AuditFailure(check="gcode-thumbnail-visuals", message=f"{key} lost transparent background")
                )
            if unique_rgba <= 1:
                failures.append(
                    AuditFailure(check="gcode-thumbnail-visuals", message=f"{key} has insufficient color variation")
                )
            if require_thumbnail_antialias and unique_rgba < 4:
                failures.append(
                    AuditFailure(
                        check="gcode-thumbnail-antialias",
                        message=f"{key} has insufficient antialias/detail variation: unique_rgba={unique_rgba}",
                    )
                )
    return failures


def validate_3mf_expectations(
    metadata: dict[str, object],
    expected_entries: list[str],
    expected_thumbnail_entries: list[str],
    expected_relationship_targets: list[str],
    expected_bbox_json_entries: list[str],
    expected_different_entries: list[str],
    require_distinct_thumbnail_entries: bool,
    require_thumbnail_visuals: bool,
    require_positive_first_layer_time: bool = True,
) -> list[AuditFailure]:
    failures: list[AuditFailure] = []
    entry_names = set(str(entry) for entry in metadata.get("entry_names", []))
    thumbnail_entries = set(str(entry) for entry in metadata.get("thumbnail_entries", []))
    relationship_targets = set(str(target) for target in metadata.get("relationship_targets", []))
    json_payloads = metadata.get("json_payloads", {})
    if not isinstance(json_payloads, dict):
        json_payloads = {}
    entry_sha256 = metadata.get("entry_sha256", {})
    if not isinstance(entry_sha256, dict):
        entry_sha256 = {}
    for entry in expected_entries:
        if entry not in entry_names:
            failures.append(
                AuditFailure(
                    check="3mf-entry",
                    message=f"missing 3MF entry: {entry}",
                )
            )
    for entry in expected_thumbnail_entries:
        if entry not in thumbnail_entries:
            failures.append(
                AuditFailure(
                    check="3mf-thumbnail-entry",
                    message=f"missing 3MF thumbnail entry: {entry}",
                )
            )
    for target in expected_relationship_targets:
        if target not in relationship_targets:
            failures.append(
                AuditFailure(
                    check="3mf-relationship-target",
                    message=f"missing 3MF relationship target: {target}",
                )
            )
    required_bbox_fields = {
        "bbox_all",
        "bbox_objects",
        "filament_ids",
        "filament_colors",
        "is_seq_print",
        "first_extruder",
        "nozzle_diameter",
        "bed_type",
        "first_layer_time",
        "version",
    }
    for entry in expected_bbox_json_entries:
        payload = json_payloads.get(entry)
        if payload is None:
            failures.append(
                AuditFailure(
                    check="3mf-bbox-json",
                    message=f"missing 3MF bbox JSON entry: {entry}",
                )
            )
            continue
        if not isinstance(payload, dict) or "__parse_error__" in payload:
            failures.append(
                AuditFailure(
                    check="3mf-bbox-json",
                    message=f"invalid 3MF bbox JSON entry: {entry}",
                )
            )
            continue
        missing_fields = sorted(required_bbox_fields.difference(payload.keys()))
        if missing_fields:
            failures.append(
                AuditFailure(
                    check="3mf-bbox-json-fields",
                    message=f"{entry} missing fields: {missing_fields}",
                )
            )
        bbox_objects = payload.get("bbox_objects")
        if not isinstance(bbox_objects, list) or not bbox_objects:
            failures.append(
                AuditFailure(
                    check="3mf-bbox-json-objects",
                    message=f"{entry} has no bbox_objects",
                )
            )
        first_layer_time = payload.get("first_layer_time")
        if require_positive_first_layer_time and (
            not isinstance(first_layer_time, (int, float))
            or not math.isfinite(float(first_layer_time))
            or float(first_layer_time) <= 0.0
        ):
            failures.append(
                AuditFailure(
                    check="3mf-bbox-json-first-layer-time",
                    message=f"{entry} has invalid first_layer_time: {first_layer_time!r}",
                )
            )
    for pair in expected_different_entries:
        left, separator, right = pair.partition(":")
        if not separator or not left or not right:
            failures.append(
                AuditFailure(
                    check="3mf-different-entry-argument",
                    message=f"invalid different-entry pair {pair!r}; use path/a:path/b",
                )
            )
            continue
        left_hash = entry_sha256.get(left)
        right_hash = entry_sha256.get(right)
        if left_hash is None or right_hash is None:
            failures.append(
                AuditFailure(
                    check="3mf-different-entry",
                    message=f"cannot compare missing entries: {left}, {right}",
                )
            )
        elif left_hash == right_hash:
            failures.append(
                AuditFailure(
                    check="3mf-different-entry",
                    message=f"entries are byte-identical but should differ: {left}, {right}",
                )
            )
    if require_distinct_thumbnail_entries:
        entries_to_compare = expected_thumbnail_entries or sorted(thumbnail_entries)
        hashes = {
            entry: entry_sha256.get(entry)
            for entry in entries_to_compare
            if entry_sha256.get(entry) is not None
        }
        if len(hashes) < 2:
            failures.append(
                AuditFailure(
                    check="3mf-distinct-thumbnail-entries",
                    message="not enough thumbnail entries available for distinctness check",
                )
            )
        elif len(set(hashes.values())) == 1:
            failures.append(
                AuditFailure(
                    check="3mf-distinct-thumbnail-entries",
                    message=f"thumbnail entries are byte-identical: {sorted(hashes)}",
                )
            )
    if require_thumbnail_visuals:
        failures.extend(validate_3mf_thumbnail_visuals(metadata, expected_thumbnail_entries))
    return failures


def metric_number(metrics: dict[str, object], key: str) -> float:
    value = metrics.get(key)
    if isinstance(value, (int, float)):
        return float(value)
    return 0.0


def validate_3mf_thumbnail_visuals(
    metadata: dict[str, object],
    expected_thumbnail_entries: list[str],
) -> list[AuditFailure]:
    failures: list[AuditFailure] = []
    raw_metrics = metadata.get("thumbnail_image_metrics", {})
    metrics_by_entry = raw_metrics if isinstance(raw_metrics, dict) else {}
    entries = expected_thumbnail_entries or [
        str(entry)
        for entry in metadata.get("thumbnail_entries", [])
        if str(entry).lower().endswith(".png")
    ]
    for entry in entries:
        if not entry.lower().endswith(".png"):
            continue
        metrics = metrics_by_entry.get(entry)
        if not isinstance(metrics, dict):
            failures.append(AuditFailure("3mf-thumbnail-visual", f"missing PNG metrics for {entry}"))
            continue
        if metrics.get("parse_error"):
            failures.append(AuditFailure("3mf-thumbnail-visual", f"{entry} is not a decodable PNG: {metrics['parse_error']}"))
            continue
        width = metric_number(metrics, "width")
        height = metric_number(metrics, "height")
        nontransparent = metric_number(metrics, "nontransparent_pixels")
        transparent = metric_number(metrics, "transparent_pixels")
        coverage = metric_number(metrics, "alpha_coverage")
        unique_rgba = metric_number(metrics, "unique_rgba")
        if width <= 0 or height <= 0:
            failures.append(AuditFailure("3mf-thumbnail-visual-dimensions", f"{entry} has invalid dimensions {width}x{height}"))
        if nontransparent <= 0:
            failures.append(AuditFailure("3mf-thumbnail-visual-blank", f"{entry} has no visible pixels"))
        if coverage < 0.0005:
            failures.append(AuditFailure("3mf-thumbnail-visual-coverage", f"{entry} visible coverage is too small: {coverage:.6f}"))
        if transparent <= 0:
            failures.append(AuditFailure("3mf-thumbnail-visual-alpha", f"{entry} has no transparent background pixels"))
        if unique_rgba <= 1:
            failures.append(
                AuditFailure(
                    "3mf-thumbnail-visual-variation",
                    f"{entry} has insufficient color/detail variation: unique_rgba={unique_rgba:.0f}",
                )
            )

    plate = metrics_by_entry.get("Metadata/plate_1.png")
    no_light = metrics_by_entry.get("Metadata/plate_no_light_1.png")
    top = metrics_by_entry.get("Metadata/top_1.png")
    pick = metrics_by_entry.get("Metadata/pick_1.png")
    if isinstance(plate, dict) and isinstance(no_light, dict) and not plate.get("parse_error") and not no_light.get("parse_error"):
        plate_luma = metric_number(plate, "average_luma")
        no_light_luma = metric_number(no_light, "average_luma")
        luma_delta = abs(no_light_luma - plate_luma)
        if luma_delta < 20.0:
            failures.append(
                AuditFailure(
                    "3mf-thumbnail-visual-no-light",
                    f"plate_no_light_1.png should be visually distinct from plate_1.png: luma delta {luma_delta:.2f}",
                )
            )
    if isinstance(top, dict) and isinstance(pick, dict) and not top.get("parse_error") and not pick.get("parse_error"):
        top_luma = metric_number(top, "average_luma")
        pick_luma = metric_number(pick, "average_luma")
        if abs(top_luma - pick_luma) < 20.0:
            failures.append(
                AuditFailure(
                    "3mf-thumbnail-visual-pick",
                    f"pick_1.png should be visually distinct from top_1.png: luma delta {abs(top_luma - pick_luma):.2f}",
                )
            )
    return failures


def compare_3mf_thumbnail_visuals(mobile: dict[str, object], orca: dict[str, object]) -> dict[str, object]:
    mobile_metrics = mobile.get("thumbnail_image_metrics", {})
    orca_metrics = orca.get("thumbnail_image_metrics", {})
    if not isinstance(mobile_metrics, dict) or not isinstance(orca_metrics, dict):
        return {"comparable_entries": [], "entries": {}}
    common_entries = sorted(set(mobile_metrics.keys()).intersection(orca_metrics.keys()))
    entries: dict[str, object] = {}
    for entry in common_entries:
        mobile_entry = mobile_metrics.get(entry)
        orca_entry = orca_metrics.get(entry)
        if not isinstance(mobile_entry, dict) or not isinstance(orca_entry, dict):
            continue
        entries[entry] = {
            "mobile_width": mobile_entry.get("width"),
            "mobile_height": mobile_entry.get("height"),
            "orca_width": orca_entry.get("width"),
            "orca_height": orca_entry.get("height"),
            "alpha_coverage_delta": abs(
                metric_number(mobile_entry, "alpha_coverage") - metric_number(orca_entry, "alpha_coverage")
            ),
            "average_luma_delta": abs(
                metric_number(mobile_entry, "average_luma") - metric_number(orca_entry, "average_luma")
            ),
            "mobile_bbox": mobile_entry.get("bbox"),
            "orca_bbox": orca_entry.get("bbox"),
        }
    return {
        "comparable_entries": list(entries.keys()),
        "entries": entries,
    }


def append_failures(output: dict[str, object], failures: list[AuditFailure]) -> None:
    if not failures:
        return
    existing = output.setdefault("failures", [])
    assert isinstance(existing, list)
    existing.extend(asdict(failure) for failure in failures)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mobile-gcode", type=Path, help="MobileSlicer G-code output to inspect")
    parser.add_argument("--orca-gcode", type=Path, help="Desktop Orca reference G-code output")
    parser.add_argument("--mobile-3mf", type=Path, help="MobileSlicer sliced 3MF output to inspect")
    parser.add_argument("--orca-3mf", type=Path, help="Desktop Orca reference sliced 3MF output")
    parser.add_argument(
        "--expect-thumbnail",
        action="append",
        type=parse_expected_thumbnail,
        default=[],
        help="Expected G-code thumbnail signature, in order. Example: thumbnail:150x150, gimage, simage",
    )
    parser.add_argument(
        "--forbid-thumbnail-tag",
        action="append",
        default=[],
        help="Fail if this thumbnail tag is present. Example: gimage",
    )
    parser.add_argument("--require-print-time", action="store_true", help="Fail if print time metadata is missing")
    parser.add_argument("--require-filament", action="store_true", help="Fail if filament usage metadata is missing")
    parser.add_argument("--require-toolchange", action="store_true", help="Fail if no toolchange command/metadata is present")
    parser.add_argument(
        "--require-gcode-thumbnail-visuals",
        action="store_true",
        help="Fail if embedded PNG G-code thumbnails are blank, opaque, or undecodable.",
    )
    parser.add_argument(
        "--require-gcode-thumbnail-antialias",
        action="store_true",
        help="Fail if embedded PNG G-code thumbnails lack antialias/detail variation.",
    )
    parser.add_argument(
        "--require-gcode-thumbnail-payload-signatures",
        action="store_true",
        help="Fail if embedded non-PNG G-code thumbnails are mislabeled or undecodable by their declared format.",
    )
    parser.add_argument(
        "--expect-3mf-thumbnail-entry",
        action="append",
        default=[],
        help="Fail if this sliced 3MF thumbnail asset is missing. Example: Metadata/plate_1.png",
    )
    parser.add_argument(
        "--expect-3mf-entry",
        action="append",
        default=[],
        help="Fail if this ZIP entry is missing. Example: Metadata/plate_1.json",
    )
    parser.add_argument(
        "--expect-3mf-bbox-json",
        action="append",
        default=[],
        help="Fail if this Orca first-layer bbox JSON is missing or incomplete. Example: Metadata/plate_1.json",
    )
    parser.add_argument(
        "--expect-3mf-relationship-target",
        action="append",
        default=[],
        help="Fail if this relationship target is missing. Example: /Metadata/plate_1.png",
    )
    parser.add_argument(
        "--expect-different-3mf-entry",
        action="append",
        default=[],
        help="Fail if two ZIP entries are byte-identical. Example: Metadata/plate_1.gcode:Metadata/plate_2.gcode",
    )
    parser.add_argument(
        "--require-distinct-3mf-thumbnail-entries",
        action="store_true",
        help="Fail if the checked 3MF thumbnail entries are all byte-identical.",
    )
    parser.add_argument(
        "--require-3mf-thumbnail-visuals",
        action="store_true",
        help="Fail if checked 3MF PNG thumbnails are blank, undecodable, or visually inconsistent by role.",
    )
    parser.add_argument("--pretty", action="store_true", help="pretty-print JSON output")
    args = parser.parse_args()

    output: dict[str, object] = {}
    if args.mobile_gcode or args.orca_gcode:
        mobile = inspect_gcode(args.mobile_gcode) if args.mobile_gcode else None
        orca = inspect_gcode(args.orca_gcode) if args.orca_gcode else None
        if mobile is not None:
            output["gcode"] = compare_gcode(mobile, orca)
            failures = validate_gcode_expectations(
                metadata=mobile,
                expected_thumbnails=args.expect_thumbnail,
                forbidden_tags=set(args.forbid_thumbnail_tag),
                require_print_time=args.require_print_time,
                require_filament=args.require_filament,
                require_toolchange=args.require_toolchange,
                require_thumbnail_visuals=args.require_gcode_thumbnail_visuals,
                require_thumbnail_antialias=args.require_gcode_thumbnail_antialias,
                require_thumbnail_payload_signatures=args.require_gcode_thumbnail_payload_signatures,
            )
            append_failures(output, failures)
        elif orca is not None:
            output["gcode"] = {"orca": asdict(orca)}
            failures = validate_gcode_expectations(
                metadata=orca,
                expected_thumbnails=args.expect_thumbnail,
                forbidden_tags=set(args.forbid_thumbnail_tag),
                require_print_time=args.require_print_time,
                require_filament=args.require_filament,
                require_toolchange=args.require_toolchange,
                require_thumbnail_visuals=args.require_gcode_thumbnail_visuals,
                require_thumbnail_antialias=args.require_gcode_thumbnail_antialias,
                require_thumbnail_payload_signatures=args.require_gcode_thumbnail_payload_signatures,
            )
            append_failures(output, failures)
    if args.mobile_3mf:
        mobile_3mf = inspect_3mf(args.mobile_3mf)
        output["mobile_3mf"] = mobile_3mf
        append_failures(
            output,
            validate_3mf_expectations(
                metadata=mobile_3mf,
                expected_entries=args.expect_3mf_entry,
                expected_thumbnail_entries=args.expect_3mf_thumbnail_entry,
                expected_relationship_targets=args.expect_3mf_relationship_target,
                expected_bbox_json_entries=args.expect_3mf_bbox_json,
                expected_different_entries=args.expect_different_3mf_entry,
                require_distinct_thumbnail_entries=args.require_distinct_3mf_thumbnail_entries,
                require_thumbnail_visuals=args.require_3mf_thumbnail_visuals,
            ),
        )
    if args.orca_3mf:
        orca_3mf = inspect_3mf(args.orca_3mf)
        output["orca_3mf"] = orca_3mf
        if args.mobile_3mf:
            output["3mf_thumbnail_visual_comparison"] = compare_3mf_thumbnail_visuals(mobile_3mf, orca_3mf)
    if not output:
        parser.error("provide at least one --mobile-gcode, --orca-gcode, --mobile-3mf, or --orca-3mf path")

    json.dump(output, sys.stdout, indent=2 if args.pretty else None, sort_keys=True)
    sys.stdout.write("\n")
    return 1 if output.get("failures") else 0


if __name__ == "__main__":
    raise SystemExit(main())
