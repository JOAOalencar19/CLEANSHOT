package com.cleanshot.processing.ai

/**
 * Recognises *declarations* related to generative AI written in conventional metadata
 * (tool names, IPTC digital source types, generation parameters).
 *
 * This is NOT an AI-image detector: it only reads text that someone wrote into the file.
 * Matching values are protected from removal so CleanShot never erases origin statements.
 */
object AiDeclarationDetector {

    /** IPTC NewsCodes "digital source type" values that declare algorithmic generation. */
    val AI_DIGITAL_SOURCE_TYPES = mapOf(
        "trainedAlgorithmicMedia" to "Criado por IA generativa (modelo treinado)",
        "compositeWithTrainedAlgorithmicMedia" to "Composição que inclui elementos gerados por IA",
        "algorithmicMedia" to "Criado por algoritmo, sem dados de amostragem",
        "compositeSynthetic" to "Composição com elementos sintéticos",
        "algorithmicallyEnhanced" to "Aprimorado por algoritmo",
        "digitalCreation" to "Criação digital",
        "compositeCapture" to "Composição de capturas",
        "digitalCapture" to "Captura digital original",
        "computationalCapture" to "Captura computacional",
        "screenCapture" to "Captura de tela",
        "virtualRecording" to "Gravação virtual",
        "humanEdits" to "Editado por pessoa",
        "minorHumanEdits" to "Pequenas edições humanas",
        "dataDrivenMedia" to "Mídia baseada em dados",
        "negativeFilm" to "Digitalização de negativo",
        "positiveFilm" to "Digitalização de positivo",
        "print" to "Digitalização de impressão",
    )

    /** Digital source types that state AI or algorithmic involvement. */
    val AI_SOURCE_KEYS = setOf(
        "trainedAlgorithmicMedia", "compositeWithTrainedAlgorithmicMedia", "algorithmicMedia",
        "compositeSynthetic", "algorithmicallyEnhanced",
    )

    private val TOOL_PATTERNS: List<Pair<Regex, String>> = listOf(
        "firefly" to "Adobe Firefly",
        "generative fill|generative expand|preenchimento generativo" to "Adobe Generative Fill",
        "dall[·\\-\\s]?e" to "DALL·E",
        "chatgpt|gpt-4o|gpt-image|openai" to "OpenAI",
        "midjourney" to "Midjourney",
        "stable[ _-]?diffusion|sdxl|sd ?1\\.5|automatic1111|stable-diffusion-webui" to "Stable Diffusion",
        "comfyui" to "ComfyUI",
        "invokeai" to "InvokeAI",
        "novelai" to "NovelAI",
        "imagen\\b|google ai|gemini|nano banana|made with google ai" to "Google (IA generativa)",
        "bing image creator|microsoft designer|designer\\.microsoft" to "Microsoft (IA generativa)",
        "leonardo\\.?ai" to "Leonardo.Ai",
        "runway(ml)?" to "Runway",
        "\\bflux(\\.1)?\\b|black forest labs" to "FLUX",
        "ideogram" to "Ideogram",
        "playground ?ai|playgroundai" to "Playground AI",
        "dreamstudio" to "DreamStudio",
        "canva magic|magic media" to "Canva Magic Media",
        "meta ai|imagine with meta" to "Meta AI",
        "grok|aurora \\(xai\\)|xai" to "xAI",
        "galaxy ai|generative edit|edição generativa" to "Samsung Galaxy AI",
        "magic editor|reimagine|pixel studio" to "Google Pixel (edição por IA)",
        "image playground|apple intelligence|clean up \\(apple\\)" to "Apple Intelligence",
        "trainedalgorithmicmedia|compositewithtrainedalgorithmicmedia" to "Declaração IPTC de mídia gerada por IA",
    ).map { (p, name) -> Regex(p, RegexOption.IGNORE_CASE) to name }

    /** Parameters written by Stable Diffusion front-ends ("Steps: 20, Sampler: ..., CFG scale: 7"). */
    private val SD_PARAMETERS = Regex("(Steps:\\s*\\d+.*(Sampler|CFG scale|Seed):)|(\"class_type\"\\s*:)|(negative prompt:)", RegexOption.IGNORE_CASE)

    /** PNG text keywords used by generators to store prompts / workflows. */
    val AI_PNG_KEYWORDS = setOf("parameters", "prompt", "workflow", "invokeai_metadata", "sd-metadata", "dream", "generation_data", "Description AI", "ai_metadata")

    data class Match(val tool: String?, val reason: String)

    fun detect(text: String?): Match? {
        if (text.isNullOrBlank()) return null
        if (SD_PARAMETERS.containsMatchIn(text)) return Match("Stable Diffusion (parâmetros de geração)", "Parâmetros de geração de imagem")
        for ((regex, tool) in TOOL_PATTERNS) {
            if (regex.containsMatchIn(text)) return Match(tool, "Menciona a ferramenta/termo de IA \"$tool\"")
        }
        return null
    }

    fun digitalSourceTypeKey(value: String): String? =
        value.trim().substringAfterLast('/').takeIf { it in AI_DIGITAL_SOURCE_TYPES }
}
