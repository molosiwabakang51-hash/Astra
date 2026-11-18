package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Fit one subject card per row on narrow devices or when Android fonts grow.
 * Maintain two columns only when the screen can genuinely fit them.
 */
internal object AstraStudyLayout {
    fun columnsFor(widthDp: Float, fontScale: Float): Int =
        if (!widthDp.isFinite() || !fontScale.isFinite() ||
            widthDp < 390f || fontScale >= 1.3f
        ) 1 else 2
}

/**
 * Study button navigates to this catalog only. A separate explicit topic tap
 * begins a lesson. AI-only lessons clearly identify their GGUF dependency.
 */
@Composable
internal fun AstraStudyHub(
    onBackToChat: () -> Unit,
    onLesson: (AstraStudyCatalog.Topic) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = AstraStudyCatalog.find(selectedId)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = { if (selected == null) onBackToChat() else selectedId = null }) {
                Text(if (selected == null) "‹  Chat" else "‹  All subjects",
                    fontWeight = FontWeight.SemiBold)
            }
            Text("LOCAL STUDY", color = AstraAurora.mint,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        if (selected == null) {
            Text("What would you like to learn?",
                color = AstraAurora.ink,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text("Choose a subject. Lessons only begin when you select a topic.",
                color = AstraAurora.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(15.dp))
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = AstraStudyLayout.columnsFor(maxWidth.value, fontScale)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(AstraStudyCatalog.subjects.chunked(columns)) { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { subject ->
                                AstraStudySubjectCard(
                                    subject = subject,
                                    accent = when (AstraStudyCatalog.subjects.indexOf(subject) % 3) {
                                        0 -> AstraAurora.cyan
                                        1 -> AstraAurora.violet
                                        else -> AstraAurora.mint
                                    },
                                    compact = columns == 1,
                                    modifier = Modifier.weight(1f),
                                    onClick = { selectedId = subject.id }
                                )
                            }
                            if (pair.size == 1 && columns == 2) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Text(selected.title, color = AstraAurora.ink,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(selected.description, color = AstraAurora.muted,
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Text("CHOOSE A TOPIC", color = AstraAurora.mint,
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(selected.topics) { topic ->
                    AstraStudyTopicCard(topic) { onLesson(topic) }
                }
                item {
                    Text(
                        "Built-in lessons and calculations run offline. " +
                            "Topics marked LOCAL AI need an installed GGUF model. " +
                            "No live market data is implied.",
                        color = AstraAurora.muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AstraStudySubjectCard(
    subject: AstraStudyCatalog.Subject,
    accent: Color,
    modifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
.heightIn(min = if (compact) 94.dp else 135.dp)
            .background(Brush.linearGradient(listOf(AstraAurora.raised, AstraAurora.surface)), shape)
            .border(1.dp, accent.copy(alpha = 0.37f), shape)
            .semantics {
                contentDescription = "Study " + subject.title + ". " + subject.description
            }
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    subject.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        subject.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AstraAurora.ink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        subject.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AstraAurora.muted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("›", color = accent, style = MaterialTheme.typography.titleLarge)
            }
        } else {
            Text(
                subject.symbol, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = accent
            )
            Spacer(Modifier.height(9.dp))
            Text(
                subject.title, style = MaterialTheme.typography.titleMedium,
                color = AstraAurora.ink, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subject.description, style = MaterialTheme.typography.bodySmall,
                color = AstraAurora.muted, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AstraStudyTopicCard(topic: AstraStudyCatalog.Topic, onClick: () -> Unit) {
    val shape = RoundedCornerShape(17.dp)
    val accent = if (topic.requiresModel) AstraAurora.violet else AstraAurora.mint
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(AstraAurora.surface, shape)
            .border(1.dp, accent.copy(alpha = 0.32f), shape)
            .semantics {
                contentDescription = "Start " + topic.title + ". " + topic.description
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(topic.title, color = AstraAurora.ink,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(if (topic.requiresModel) "LOCAL AI" else "OFFLINE",
                color = accent, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(5.dp))
        Text(topic.description, color = AstraAurora.muted,
            style = MaterialTheme.typography.bodyMedium)
    }
}
