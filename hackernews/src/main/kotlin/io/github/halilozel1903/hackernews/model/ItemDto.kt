package io.github.halilozel1903.hackernews.model

import kotlinx.serialization.Serializable

/** The item JSON exactly as the API sends it; every field but `id` is optional. */
@Serializable
internal class ItemDto(
    val id: Long,
    val deleted: Boolean = false,
    val type: String? = null,
    val by: String? = null,
    val time: Long? = null,
    val text: String? = null,
    val dead: Boolean = false,
    val parent: Long? = null,
    val poll: Long? = null,
    val kids: List<Long> = emptyList(),
    val url: String? = null,
    val score: Int? = null,
    val title: String? = null,
    val parts: List<Long> = emptyList(),
    val descendants: Int? = null,
) {
    fun toItem(): Item {
        val time = time ?: 0L
        return when (type) {
            "story" -> Item.Story(
                id = id, by = by, time = time, title = title.orEmpty(), url = url?.takeIf { it.isNotBlank() },
                text = text?.takeIf { it.isNotEmpty() }, score = score ?: 0, descendants = descendants ?: 0,
                kids = kids, deleted = deleted, dead = dead,
            )
            "comment" -> Item.Comment(
                id = id, by = by, time = time, text = text, parent = parent ?: 0L,
                kids = kids, deleted = deleted, dead = dead,
            )
            "job" -> Item.Job(
                id = id, by = by, time = time, title = title.orEmpty(), url = url?.takeIf { it.isNotBlank() },
                text = text?.takeIf { it.isNotEmpty() }, score = score ?: 0,
                kids = kids, deleted = deleted, dead = dead,
            )
            "poll" -> Item.Poll(
                id = id, by = by, time = time, title = title.orEmpty(), text = text?.takeIf { it.isNotEmpty() },
                score = score ?: 0, descendants = descendants ?: 0, parts = parts,
                kids = kids, deleted = deleted, dead = dead,
            )
            "pollopt" -> Item.PollOption(
                id = id, by = by, time = time, text = text, poll = poll ?: 0L, score = score ?: 0,
                kids = kids, deleted = deleted, dead = dead,
            )
            else -> Item.Unknown(id = id, type = type, by = by, time = time, kids = kids, deleted = deleted, dead = dead)
        }
    }
}
