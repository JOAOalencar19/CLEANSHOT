package com.cleanshot.feature.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val ANALYSIS = "analysis?uri={uri}&next={next}"
    const val CLEANER = "cleaner/{sid}"
    const val REPORT = "report/{sid}"
    const val COMPARE = "compare/{sid}"
    const val PROVENANCE = "provenance/{sid}"
    const val EXPORT = "export/{sid}"
    const val BATCH = "batch"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun analysis(uri: String, next: String = "") = "analysis?uri=${Uri.encode(uri)}&next=$next"
    fun cleaner(sid: String) = "cleaner/$sid"
    fun report(sid: String) = "report/$sid"
    fun compare(sid: String) = "compare/$sid"
    fun provenance(sid: String) = "provenance/$sid"
    fun export(sid: String) = "export/$sid"
}
