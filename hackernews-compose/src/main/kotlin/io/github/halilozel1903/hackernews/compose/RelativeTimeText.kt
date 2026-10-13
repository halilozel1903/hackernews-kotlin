package io.github.halilozel1903.hackernews.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import io.github.halilozel1903.hackernews.text.RelativeTime
import kotlinx.coroutines.delay

/**
 * "3 hours ago" for [epochSeconds], updated when the text changes (every minute for recent items, less often for
 * older ones) while it is in composition.
 */
@Composable
public fun rememberRelativeTime(epochSeconds: Long, style: RelativeTime.Style = RelativeTime.Style.Full): State<String> =
    produceState(RelativeTime.format(epochSeconds, style = style), epochSeconds, style) {
        while (true) {
            val now = RelativeTime.nowSeconds()
            value = RelativeTime.format(epochSeconds, now, style)
            delay(RelativeTime.secondsUntilChange(epochSeconds, now) * 1_000)
        }
    }
