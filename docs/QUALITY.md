# Preservação de qualidade

O CleanShot **não usa `Bitmap.compress()`** e não recodifica pixels em nenhum formato suportado.

| Formato | Estratégia | O que é comprovado após cada limpeza |
|---|---|---|
| JPEG | Reescrita de segmentos: APP1 EXIF/XMP, APP13 IPTC e COM reconstruídos; tudo desde o SOS (dados comprimidos, EOI, trailers como gain map/Motion Photo) copiado byte a byte | SHA-256 dos dados comprimidos, tabelas DQT/DHT/SOF, ICC, MPF, orientação, dimensões |
| PNG | Filtragem de chunks com CRC recalculado só nos alterados | IHDR/IDAT/PLTE/tRNS/APNG idênticos, transparência, iCCP/sRGB/gAMA/cHRM/cICP |
| WebP | Filtragem de chunks RIFF, flags VP8X atualizadas | VP8/VP8L/ALPH/ANIM/ANMF idênticos, alfa, animação, ICCP |
| HEIF/HEIC, AVIF | **Reescrita no lugar**: itens Exif/XMP regravados com o mesmo tamanho (preenchimento neutro). Nenhuma caixa é movida, `iloc` continua válido | Mesmo tamanho; todos os bytes fora dos itens Exif/XMP idênticos; dimensões |
| GIF, TIFF, DNG, BMP | Somente análise | Nunca modificados (sem conversão silenciosa) |

Regras adicionais:

- **Orientação**, perfis ICC, espaço de cor, dimensões, transparência, Motion Photo/HDR (XMP
  `GContainer`, `hdrgm`) são sempre preservados.
- A **MakerNote** é mantida no offset original sempre que possível (muitos fabricantes usam
  offsets absolutos); se precisar ser realocada, o relatório avisa.
- O digest IPTC do Photoshop (0x0425) é recalculado (MD5).
- JPEG truncado (sem EOI), estruturas inconsistentes, sub-IFDs desconhecidos → a operação é
  interrompida e explicada.

## Evidência

- Testes JVM comparam pixels decodificados pelo `javax.imageio` (independente do CleanShot) e hashes
  dos chunks de imagem.
- `scripts/verify_outputs.py` decodifica originais e resultados com Pillow/libheif (incluindo
  HEIC, AVIF e WebP animado) e compara cada pixel de cada quadro.
- O teste instrumentado `OnDeviceProcessingTest` repete a limpeza no runtime Android e confirma
  com `BitmapFactory`/`ImageDecoder`.
