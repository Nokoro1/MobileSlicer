#!/usr/bin/env python3
"""Regression tests for preview layer counting and range packing.

These tests mirror the native counter in engine-wrapper/orca_wrapper_preview_input.inc
closely enough to lock down the single-pass marker handling behavior. They are
kept in Python because the project does not currently have a host C++ test
target for the Android wrapper.
"""

from __future__ import annotations

import math
import re
import unittest
from dataclasses import dataclass, field


NUMBER_RE = re.compile(r"([XYZEAIJ])\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)", re.IGNORECASE)


def trim(value: str) -> str:
    return value.strip()


def command_matches(command: str, expected: str) -> bool:
    command = trim(command)
    return command.upper() == expected.upper() or command.upper().startswith(expected.upper() + " ")


def role_from_marker(marker: str) -> str:
    marker = trim(marker).lower()
    if marker in {"skirt", "brim", "inner wall", "internal perimeter", "outer wall", "external perimeter",
                  "overhang wall", "overhang perimeter", "sparse infill", "internal infill",
                  "internal solid infill", "solid infill", "top surface", "top solid infill",
                  "bottom surface", "bridge", "internal bridge", "gap infill", "gap fill",
                  "iron", "ironing", "prime tower", "wipe tower", "custom"}:
        return marker
    if "support interface" in marker:
        return "support interface"
    if "support" in marker:
        return "support"
    return "none"


@dataclass
class MoveWords:
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    e: float = 0.0
    i: float = 0.0
    j: float = 0.0
    has_x: bool = False
    has_y: bool = False
    has_z: bool = False
    has_e: bool = False
    has_i: bool = False
    has_j: bool = False


def parse_move_words(command: str) -> MoveWords | None:
    words = MoveWords()
    parsed = False
    for match in NUMBER_RE.finditer(command):
        letter = match.group(1).upper()
        value = float(match.group(2))
        if letter == "X":
            words.x, words.has_x = value, True
        elif letter == "Y":
            words.y, words.has_y = value, True
        elif letter == "Z":
            words.z, words.has_z = value, True
        elif letter == "E":
            words.e, words.has_e = value, True
        elif letter == "I":
            words.i, words.has_i = value, True
        elif letter == "J":
            words.j, words.has_j = value, True
        parsed = True
    return words if parsed else None


def estimate_arc_distance(clockwise: bool, words: MoveWords, px: float, py: float, pz: float, x: float, y: float, z: float) -> float:
    if not words.has_i and not words.has_j:
        return math.dist((px, py, pz), (x, y, z))
    center_x = px + (words.i if words.has_i else 0.0)
    center_y = py + (words.j if words.has_j else 0.0)
    radius = math.hypot(px - center_x, py - center_y)
    if radius <= 0.0:
        return 0.0
    start_angle = math.atan2(py - center_y, px - center_x)
    end_angle = math.atan2(y - center_y, x - center_x)
    sweep = start_angle - end_angle if clockwise else end_angle - start_angle
    while sweep <= 0.0:
        sweep += 2.0 * math.pi
    planar_distance = radius * sweep
    return math.hypot(planar_distance, z - pz)


@dataclass
class PreviewCounter:
    empty: bool = True
    previous_type: str = "noop"
    previous_role: str = "none"
    previous_layer: int = 0
    layer_vertices: list[int] = field(default_factory=list)

    def add(self, layer_id: int) -> None:
        while len(self.layer_vertices) <= layer_id:
            self.layer_vertices.append(0)
        self.layer_vertices[layer_id] += 1

    def emit(self, role: str, move_type: str, layer_id: int, force_path_break: bool) -> None:
        path_break = (
            force_path_break
            or self.empty
            or self.previous_type != move_type
            or self.previous_role != role
            or self.previous_layer != layer_id
        )
        if path_break:
            if not self.empty:
                self.add(self.previous_layer)
            self.add(layer_id)
        self.add(layer_id)
        self.empty = False
        self.previous_type = move_type
        self.previous_role = role
        self.previous_layer = layer_id


