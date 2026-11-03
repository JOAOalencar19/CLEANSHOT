# Arquitetura

Clean Architecture + MVVM, com o motor de imagens isolado em módulos sem dependência de Android.

```
app/                 Application (Hilt, WorkManager), MainActivity, manifesto, ícones
feature/             Telas Compose + ViewModels (home, analysis, cleaner, report, provenance,
                     export, batch, history, settings) e navegação
core/designsystem/   Design System CleanShot: cores, tipografia, espaçamento, movimento,
                     componentes de vidro, botões, indicadores, logotipo
core/common/         Dispatchers (Hilt) e formatadores
data/                Room (histórico), DataStore (configurações), workspace privado, importação
                     (Photo Picker/SAF/ExifInterface), ExportManager, fila WorkManager, DI
domain/  (JVM)       Modelos, contratos (interfaces), casos de uso, sessão em memória
processing/ (JVM)    Motor: formatos, EXIF/IPTC/XMP, C2PA, limpeza, verificação de preservação
jvm-build/           Build Gradle autônomo de domain + processing (sem Android SDK)
```

## Componentes pedidos → implementação

| Componente | Classe |
|---|---|
| MetadataAnalyzer | `processing/analyzer/DefaultMetadataAnalyzer` |
| ExifProcessor | `processing/exif/ExifProcessor` (+ `TiffParser`/`TiffWriter`) |
| IptcProcessor | `processing/iptc/IptcProcessor` (IIM + Photoshop IRB) |
| XmpProcessor | `processing/xmp/XmpProcessor` (DOM com namespaces) |
| C2paInspector | `processing/c2pa/DefaultC2paInspector` (JUMBF, CBOR, COSE) |
| ImageFormatDetector | `processing/format/DefaultImageFormatDetector` |
| ImagePreservationEngine | `processing/preservation/DefaultImagePreservationEngine` |
| PrivacyCleaner | `processing/cleaner/DefaultPrivacyCleaner` |
| ExportManager | `data/export/AndroidExportManager` |
| ProcessingQueue | `data/batch/WorkManagerProcessingQueue` + `BatchCleaningWorker` (`processing/batch/BatchCleaner` na JVM) |
| HistoryRepository | `data/history/RoomHistoryRepository` |

Todas as regras de negócio dependem apenas das interfaces de `domain/engine` e
`domain/repository`; trocar uma biblioteca (ex.: usar o SDK C2PA oficial) significa fornecer outra
implementação em `data/di/DataModule.kt`.

## Fluxo de uma limpeza

1. O usuário escolhe a foto (Photo Picker / SAF). O app lê o URI **somente para leitura**.
2. `AnalyzeImageUseCase` copia o arquivo para `noBackupFilesDir/work/<uuid>/` e analisa.
3. `PrivacyCleaner.plan` gera o plano mostrado na confirmação (remover / manter / protegido / não suportado).
4. `CleanImageUseCase` gera a cópia em memória, executa o `ImagePreservationEngine`, relê o
   resultado, confirma com o decodificador do Android (`AndroidDecodeVerifier`) e só então grava.
   Qualquer falha interrompe a operação sem gerar arquivo.
5. Exportação via `ACTION_CREATE_DOCUMENT` ou compartilhamento por `FileProvider` (somente a pasta `cache/shared`).
6. Cópias privadas são apagadas ao sair do fluxo; restos antigos são removidos na inicialização.

## Histórico

`HistoryEntity` guarda apenas nome do arquivo, data, operação, formatos, tamanhos, resultado e
número de campos removidos. Nenhuma imagem, URI, caminho, coordenada ou valor de metadado.
