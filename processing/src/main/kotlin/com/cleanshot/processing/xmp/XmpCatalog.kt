package com.cleanshot.processing.xmp

import com.cleanshot.domain.model.CleaningTarget as T
import com.cleanshot.domain.model.MetadataCategory as C
import com.cleanshot.domain.model.PrivacyLevel as P
import com.cleanshot.processing.catalog.FieldInfo

/** XMP namespaces and property descriptions. */
object XmpCatalog {

    const val NS_RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    const val NS_X = "adobe:ns:meta/"

    /** Canonical prefixes, used to build stable keys regardless of the prefix used in the file. */
    val PREFIXES = mapOf(
        "http://purl.org/dc/elements/1.1/" to "dc",
        "http://purl.org/dc/terms/" to "dcterms",
        "http://ns.adobe.com/xap/1.0/" to "xmp",
        "http://ns.adobe.com/xap/1.0/rights/" to "xmpRights",
        "http://ns.adobe.com/xap/1.0/mm/" to "xmpMM",
        "http://ns.adobe.com/xap/1.0/sType/ResourceEvent#" to "stEvt",
        "http://ns.adobe.com/xap/1.0/sType/ResourceRef#" to "stRef",
        "http://ns.adobe.com/photoshop/1.0/" to "photoshop",
        "http://iptc.org/std/Iptc4xmpCore/1.0/xmlns/" to "Iptc4xmpCore",
        "http://iptc.org/std/Iptc4xmpExt/2008-02-29/" to "Iptc4xmpExt",
        "http://ns.adobe.com/exif/1.0/" to "exif",
        "http://cipa.jp/exif/1.0/" to "exifEX",
        "http://ns.adobe.com/tiff/1.0/" to "tiff",
        "http://ns.adobe.com/exif/1.0/aux/" to "aux",
        "http://ns.adobe.com/camera-raw-settings/1.0/" to "crs",
        "http://ns.adobe.com/lightroom/1.0/" to "lr",
        "http://ns.microsoft.com/photo/1.0/" to "MicrosoftPhoto",
        "http://www.metadataworkinggroup.com/schemas/regions/" to "mwg-rs",
        "http://ns.useplus.org/ldf/xmp/1.0/" to "plus",
        "http://ns.google.com/photos/1.0/container/" to "GContainer",
        "http://ns.google.com/photos/1.0/container/item/" to "Item",
        "http://ns.google.com/photos/1.0/camera/" to "GCamera",
        "http://ns.google.com/photos/1.0/image/" to "GImage",
        "http://ns.google.com/photos/1.0/panorama/" to "GPano",
        "http://ns.google.com/photos/dd/1.0/device/" to "Device",
        "http://ns.adobe.com/hdr-gain-map/1.0/" to "hdrgm",
        "http://ns.apple.com/HDRGainMap/1.0/" to "HDRGainMap",
        "http://ns.adobe.com/xmp/note/" to "xmpNote",
        "http://ns.adobe.com/xap/1.0/g/img/" to "xmpGImg",
        "http://ns.adobe.com/pdf/1.3/" to "pdf",
    )

    /** Namespaces required to render the image correctly (gain maps, motion photos, panoramas). */
    val RENDERING_NAMESPACES = setOf("GContainer", "Item", "GCamera", "GImage", "GPano", "hdrgm", "HDRGainMap", "Device", "xmpNote")

    private fun f(name: String, desc: String, c: C, p: P, t: T? = null, rendering: Boolean = false) = FieldInfo(name, desc, c, p, t, rendering)

    private val provenance = f("Procedência", "Declaração de origem/procedência. Sempre preservada.", C.PROVENANCE, P.NONE)

    /** Properties protected as provenance / AI-origin statements. */
    val PROVENANCE_PROPERTIES = setOf(
        "Iptc4xmpExt:DigitalSourceType", "Iptc4xmpExt:DigitalSourceFileType", "Iptc4xmpExt:AISystemUsed",
        "Iptc4xmpExt:AISystemVersionUsed", "Iptc4xmpExt:AIPromptInformation", "Iptc4xmpExt:AIPromptWriterName",
        "dcterms:provenance",
    )

