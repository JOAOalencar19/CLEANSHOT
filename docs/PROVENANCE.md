# C2PA e procedência

## Escolha da implementação

Avaliados:
- **SDK oficial `c2pa-android`** (Rust/JNI, Content Authenticity Initiative): é a opção mais
  completa (inclui lista de confiança, carimbo de tempo e vinculações BMFF). Não pôde ser
  integrado/validado neste ambiente de desenvolvimento (sem acesso ao repositório de pacotes e ao
  Android SDK), e não inventamos APIs.
- **Implementação nativa em Kotlin (adotada)**: leitura do manifesto e verificações bem
  definidas pela especificação, sem código nativo, validada contra o **SDK oficial `c2pa-python`**.

A interface `C2paInspector` (domain) permite trocar por um inspetor baseado no SDK oficial em
`data/di/DataModule.kt` sem alterar telas ou regras.

## O que é verificado (offline)

| Verificação | Estado |
|---|---|
| Localização do manifesto (JPEG APP11, PNG `caBX`, WebP `C2PA`, HEIF/AVIF `uuid`) | ✅ |
| Estrutura JUMBF, claim v1/v2 (gerador, título, asserções, ações, ingredientes) | ✅ |
| Hash de cada asserção referenciada | ✅ |
| Assinatura COSE_Sign1 (ES256/384/512, PS256/384/512, Ed25519) com o certificado incluído | ✅ |
| Vinculação `c2pa.hash.data` (JPEG/PNG/WebP) | ✅ |
| Vinculação BMFF (`c2pa.hash.bmff*`) e `c2pa.hash.boxes` | ⚠️ não verificado (exibido como tal) |
| Lista de confiança C2PA, revogação, carimbo de tempo | ⚠️ não verificado (exibido como tal) |
| Manifestos remotos / sidecar | ⚠️ apenas detectados (sem download — app offline) |

Resultados validados contra o SDK oficial (`processing/src/test/resources/fixtures/c2pa-rs/NOTICE.md`):
arquivos válidos → "verificadas"; assinatura adulterada (`E-sig-CA.jpg`) e conteúdo adulterado
(`XCA.jpg`) → "inválidas", com a mesma causa apontada pelo SDK.

## Preservação

Credenciais C2PA, `Iptc4xmpExt:DigitalSourceType`, campos `AISystemUsed`/`AIPrompt*`,
`dcterms:provenance`, parâmetros de geração em PNG (`parameters`, `prompt`, `workflow`...) e
textos que mencionem ferramentas de IA (ex.: `CreatorTool = Adobe Firefly`, `UserComment` com
parâmetros do Stable Diffusion) **nunca são removidos**.

Remover qualquer metadado de um arquivo assinado altera bytes cobertos pela assinatura. O
manifesto permanece intacto (verificado byte a byte), e a tela de confirmação avisa que
verificadores passarão a indicar modificação após a assinatura — o comportamento correto e honesto.

## Limites deliberados

- Ausência de credenciais **não** indica que a imagem é autêntica ou não gerada por IA.
- Marcas d'água invisíveis e sinais nos pixels (ex.: SynthID) não são metadados e não são analisados.
- O app não alega detectar toda imagem gerada por IA.
