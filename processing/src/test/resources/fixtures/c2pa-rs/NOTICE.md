# Test fixtures from c2pa-rs

Files in this folder were copied unmodified from
https://github.com/contentauth/c2pa-rs/tree/main/sdk/tests/fixtures
(dual-licensed MIT / Apache-2.0, © Adobe and C2PA contributors).

They are used only by automated tests. Reference results were produced with the official
`c2pa-python` 0.38.1 SDK (`c2pa.Reader(...).json()` → `validation_state` / `validation_status`):

| File | Official SDK result |
|---|---|
| C.jpg | Valid (signingCredential.untrusted) |
| CA.jpg | Valid (signingCredential.untrusted) |
| boxhash.jpg | Valid (signingCredential.untrusted) |
| E-sig-CA.jpg | Invalid (claimSignature.mismatch) |
| XCA.jpg | Invalid (assertion.dataHash.mismatch) |
| cloud.jpg | Remote manifest (not embedded) |
| sample1.png / .webp / .heic / .avif, no_manifest.jpg, libpng-test.png | No manifest |
