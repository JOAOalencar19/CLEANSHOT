# CleanShot

**Gerenciador de metadados e privacidade de imagens para Android — 100% local.**

CleanShot inspeciona fotografias em profundidade (EXIF, GPS, IPTC, XMP, ICC, texto PNG, C2PA),
mostra o que cada campo revela, remove metadados pessoais **sem recodificar os pixels** e analisa
credenciais de procedência / declarações de IA — que **nunca** são removidas ou alteradas.

- Sem permissão de internet (o manifesto remove `INTERNET` mesmo que uma biblioteca a declare).
- Sem anúncios, sem rastreamento, sem permissões amplas de armazenamento.
- O original nunca é alterado: o app trabalha em cópias privadas apagadas após o uso.

## Instalação do APK de depuração

1. Abra a aba **Actions** do repositório → execução mais recente de **Android CI** → artefato
   **`cleanshot-debug-apk`**.
2. Descompacte e instale: `adb install -r app-debug.apk` (ou abra o arquivo no aparelho, permitindo
   "instalar apps desconhecidos").
3. Requisitos: Android 8.0 (API 26) ou superior. HEIF exige Android 9+ para pré-visualização;
   AVIF, Android 12+.

## Compilar localmente

Requisitos: JDK 17+, Android SDK (plataforma 35) — Android Studio Narwhal ou mais recente.

```bash
./gradlew assembleDebug              # APK em app/build/outputs/apk/debug/
./gradlew test                       # testes JVM e unitários
./gradlew connectedDebugAndroidTest  # testes instrumentados (aparelho/emulador)
```

O motor de processamento é Kotlin/JVM puro e pode ser testado **sem Android SDK**:

```bash
./gradlew -p jvm-build test
```

Verificação cruzada independente (decodificadores Pillow/libheif e SDK oficial C2PA):

```bash
pip install pillow pillow-heif c2pa-python
./gradlew -p jvm-build test --rerun -PdumpDir=/tmp/cleanshot-out
python3 scripts/verify_outputs.py /tmp/cleanshot-out
```

## Documentação

- [Arquitetura](docs/ARCHITECTURE.md)
- [Preservação de qualidade e formatos](docs/QUALITY.md)
- [C2PA e procedência](docs/PROVENANCE.md)
- [Limitações conhecidas](docs/LIMITATIONS.md)
- [Manutenção](docs/MAINTENANCE.md)
