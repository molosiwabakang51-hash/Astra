package com.astra.agent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Observatory lives only in an empty conversation. It combines one
 * memorable, locally drawn instrument with clear, real assistant actions.
 *
 * No auto-playing graphics or model loading: it is a lightweight Compose
 * welcome surface, not a separate app mode or an online service.
 */
internal object AstraWelcomePolicy {
    val commands = listOf(
        "Study",
        "Memory overview",
        "Show tasks",
        "Show notes"
    )

    fun show(roles: List<String>): Boolean = roles.none { it == "You" || it == "user" }

    fun animateForScale(scale: Float): Boolean = AstraAurora.motionEnabled(scale)

    /** Hide the duplicate built-in greeting only when the full welcome is visible. */
    fun hideInitialGreeting(showWelcome: Boolean, role: String, text: String): Boolean =
        showWelcome && role == "Astra" && text == "Hello. How can I help you?"
}

@Composable
internal fun AstraWelcomePanel(
    onOpenNavigation: () -> Unit,
    onOpenStudy: () -> Unit,
    onCommand: (String) -> Unit
) {
    val frame = RoundedCornerShape(29.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF15233B), Color(0xFF101A2C), AstraAurora.background)
                ), frame
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        AstraAurora.cyan.copy(alpha = 0.52f),
                        AstraAurora.violet.copy(alpha = 0.25f),
                        AstraAurora.cyan.copy(alpha = 0.10f)
                    )
                ), frame
            )
            .padding(horizontal = 17.dp, vertical = 17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(AstraAurora.mint, CircleShape))
            Spacer(Modifier.size(9.dp))
            Text(
                "ASTRA  /  OBSERVATORY",
                color = AstraAurora.mint,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "01 — 04",
                color = AstraAurora.muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your ideas,\nin motion.",
            color = AstraAurora.ink,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            lineHeight = 37.sp,
            letterSpacing = (-0.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask freely. Discover what you know. Turn questions into action.",
            color = AstraAurora.muted,
            style = MaterialTheme.typography.bodyMedium
        )

        // Instrument graphic: one clear point of interest, not a competing
        // list of fake statistics or network-dependent visual resources.
        Box(
            modifier = Modifier.fillMaxWidth().height(203.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cy = center.y
                val cx = center.x
                drawLine(
                    AstraAurora.cyan.copy(alpha = 0.11f),
                    Offset(0f, cy), Offset(size.width, cy),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    AstraAurora.cyan.copy(alpha = 0.08f),
                    Offset(cx, 8.dp.toPx()), Offset(cx, size.height - 8.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                val centerGlow = Brush.radialGradient(
                    listOf(
                        AstraAurora.cyan.copy(alpha = 0.11f),
                        AstraAurora.violet.copy(alpha = 0.035f),
                        Color.Transparent
                    ), center = center, radius = size.height * 0.51f
                )
                drawCircle(centerGlow, radius = size.height * 0.51f)
                drawCircle(
                    AstraAurora.cyan.copy(alpha = 0.12f),
                    radius = size.height * 0.46f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                )
                for (side in listOf(-1f, 1f)) {
                    val edge = cx + side * size.height * 0.50f
                    drawCircle(
                        AstraAurora.violet.copy(alpha = 0.72f),
                        radius = 2.dp.toPx(),
                        center = Offset(edge, cy)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .semantics {
                        contentDescription = "Open Astra navigation and workspace"
                    }
                    .clickable(onClick = onOpenNavigation),
                contentAlignment = Alignment.Center
            ) {
                AstraOrb(diameter = 176.dp)
            }
            Text(
                "TOUCH THE CORE",
                modifier = Modifier.align(Alignment.BottomCenter),
                color = AstraAurora.muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CHOOSE A PATH",
                color = AstraAurora.mint,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "LOCAL WORKSPACE",
                color = AstraAurora.muted,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // An asymmetrical, purposeful quick-action system. Every card maps
        // to an existing command; no tile silently starts a model-only lesson.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AstraObservatoryAction(
                glyph = "✦", title = "Study", subtitle = "Choose a subject",
                command = AstraWelcomePolicy.commands[0],
                onCommand = { onOpenStudy() },
                modifier = Modifier.weight(1.16f),
                accent = AstraAurora.cyan, featured = true
            )
            AstraObservatoryAction(
                glyph = "◎", title = "Recall", subtitle = "Local memory",
                command = AstraWelcomePolicy.commands[1],
                onCommand = { onCommand(AstraWelcomePolicy.commands[1]) },
                modifier = Modifier.weight(0.84f),
                accent = AstraAurora.violet, featured = true
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AstraObservatoryAction(
                glyph = "⌁", title = "Tasks", subtitle = "Track progress",
                command = AstraWelcomePolicy.commands[2],
                onCommand = { onCommand(AstraWelcomePolicy.commands[2]) },
                modifier = Modifier.weight(1f),
                accent = AstraAurora.mint
            )
            AstraObservatoryAction(
                glyph = "▥", title = "Notes", subtitle = "Capture an idea",
                command = AstraWelcomePolicy.commands[3],
                onCommand = { onCommand(AstraWelcomePolicy.commands[3]) },
                modifier = Modifier.weight(1f),
                accent = AstraAurora.cyan
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "The conversation takes over when you start chatting.",
            modifier = Modifier.fillMaxWidth(),
            color = AstraAurora.muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun AstraObservatoryAction(
    glyph: String,
    title: String,
    subtitle: String,
    command: String,
    onCommand: () -> Unit,
    modifier: Modifier,
    accent: Color,
    featured: Boolean = false
) {
    val shape = RoundedCornerShape(if (featured) 21.dp else 17.dp)
    Column(
        modifier = modifier
            .heightIn(min = if (featured) 108.dp else 84.dp)
            .background(
                Brush.linearGradient(
                    if (featured)
                        listOf(accent.copy(alpha = 0.19f), AstraAurora.raised)
                    else
                        listOf(AstraAurora.raised, AstraAurora.surface)
                ), shape
            )
            .border(1.dp, accent.copy(alpha = if (featured) 0.45f else 0.25f), shape)
            .semantics { contentDescription = "$title. $subtitle. $command" }
            .clickable(onClick = onCommand)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(glyph, color = accent, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Text("↗", color = accent, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            color = AstraAurora.ink,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            color = AstraAurora.muted,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
