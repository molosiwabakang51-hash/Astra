package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Astra's lightweight, left-edge navigation sheet. Destinations are a
 * continuous typographic list with a single rounded active indicator,
 * rather than a stack of unrelated buttons or boxed cards.
 *
 * The modal overlay and screen-reader labels retain the behaviour of the
 * former popup; every route and chat action still has a direct control.
 */
internal enum class AstraTrayAction(val label: String) {
    FIND_IN_CHAT("Find in conversation"),
    MEMORY_SEARCH("Search local memory"),
    COPY_CHAT("Copy conversation"),
    SHARE_CHAT("Share conversation"),
    NEW_CONVERSATION("New conversation"),
    CONVERSATIONS("All conversations"),
    RENAME_CHAT("Rename conversation"),
    MISSION_CONTROL("Mission control"),
    READ_LAST_REPLY("Read last answer aloud"),
    CLEAR_CHAT("Clear conversation")
}

internal object AstraTrayPolicy {
    val destinations = listOf(
        "Chat" to "Your conversations",
        "Tasks" to "Plans and priorities",
        "Notes" to "Capture ideas",
        "Tools" to "Local utilities",
        "Settings" to "Personalize Astra",
        "Study" to "Explore subjects"
    )

    val conversationActions = listOf(
        AstraTrayAction.NEW_CONVERSATION,
        AstraTrayAction.CONVERSATIONS,
        AstraTrayAction.FIND_IN_CHAT,
        AstraTrayAction.MEMORY_SEARCH,
        AstraTrayAction.RENAME_CHAT,
        AstraTrayAction.COPY_CHAT,
        AstraTrayAction.SHARE_CHAT,
        AstraTrayAction.MISSION_CONTROL,
        AstraTrayAction.READ_LAST_REPLY,
        AstraTrayAction.CLEAR_CHAT
    )

    fun isDestructive(action: AstraTrayAction) =
        action == AstraTrayAction.CLEAR_CHAT
}

