package com.astra.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Offline Finance lessons stay inside Study, with tappable answers.
 * No GGUF generation, external image, microphone or network call.
 */
@Composable
internal fun AstraFinanceLessonScreen(
    lessonNumber: Int,
    course: AstraFinanceCourse,
    quizRequested: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onProgress: () -> Unit
) {
    require(lessonNumber in 1..AstraFinanceCourse.lessons.size)
    val lesson = AstraFinanceCourse.lessons[lessonNumber - 1]
    var quizOpen by remember(lessonNumber) { mutableStateOf(quizRequested) }
    var feedback by remember(lessonNumber) { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "FINANCE  /  LESSON " + lessonNumber.toString().padStart(2, '0') +
                    " OF " + AstraFinanceCourse.lessons.size,
                color = AstraAurora.mint,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                lesson.title,
                color = AstraAurora.ink,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        item { AstraFinanceLessonSection("THE CONCEPT", lesson.explanation, AstraAurora.cyan) }
        item { AstraFinanceLessonSection("WORKED EXAMPLE", lesson.workedExample, AstraAurora.violet) }
        item { AstraFinanceLessonSection("CHECK THE ASSUMPTIONS", lesson.caution, AstraAurora.mint) }
        item {
            Button(
                onClick = {
                    course.respond("finance quiz $lessonNumber")
                    feedback = ""
                    quizOpen = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (quizOpen) "Restart this quiz" else "Test your understanding")
            }
        }
        if (quizOpen) {
            item {
                Text(
                    "KNOWLEDGE CHECK · OFFLINE",
                    color = AstraAurora.mint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    lesson.quiz,
                    color = AstraAurora.ink,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(lesson.choices.size) { index ->
                val letter = ('A'.code + index).toChar()
                val answer = lesson.choices[index]
                val frame = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 54.dp)
                        .background(AstraAurora.raised, frame)
                        .border(1.dp, AstraAurora.cyan.copy(alpha = 0.34f), frame)
                        .semantics {
                            contentDescription = "Choose $letter. $answer"
                        }
                        .clickable {
                            feedback = course.respond("answer finance $letter")
                                ?: "Unable to check that answer."
                            quizOpen = false
                        }
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$letter",
                        color = AstraAurora.mint,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(13.dp))
                    Text(
                        answer,
                        color = AstraAurora.ink,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        if (feedback.isNotBlank()) {
            item {
                AstraFinanceLessonSection(
                    "YOUR RESULT", feedback,
                    if (feedback.startsWith("Correct.")) AstraAurora.mint
                    else AstraAurora.violet
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onPrevious,
                    enabled = lessonNumber > 1,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("‹  Previous") }
                if (lessonNumber == AstraFinanceCourse.lessons.size) {
                    TextButton(
                        onClick = onProgress,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("View progress") }
                } else {
                    TextButton(
                        onClick = onNext,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Next lesson  ›") }
                }
            }
        }
    }
}

@Composable
internal fun AstraFinanceProgressScreen(
    course: AstraFinanceCourse,
    onBackToLessons: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "YOUR FINANCE PROGRESS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AstraAurora.ink
        )
        AstraFinanceLessonSection(
            "LOCAL STUDY RECORD",
            course.respond("finance progress").orEmpty(),
            AstraAurora.mint
        )
        Text(
            "Opened lessons and quiz results are kept on this device. " +
                "Progress is not a professional qualification.",
            color = AstraAurora.muted,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onBackToLessons,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text("Browse Finance lessons") }
    }
}

@Composable
private fun AstraFinanceLessonSection(
    heading: String,
    body: String,
    accent: Color
) {
    val frame = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(AstraAurora.surface, frame)
            .border(1.dp, accent.copy(alpha = 0.28f), frame)
            .padding(16.dp)
    ) {
        Text(
            heading,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = AstraAurora.ink,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
