package com.astra.agent

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
 import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sci-fi command deck. Status labels are deliberately factual; it does not
 * claim autonomous capabilities, model inference or connectivity it lacks.
 */
internal object AstraDeck {
    val prompts = listOf(
        "Mission control",
        "Workflow help",
        "What's next",
        "Memory overview",
        "Show tasks",
        "Plan my day",
        "Russian quiz",
        "Show notes",
        "Bond analyze 1000 5 6 10 2",
        "Research the solar system",
        "Help"
    )

    fun completionPercent(open: Int, done: Int): Int {
        val safeOpen = open.coerceAtLeast(0)
        val safeDone = done.coerceAtLeast(0)
        val total = safeOpen.toLong() + safeDone.toLong()
        return if (total == 0L) 0 else (safeDone.toLong() * 100L / total).toInt()
    }

    fun status(
        listening: Boolean,
        researching: Boolean,
        awaitingReply: Boolean = false
    ): String = when {
        listening -> "VOICE INPUT ACTIVE"
        researching -> "RESEARCH IN PROGRESS"
        awaitingReply -> "AWAITING YOUR RESPONSE"
        else -> "LOCAL SYSTEM READY"
    }
}

@Composable
internal fun AstraCommandDeck(
    listening: Boolean,
    researching: Boolean,
    openTasks: Int,
    completedTasks: Int,
    storedNotes: Int,
    awaitingReply: Boolean = false,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCommand: (String) -> Unit
) {
    val status = AstraDeck.status(listening, researching, awaitingReply)
    val context = LocalContext.current
    val motion = remember(context) {
        AstraAurora.motionEnabled(runCatching {
            Settings.Global.getFloat(
                context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            )
        }.getOrDefault(1f))
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF142A42), Color(0xFF0C1729))
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .border(1.dp, AstraAurora.cyan.copy(alpha = 0.22f), shape)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        // The collapsed surface remains a single accessible touch target.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "Mission Control, $status" }
                .clickable { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MISSION CONTROL",
                    color = AstraAurora.ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    status,
                    color = AstraAurora.mint,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (expanded) "⌃" else "⌄",
                    color = Color(0xFFBBDFFF),
                    style = MaterialTheme.typography.titleMedium
                )
                if (!expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "OPEN $openTasks  ·  NOTES $storedNotes",
                        color = Color(0xFF92B7D2),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (motion) expandVertically(animationSpec = tween(240)) +
                fadeIn(animationSpec = tween(180)) else EnterTransition.None,
            exit = if (motion) shrinkVertically(animationSpec = tween(220)) +
                fadeOut(animationSpec = tween(150)) else ExitTransition.None
        ) {
            Column {
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AstraDeckMetric("OPEN", openTasks, Modifier.weight(1f))
                    AstraDeckMetric("DONE", completedTasks, Modifier.weight(1f))
                    AstraDeckMetric("NOTES", storedNotes, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TASK COMPLETION",
                        color = AstraAurora.muted,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${AstraDeck.completionPercent(openTasks, completedTasks)}%",
                        color = AstraAurora.ink,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(7.dp))
                // Static progress indicator: no infinite scanline, no idle battery draw.
                Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
                    val y = size.height / 2f
                    drawLine(
                        Color(0xFF1D4564),
                        Offset(0f, y),
                        Offset(size.width, y),
                        2.dp.toPx()
                    )
                    val progress =
                        AstraDeck.completionPercent(openTasks, completedTasks) / 100f
                    drawLine(
                        Color(0xFF238FCB).copy(alpha = 0.64f),
                        Offset(0f, y),
                        Offset(size.width * progress, y),
                        3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    "QUICK COMMANDS  ·  SWIPE FOR MORE",
                    color = AstraAurora.muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (if (awaitingReply) listOf("Cancel") + AstraDeck.prompts
                        else AstraDeck.prompts).forEach { command ->
                        Text(
                            text = command,
                            modifier = Modifier
                                .background(Color(0xFF20364F), RoundedCornerShape(50))
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = "Run $command" }
                                .clickable { onCommand(command) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = Color(0xFFE4F5FF),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
            }
        }
    }
}

@Composable
private fun AstraDeckMetric(label: String, value: Int, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(AstraAurora.raised, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = AstraAurora.muted,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            value.coerceAtLeast(0).toString(),
            color = AstraAurora.ink,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
