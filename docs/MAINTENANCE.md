# Manutenção

## Dependências
Versões centralizadas em `gradle/libs.versions.toml`. Ao atualizar Kotlin, alinhar KSP
(`<kotlin>-<ksp>`) e Hilt (precisa reconhecer a versão de metadados do Kotlin).

## Testes
- `./gradlew -p jvm-build test` — motor e casos de uso (rápido, sem Android SDK).
- `./gradlew connectedDebugAndroidTest` — Room, exportação, limpeza no runtime Android, UI.
- `scripts/generate_fixtures.py` regenera as fixtures (requer Pillow, pillow-heif, piexif e
  c2pa-python). As fixtures de `c2pa-rs` vêm do projeto oficial (MIT/Apache-2.0).
- `scripts/verify_outputs.py` faz a verificação cruzada externa dos resultados.

## Adicionar um campo/tag
- EXIF: `processing/exif/ExifCatalog.kt` (nome, descrição, categoria, privacidade, grupo).
- IPTC: `IptcProcessor.CATALOG`. XMP: `XmpCatalog.PROPERTIES`. Texto PNG: `PngTextCatalog`.
- Ferramentas/termos de IA protegidos: `processing/ai/AiDeclarationDetector.kt`.

## Adicionar suporte de escrita a um formato
1. Parser/escritor de contêiner em `processing/container`.
2. Estratégia em `DefaultPrivacyCleaner` (sem recodificação).
3. Verificação correspondente em `DefaultImagePreservationEngine`.
4. Atualizar `ImageFormat.cleaningSupport` e adicionar fixtures/testes.

## CI
`.github/workflows/android.yml`: testes JVM → `assembleDebug`/`assembleDebugAndroidTest` →
artefato do APK → testes instrumentados em emulador.
