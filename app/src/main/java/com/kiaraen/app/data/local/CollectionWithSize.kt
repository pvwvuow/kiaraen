package com.kiaraen.app.data.local

import androidx.room.Embedded

/** A collection plus its word count, loaded in ONE query (no Flow per card). */
data class CollectionWithSize(
    @Embedded val collection: CollectionEntity,
    val size: Int,
)
