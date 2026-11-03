package com.cleanshot.processing

import com.cleanshot.domain.model.AnalysisReport
import com.cleanshot.domain.model.CleaningOptions
import com.cleanshot.domain.model.CleaningReport
import com.cleanshot.domain.model.FindingStatus
import com.cleanshot.processing.analyzer.DefaultMetadataAnalyzer
import com.cleanshot.processing.cleaner.DefaultPrivacyCleaner
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

object Fixtures {
    fun bytes(path: String): ByteArray =
        Fixtures::class.java.getResourceAsStream("/fixtures/$path")?.readBytes() ?: error("fixture $path not found")

    fun file(path: String): File {
        val dir = Files.createTempDirectory("cleanshot-test").toFile().apply { deleteOnExit() }
        return File(dir, path.substringAfterLast('/')).apply { writeBytes(bytes(path)); deleteOnExit() }
    }

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    val analyzer = DefaultMetadataAnalyzer()
    val cleaner = DefaultPrivacyCleaner()

    fun analyze(path: String): AnalysisReport = analyzer.analyzeBytes(bytes(path), path.substringAfterLast('/'))

    data class Cleaned(val input: File, val output: File, val report: CleaningReport, val after: AnalysisReport)

    fun clean(path: String, options: CleaningOptions = CleaningOptions.MAXIMUM): Cleaned {
        val input = file(path)
        val output = File(input.parentFile, "out_" + input.name)
        val report = cleaner.clean(input, output, input.name, options)
        val after = analyzer.analyze(output, output.name)
        System.getProperty("cleanshot.dumpDir")?.takeIf { it.isNotBlank() }?.let { dir ->
            File(dir).mkdirs()
            input.copyTo(File(dir, input.name), overwrite = true)
            output.copyTo(File(dir, "cleaned_" + input.name), overwrite = true)
        }
        return Cleaned(input, output, report, after)
    }
}

fun AnalysisReport.found(key: String) = allEntries.firstOrNull { it.key == key && it.status == FindingStatus.FOUND }
fun AnalysisReport.has(key: String) = found(key) != null
