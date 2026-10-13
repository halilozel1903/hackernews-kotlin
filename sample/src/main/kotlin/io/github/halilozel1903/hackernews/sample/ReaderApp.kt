package io.github.halilozel1903.hackernews.sample

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.halilozel1903.hackernews.StoryList

/** A screen above the story list. */
sealed interface Route {
    val key: String

    data class Story(val id: Long) : Route {
        override val key: String get() = "story:$id"
    }

    data class User(val name: String) : Route {
        override val key: String get() = "user:$name"
    }

    companion object {
        fun parse(key: String): Route? = when {
            key.startsWith("story:") -> key.removePrefix("story:").toLongOrNull()?.let { Story(it) }
            key.startsWith("user:") -> User(key.removePrefix("user:"))
            else -> null
        }
    }
}

/** Saves the back stack as route keys, so it survives rotation and process death. */
private val BackStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { stack -> stack.map { it.key } },
    restore = { keys -> keys.mapNotNull { Route.parse(it) }.toMutableStateList() },
)

/**
 * The whole app: the story list on phones, which opens stories and profiles on top of it, and list and detail
 * side by side on wide screens. Everything loads from the live Hacker News API. Screenshot [scene]s open a fixed
 * screen and report to a [SceneMonitor] when their data has loaded.
 */
@Composable
fun ReaderApp(scene: Scene?, repository: ReaderRepository) {
    val monitor = remember(scene) { scene?.let { SceneMonitor(it) } }
    monitor?.Report()

    var feed by rememberSaveable { mutableStateOf(scene?.feed ?: StoryList.Top) }
    val backStack: SnapshotStateList<Route> = rememberSaveable(saver = BackStackSaver) {
        mutableStateListOf<Route>().apply { scene?.user?.let { add(Route.User(it)) } }
    }

    // Story scenes open the first front page story with a real discussion, picked live, once.
    var storyPicked by rememberSaveable { mutableStateOf(scene?.opensStory != true) }
    val pop: () -> Unit = { backStack.removeAt(backStack.lastIndex) }

    CompositionLocalProvider(LocalSceneMonitor provides monitor) {
        if (scene?.opensStory == true) {
            // Stays in composition, so the scene keeps counting this load after the story opens.
            val picked by produceState<Load<Long>>(Load.Loading, repository) {
                value = load { repository.storyWithDiscussion() }
            }
            ReportLoad(picked)
            LaunchedEffect(picked) {
                val id = (picked as? Load.Ready)?.value ?: return@LaunchedEffect
                if (!storyPicked) {
                    backStack.clear()
                    backStack.add(Route.Story(id))
                    storyPicked = true
                }
            }
        }

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            val wide = maxWidth >= 840.dp
            val open: (Route) -> Unit = { route -> backStack.add(route) }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    FeedScreen(
                        repository = repository,
                        feed = feed,
                        onFeedChange = { feed = it },
                        selectedId = (backStack.firstOrNull() as? Route.Story)?.id,
                        onOpenStory = { id ->
                            backStack.clear()
                            backStack.add(Route.Story(id))
                        },
                        modifier = Modifier.weight(0.44f),
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Box(Modifier.weight(0.56f)) {
                        val route = backStack.lastOrNull()
                        if (route == null) {
                            EmptyDetail()
                        } else {
                            val canGoBack = backStack.size > 1
                            BackHandler(enabled = canGoBack, onBack = pop)
                            RouteScreen(
                                route = route,
                                repository = repository,
                                onBack = if (canGoBack) pop else null,
                                onOpen = open,
                            )
                        }
                    }
                }
            } else {
                val route = backStack.lastOrNull()
                if (!storyPicked) {
                    // A story scene shows its story only; the list would be a different screen.
                    LoadingState()
                } else if (route == null) {
                    FeedScreen(
                        repository = repository,
                        feed = feed,
                        onFeedChange = { feed = it },
                        selectedId = null,
                        onOpenStory = { id -> backStack.add(Route.Story(id)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BackHandler(onBack = pop)
                    RouteScreen(
                        route = route,
                        repository = repository,
                        onBack = pop,
                        onOpen = open,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(route: Route, repository: ReaderRepository, onBack: (() -> Unit)?, onOpen: (Route) -> Unit) {
    key(route.key) {
        when (route) {
            is Route.Story -> StoryScreen(
                repository = repository,
                id = route.id,
                onBack = onBack,
                onOpenUser = { onOpen(Route.User(it)) },
            )
            is Route.User -> UserScreen(
                repository = repository,
                name = route.name,
                onBack = onBack,
                onOpenStory = { onOpen(Route.Story(it)) },
            )
        }
    }
}

@Composable
private fun EmptyDetail() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        HnLogo(size = 48.dp)
        Text(
            "Pick a story to read its discussion.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
