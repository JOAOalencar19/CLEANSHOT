package com.cleanshot.feature.report

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.cleanshot.domain.session.ImageSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Reads the cleaning result of a session entry (shared by report and comparison screens). */
@HiltViewModel
class ReportViewModel @Inject constructor(savedState: SavedStateHandle, session: ImageSession) : ViewModel() {
    val sessionId: String = savedState.get<String>("sid").orEmpty()
    val entry: ImageSession.Entry? = session.get(sessionId)
}