    val PROPERTIES: Map<String, FieldInfo> = mapOf(
        "dc:creator" to f("Criador", "Nome(s) do(s) autor(es).", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "dc:contributor" to f("Colaboradores", "Pessoas que contribuíram.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "dc:rights" to f("Direitos", "Aviso de direitos autorais.", C.AUTHORSHIP, P.MEDIUM, T.COPYRIGHT),
        "dc:description" to f("Descrição", "Descrição do conteúdo.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "dc:title" to f("Título", "Título da imagem.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "dc:subject" to f("Palavras-chave", "Palavras-chave.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        "dc:publisher" to f("Editora", "Responsável pela publicação.", C.AUTHORSHIP, P.LOW, T.IPTC_EDITORIAL),
        "dc:source" to f("Fonte", "Origem do recurso.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
        "dc:date" to f("Data (DC)", "Data associada ao recurso.", C.DATES, P.LOW, T.DATES),
        "dc:format" to f("Formato (DC)", "Tipo MIME declarado.", C.FILE, P.NONE),
        "xmp:CreatorTool" to f("Ferramenta de criação", "Software usado para criar/editar o arquivo.", C.SOFTWARE, P.LOW, T.SOFTWARE),
        "xmp:CreateDate" to f("Data de criação (XMP)", "Data de criação.", C.DATES, P.MEDIUM, T.DATES),
        "xmp:ModifyDate" to f("Data de modificação (XMP)", "Data de modificação.", C.DATES, P.LOW, T.DATES),
        "xmp:MetadataDate" to f("Data dos metadados", "Última alteração dos metadados.", C.DATES, P.LOW, T.DATES),
        "xmp:Thumbnails" to f("Miniaturas XMP", "Prévias embutidas no XMP.", C.FILE, P.MEDIUM, T.THUMBNAIL),
        "xmp:Rating" to f("Classificação", "Estrelas atribuídas.", C.OTHER, P.NONE),
        "xmp:Label" to f("Rótulo", "Rótulo de cor/organização.", C.OTHER, P.NONE),
        "xmpRights:Marked" to f("Protegido por direitos", "Indica se há direitos reservados.", C.AUTHORSHIP, P.LOW, T.COPYRIGHT),
        "xmpRights:Owner" to f("Titular dos direitos", "Proprietário dos direitos.", C.AUTHORSHIP, P.HIGH, T.COPYRIGHT),
        "xmpRights:UsageTerms" to f("Termos de uso", "Licença/termos.", C.AUTHORSHIP, P.LOW, T.COPYRIGHT),
        "xmpRights:WebStatement" to f("Declaração de direitos (URL)", "Endereço com informações de licença.", C.AUTHORSHIP, P.LOW, T.COPYRIGHT),
        "xmpRights:Certificate" to f("Certificado de direitos", "URL de certificado.", C.AUTHORSHIP, P.LOW, T.COPYRIGHT),
        "xmpMM:History" to f("Histórico de edição", "Etapas de edição registradas pelo software.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:DocumentID" to f("ID do documento", "Identificador que permite rastrear versões do arquivo.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:InstanceID" to f("ID da instância", "Identificador desta versão.", C.SOFTWARE, P.LOW, T.EDIT_HISTORY),
        "xmpMM:OriginalDocumentID" to f("ID do documento original", "Liga o arquivo ao original.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:DerivedFrom" to f("Derivado de", "Referência ao arquivo de origem.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:Ingredients" to f("Ingredientes", "Arquivos usados na composição.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:Pantry" to f("Pantry", "Metadados de arquivos incorporados.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:PreservedFileName" to f("Nome de arquivo original", "Nome original do arquivo.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "xmpMM:VersionID" to f("Versão", "Identificador de versão.", C.SOFTWARE, P.LOW, T.EDIT_HISTORY),
        "xmpMM:Versions" to f("Versões", "Histórico de versões.", C.SOFTWARE, P.LOW, T.EDIT_HISTORY),
        "photoshop:AuthorsPosition" to f("Cargo do autor", "Função do autor.", C.AUTHORSHIP, P.MEDIUM, T.AUTHOR_IDENTITY),
        "photoshop:CaptionWriter" to f("Redator da legenda", "Pessoa que escreveu a descrição.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "photoshop:City" to f("Cidade", "Cidade.", C.LOCATION, P.HIGH, T.LOCATION),
        "photoshop:State" to f("Estado", "Estado/província.", C.LOCATION, P.MEDIUM, T.LOCATION),
        "photoshop:Country" to f("País", "País.", C.LOCATION, P.MEDIUM, T.LOCATION),
        "photoshop:Credit" to f("Crédito", "Linha de crédito.", C.AUTHORSHIP, P.MEDIUM, T.IPTC_EDITORIAL),
        "photoshop:Source" to f("Fonte", "Detentor original.", C.AUTHORSHIP, P.MEDIUM, T.IPTC_EDITORIAL),
        "photoshop:Instructions" to f("Instruções", "Instruções editoriais.", C.OTHER, P.MEDIUM, T.IPTC_EDITORIAL),
        "photoshop:TransmissionReference" to f("Referência de transmissão", "Código de transmissão.", C.OTHER, P.LOW, T.IPTC_EDITORIAL),
        "photoshop:Category" to f("Categoria", "Categoria editorial.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
        "photoshop:SupplementalCategories" to f("Categorias suplementares", "Categorias adicionais.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
        "photoshop:Urgency" to f("Urgência", "Prioridade editorial.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
        "photoshop:DateCreated" to f("Data de criação (Photoshop)", "Data de criação do conteúdo.", C.DATES, P.MEDIUM, T.DATES),
        "photoshop:Headline" to f("Manchete", "Resumo do conteúdo.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "photoshop:History" to f("Histórico (Photoshop)", "Histórico textual de edição.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "photoshop:DocumentAncestors" to f("Documentos ancestrais", "IDs de arquivos usados como base.", C.SOFTWARE, P.MEDIUM, T.EDIT_HISTORY),
        "photoshop:TextLayers" to f("Camadas de texto", "Textos das camadas.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "photoshop:ICCProfile" to f("Nome do perfil ICC", "Perfil de cores.", C.COLOR, P.NONE, rendering = true),
        "photoshop:ColorMode" to f("Modo de cor", "Modo de cor.", C.COLOR, P.NONE, rendering = true),
        "Iptc4xmpCore:CreatorContactInfo" to f("Contato do criador", "Endereço, telefone, e-mail do autor.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "Iptc4xmpCore:Location" to f("Sublocalidade", "Local específico.", C.LOCATION, P.HIGH, T.LOCATION),
        "Iptc4xmpCore:CountryCode" to f("Código do país", "Código ISO do país.", C.LOCATION, P.MEDIUM, T.LOCATION),
        "Iptc4xmpCore:SubjectCode" to f("Códigos de assunto", "Códigos IPTC.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        "Iptc4xmpCore:Scene" to f("Cena", "Códigos de cena.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        "Iptc4xmpCore:IntellectualGenre" to f("Gênero", "Gênero editorial.", C.OTHER, P.NONE, T.IPTC_EDITORIAL),
        "Iptc4xmpCore:AltTextAccessibility" to f("Texto alternativo", "Descrição de acessibilidade.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "Iptc4xmpCore:ExtDescrAccessibility" to f("Descrição estendida", "Descrição de acessibilidade.", C.DESCRIPTION, P.LOW, T.COMMENTS),
        "Iptc4xmpExt:LocationCreated" to f("Local da captura", "Onde a foto foi criada.", C.LOCATION, P.HIGH, T.LOCATION),
        "Iptc4xmpExt:LocationShown" to f("Local retratado", "Local mostrado na imagem.", C.LOCATION, P.HIGH, T.LOCATION),
        "Iptc4xmpExt:PersonInImage" to f("Pessoas na imagem", "Nomes de pessoas retratadas.", C.DESCRIPTION, P.HIGH, T.COMMENTS),
        "Iptc4xmpExt:Event" to f("Evento", "Evento retratado.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "Iptc4xmpExt:ImageCreator" to f("Criador da imagem", "Criador (PLUS).", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "Iptc4xmpExt:CopyrightOwner" to f("Titular do copyright", "Proprietário dos direitos.", C.AUTHORSHIP, P.HIGH, T.COPYRIGHT),
        "Iptc4xmpExt:ImageSupplier" to f("Fornecedor", "Fornecedor da imagem.", C.AUTHORSHIP, P.MEDIUM, T.IPTC_EDITORIAL),
        "Iptc4xmpExt:DigitalSourceType" to provenance.copy(name = "Tipo de fonte digital", description = "Declaração IPTC de como a imagem foi produzida (ex.: gerada por IA). Sempre preservada."),
        "Iptc4xmpExt:DigitalSourceFileType" to provenance.copy(name = "Tipo de arquivo de origem"),
        "Iptc4xmpExt:AISystemUsed" to provenance.copy(name = "Sistema de IA utilizado"),
        "Iptc4xmpExt:AISystemVersionUsed" to provenance.copy(name = "Versão do sistema de IA"),
        "Iptc4xmpExt:AIPromptInformation" to provenance.copy(name = "Informações do prompt de IA"),
        "Iptc4xmpExt:AIPromptWriterName" to provenance.copy(name = "Autor do prompt de IA"),
        "dcterms:provenance" to provenance.copy(name = "Referência de procedência", description = "Referência a um manifesto de procedência (C2PA). Sempre preservada."),
        "exif:GPSLatitude" to f("Latitude (XMP)", "Latitude.", C.LOCATION, P.HIGH, T.LOCATION),
        "exif:GPSLongitude" to f("Longitude (XMP)", "Longitude.", C.LOCATION, P.HIGH, T.LOCATION),
        "exif:GPSAltitude" to f("Altitude (XMP)", "Altitude.", C.LOCATION, P.HIGH, T.LOCATION),
        "exif:DateTimeOriginal" to f("Data de captura (XMP)", "Data da captura.", C.DATES, P.MEDIUM, T.DATES),
        "exif:DateTimeDigitized" to f("Data de digitalização (XMP)", "Data de digitalização.", C.DATES, P.MEDIUM, T.DATES),
        "exif:UserComment" to f("Comentário (XMP)", "Comentário do usuário.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "exifEX:BodySerialNumber" to f("Número de série do corpo (XMP)", "Identifica a câmera.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        "exifEX:LensSerialNumber" to f("Número de série da lente (XMP)", "Identifica a lente.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        "exifEX:CameraOwnerName" to f("Proprietário da câmera (XMP)", "Dono do equipamento.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "exifEX:LensMake" to f("Fabricante da lente (XMP)", "Fabricante da lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "exifEX:LensModel" to f("Modelo da lente (XMP)", "Modelo da lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "tiff:Make" to f("Fabricante (XMP)", "Fabricante do dispositivo.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "tiff:Model" to f("Modelo (XMP)", "Modelo do dispositivo.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "tiff:Artist" to f("Artista (XMP)", "Autor.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "tiff:Copyright" to f("Copyright (XMP)", "Direitos.", C.AUTHORSHIP, P.MEDIUM, T.COPYRIGHT),
        "tiff:ImageDescription" to f("Descrição (XMP/TIFF)", "Descrição.", C.DESCRIPTION, P.MEDIUM, T.COMMENTS),
        "tiff:Software" to f("Software (XMP/TIFF)", "Software.", C.SOFTWARE, P.LOW, T.SOFTWARE),
        "tiff:DateTime" to f("Data de modificação (XMP/TIFF)", "Data.", C.DATES, P.LOW, T.DATES),
        "tiff:Orientation" to f("Orientação (XMP)", "Orientação de exibição.", C.FILE, P.NONE, rendering = true),
        "aux:SerialNumber" to f("Número de série (aux)", "Identifica a câmera.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        "aux:LensSerialNumber" to f("Série da lente (aux)", "Identifica a lente.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        "aux:OwnerName" to f("Proprietário (aux)", "Dono da câmera.", C.AUTHORSHIP, P.HIGH, T.AUTHOR_IDENTITY),
        "aux:Lens" to f("Lente (aux)", "Lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "aux:LensID" to f("ID da lente (aux)", "Identificador da lente.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "aux:LensInfo" to f("Info da lente (aux)", "Faixa focal.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "aux:ImageNumber" to f("Número da imagem (aux)", "Contador de disparos.", C.DEVICE, P.MEDIUM, T.DEVICE_IDENTIFIERS),
        "aux:Firmware" to f("Firmware (aux)", "Versão do firmware.", C.DEVICE, P.LOW, T.DEVICE_IDENTIFIERS),
        "lr:hierarchicalSubject" to f("Palavras-chave hierárquicas", "Tags do Lightroom.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        "MicrosoftPhoto:LastKeywordXMP" to f("Palavras-chave (Microsoft)", "Tags.", C.DESCRIPTION, P.LOW, T.KEYWORDS),
        "MicrosoftPhoto:CameraSerialNumber" to f("Número de série (Microsoft)", "Identifica a câmera.", C.DEVICE, P.HIGH, T.DEVICE_IDENTIFIERS),
        "mwg-rs:Regions" to f("Regiões (rostos/pessoas)", "Áreas marcadas, frequentemente com nomes de pessoas.", C.DESCRIPTION, P.HIGH, T.COMMENTS),
        "plus:DataMining" to f("Mineração de dados (PLUS)", "Permissão de uso para mineração/treinamento de IA.", C.AUTHORSHIP, P.NONE, T.COPYRIGHT),
    )

    fun info(prefix: String, local: String): FieldInfo {
        val key = "$prefix:$local"
        PROPERTIES[key]?.let { return it }
        return when {
            prefix == "exif" && local.startsWith("GPS") -> f("$local (XMP)", "Informação de geolocalização.", C.LOCATION, P.HIGH, T.LOCATION)
            prefix == "crs" -> f("Ajuste de revelação: $local", "Configuração do Adobe Camera Raw/Lightroom.", C.SOFTWARE, P.NONE, T.EDIT_HISTORY)
            prefix in RENDERING_NAMESPACES -> f("$prefix:$local", "Estrutura necessária para HDR, Motion Photo ou panorama. Preservada.", C.FILE, P.NONE, rendering = true)
            prefix == "exif" || prefix == "exifEX" || prefix == "tiff" || prefix == "aux" ->
                f("$local (XMP)", "Parâmetro técnico espelhado do EXIF.", C.CAPTURE, P.NONE)
            else -> f(key, "Propriedade XMP não catalogada. Mantida.", C.OTHER, P.LOW)
        }
    }
}
