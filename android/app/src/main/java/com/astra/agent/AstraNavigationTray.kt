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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Intentional, non-destructive actions available when viewing Chat. */
internal enum class AstraTrayAction(
    val title: String,
    val description: String
) {
    NEW_CHAT("New conversation", "Begin a fresh thread"),
    CONVERSATIONS("Your conversations", "Revisit saved discussions"),
    FIND_CHAT("Find in chat", "Search this thread"),
    MEMORY_SEARCH("Search local memory", "Locate earlier information"),
    RENAME_CHAT("Rename conversation", "Make this thread easier to find"),
    COPY_CHAT("Copy conversation", "Copy to your clipboard"),
    SHARE_CHAT("Share conversation", "Choose an Android share destination"),
    MISSION("Mission control", "Tasks, progress and shortcuts"),
    READ_ALOUD("Read latest answer", "Speak the last Astra reply"),
    CLEAR_CHAT("Clear current chat", "Requires confirmation")
}

/**
 * A distinctive, compact left-side sheet inspired by Material navigation
 * hierarchy, not a stack of outlined rectangular cards. The selected
 * workspace is the single emphasized pill. All navigation and destructive
 * actions retain visible text, semantic labels and >= 48dp touch targets.
 *
 * Pure Compose vector graphics; no bundled imagery, remote APIs, or assets.
 */
@Composable
internal fun AstraNavigationTray(
    selected: AstraNavSection,
    hasAssistantReply: Boolean,
    missionControlExpanded: Boolean,
    onNavigate: (AstraNavSection) -> Unit,
    onAction: (AstraTrayAction) -> Unit,
    onClose: () -> Unit
) {
    val curvedEdge = RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp).fillMaxHeight(),
        drawerShape = curvedEdge,
        drawerContainerColor = AstraAurora.night,
        drawerTonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF14253A), AstraAurora.night, Color(0xFF080D1C))
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 17.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().height(162.dp)
            ) {
                // Quiet orbital geometry evokes an instrument panel rather
                // than crowding navigation with decorative square tiles.
                Canvas(Modifier.fillMaxWidth().height(162.dp)) {
                    val centerPoint = Offset(size.width * 0.82f, size.height * 0.48f)
                    drawCircle(
                        AstraAurora.cyan.copy(alpha = 0.075f),
                        radius = size.height * 0.44f,
                        center = centerPoint
                    )
                    for (n in 0..2) {
                        drawCircle(
                            if (n == 1) AstraAurora.violet.copy(alpha = 0.19f)
                            else AstraAurora.cyan.copy(alpha = 0.12f),
                            radius = size.height * (0.22f + n * 0.11f),
                            center = centerPoint,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                    drawLine(
                        AstraAurora.mint.copy(alpha = 0.34f),
                        Offset(size.width * 0.18f, size.height * 0.87f),
                        Offset(size.width * 0.87f, size.height * 0.87f),
                        strokeWidth = 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalArrangement = Arrangement.Center
                ) {
                    AstraOrb(diameter = 55.dp, compact = true)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Astra",
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                        color = AstraAurora.ink
                    )
                    Text(
                        "A PRIVATE INTELLIGENCE SPACE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.85.sp,
                        color = AstraAurora.mint
                    )
                }
                Text(
                    "✕",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = "Close Astra navigation" }
                        .clickable(onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    fontSize = 18.sp,
                    color = AstraAurora.ink
                )
            }
            Spacer(Modifier.height(14.dp))
            AstraTraySectionLabel("WORKSPACES")
            Spacer(Modifier.height(7.dp))

            for (section in listOf(
                AstraNavSection.CHAT, AstraNavSection.STUDY,
                AstraNavSection.TASKS, AstraNavSection.NOTES,
                AstraNavSection.TOOLS, AstraNavSection.SETTINGS
            )) {
                AstraTrayDestinationRow(
                    section = section,
                    selected = section == selected,
                    onClick = { onNavigate(section) }
                )
                Spacer(Modifier.height(3.dp))
            }

            if (selected == AstraNavSection.CHAT) {
                Spacer(Modifier.height(17.dp))
                HorizontalDivider(
                    color = AstraAurora.cyan.copy(alpha = 0.14f)
                )
                Spacer(Modifier.height(15.dp))
                AstraTraySectionLabel("THIS CONVERSATION")
                Spacer(Modifier.height(8.dp))
                for (action in AstraTrayAction.entries) {
                    val disabled =
                        action == AstraTrayAction.READ_ALOUD && !hasAssistantReply
                    AstraTrayActionRow(
                        action = action,
                        enabled = !disabled,
                        title = if (action == AstraTrayAction.MISSION &&
                            missionControlExpanded
                        ) "Hide mission control" else action.title,
                        onClick = { onAction(action) }
                    )
                }
            }
            Spacer(Modifier.height(25.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        AstraAurora.cyan.copy(alpha = 0.055f),
                        RoundedCornerShape(23.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(7.dp)
                        .background(AstraAurora.mint, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "LOCAL-FIRST WORKSPACE",
                        color = AstraAurora.mint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        "Chats and notes stay on this device.",
                        color = AstraAurora.muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(19.dp))
        }
    }
}

@Composable
private fun AstraTraySectionLabel(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 13.dp),
        color = AstraAurora.muted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.45.sp
    )
}

