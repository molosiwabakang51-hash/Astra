package com.astra.agent

/**
 * Shared question-length limits for retrieval and prompt enrichment.
 *
 * If references do not fit in the prompt, they are not evidence that the
 * model saw. The caller must not attach their citations to a generated answer.
 * Separate limits reflect the maximum number/size of source excerpts.
 */
internal object AstraReferenceFit {
    private const val MAX_BOOK_QUESTION = 1_800
    private const val MAX_OPEN_COURSE_QUESTION = 1_650

    fun permitsBook(question: String): Boolean =
        question.length <= MAX_BOOK_QUESTION

    fun permitsOpenCourse(question: String): Boolean =
        question.length <= MAX_OPEN_COURSE_QUESTION
}
