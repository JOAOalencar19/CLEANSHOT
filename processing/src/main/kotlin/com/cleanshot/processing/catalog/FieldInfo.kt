package com.cleanshot.processing.catalog

import com.cleanshot.domain.model.CleaningTarget
import com.cleanshot.domain.model.MetadataCategory
import com.cleanshot.domain.model.PrivacyLevel

/**
 * Static description of a metadata field.
 *
 * @param target cleaning group that removes the field (null = never removed by CleanShot).
 * @param rendering true when the field is needed to display the image correctly (orientation,
 *                  colour space, ICC...). Such fields are always preserved.
 */
data class FieldInfo(
    val name: String,
    val description: String,
    val category: MetadataCategory,
    val privacy: PrivacyLevel,
    val target: CleaningTarget? = null,
    val rendering: Boolean = false,
)