@dataclass
class LayerCountParser:
    layer_markers_available: bool
    counter: PreviewCounter = field(default_factory=PreviewCounter)
    x: float = 0.0
    y: float = 0.0
    z: float = 0.0
    e: float = 0.0
    absolute_xyz: bool = True
    absolute_e: bool = True
    layer_id: int = 0
    role: str = "custom"
    last_extrusion_z: float = 0.0
    pending_layer_change: bool = False
    saw_layer_change_marker: bool = False
    first_marked_layer_started: bool = False
    extrusion_path_open: bool = False
    have_extrusion_z: bool = False

    def __post_init__(self) -> None:
        self.print_toolpath_started = not self.layer_markers_available
        self.preview_position_synced = not self.layer_markers_available

    def process_line(self, raw_line: str) -> bool:
        command_raw, separator, comment_raw = raw_line.partition(";")
        command = trim(command_raw)
        comment = trim(comment_raw) if separator else ""
        line_has_marker = False

        if comment.startswith("TYPE:"):
            self.role = role_from_marker(comment[5:])
            self.print_toolpath_started = True
            self.preview_position_synced = False
        elif comment.startswith("FEATURE:"):
            self.role = role_from_marker(comment[8:])
            self.print_toolpath_started = True
            self.preview_position_synced = False
        elif comment in {"LAYER_CHANGE", "CHANGE_LAYER"}:
            line_has_marker = True
            self.saw_layer_change_marker = True
            self.pending_layer_change = True
            self.print_toolpath_started = True
            self.preview_position_synced = False

        if not command or command_matches(command, "G130"):
            return line_has_marker
        if command_matches(command, "G90"):
            self.absolute_xyz = True
            return line_has_marker
        if command_matches(command, "G91"):
            self.absolute_xyz = False
            return line_has_marker
        if command_matches(command, "M82"):
            self.absolute_e = True
            return line_has_marker
        if command_matches(command, "M83"):
            self.absolute_e = False
            return line_has_marker

        is_arc = command_matches(command, "G2") or command_matches(command, "G3")
        is_move = command_matches(command, "G0") or command_matches(command, "G1") or is_arc
        words = parse_move_words(command)
        if not is_move:
            if command_matches(command, "G92") and words is not None:
                if words.has_x:
                    self.x = words.x
                if words.has_y:
                    self.y = words.y
                if words.has_z:
                    self.z = words.z
                if words.has_e:
                    self.e = words.e
            return line_has_marker
        if words is None:
            return line_has_marker

        px, py, pz, pe = self.x, self.y, self.z, self.e
        if words.has_x:
            self.x = words.x if self.absolute_xyz else self.x + words.x
        if words.has_y:
            self.y = words.y if self.absolute_xyz else self.y + words.y
        if words.has_z:
            self.z = words.z if self.absolute_xyz else self.z + words.z
        if words.has_e:
            self.e = words.e if self.absolute_e else self.e + words.e

        de = self.e - pe
        distance = (
            estimate_arc_distance(command_matches(command, "G2"), words, px, py, pz, self.x, self.y, self.z)
            if is_arc
            else math.dist((px, py, pz), (self.x, self.y, self.z))
        )
        if not (de > 0.0 and distance > 0.0):
            self.extrusion_path_open = False
            if self.print_toolpath_started:
                self.preview_position_synced = True
            return line_has_marker
        if not self.print_toolpath_started or self.role == "none":
            self.extrusion_path_open = False
            return line_has_marker
        if not self.preview_position_synced:
            self.preview_position_synced = True
            self.extrusion_path_open = False
            return line_has_marker

        if self.layer_markers_available:
            if self.pending_layer_change:
                if self.first_marked_layer_started:
                    self.layer_id += 1
                else:
                    self.first_marked_layer_started = True
            elif self.saw_layer_change_marker and not self.first_marked_layer_started:
                self.first_marked_layer_started = True
        elif self.have_extrusion_z and self.z > self.last_extrusion_z + 0.0001:
            self.layer_id += 1
        self.pending_layer_change = False
        self.last_extrusion_z = self.z
        self.have_extrusion_z = True

        mm3_per_mm = de * (math.pi * 0.875 * 0.875) / distance
        if self.role == "wipe tower" and distance > 25.0 and mm3_per_mm < 0.005:
            self.extrusion_path_open = False
            self.preview_position_synced = True
            return line_has_marker

        segment_count = max(4, min(96, math.ceil(distance / 0.6))) if is_arc else 1
        for segment in range(1, segment_count + 1):
            self.counter.emit(self.role, "extrude", self.layer_id, (not self.extrusion_path_open) and segment == 1)
        self.extrusion_path_open = True
        return line_has_marker


