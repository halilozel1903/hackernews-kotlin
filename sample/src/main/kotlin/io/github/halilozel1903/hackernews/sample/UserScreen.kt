package io.github.halilozel1903.hackernews.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.hackernews.compose.HnHtmlText
import io.github.halilozel1903.hackernews.compose.rememberRelativeTime
import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.title
import io.github.halilozel1903.hackernews.text.HnHtml

/** A user's karma, age, about text and newest submissions. */
@Composable
fun UserScreen(repository: ReaderRepository, name: String, onBack: (() -> Unit)?, onOpenStory: (Long) -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    val detail by produceState<Load<UserDetail>>(Load.Loading, name, reload) {
        value = Load.Loading
        value = load { repository.user(name) }
    }
    ReportLoad(detail)
    Column(Modifier.fillMaxSize()) {
        TopBar(title = if (detail is Load.Ready) "Profile" else "", trailing = null, onBack = onBack)
        when (val state = detail) {
            Load.Loading -> LoadingState()
            is Load.Failed -> ErrorState(state.message, onRetry = { reload++ })
            is Load.Ready -> {
                val user = state.value.user
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item(key = "header") {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(HnOrange),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        user.id.take(1).uppercase(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(Modifier.padding(start = 16.dp)) {
                                    Text(user.id, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    val joined by rememberRelativeTime(user.created)
                                    Text(
                                        "%,d karma · joined %s".format(java.util.Locale.US, user.karma, joined),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            val about = user.about
                            if (!about.isNullOrBlank()) {
                                HnHtmlText(
                                    about,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(top = 16.dp),
                                    maxLines = 12,
                                )
                            }
                            Text(
                                "Recent submissions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    if (state.value.submissions.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "Nothing public yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                    items(state.value.submissions, key = { it.id }) { item ->
                        SubmissionRow(item, onClick = { onOpenStory(item.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubmissionRow(item: Item, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val age by rememberRelativeTime(item.time)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        val kind = when (item) {
            is Item.Comment -> "Comment"
            is Item.Story -> "Story"
            is Item.Poll -> "Poll"
            is Item.Job -> "Job"
            else -> "Item"
        }
        Text(
            "$kind · $age",
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            maxLines = 1,
        )
        val title = item.title
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            StoryMeta(item, Modifier.padding(top = 4.dp))
        } else if (item is Item.Comment) {
            // Plain text keeps the row short; the full comment opens on tap.
            Text(
                remember(item.text) { HnHtml.toPlainText(item.text.orEmpty()) },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
