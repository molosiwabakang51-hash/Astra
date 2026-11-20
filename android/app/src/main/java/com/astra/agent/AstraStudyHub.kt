package com.astra.agent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
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
            widthDp < 390f || fontScale <= 0f || fontScale >= 1.3f
        ) 1 else 2
}

/**
 * Pure, ordered topic search. No online service, model loading or persistent
 * query history. The bounded search avoids a large input monopolizing UI.
 */
internal object AstraStudyTopicFilter {
    fun apply(topics: List<AstraStudyCatalog.Topic>, rawQuery: String): List<AstraStudyCatalog.Topic> {
        val query = AstraStudySearch.query(rawQuery)
        if (query.isEmpty()) return topics
        return topics.filter { topic ->
            AstraStudySearch.matches(topic.title, query) ||
                AstraStudySearch.matches(topic.description, query) ||
                AstraStudySearch.matches(topic.command, query)
        }
    }
}

/**
 * Search the complete Study library before a subject is opened. Search
 * includes individual lesson titles but never executes a lesson implicitly.
 */
internal object AstraStudySubjectFilter {
    fun apply(
        subjects: List<AstraStudyCatalog.Subject>,
        rawQuery: String
    ): List<AstraStudyCatalog.Subject> {
        val query = AstraStudySearch.query(rawQuery)
        if (query.isEmpty()) return subjects
        return subjects.filter { subject ->
            AstraStudySearch.matches(subject.title, query) ||
                AstraStudySearch.matches(subject.description, query) ||
                subject.topics.any { topic ->
                    AstraStudySearch.matches(topic.title, query) ||
                        AstraStudySearch.matches(topic.description, query)
                }
        }
    }
}

/**
 * Browse a broad library by discipline without implying the indexed
 * syllabus-only subjects are authored full courses.
 */
internal object AstraStudyFieldFilter {
    const val CORE = "Core courses"
    val fields: List<String> by lazy {
        listOf(CORE) + AstraSubjectAtlas.entries.map { it.category }.distinct()
    }
    private val atlasIds: Set<String> by lazy {
        AstraSubjectAtlas.entries.map { it.id }.toSet()
    }
    private val byField: Map<String, Set<String>> by lazy {
        AstraSubjectAtlas.entries.groupBy { it.category }
            .mapValues { (_, members) -> members.map { it.id }.toSet() }
    }

    fun apply(
        subjects: List<AstraStudyCatalog.Subject>,
        field: String?
    ): List<AstraStudyCatalog.Subject> = when (field) {
        null -> subjects
        CORE -> subjects.filter { it.id !in atlasIds }
        else -> {
            val ids = byField[field].orEmpty()
            subjects.filter { it.id in ids }
        }
    }
}

/** Indicate when a lesson can actually start without an imported GGUF model. */
internal object AstraStudyAccess {
    fun label(subject: AstraStudyCatalog.Subject): String = when {
        subject.topics.all { it.requiresModel } -> "MODEL NEEDED"
        subject.topics.any { it.requiresModel } -> "OFFLINE + AI"
        else -> "OFFLINE"
    }
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
    // Inside the Study Hub, system Back must reverse subject selection
    // before the parent Activity's BackHandler leaves Study entirely.
    BackHandler(enabled = selected != null) {
        selectedId = null
    }
    var subjectQuery by remember { mutableStateOf("") }
    var selectedField by remember { mutableStateOf<String?>(null) }
    var topicQuery by remember(selectedId) { mutableStateOf("") }
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
            Text("Choose a course or browse a syllabus. Roadmap-only fields " +
                "are identified separately from completed offline lessons.",
                color = AstraAurora.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(13.dp))
            OutlinedTextField(
                value = subjectQuery,
                onValueChange = { subjectQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text("Search all subjects", color = AstraAurora.ink) },
                placeholder = {
                    Text("Try bonds, algebra or French", color = AstraAurora.muted)
                }
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedField == null,
                    onClick = { selectedField = null },
                    label = { Text("All subjects", maxLines = 1) }
                )
                AstraStudyFieldFilter.fields.forEach { field ->
                    FilterChip(
                        selected = selectedField == field,
                        onClick = {
                            selectedField = if (selectedField == field) null else field
                        },
                        label = { Text(field, maxLines = 1) }
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            val visibleSubjects = AstraStudyFieldFilter.apply(
                AstraStudySubjectFilter.apply(
                    AstraStudyCatalog.subjects, subjectQuery
                ),
                selectedField
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SUBJECT LIBRARY", color = AstraAurora.mint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Text("${visibleSubjects.size} / ${AstraStudyCatalog.subjects.size} subjects",
                    color = AstraAurora.muted,
                    style = MaterialTheme.typography.labelMedium)
            }
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = AstraStudyLayout.columnsFor(maxWidth.value, fontScale)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visibleSubjects.chunked(columns), key = { pair -> pair.first().id }) { pair ->
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
                    if (visibleSubjects.isEmpty()) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                                Text("No subjects found", color = AstraAurora.ink,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("Search by subject name, skill or lesson topic.",
                                    color = AstraAurora.muted,
                                    style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = {
                                    subjectQuery = ""
                                    selectedField = null
                                }) {
                                    Text("Show all subjects")
                                }
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
            val visibleTopics = AstraStudyTopicFilter.apply(selected.topics, topicQuery)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CURRICULUM", color = AstraAurora.mint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
                Text("${visibleTopics.size} / ${selected.topics.size} topics",
                    color = AstraAurora.muted,
                    style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(9.dp))
            OutlinedTextField(
                value = topicQuery,
                onValueChange = { topicQuery = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = {
                    Text("Find a lesson or skill", color = AstraAurora.muted)
                },
                label = {
                    Text("Search this subject", color = AstraAurora.ink)
                }
            )
            Spacer(Modifier.height(11.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleTopics, key = { it.title }) { topic ->
                    AstraStudyTopicCard(topic) { onLesson(topic) }
                }
                if (visibleTopics.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 21.dp)) {
                            Text("No matching lessons", color = AstraAurora.ink,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text("Try a different word or clear the search.",
                                color = AstraAurora.muted,
                                style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { topicQuery = "" }) {
                                Text("Show all ${selected.topics.size} topics")
                            }
                        }
                    }
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
            .heightIn(min = if (compact) 116.dp else 152.dp)
            .background(Brush.linearGradient(listOf(AstraAurora.raised, AstraAurora.surface)), shape)
            .border(1.dp, accent.copy(alpha = 0.37f), shape)
            .semantics {
                contentDescription = "Study " + subject.title + ". " +
                    subject.description + ". " + AstraStudyAccess.label(subject)
            }
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AstraStudyEmblem(subject.symbol, accent)
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
            AstraStudyEmblem(subject.symbol, accent)
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
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${subject.topics.size} topics",
                color = AstraAurora.muted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold)
            Text(AstraStudyAccess.label(subject),
                color = accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AstraStudyEmblem(symbol: String, accent: Color) {
    val frame = RoundedCornerShape(13.dp)
    Box(
        modifier = Modifier.size(45.dp)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.22f), AstraAurora.raised)
                ),
                frame
            )
            .border(1.dp, accent.copy(alpha = 0.52f), frame),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol,
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold)
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
