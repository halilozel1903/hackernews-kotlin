package io.github.halilozel1903.hackernews.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

/**
 * A Hacker News reader built on hackernews-kotlin. It reads the live Hacker News API. `scripts/screenshots.sh`
 * starts it with `--es scene <scene>` to open a fixed screen, loaded live like a normal launch:
 *
 * - `top`: the Top stories list
 * - `story`: the first top story with a real discussion, and its comment tree
 * - `ask`: the Ask HN list
 * - `user`: the profile of `pg` with his latest submissions
 * - `tablet`: Top stories and a story's comments side by side (tablets in landscape)
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = Scene.from(intent.getStringExtra(EXTRA_SCENE))
        setContent {
            ReaderTheme(dark = isSystemInDarkTheme()) {
                // The Surface makes text default to onBackground, so it stays readable in dark mode.
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    ReaderApp(
                        scene = scene,
                        repository = if (scene != null) Repositories.scenes() else Repositories.live(),
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCENE = "scene"
    }
}
