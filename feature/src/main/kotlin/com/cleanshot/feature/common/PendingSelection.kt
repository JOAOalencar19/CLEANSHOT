package com.cleanshot.feature.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Hands multiple selected URIs (or a folder) from the dashboard to the batch screen. */
@Singleton
class PendingSelection @Inject constructor() {
    sealed interface Selection {
        data class Uris(val uris: List<String>) : Selection
        data class Folder(val treeUri: String) : Selection
    }

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    fun set(selection: Selection) { _selection.value = selection }
    fun consume(): Selection? = _selection.value.also { _selection.value = null }
}
