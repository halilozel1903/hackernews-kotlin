package io.github.halilozel1903.hackernews.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /updates`: the items and profiles that changed recently. */
@Serializable
public data class Updates(
    /** Ids of recently changed items. */
    val items: List<Long> = emptyList(),
    /** Usernames of recently changed profiles. */
    @SerialName("profiles") val profiles: List<String> = emptyList(),
)
