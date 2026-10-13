package io.github.halilozel1903.hackernews.sample

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Tells `scripts/screenshots.sh` when a screenshot scene is complete: everything on screen came from the live
 * Hacker News API. The script waits for the `SCENE_READY <scene>` line in logcat before it captures, and fails on
 * `SCENE_FAILED`, so an error state or a half loaded screen never ends up in the README. There is no fallback data.
 */
class SceneMonitor(private val scene: Scene) {
    private val loads = mutableStateMapOf<Any, Load<*>>()

    fun load(id: Any, state: Load<*>?) {
        if (state == null) loads.remove(id) else loads[id] = state
    }

    private sealed interface Status {
        data object Waiting : Status
        data object Ready : Status
        data class Failed(val reason: String) : Status
    }

    private fun status(): Status {
        val failed = loads.values.filterIsInstance<Load.Failed>().firstOrNull()
        return when {
            failed != null -> Status.Failed(failed.message)
            loads.size >= scene.loads && loads.values.all { it is Load.Ready } -> Status.Ready
            else -> Status.Waiting
        }
    }

    @Composable
    fun Report() {
        LaunchedEffect(this) {
            snapshotFlow { status() }.collectLatest { status ->
                when (status) {
                    Status.Waiting -> Unit
                    // Ready for a moment without anything new loading: layout has settled.
                    Status.Ready -> {
                        delay(1_500)
                        withFrameNanos { }
                        Log.i(TAG, "SCENE_READY ${scene.key}")
                    }
                    is Status.Failed -> Log.e(TAG, "SCENE_FAILED ${scene.key}: ${status.reason}")
                }
            }
        }
    }

    companion object {
        const val TAG = "HackerNewsScene"
    }
}

/** The monitor of the screenshot scene being shown, or `null` in normal use. */
val LocalSceneMonitor = staticCompositionLocalOf<SceneMonitor?> { null }

/** Reports [state] to the scene monitor, if there is one. */
@Composable
fun ReportLoad(state: Load<*>) {
    val monitor = LocalSceneMonitor.current ?: return
    val id = remember { Any() }
    DisposableEffect(monitor, id) { onDispose { monitor.load(id, null) } }
    LaunchedEffect(monitor, id, state) { monitor.load(id, state) }
}
