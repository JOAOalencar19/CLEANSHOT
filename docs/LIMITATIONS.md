# Limitações conhecidas

- **Formatos somente leitura**: GIF, TIFF, DNG e BMP são analisados (via metadata-extractor), mas
  nunca modificados.
- **XMP estendido (JPEG)** e perfis brutos do ImageMagick em PNG (`Raw profile type ...`) são
  exibidos e preservados sem alteração; o plano os lista como "não suportados".
- **Imagens secundárias** (MPF/gain map HDR, Motion Photo) são preservadas intactas; seus metadados
  internos não são processados.
- **HEIF/AVIF**: itens Exif/XMP comprimidos ou com referências externas não são editados; a
  operação é interrompida se a reescrita no lugar não puder ser garantida.
- **C2PA**: ver [PROVENANCE.md](PROVENANCE.md) — confiança do emissor, carimbo de tempo e
  vinculação BMFF não são verificados.
- **Tamanho máximo**: 200 MB por arquivo (processamento em memória para garantir atomicidade).
- **Lote**: executado por WorkManager sem serviço em primeiro plano; lotes muito grandes podem ser
  pausados pelo sistema e retomados (itens já concluídos não são reprocessados).
- **Textos da interface** estão em português diretamente no código Compose; para tradução, mover
  para `strings.xml`.
- **Efeito de vidro**: simulado por camadas translúcidas sobre o fundo animado (desfoque real do
  conteúdo de fundo não é suportado de forma eficiente em todas as versões do Android).
- **Validação em aparelhos reais**: o build e os testes instrumentados rodam no CI (emulador
  API 34). Recomenda-se testar em aparelhos físicos, sobretudo HEIC de diferentes fabricantes.
