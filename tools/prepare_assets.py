"""Materialize byte-identical upstream assets before building Noor."""
import hashlib
import json
from pathlib import Path
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "docs/upstream/IMPORT_MANIFEST.json").read_text())
ASSETS = {
    "app/src/main/assets/quran/uthmani-v1.1.txt",
    "app/src/main/res/font/amiri_quran.ttf",
}
base = (
    "https://raw.githubusercontent.com/nexvary/NEXVARY-HifzAI/"
    + MANIFEST["source_commit"]
)

for entry in MANIFEST["files"]:
    if entry["destination_path"] not in ASSETS:
        continue
    destination = ROOT / entry["destination_path"]
    expected = entry["sha256"]
    if destination.exists():
        if hashlib.sha256(destination.read_bytes()).hexdigest() != expected:
            raise SystemExit(f"Unexpected bytes in {destination}")
        continue
    with urlopen(f"{base}/{entry['source_path']}", timeout=60) as response:
        data = response.read(2_000_001)
    if hashlib.sha256(data).hexdigest() != expected:
        raise SystemExit(f"Upstream digest mismatch for {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    pending = destination.with_suffix(destination.suffix + ".pending")
    pending.write_bytes(data)
    pending.replace(destination)