@Composable
internal fun AstraNavigationTray(
    expanded: Boolean,
    selectedTab: Int,
    canReadLastReply: Boolean,
    missionControlExpanded: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (Int) -> Unit,
    onAction: (AstraTrayAction) -> Unit
) {
    if (!expanded) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            // Tap the softly dimmed workspace to return to the conversation.
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xD9080C18))
                    .semantics { contentDescription = "Close Astra navigation" }
                    .clickable(onClick = onDismiss)
            )
            val sheetShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.86f)
                    .widthIn(max = 350.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF172941),
                                Color(0xFF101B2F),
                                AstraAurora.background
                            )
                        ),
                        sheetShape
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                AstraAurora.cyan.copy(alpha = 0.34f),
                                AstraAurora.violet.copy(alpha = 0.13f),
                                Color.Transparent
                            )
                        ),
                        sheetShape
                    )
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // An open hero, not another navigation card.
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    AstraAurora.cyan.copy(alpha = 0.15f),
                                    AstraAurora.violet.copy(alpha = 0.075f),
                                    Color.Transparent
                                ),
                                radius = 470f
                            )
                        )
                        .padding(start = 22.dp, end = 20.dp, top = 23.dp, bottom = 17.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        AstraOrb(diameter = 64.dp, compact = true)
                        Column(Modifier.weight(1f)) {
                            Text(
                                "ASTRA", color = AstraAurora.ink,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "YOUR PRIVATE WORKSPACE",
                                color = AstraAurora.mint,
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = 0.7.sp
                            )
                        }
                        Box(
                            // The visible button and touch area are both 48dp.
                            // Do not force users to accurately hit a tiny
                            // decorative close glyph in the modal tray.
                            Modifier.size(48.dp)
                                .background(AstraAurora.raised, CircleShape)
                                .semantics { contentDescription = "Close navigation" }
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", color = AstraAurora.ink,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.height(19.dp))
                    Text(
                        "Find your flow.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = AstraAurora.ink
                    )
                    Text(
                        "One place for every thought and task.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AstraAurora.muted
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp, end = 12.dp, bottom = 14.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        AstraTraySectionHeading("WORKSPACES")
                        Spacer(Modifier.height(5.dp))
                    }
                    items(AstraTrayPolicy.destinations.indices.toList()) { index ->
                        val (label, detail) = AstraTrayPolicy.destinations[index]
                        val selected = selectedTab == index
                        val accent = if (index % 3 == 0) AstraAurora.cyan
                            else if (index % 3 == 1) AstraAurora.violet
                            else AstraAurora.mint
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 58.dp)
                                .background(
                                    if (selected) Brush.horizontalGradient(
                                        listOf(
                                            accent.copy(alpha = 0.22f),
                                            accent.copy(alpha = 0.065f)
                                        )
                                    ) else Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color.Transparent)
                                    ),
                                    RoundedCornerShape(19.dp)
                                )
                                .semantics {
                                    contentDescription =
                                        "$label. $detail." +
                                            if (selected) " Current destination." else ""
                                }
                                .clickable { onNavigate(index) }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.width(3.dp).height(27.dp)
                                    .background(
                                        if (selected) accent else Color.Transparent,
                                        RoundedCornerShape(50)
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            AstraNavigationGlyph(
                                AstraNavSection.entries[index],
                                if (selected) accent else AstraAurora.muted
                            )
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    label, color = AstraAurora.ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold
                                        else FontWeight.Medium
                                )
                                Text(
                                    detail, color = AstraAurora.muted,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (selected) {
                                Text("•", color = accent,
                                    style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                    if (selectedTab == 0) {
                        item {
                            Spacer(Modifier.height(14.dp))
                            HorizontalDivider(
                                color = AstraAurora.cyan.copy(alpha = 0.15f),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            AstraTraySectionHeading("CONVERSATION")
                            Spacer(Modifier.height(4.dp))
                        }
                        items(AstraTrayPolicy.conversationActions) { action ->
                            val disabled =
                                action == AstraTrayAction.READ_LAST_REPLY && !canReadLastReply
                            val accent = when {
                                AstraTrayPolicy.isDestructive(action) -> Color(0xFFFF9EAB)
                                action == AstraTrayAction.NEW_CONVERSATION -> AstraAurora.mint
                                else -> AstraAurora.muted
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 49.dp)
                                    .semantics {
                                        contentDescription = when (action) {
                                            AstraTrayAction.MISSION_CONTROL ->
                                                (if (missionControlExpanded) "Hide" else "Show") +
                                                    " mission control"
                                            else -> action.label
                                        }
                                    }
                                    .clickable(enabled = !disabled) { onAction(action) }
                                    .padding(start = 24.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(7.dp)
                                        .background(
                                            accent.copy(alpha = if (disabled) 0.65f else 0.8f),
                                            CircleShape
                                        )
                                )
                                Spacer(Modifier.width(17.dp))
                                Text(
                                    if (action == AstraTrayAction.MISSION_CONTROL)
                                        (if (missionControlExpanded) "Hide mission control"
                                         else "Show mission control")
                                    else action.label,
                                    modifier = Modifier.weight(1f),
                                    color = if (disabled) AstraAurora.muted.copy(alpha = 0.45f)
                                            else if (AstraTrayPolicy.isDestructive(action))
                                                Color(0xFFFFB6C0)
                                            else AstraAurora.ink,
                                    fontWeight = if (action == AstraTrayAction.NEW_CONVERSATION)
                                        FontWeight.SemiBold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (action == AstraTrayAction.NEW_CONVERSATION) {
                                    Text("+", color = AstraAurora.mint,
                                        style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth()
                        .padding(start = 23.dp, end = 18.dp, top = 10.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(AstraAurora.mint, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "ON-DEVICE WORKSPACE",
                        color = AstraAurora.muted,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AstraTraySectionHeading(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 2.dp),
        color = AstraAurora.mint,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}
