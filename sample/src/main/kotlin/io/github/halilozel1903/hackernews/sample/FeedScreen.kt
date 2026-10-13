package io.github.halilozel1903.hackernews.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.hackernews.StoryList
import io.github.halilozel1903.hackernews.compose.rememberRelativeTime
import io.github.halilozel1903.hackernews.model.Item
import io.github.halilozel1903.hackernews.model.StoryPage
import io.github.halilozel1903.hackernews.model.score
import io.github.halilozel1903.hackernews.model.title

/** The ranked story lists: Top, New, Best, Ask, Show and Jobs, loaded a page at a time. */
@Composable
fun FeedScreen(
    repository: ReaderRepository,
    feed: StoryList,
    onFeedChange: (StoryList) -> Unit,
    selectedId: Long?,
    onOpenStory: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reload by remember { mutableIntStateOf(0) }
    Column(modifier) {
        Row(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HnLogo(size = 30.dp)
            Text(
                "Hacker News",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                maxLines = 1,
            )
            TextButton(onClick = { reload++ }) { Text("Refresh") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            items(StoryList.entries, key = { it.name }) { list ->
                FilterChip(
                    selected = list == feed,
                    onClick = { onFeedChange(list) },
                    label = { Text(list.label) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        key(feed) {
            FeedList(repository, feed, reload, selectedId, onOpenStory, onRetry = { reload++ })
        }
    }
}

@Composable
private fun FeedList(
    repository: ReaderRepository,
    feed: StoryList,
    reload: Int,
    selectedId: Long?,
    onOpenStory: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val first by produceState<Load<StoryPage>>(Load.Loading, feed, reload) {
        value = Load.Loading
        value = load { repository.page(feed, 0, refresh = reload > 0) }
    }
    ReportLoad(first)
    when (val state = first) {
        Load.Loading -> LoadingState()
        is Load.Failed -> ErrorState(state.message, onRetry = onRetry)
        is Load.Ready -> {
            val pages = remember(state.value) { mutableStateListOf(state.value) }
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                pages.forEach { page ->
                    val ranked = page.items.mapIndexed { index, item -> (page.firstRank + index) to item }
                    items(ranked, key = { "${page.page}-${it.second.id}" }) { (rank, item) ->
                        StoryRow(rank, item, selected = item.id == selectedId, onClick = { onOpenStory(item.id) })
                    }
                }
                val last = pages.last()
                if (last.hasNext) {
                    item(key = "more-${last.page}") {
                        LoadMore(repository, feed, last.page + 1, onLoaded = { pages.add(it) })
                    }
                }
            }
        }
    }
}

/** Loads the next page as soon as it scrolls into view. */
@Composable
private fun LoadMore(repository: ReaderRepository, feed: StoryList, page: Int, onLoaded: (StoryPage) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(page, attempt) {
        error = null
        when (val result = load { repository.page(feed, page) }) {
            is Load.Ready -> onLoaded(result.value)
            is Load.Failed -> error = result.message
            Load.Loading -> Unit
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val message = error
        if (message == null) {
            CircularProgressIndicator(Modifier.size(28.dp))
        } else {
            TextButton(onClick = { attempt++ }) { Text("Could not load more. Try again") }
        }
    }
}

@Composable
fun StoryRow(rank: Int, item: Item, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.55f) else colors.background)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Text(
            "$rank.",
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.title ?: "Untitled",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val domain = when (item) {
                is Item.Story -> item.domain
                is Item.Job -> item.domain
                else -> null
            }
            if (domain != null) {
                Text(
                    domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            StoryMeta(item, Modifier.padding(top = 6.dp))
        }
    }
}

/** "▲ 123 points · by user · 3 hours ago · 45 comments" */
@Composable
fun StoryMeta(item: Item, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val age by rememberRelativeTime(item.time)
    val style = MaterialTheme.typography.labelMedium
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val score = item.score
        if (score != null && item !is Item.Job) {
            UpvoteIcon(HnOrange, Modifier.size(12.dp))
            Text(" ${pluralize(score, "point")}", style = style, color = colors.onSurfaceVariant, maxLines = 1)
            Text(" · ", style = style, color = colors.onSurfaceVariant)
        }
        Text(
            listOfNotNull(item.by?.let { "by $it" }, age.takeIf { it.isNotEmpty() }).joinToString(" · "),
            style = style,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        val comments = when (item) {
            is Item.Story -> item.descendants
            is Item.Poll -> item.descendants
            else -> null
        }
        if (comments != null) {
            Text(" · ", style = style, color = colors.onSurfaceVariant)
            CommentIcon(colors.onSurfaceVariant, Modifier.size(13.dp))
            Text(" $comments", style = style, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (item is Item.Job) {
            Text(" · job", style = style, color = colors.onSurfaceVariant, maxLines = 1)
        }
    }
}

fun pluralize(count: Int, word: String): String = "%,d %s%s".format(java.util.Locale.US, count, word, if (count == 1) "" else "s")
