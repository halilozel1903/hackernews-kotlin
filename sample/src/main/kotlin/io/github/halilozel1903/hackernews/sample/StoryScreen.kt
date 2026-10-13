package io.github.halilozel1903.hackernews.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.hackernews.comments.CommentRow
import io.github.halilozel1903.hackernews.compose.HnHtmlText
import io.github.halilozel1903.hackernews.compose.commentIndent
import io.github.halilozel1903.hackernews.compose.rememberRelativeTime
import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.title

/** Saves the ids of collapsed comments. */
private val CollapsedSaver = listSaver<Set<Long>, Long>(
    save = { ids -> ids.toList() },
    restore = { ids -> ids.toSet() },
)

/** A story (or any item) with its comment tree; tap a comment's header to collapse its replies. */
@Composable
fun StoryScreen(repository: ReaderRepository, id: Long, onBack: (() -> Unit)?, onOpenUser: (String) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val detail by produceState<Load<StoryDetail>>(Load.Loading, id, reload) {
        value = Load.Loading
        value = load { repository.story(id) }
    }
    ReportLoad(detail)
    var collapsed by rememberSaveable(stateSaver = CollapsedSaver) { mutableStateOf(emptySet<Long>()) }

    Column(Modifier.fillMaxSize()) {
        val ready = detail as? Load.Ready
        TopBar(
            title = if (ready != null) "Discussion" else "",
            trailing = ready?.value?.comments?.let { pluralize(it.size, "comment") },
            onBack = onBack,
        )
        when (val state = detail) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(state.message, onRetry = { reload++ })
            is Load.Ready -> {
                val story = state.value.story
                val rows = remember(state.value.comments, collapsed) { state.value.comments.flatten(collapsed) }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item(key = "header") {
                        StoryHeader(story, onOpenUser)
                    }
                    if (rows.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No comments yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    items(rows, key = { it.comment.id }) { row ->
                        CommentItem(
                            row = row,
                            isOp = story.by != null && row.comment.by == story.by,
                            onToggle = {
                                val commentId = row.comment.id
                                collapsed = if (commentId in collapsed) collapsed - commentId else collapsed + commentId
                            },
                            onOpenUser = onOpenUser,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopBar(title: String, trailing: String?, onBack: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { BackIcon(MaterialTheme.colorScheme.onSurface, Modifier.size(22.dp)) }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StoryHeader(item: Item, onOpenUser: (String) -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp)) {
        val title = item.title
        if (title != null) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        val url = when (item) {
            is Item.Story -> item.url
            is Item.Job -> item.url
            else -> null
        }
        val domain = when (item) {
            is Item.Story -> item.domain
            is Item.Job -> item.domain
            else -> null
        }
        if (domain != null) {
            Text(domain, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StoryMeta(item, Modifier.weight(1f, fill = false))
        }
        val by = item.by
        if (by != null) {
            Text(
                "View $by's profile",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { onOpenUser(by) }
                    .padding(vertical = 6.dp),
            )
        }
        val text = when (item) {
            is Item.Story -> item.text
            is Item.Job -> item.text
            is Item.Poll -> item.text
            is Item.Comment -> item.text
            is Item.PollOption -> item.text
            is Item.Unknown -> null
        }
        if (!text.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            HnHtmlText(text, style = MaterialTheme.typography.bodyLarge, maxLines = 14)
        }
        if (url != null) {
            OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Open link", maxLines = 1)
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun CommentItem(row: CommentRow, isOp: Boolean, onToggle: () -> Unit, onOpenUser: (String) -> Unit) {
    val comment = row.comment
    val colors = MaterialTheme.colorScheme
    val age by rememberRelativeTime(comment.time)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp)
            .commentIndent(row.depth)
            .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val by = comment.by
            if (by != null && !comment.deleted) {
                Text(
                    by,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isOp) HnOrange else colors.onSurface,
                    modifier = Modifier.clickable { onOpenUser(by) },
                    maxLines = 1,
                )
            }
            if (isOp && !comment.deleted) {
                Text("OP", style = MaterialTheme.typography.labelSmall, color = HnOrange, fontWeight = FontWeight.Bold)
            }
            Text(age, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(
                if (row.collapsed) "[+${row.hiddenReplies + 1}]" else "[–]",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (!row.collapsed) {
            when {
                comment.deleted -> RemovedText("[deleted]")
                comment.dead -> RemovedText("[flagged]")
                else -> HnHtmlText(
                    comment.text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (row.node.unloadedReplies > 0) {
                Text(
                    row.node.unloadedReplies.let { n -> if (n == 1) "1 more reply not loaded" else "$n more replies not loaded" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun RemovedText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