@Composable
private fun AstraTrayDestinationRow(
    section: AstraNavSection,
    selected: Boolean,
    onClick: () -> Unit
) {
    val (title, subtitle) = when (section) {
        AstraNavSection.CHAT -> "Chat" to "Your ideas, explored"
        AstraNavSection.STUDY -> "Study" to "Learn at your pace"
        AstraNavSection.TASKS -> "Tasks" to "Make progress visible"
        AstraNavSection.NOTES -> "Notes" to "Keep what matters"
        AstraNavSection.TOOLS -> "Tools" to "Useful local utilities"
        AstraNavSection.SETTINGS -> "Settings" to "Customize your space"
    }
    val tint = when (section) {
        AstraNavSection.STUDY, AstraNavSection.TOOLS -> AstraAurora.violet
        AstraNavSection.TASKS, AstraNavSection.SETTINGS -> AstraAurora.mint
        else -> AstraAurora.cyan
    }
    val selectedShape = RoundedCornerShape(23.dp)
    val background = if (selected) {
        Brush.horizontalGradient(
            listOf(tint.copy(alpha = 0.19f), AstraAurora.violet.copy(alpha = 0.065f))
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(background, selectedShape)
            .then(
                if (selected) Modifier.border(
                    1.dp, tint.copy(alpha = 0.24f), selectedShape
                ) else Modifier
            )
            .semantics {
                contentDescription = if (selected)
                    "$title, selected. $subtitle"
                else "$title. $subtitle"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier.size(36.dp)
                        .background(tint.copy(alpha = 0.09f), CircleShape)
                )
            }
            AstraNavigationGlyph(
                section = section,
                tint = if (selected) tint else AstraAurora.muted
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = AstraAurora.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                color = AstraAurora.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Box(Modifier.size(6.dp).background(tint, CircleShape))
        } else {
            Text("›", fontSize = 23.sp, color = AstraAurora.muted)
        }
    }
}

@Composable
private fun AstraTrayActionRow(
    action: AstraTrayAction,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 49.dp)
            .semantics {
                contentDescription = title + ". " + action.description
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 15.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(5.dp).background(
                if (enabled) AstraAurora.violet.copy(alpha = 0.72f)
                else AstraAurora.muted.copy(alpha = 0.25f),
                CircleShape
            )
        )
        Spacer(Modifier.width(15.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = if (enabled) AstraAurora.ink else AstraAurora.muted,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "›",
            fontSize = 20.sp,
            color = AstraAurora.muted.copy(alpha = if (enabled) 0.85f else 0.3f)
        )
    }
}
