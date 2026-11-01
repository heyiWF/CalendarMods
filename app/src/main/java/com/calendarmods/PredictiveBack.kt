package com.calendarmods

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One progress/cancel contract for fullscreen, store, edit mode and nested dialogs. */
@Composable
internal fun predictiveBackModifier(enabled: Boolean = true, onBack: () -> Unit): Modifier {
    val progress = remember { Animatable(0f) }
    var edge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }
    val currentBack by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { event ->
                edge = event.swipeEdge
                progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            // A committed gesture alone changes navigation or saves pending edits.
            currentBack()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { progress.animateTo(0f, tween(180)) }
            throw cancelled
        } finally {
            withContext(NonCancellable) { progress.snapTo(0f) }
        }
    }
    return Modifier.graphicsLayer {
        val p = progress.value
        scaleX = 1f - .08f * p
        scaleY = 1f - .08f * p
        translationX = (if (edge == BackEventCompat.EDGE_LEFT) 1 else -1) * 24.dp.toPx() * p
        shape = RoundedCornerShape((28f * p).dp)
        clip = p > 0f
    }
}

@Composable
internal fun PredictiveDialog(onDismiss: () -> Unit, enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = { if (enabled) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = false)) {
        val back = predictiveBackModifier { if (enabled) onDismiss() }
        Surface(modifier = back, shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
internal fun CalendarAlertDialog(modifier: Modifier = Modifier, onDismissRequest: () -> Unit,
    title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit, dismissButton: @Composable () -> Unit) {
    PredictiveDialog(onDismissRequest) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton(); confirmButton()
            }
        }
    }
}