@dataclass(frozen=True)
class ProcessorMove:
    move_type: str
    role: str = "none"
    mm3_per_mm: float = 0.0
    layer_id: int = 0


WRAPPER_PATH_START_MOVE_TYPES = {"noop", "extrude", "travel", "wipe"}
PRESERVED_PATH_START_MOVE_TYPES = {"noop", "travel", "wipe", "extrude"}


def _add_processor_vertex(layer_vertices: list[int], layer_id: int) -> None:
    while len(layer_vertices) <= layer_id:
        layer_vertices.append(0)
    layer_vertices[layer_id] += 1


def wrapper_processor_counts(moves: list[ProcessorMove]) -> list[int]:
    """Mirror count_preview_vertices_by_layer_from_processor_result()."""
    if len(moves) < 2:
        return []
    layer_vertices: list[int] = []
    for index in range(1, len(moves)):
        current = moves[index]
        previous = moves[index - 1]
        if current.move_type == "count":
            continue
        if current.move_type in WRAPPER_PATH_START_MOVE_TYPES:
            if (
                not layer_vertices
                or previous.move_type != current.move_type
                or previous.role != current.role
                or previous.mm3_per_mm != current.mm3_per_mm
            ):
                _add_processor_vertex(layer_vertices, current.layer_id)
        _add_processor_vertex(layer_vertices, current.layer_id)
    return layer_vertices


def preserved_processor_counts(moves: list[ProcessorMove]) -> list[int]:
    """Mirror mobile_preview_layer_vertex_counts() before moves are released."""
    if len(moves) < 2:
        return []
    layer_vertices: list[int] = []
    for index in range(1, len(moves)):
        current = moves[index]
        if current.move_type == "count":
            continue
        previous = moves[index - 1]
        if (
            current.move_type in PRESERVED_PATH_START_MOVE_TYPES
            and (
                not layer_vertices
                or previous.move_type != current.move_type
                or previous.role != current.role
                or previous.mm3_per_mm != current.mm3_per_mm
            )
        ):
            _add_processor_vertex(layer_vertices, current.layer_id)
        _add_processor_vertex(layer_vertices, current.layer_id)
    return layer_vertices


def two_pass_counts(gcode: str) -> list[int]:
    markers_available = "LAYER_CHANGE" in gcode or "CHANGE_LAYER" in gcode
    parser = LayerCountParser(markers_available)
    for line in gcode.splitlines():
        parser.process_line(line)
    return parser.counter.layer_vertices


def single_pass_counts(gcode: str) -> list[int]:
    no_marker_parser = LayerCountParser(False)
    marker_parser = LayerCountParser(True)
    saw_marker = False
    for line in gcode.splitlines():
        if saw_marker:
            marker_parser.process_line(line)
        else:
            marker_seen_by_no_marker_parser = no_marker_parser.process_line(line)
            marker_seen_by_marker_parser = marker_parser.process_line(line)
            saw_marker = marker_seen_by_no_marker_parser or marker_seen_by_marker_parser
    return marker_parser.counter.layer_vertices if saw_marker else no_marker_parser.counter.layer_vertices


def pack_ranges(counts: list[int], min_layer: int, max_layer: int, budget: int) -> str:
    capped_max = min(max_layer, len(counts) - 1)
    if capped_max < min_layer:
        return ""
    chunks: list[str] = []
    start = min_layer
    while start <= capped_max:
        if counts[start] >= budget:
            return ""
        total = 0
        end = start
        while end <= capped_max:
            if end > start and total + counts[end] >= budget:
                break
            total += counts[end]
            end += 1
        inclusive_end = max(start, end - 1)
        chunks.append(f"{start}-{inclusive_end}")
        start = inclusive_end + 1
    return ";".join(chunks)


