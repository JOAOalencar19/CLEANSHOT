#!/usr/bin/env python3
"""
Independent cross-check of CleanShot outputs.

1. Decodes each original and cleaned file with Pillow / libheif (decoders unrelated to
   CleanShot) and compares the decoded pixels byte for byte (all frames for animations).
2. Runs the official c2pa-python SDK on cleaned signed files: the manifest must still be
   readable; the only acceptable new errors are hash-binding mismatches caused by the edit.

Usage:
  ./gradlew -p jvm-build test -PdumpDir=/tmp/cleanshot-out
  python3 scripts/verify_outputs.py /tmp/cleanshot-out
"""
import glob
import json
import os
import sys

from PIL import Image, ImageSequence
import pillow_heif

pillow_heif.register_heif_opener()


def frames(path):
    with Image.open(path) as im:
        return [(f.mode, f.size, f.tobytes()) for f in ImageSequence.Iterator(im)]


def main(d):
    failures = 0
    for cleaned in sorted(glob.glob(os.path.join(d, "cleaned_*"))):
        original = os.path.join(d, os.path.basename(cleaned)[len("cleaned_"):])
        same = frames(original) == frames(cleaned)
        print(("OK  " if same else "FAIL"), "pixels", os.path.basename(original))
        failures += 0 if same else 1
    try:
        import c2pa
    except ImportError:
        print("c2pa-python not installed; skipping manifest check")
        return failures
    for cleaned in sorted(glob.glob(os.path.join(d, "cleaned_signed.*"))):
        j = json.loads(c2pa.Reader(cleaned).json())
        codes = [v["code"] for v in j.get("validation_status") or []]
        unexpected = [c for c in codes if c not in ("signingCredential.untrusted",) and "Hash.mismatch" not in c and "hash.mismatch" not in c.lower()]
        ok = j.get("active_manifest") is not None and not unexpected
        print(("OK  " if ok else "FAIL"), "c2pa", os.path.basename(cleaned), j.get("validation_state"), codes)
        failures += 0 if ok else 1
    return failures


if __name__ == "__main__":
    sys.exit(1 if main(sys.argv[1]) else 0)
