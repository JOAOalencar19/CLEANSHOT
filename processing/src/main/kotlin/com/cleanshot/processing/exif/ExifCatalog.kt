package com.cleanshot.processing.exif

import com.cleanshot.domain.model.CleaningTarget as T
import com.cleanshot.domain.model.MetadataCategory as C
import com.cleanshot.domain.model.PrivacyLevel as P
import com.cleanshot.processing.catalog.FieldInfo

/** Descriptions of the EXIF tags CleanShot knows about. */
object ExifCatalog {

    private fun f(name: String, desc: String, c: C, p: P, t: T? = null, rendering: Boolean = false) =
        FieldInfo(name, desc, c, p, t, rendering)

    val IFD0: Map<Int, FieldInfo> = mapOf(
        0x000B to f("Software de processamento", "Software que processou a imagem.", C.SOFTWARE, P.LOW, T.SOFTWARE),
        0x010E to f("Descrição da imagem", "Texto descritivo livre; pode conter nomes ou locais.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        0x010F to f("Fabricante", "Fabricante da câmera ou do celular.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        0x0110 to f("Modelo da câmera", "Modelo do dispositivo que capturou a foto.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        0x0112 to f("Orientação", "Indica como a imagem deve ser girada para exibição. Preservado para não alterar a aparência.", C.FILE, P.NONE, rendering = true),
        0x011A to f("Resolução horizontal", "Densidade de impressão (DPI) horizontal.", C.FILE, P.NONE),
        0x011B to f("Resolução vertical", "Densidade de impressão (DPI) vertical.", C.FILE, P.NONE),
        0x0128 to f("Unidade de resolução", "Unidade usada pelos campos de resolução.", C.FILE, P.NONE),
        0x0131 to f("Software", "Software ou firmware que gerou/editou o arquivo.", C.SOFTWARE, P.LOW, T.SOFTWARE),
        0x0132 to f("Data de modificação", "Data e hora da última alteração do arquivo.", C.DATES, P.LOW, T.DATES),
        0x013B to f("Artista / autor", "Nome da pessoa que criou a imagem.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        0x013C to f("Computador", "Nome do computador/sistema usado; pode identificar o usuário.", C.SOFTWARE, P.MEDIUM, T.SOFTWARE),
        0x013E to f("Ponto branco", "Cromaticidade do ponto branco.", C.COLOR, P.NONE, rendering = true),
        0x013F to f("Cromaticidades primárias", "Cromaticidade das cores primárias.", C.COLOR, P.NONE, rendering = true),
        0x0211 to f("Coeficientes YCbCr", "Coeficientes de conversão de cor.", C.COLOR, P.NONE, rendering = true),
        0x0213 to f("Posicionamento YCbCr", "Posição das amostras de crominância.", C.COLOR, P.NONE, rendering = true),
        0x0214 to f("Referência preto/branco", "Valores de referência de cor.", C.COLOR, P.NONE, rendering = true),
        0x8298 to f("Copyright", "Aviso de direitos autorais.", C.AUTHORSHIP, P.MEDIUM, T.COPYRIGHT),
        0x8773 to f("Perfil ICC (TIFF)", "Perfil de cores embutido.", C.COLOR, P.NONE, rendering = true),
        0x9C9B to f("Título (Windows)", "Título gravado pelo Windows.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        0x9C9C to f("Comentário (Windows)", "Comentário gravado pelo Windows.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        0x9C9D to f("Autor (Windows)", "Autor gravado pelo Windows.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        0x9C9E to f("Palavras-chave (Windows)", "Palavras-chave gravadas pelo Windows.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        0x9C9F to f("Assunto (Windows)", "Assunto gravado pelo Windows.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        0xC4A5 to f("Print Image Matching", "Parâmetros de impressão do fabricante.", C.OTHER, P.NONE),
    )

    val EXIF: Map<Int, FieldInfo> = mapOf(
        0x829A to f("Velocidade do obturador", "Tempo de exposição.", C.CAPTURE, P.NONE),
        0x829D to f("Abertura (f)", "Número f da lente.", C.CAPTURE, P.NONE),
        0x8822 to f("Programa de exposição", "Modo de exposição da câmera.", C.CAPTURE, P.NONE),
        0x8827 to f("ISO", "Sensibilidade ISO.", C.CAPTURE, P.NONE),
        0x8830 to f("Tipo de sensibilidade", "Como o valor ISO foi calculado.", C.CAPTURE, P.NONE),
        0x9000 to f("Versão EXIF", "Versão do padrão EXIF.", C.FILE, P.NONE),
        0x9003 to f("Data de captura", "Data e hora em que a foto foi tirada.", C.DATES, P.MEDIUM, T.DATES),
        0x9004 to f("Data de digitalização", "Data e hora da digitalização.", C.DATES, P.MEDIUM, T.DATES),
        0x9010 to f("Fuso horário (modificação)", "Diferença de fuso horário; revela a região.", C.DATES, P.MEDIUM, T.DATES),
        0x9011 to f("Fuso horário (captura)", "Diferença de fuso horário da captura; revela a região.", C.DATES, P.MEDIUM, T.DATES),
        0x9012 to f("Fuso horário (digitalização)", "Diferença de fuso horário da digitalização.", C.DATES, P.MEDIUM, T.DATES),
        0x9101 to f("Configuração de componentes", "Ordem dos componentes de cor.", C.COLOR, P.NONE, rendering = true),
        0x9102 to f("Bits por pixel comprimido", "Taxa de compressão.", C.FILE, P.NONE),
        0x9201 to f("Velocidade (APEX)", "Velocidade do obturador em unidades APEX.", C.CAPTURE, P.NONE),
        0x9202 to f("Abertura (APEX)", "Abertura em unidades APEX.", C.CAPTURE, P.NONE),
        0x9203 to f("Brilho", "Brilho medido.", C.CAPTURE, P.NONE),
        0x9204 to f("Compensação de exposição", "Compensação aplicada.", C.CAPTURE, P.NONE),
        0x9205 to f("Abertura máxima", "Maior abertura da lente.", C.CAPTURE, P.NONE),
        0x9206 to f("Distância do assunto", "Distância de foco.", C.CAPTURE, P.NONE),
        0x9207 to f("Modo de medição", "Modo de fotometria.", C.CAPTURE, P.NONE),
        0x9208 to f("Fonte de luz", "Tipo de iluminação.", C.CAPTURE, P.NONE),
        0x9209 to f("Flash", "Estado do flash.", C.CAPTURE, P.NONE),
        0x920A to f("Distância focal", "Distância focal da lente.", C.CAPTURE, P.NONE),
        0x9214 to f("Área do assunto", "Região do assunto principal.", C.CAPTURE, P.NONE),
        0x927C to f("MakerNote", "Dados proprietários do fabricante; frequentemente incluem número de série e outras informações do aparelho.", C.DEVICE, P.MEDIUM, T.DEVICE_IDENTIFIERS),
        0x9286 to f("Comentário do usuário", "Comentário livre; geradores de imagem às vezes gravam aqui os parâmetros usados.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        0x9290 to f("Subsegundos", "Frações de segundo da data de modificação.", C.DATES, P.LOW, T.DATES),
        0x9291 to f("Subsegundos (captura)", "Frações de segundo da captura.", C.DATES, P.LOW, T.DATES),
        0x9292 to f("Subsegundos (digitalização)", "Frações de segundo da digitalização.", C.DATES, P.LOW, T.DATES),
        0xA000 to f("Versão FlashPix", "Versão FlashPix.", C.FILE, P.NONE),
        0xA001 to f("Espaço de cor", "Espaço de cor (sRGB etc.). Preservado.", C.COLOR, P.NONE, rendering = true),
        0xA002 to f("Largura em pixels", "Largura declarada da imagem.", C.FILE, P.NONE),
        0xA003 to f("Altura em pixels", "Altura declarada da imagem.", C.FILE, P.NONE),
        0xA20E to f("Resolução do plano focal X", "Resolução do sensor.", C.CAPTURE, P.NONE),
        0xA20F to f("Resolução do plano focal Y", "Resolução do sensor.", C.CAPTURE, P.NONE),
        0xA210 to f("Unidade do plano focal", "Unidade da resolução do sensor.", C.CAPTURE, P.NONE),
        0xA217 to f("Método de captura", "Tipo de sensor.", C.CAPTURE, P.NONE),
        0xA300 to f("Fonte do arquivo", "Origem da imagem (câmera digital etc.).", C.CAPTURE, P.NONE),
        0xA301 to f("Tipo de cena", "Tipo de cena.", C.CAPTURE, P.NONE),
        0xA401 to f("Processamento personalizado", "Processamento especial aplicado.", C.CAPTURE, P.NONE),
        0xA402 to f("Modo de exposição", "Automático/manual.", C.CAPTURE, P.NONE),
        0xA403 to f("Balanço de branco", "Modo de balanço de branco.", C.CAPTURE, P.NONE),
        0xA404 to f("Zoom digital", "Fator de zoom digital.", C.CAPTURE, P.NONE),
        0xA405 to f("Distância focal (35 mm)", "Distância focal equivalente.", C.CAPTURE, P.NONE),
        0xA406 to f("Tipo de captura de cena", "Retrato, paisagem etc.", C.CAPTURE, P.NONE),
        0xA407 to f("Controle de ganho", "Ganho aplicado.", C.CAPTURE, P.NONE),
        0xA408 to f("Contraste", "Contraste aplicado pela câmera.", C.CAPTURE, P.NONE),
        0xA409 to f("Saturação", "Saturação aplicada pela câmera.", C.CAPTURE, P.NONE),
        0xA40A to f("Nitidez", "Nitidez aplicada pela câmera.", C.CAPTURE, P.NONE),
        0xA40C to f("Faixa de distância", "Faixa de distância do assunto.", C.CAPTURE, P.NONE),
        0xA420 to f("ID único da imagem", "Identificador único; permite correlacionar arquivos.", C.DEVICE, P.MEDIUM, T.DEVICE_IDENTIFIERS),
        0xA430 to f("Proprietário da câmera", "Nome do dono do equipamento.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        0xA431 to f("Número de série do corpo", "Número de série da câmera; identifica o aparelho.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        0xA432 to f("Especificação da lente", "Faixa focal e aberturas da lente.", C.CAPTURE, P.NONE),
        0xA433 to f("Fabricante da lente", "Fabricante da lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        0xA434 to f("Modelo da lente", "Modelo da lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        0xA435 to f("Número de série da lente", "Identifica a lente específica.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        0xA460 to f("Imagem composta", "Indica se a foto é composta de várias capturas.", C.CAPTURE, P.NONE),
        0xA500 to f("Gama", "Valor de gama.", C.COLOR, P.NONE, rendering = true),
    )

    private val gps = f("GPS", "Informação de geolocalização.", C.LOCATION, P.HIGH, T.LOCATION)

    val GPS: Map<Int, FieldInfo> = mapOf(
        0x00 to gps.copy(name = "Versão GPS", privacy = P.LOW),
        0x01 to gps.copy(name = "Referência de latitude"),
        0x02 to gps.copy(name = "Latitude", description = "Latitude do local da captura."),
        0x03 to gps.copy(name = "Referência de longitude"),
        0x04 to gps.copy(name = "Longitude", description = "Longitude do local da captura."),
        0x05 to gps.copy(name = "Referência de altitude"),
        0x06 to gps.copy(name = "Altitude", description = "Altitude do local da captura."),
        0x07 to gps.copy(name = "Hora GPS (UTC)"),
        0x08 to gps.copy(name = "Satélites"),
        0x09 to gps.copy(name = "Status do receptor"),
        0x0A to gps.copy(name = "Modo de medição GPS"),
        0x0B to gps.copy(name = "Precisão (DOP)"),
        0x0C to gps.copy(name = "Unidade de velocidade"),
        0x0D to gps.copy(name = "Velocidade", description = "Velocidade de deslocamento no momento da captura."),
        0x0E to gps.copy(name = "Referência de direção"),
        0x0F to gps.copy(name = "Direção de movimento"),
        0x10 to gps.copy(name = "Referência da direção da imagem"),
        0x11 to gps.copy(name = "Direção da imagem", description = "Para onde a câmera estava apontada."),
        0x12 to gps.copy(name = "Datum do mapa"),
        0x1B to gps.copy(name = "Método de processamento GPS"),
        0x1C to gps.copy(name = "Nome da área"),
        0x1D to gps.copy(name = "Data GPS (UTC)"),
        0x1E to gps.copy(name = "Correção diferencial"),
        0x1F to gps.copy(name = "Erro de posicionamento horizontal"),
    )

    val INTEROP: Map<Int, FieldInfo> = mapOf(
        0x0001 to f("Índice de interoperabilidade", "Regras de interoperabilidade (R98 etc.).", C.FILE, P.NONE),
        0x0002 to f("Versão de interoperabilidade", "Versão das regras.", C.FILE, P.NONE),
    )

    fun info(ifd: IfdType, tag: Int): FieldInfo = when (ifd) {
        IfdType.IFD0, IfdType.IFD1 -> IFD0[tag]
        IfdType.EXIF -> EXIF[tag] ?: IFD0[tag]
        IfdType.GPS -> GPS[tag] ?: gps.copy(name = "GPS tag 0x%04X".format(tag))
        IfdType.INTEROP -> INTEROP[tag]
    } ?: f(
        "Tag 0x%04X".format(tag),
        "Campo EXIF não catalogado (privado ou de fabricante). Mantido, pois não pertence a nenhum grupo selecionável.",
        C.OTHER, P.LOW,
    )
}
