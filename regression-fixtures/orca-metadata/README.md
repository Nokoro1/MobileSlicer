# Orca Metadata Golden Fixtures

This directory is the parity contract for export metadata. The manifest lists
the MobileSlicer and desktop Orca outputs that must match for thumbnails,
print-time metadata, filament metadata, and sliced 3MF package metadata.

The fixture gate has two modes:

- Non-strict mode validates the manifest and any outputs that are present. This
  is useful while developing new fixture cases.
- Strict mode fails when a declared `references/` output is missing, a declared
  MobileSlicer output is missing, or a case remains marked `pending`. This is
  the mode used by the normal local/release gate.

Commands:

```sh
python3 scripts/orca_metadata_fixture_gate.py --pretty
python3 scripts/orca_metadata_fixture_gate.py --strict-references --pretty
python3 scripts/orca_gcode_metadata_parity_audit.py --pretty
```

`orca_metadata_fixture_gate.py` is the structural gate. It checks required
thumbnail blocks, package entries, relationship targets, print-time presence,
filament metadata presence, and sliced 3MF JSON shape.

`orca_gcode_metadata_parity_audit.py` is the visible-value audit. It extracts
print-time seconds, filament usage/cost numbers, layer count, generator markers,
toolchange commands, thumbnail signatures, and stripped body hashes from each
committed G-code fixture. When both `mobile/` and `references/` outputs exist it
reports desktop-Orca-vs-MobileSlicer drift. Desktop references are generated
from `mobile-baseline-config.json` through
`scripts/generate_orca_reference_fixtures.sh`, which writes the translated Orca
profile inputs and hashes under `reference-inputs/`.

Orca's official CLI does not emit G-code thumbnail blocks because that path uses
a null thumbnail callback. The visible-value audit reports that as
`reference_unavailable`, not real metadata drift. Thumbnail blocks are still
gated from MobileSlicer outputs and the Orca source contract.

Expected layout:

```text
mobile/       MobileSlicer outputs produced by local automation or checked fixtures.
references/  Desktop OrcaSlicer golden outputs for the same cases.
reference-inputs/  Generated desktop Orca profile bundles and manifests.
```

Do not edit exported G-code or `.gcode.3mf` files by hand. Regenerate them from
the declared model/profile case, then rerun the fixture gate:

```sh
scripts/generate_orca_reference_fixtures.sh
```
