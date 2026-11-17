package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A unique, expressive start surface without sacrificing chat space.
 * It disappears after the first user turn and its actions execute existing
 * local commands, never pretend that an uninstalled model is responding.
 */
internal object AstraWelcomePolicy {
    val commands = listOf(
        "Teach me finance",
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
    onCommand: (String) -> Unit
) {
    val card = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF16243B),
                        Color(0xFF12233E),
                        Color(0xFF101629)
                    )
                ),
                shape = card
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        AstraAurora.cyan.copy(alpha = 0.43f),
                        AstraAurora.violet.copy(alpha = 0.22f),
                        AstraAurora.cyan.copy(alpha = 0.08f)
                    )
                ),
                shape = card
            )
            .padding(horizontal = 17.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "YOUR SPACE TO THINK",
            color = AstraAurora.mint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .size(148.dp)
                .semantics { contentDescription = "Open Astra navigation and workspace" }
                .clickable(onClick = onOpenNavigation),
            contentAlignment = Alignment.Center
        ) {
            AstraOrb(diameter = 146.dp)
        }
        Text(
            "Ideas, in your orbit.",
            color = AstraAurora.ink,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Ask anything. Explore your knowledge. Move from thought to action.",
            color = AstraAurora.muted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AstraStarterAction("✦", "Study", "Finance & concepts",
                AstraWelcomePolicy.commands[0], onCommand, Modifier.weight(1f), AstraAurora.cyan)
            AstraStarterAction("◎", "Recall", "Private memory",
                AstraWelcomePolicy.commands[1], onCommand, Modifier.weight(1f), AstraAurora.violet)
        }
        Spacer(Modifier.height(9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            AstraStarterAction("☷", "Tasks", "Your next move",
                AstraWelcomePolicy.commands[2], onCommand, Modifier.weight(1f), AstraAurora.mint)
            AstraStarterAction("▤", "Notes", "Your ideas, saved",
                AstraWelcomePolicy.commands[3], onCommand, Modifier.weight(1f), AstraAurora.cyan)
        }
        Spacer(Modifier.height(13.dp))
        Text(
            "Tap the core to open your workspace",
            color = AstraAurora.muted.copy(alpha = 0.82f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AstraStarterAction(
    glyph: String,
    title: String,
    subtitle: String,
    command: String,
    onCommand: (String) -> Unit,
    modifier: Modifier,
    accent: Color
) {
    val shape = RoundedCornerShape(17.dp)
    Row(
        modifier = modifier
            .heightIn(min = 68.dp)
            .background(AstraAurora.raised.copy(alpha = 0.81f), shape)
            .border(1.dp, accent.copy(alpha = 0.19f), shape)
            .semantics { contentDescription = "$title: $subtitle. $command" }
            .clickable { onCommand(command) }
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            glyph,
            color = accent,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
        Column {
            Text(
                title,
                color = AstraAurora.ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                subtitle,
                color = AstraAurora.muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2
            )
        }
    }
}
