#!/usr/bin/env python3
"""
Generates the metadata test fixtures used by :processing tests.

The JPEG fixtures keep the *real* entropy-coded image data of a real photograph
(no_manifest.jpg from c2pa-rs) and only replace/insert metadata segments, so the
tests exercise genuine camera-like files.

Requirements:  pip install pillow pillow-heif piexif c2pa-python
Usage:         python3 scripts/generate_fixtures.py
"""
import io
import json
import os
import struct
import sys
import zlib

import piexif
from PIL import Image
import pillow_heif

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FX = os.path.join(ROOT, "processing", "src", "test", "resources", "fixtures")
SRC = os.path.join(FX, "c2pa-rs")
OUT = os.path.join(FX, "generated")
os.makedirs(OUT, exist_ok=True)
pillow_heif.register_heif_opener()

XMP = """<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:dc="http://purl.org/dc/elements/1.1/"
    xmlns:xmp="http://ns.adobe.com/xap/1.0/"
    xmlns:xmpMM="http://ns.adobe.com/xap/1.0/mm/"
    xmlns:stEvt="http://ns.adobe.com/xap/1.0/sType/ResourceEvent#"
    xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/"
    xmlns:exif="http://ns.adobe.com/exif/1.0/"
    xmlns:Iptc4xmpExt="http://iptc.org/std/Iptc4xmpExt/2008-02-29/"
    xmp:CreatorTool="Adobe Photoshop 25.0"
    xmp:CreateDate="2024-05-01T10:20:30"
    photoshop:City="Sao Paulo"
    photoshop:Country="Brasil"
    exif:GPSLatitude="23,33.0S"
    exif:GPSLongitude="46,38.0W"
    xmpMM:DocumentID="xmp.did:1234-ABCD">
   <dc:creator><rdf:Seq><rdf:li>Maria Fotografa</rdf:li></rdf:Seq></dc:creator>
   <dc:rights><rdf:Alt><rdf:li xml:lang="x-default">(c) Maria Fotografa</rdf:li></rdf:Alt></dc:rights>
   <dc:description><rdf:Alt><rdf:li xml:lang="x-default">Minha casa na rua X</rdf:li></rdf:Alt></dc:description>
   <dc:subject><rdf:Bag><rdf:li>familia</rdf:li><rdf:li>casa</rdf:li></rdf:Bag></dc:subject>
   <xmpMM:History><rdf:Seq><rdf:li stEvt:action="saved" stEvt:softwareAgent="Adobe Photoshop 25.0" stEvt:when="2024-05-01T10:30:00"/></rdf:Seq></xmpMM:History>
   <Iptc4xmpExt:DigitalSourceType>http://cv.iptc.org/newscodes/digitalsourcetype/compositeWithTrainedAlgorithmicMedia</Iptc4xmpExt:DigitalSourceType>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""


def exif_bytes(thumbnail=True, orientation=6, user_comment=b"ASCII\x00\x00\x00Lembranca da viagem"):
    thumb = None
    if thumbnail:
        t = Image.new("RGB", (160, 120), (200, 30, 30))
        b = io.BytesIO(); t.save(b, "JPEG", quality=70); thumb = b.getvalue()
    d = {
        "0th": {
            piexif.ImageIFD.Make: b"CleanCam",
            piexif.ImageIFD.Model: b"CC-1000 Pro",
            piexif.ImageIFD.Orientation: orientation,
            piexif.ImageIFD.Software: b"CleanCam Firmware 1.2.3",
            piexif.ImageIFD.Artist: b"Maria Fotografa",
            piexif.ImageIFD.Copyright: b"(c) 2024 Maria Fotografa",
            piexif.ImageIFD.ImageDescription: b"Casa da familia",
            piexif.ImageIFD.DateTime: b"2024:05:01 10:20:30",
            piexif.ImageIFD.XResolution: (72, 1),
            piexif.ImageIFD.YResolution: (72, 1),
            piexif.ImageIFD.ResolutionUnit: 2,
        },
        "Exif": {
            piexif.ExifIFD.DateTimeOriginal: b"2024:05:01 10:20:30",
            piexif.ExifIFD.DateTimeDigitized: b"2024:05:01 10:20:30",
            piexif.ExifIFD.ISOSpeedRatings: 200,
            piexif.ExifIFD.FNumber: (18, 10),
            piexif.ExifIFD.ExposureTime: (1, 120),
            piexif.ExifIFD.FocalLength: (42, 10),
            piexif.ExifIFD.ColorSpace: 1,
            piexif.ExifIFD.UserComment: user_comment,
            piexif.ExifIFD.BodySerialNumber: b"SN-0099887766",
            piexif.ExifIFD.CameraOwnerName: b"Maria Fotografa",
            piexif.ExifIFD.LensModel: b"CleanLens 4.2mm",
            piexif.ExifIFD.MakerNote: b"CLEANCAM\x00\x01SERIAL=SN-0099887766;OWNER=MARIA",
        },
        "GPS": {
            piexif.GPSIFD.GPSVersionID: (2, 3, 0, 0),
            piexif.GPSIFD.GPSLatitudeRef: b"S",
            piexif.GPSIFD.GPSLatitude: ((23, 1), (33, 1), (1234, 100)),
            piexif.GPSIFD.GPSLongitudeRef: b"W",
            piexif.GPSIFD.GPSLongitude: ((46, 1), (38, 1), (5678, 100)),
            piexif.GPSIFD.GPSAltitudeRef: 0,
            piexif.GPSIFD.GPSAltitude: (760, 1),
            piexif.GPSIFD.GPSDateStamp: b"2024:05:01",
        },
        "1st": {piexif.ImageIFD.XResolution: (72, 1), piexif.ImageIFD.YResolution: (72, 1), piexif.ImageIFD.ResolutionUnit: 2} if thumbnail else {},
        "thumbnail": thumb,
    }
    return piexif.dump(d)  # starts with b"Exif\0\0"


def iptc_iim():
    def ds(rec, num, data):
        data = data.encode("utf-8") if isinstance(data, str) else data
        return b"\x1c" + bytes([rec, num]) + struct.pack(">H", len(data)) + data
    return b"".join([
        ds(1, 90, b"\x1b%G"),
        ds(2, 0, b"\x00\x04"),
        ds(2, 80, "Maria Fotógrafa"),
        ds(2, 116, "(c) 2024 Maria"),
        ds(2, 120, "Festa de aniversário na casa da avó"),
        ds(2, 25, "família"), ds(2, 25, "aniversário"),
        ds(2, 90, "São Paulo"),
        ds(2, 101, "Brasil"),
        ds(2, 118, "maria@example.com"),
        ds(2, 110, "Agência Exemplo"),
    ])


def irb(blocks):
    out = b""
    for rid, data in blocks:
        out += b"8BIM" + struct.pack(">H", rid) + b"\x00\x00" + struct.pack(">I", len(data)) + data
        if len(data) % 2:
            out += b"\x00"
    return out


def segment(marker, payload):
    return b"\xff" + bytes([marker]) + struct.pack(">H", len(payload) + 2) + payload


def jpeg_split(data):
    """Returns (header segments, rest-from-SOS)."""
    assert data[:2] == b"\xff\xd8"
    p, segs = 2, []
    while True:
        marker = data[p + 1]
        if marker == 0xDA:
            return segs, data[p:]
        length = struct.unpack(">H", data[p + 2:p + 4])[0]
        segs.append((marker, data[p + 4:p + 2 + length]))
        p += 2 + length


def build_jpeg(base, extra_segments, keep=lambda m, pl: m not in (0xE1, 0xED, 0xFE)):
    segs, rest = jpeg_split(base)
    kept = [(m, pl) for m, pl in segs if keep(m, pl)]
    # Keep APP0/APP14 first, then metadata, then the rest (DQT/SOF/DHT...).
    app = [s for s in kept if 0xE0 <= s[0] <= 0xEF]
    other = [s for s in kept if not (0xE0 <= s[0] <= 0xEF)]
    out = b"\xff\xd8" + b"".join(segment(m, pl) for m, pl in app[:1])
    out += b"".join(segment(m, pl) for m, pl in extra_segments)
    out += b"".join(segment(m, pl) for m, pl in app[1:] + other)
    return out + rest


def write(name, data):
    with open(os.path.join(OUT, name), "wb") as f:
        f.write(data)
    print("wrote", name, len(data))


def png_chunk(t, data):
    return struct.pack(">I", len(data)) + t + data + struct.pack(">I", zlib.crc32(t + data) & 0xFFFFFFFF)


def png_insert(png, chunks):
    """Inserts chunks right after IHDR."""
    ihdr_end = 8 + 8 + struct.unpack(">I", png[8:12])[0] + 4
    return png[:ihdr_end] + b"".join(png_chunk(t, d) for t, d in chunks) + png[ihdr_end:]


def main():
    base = open(os.path.join(SRC, "no_manifest.jpg"), "rb").read()
    xmp_seg = b"http://ns.adobe.com/xap/1.0/\x00" + XMP.encode("utf-8")
    full = build_jpeg(base, [
        (0xE1, exif_bytes()),
        (0xE1, xmp_seg),
        (0xED, b"Photoshop 3.0\x00" + irb([(0x0404, iptc_iim()), (0x0425, b"\x00" * 16), (0x03ED, b"\x00\x48\x00\x00\x00\x01\x00\x01\x00\x48\x00\x00\x00\x01\x00\x01")])),
        (0xFE, "Foto tirada na casa da Maria".encode("utf-8")),
    ])
    write("photo_full.jpg", full)

    sd = b"ASCII\x00\x00\x00a castle on a hill\nNegative prompt: blurry\nSteps: 30, Sampler: DPM++ 2M, CFG scale: 7, Seed: 12345, Model: sdxl"
    ai = build_jpeg(base, [(0xE1, exif_bytes(thumbnail=False, user_comment=sd))])
    write("ai_parameters.jpg", ai)

    # Truncated / corrupted variants.
    write("truncated.jpg", full[: len(full) // 3])
    segs, _ = jpeg_split(full)
    write("no_sos.jpg", b"\xff\xd8" + b"".join(segment(m, pl) for m, pl in segs) + b"\xff\xd9")
    write("random.bin", os.urandom(4096))

    # PNG with transparency and every kind of text chunk.
    rgba = Image.open(os.path.join(SRC, "sample1.png")).convert("RGBA").resize((512, 342))
    px = rgba.load()
    for y in range(342):
        for x in range(0, 120):
            r, g, b, _ = px[x, y]
            px[x, y] = (r, g, b, int(255 * x / 120))
    buf = io.BytesIO(); rgba.save(buf, "PNG", icc_profile=None); png = buf.getvalue()
    itxt_xmp = b"XML:com.adobe.xmp\x00\x00\x00\x00\x00" + XMP.encode("utf-8")
    ztxt = b"Description\x00\x00" + zlib.compress("Piscina da casa de praia".encode("latin-1"))
    png = png_insert(png, [
        (b"eXIf", exif_bytes(thumbnail=False)[6:]),
        (b"tEXt", b"Author\x00Maria Fotografa"),
        (b"tEXt", b"Software\x00GIMP 2.10"),
        (b"tEXt", b"Comment\x00Feito no celular da Maria"),
        (b"zTXt", ztxt),
        (b"iTXt", itxt_xmp),
        (b"tEXt", b"parameters\x00a cat astronaut\nSteps: 20, Sampler: Euler a, CFG scale: 7, Seed: 1"),
        (b"tIME", struct.pack(">HBBBBB", 2024, 5, 1, 10, 20, 30)),
        (b"sRGB", b"\x00"),
    ])
    write("alpha_meta.png", png)
    bad = bytearray(png); bad[60] ^= 0xFF
    write("bad_crc.png", bytes(bad))

    # WebP lossless with alpha + EXIF + XMP, and an animated WebP with EXIF.
    buf = io.BytesIO(); rgba.save(buf, "WEBP", lossless=True, exif=exif_bytes(thumbnail=False), xmp=XMP.encode("utf-8")); write("alpha_meta.webp", buf.getvalue())
    frames = [rgba.copy().rotate(a) for a in (0, 10, 20)]
    buf = io.BytesIO(); frames[0].save(buf, "WEBP", save_all=True, append_images=frames[1:], duration=100, loop=0, exif=exif_bytes(thumbnail=False)); write("animated_meta.webp", buf.getvalue())
    buf = io.BytesIO(); rgba.convert("RGB").save(buf, "WEBP", quality=90, exif=exif_bytes(thumbnail=False)); write("lossy_meta.webp", buf.getvalue())

    # HEIC and AVIF with EXIF (GPS) and XMP.
    rgb = rgba.convert("RGB")
    heif = pillow_heif.from_pillow(rgb)
    buf = io.BytesIO(); heif.save(buf, format="HEIF", exif=exif_bytes(thumbnail=False), xmp=XMP.encode("utf-8"), quality=90); write("meta.heic", buf.getvalue())
    buf = io.BytesIO(); rgba.save(buf, "AVIF", exif=exif_bytes(thumbnail=False), xmp=XMP.encode("utf-8"), quality=80); write("alpha_meta.avif", buf.getvalue())

    sign_with_c2pa()


def sign_with_c2pa():
    """Signs copies of the fixtures with the official c2pa-python SDK and its public test certificate."""
    try:
        import c2pa
    except ImportError:
        print("c2pa-python not installed: skipping signed fixtures", file=sys.stderr)
        return
    certs = os.path.join(ROOT, "scripts", "c2pa-test-certs")
    cert = open(os.path.join(certs, "es256.pub"), "rb").read()
    key = open(os.path.join(certs, "es256.pem"), "rb").read()
    info = c2pa.C2paSignerInfo(alg=b"es256", sign_cert=cert, private_key=key, ta_url=None)
    signer = c2pa.Signer.from_info(info)
    manifest = {
        "claim_generator_info": [{"name": "CleanShot fixture generator", "version": "1.0"}],
        "title": "fixture",
        "assertions": [{
            "label": "c2pa.actions",
            "data": {"actions": [{
                "action": "c2pa.created",
                "digitalSourceType": "http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia",
                "softwareAgent": {"name": "Example Generative Model"},
            }]},
        }],
    }
    for src, mime, dst in [
        ("photo_full.jpg", "image/jpeg", "signed.jpg"),
        ("alpha_meta.png", "image/png", "signed.png"),
        ("alpha_meta.webp", "image/webp", "signed.webp"),
        ("meta.heic", "image/heic", "signed.heic"),
        ("alpha_meta.avif", "image/avif", "signed.avif"),
    ]:
        try:
            builder = c2pa.Builder(json.dumps(manifest))
            with open(os.path.join(OUT, src), "rb") as s, open(os.path.join(OUT, dst), "w+b") as d:
                builder.sign(signer, mime, s, d)
            print("signed", dst)
        except Exception as e:  # format not supported by the SDK build
            print("could not sign", dst, e, file=sys.stderr)


if __name__ == "__main__":
    main()
