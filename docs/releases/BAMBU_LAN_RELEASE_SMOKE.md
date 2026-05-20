# Bambu LAN Release Smoke

Run this gate before a public beta claims Bambu LAN sending is ready for broad
testing. The evidence file must record real-printer results. Do not mark a case
as passed from mocks, screenshots alone, or code review.

## Required Cases

- `connect`: app connects to the printer using host, access code, and serial.
- `upload_only`: app uploads a G-code file without starting the print.
- `upload_and_start`: app uploads a G-code file and starts the print after user
  confirmation.
- `wrong_access_code`: app rejects a known-bad access code with a clear error.
- `offline_printer`: app handles an unreachable printer without crashing or
  claiming success.

## Evidence Format

```json
{
  "completed_at": "2026-05-20T00:00:00Z",
  "operator": "github-user-or-release-owner",
  "printer": {
    "model": "Bambu Lab printer model",
    "firmware": "firmware version",
    "serial_hash": "sha256-of-serial-or-device-id"
  },
  "app": {
    "versionName": "0.1.4-beta",
    "versionCode": "7",
    "apk_sha256": "release-apk-sha256"
  },
  "cases": {
    "connect": {
      "status": "pass",
      "notes": "Connected from Android device on same LAN."
    },
    "upload_only": {
      "status": "pass",
      "notes": "Uploaded file without starting a print."
    },
    "upload_and_start": {
      "status": "pass",
      "notes": "Started a small verified test print."
    },
    "wrong_access_code": {
      "status": "pass",
      "notes": "Rejected invalid access code."
    },
    "offline_printer": {
      "status": "pass",
      "notes": "Reported printer unreachable without crash."
    }
  }
}
```

Verify the evidence with:

```bash
scripts/bambu_lan_release_smoke.sh path/to/bambu-lan-smoke-evidence.json
```

The Android release gate can run the same check when
`MOBILE_SLICER_BAMBU_SMOKE_EVIDENCE` points to the evidence file.