class GcodePreviewLayerCounterTest(unittest.TestCase):
    def assert_single_pass_matches_two_pass(self, gcode: str) -> list[int]:
        expected = two_pass_counts(gcode)
        actual = single_pass_counts(gcode)
        self.assertEqual(expected, actual)
        return actual

    def test_marker_gcode_keeps_marker_layer_boundaries(self) -> None:
        gcode = """
        ;TYPE:Inner wall
        ;LAYER_CHANGE
        G1 X0 Y0 Z0.2 F1200
        G1 X10 Y0 E1
        G1 X20 Y0 E2
        ;LAYER_CHANGE
        G1 X20 Y0 Z0.4
        G1 X20 Y10 E3
        G1 X20 Y20 E4
        """
        counts = self.assert_single_pass_matches_two_pass(gcode)
        self.assertEqual([4, 3], counts)

    def test_non_marker_gcode_uses_z_height_layer_boundaries(self) -> None:
        gcode = """
        ;TYPE:Inner wall
        G1 X0 Y0 Z0.2 F1200
        G1 X10 Y0 E1
        G1 X10 Y10 E2
        G1 X10 Y10 Z0.4
        G1 X0 Y10 E3
        G1 X0 Y0 E4
        """
        counts = self.assert_single_pass_matches_two_pass(gcode)
        self.assertEqual([4, 3], counts)

    def test_motion_modes_g92_ignored_command_and_arcs_are_stable(self) -> None:
        gcode = """
        ;TYPE:Inner wall
        ;LAYER_CHANGE
        G90
        M82
        G1 X0 Y0 Z0.2 F1200
        G1 X10 Y0 E1
        G130 X200 Y200 E99
        G92 E0
        M83
        G91
        G1 X0 Y10 E1
        G90
        G2 X0 Y0 I-5 J0 E2
        ;LAYER_CHANGE
        G1 Z0.4
        G1 X5 Y0 E3
        """
        counts = self.assert_single_pass_matches_two_pass(gcode)
        self.assertEqual([21, 2], counts)

    def test_range_packing_stays_stable_for_large_counts(self) -> None:
        counts = [100, 200, 300, 250, 250, 500]
        self.assertEqual("0-1;2-3;4-4;5-5", pack_ranges(counts, 0, 5, 600))

    def test_preserved_processor_counts_match_wrapper_processor_counts(self) -> None:
        moves = [
            ProcessorMove("noop", layer_id=0),
            ProcessorMove("extrude", "perimeter", 0.04, 0),
            ProcessorMove("extrude", "perimeter", 0.04, 0),
            ProcessorMove("extrude", "infill", 0.04, 0),
            ProcessorMove("travel", "none", 0.0, 0),
            ProcessorMove("travel", "none", 0.0, 1),
            ProcessorMove("wipe", "none", 0.0, 1),
            ProcessorMove("retract", "none", 0.0, 1),
            ProcessorMove("unretract", "none", 0.0, 1),
            ProcessorMove("count", "none", 0.0, 99),
            ProcessorMove("extrude", "infill", 0.08, 2),
        ]

        expected = wrapper_processor_counts(moves)
        self.assertEqual([7, 5, 2], expected)
        self.assertEqual(expected, preserved_processor_counts(moves))

    def test_preserved_processor_counts_keep_same_parameter_layer_transition(self) -> None:
        moves = [
            ProcessorMove("noop", layer_id=0),
            ProcessorMove("extrude", "perimeter", 0.04, 0),
            ProcessorMove("extrude", "perimeter", 0.04, 1),
        ]

        expected = wrapper_processor_counts(moves)
        self.assertEqual([2, 1], expected)
        self.assertEqual(expected, preserved_processor_counts(moves))

    def test_preserved_processor_counts_ignore_too_few_moves(self) -> None:
        self.assertEqual([], preserved_processor_counts([]))
        self.assertEqual([], preserved_processor_counts([ProcessorMove("noop", layer_id=0)]))


if __name__ == "__main__":
    raise SystemExit(unittest.main())
