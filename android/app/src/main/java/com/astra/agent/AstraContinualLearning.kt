package com.astra.agent

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.Random
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Actual on-device continual SGD over a small neural topic adapter.
 *
 * It trains after opted-in user messages; Qwen's GGUF weights are NOT changed.
 * Only floating-point adapter weights and aggregate counters are persisted.
 * Original messages, labels, transcripts and external user data are not stored.
 * Prediction can enrich knowledge retrieval, NEVER execute tool commands.
 */
internal class AstraContinualLearning(private val file: File) {
    enum class Topic { RUSSIAN, FINANCE, GENERAL }
    data class Prediction(val topic: Topic, val confidence: Double)
    data class State(val steps: Int, val lastLoss: Double)

    private val inputs = 128
    private val hidden = 24
    private val outputs = Topic.entries.size
    private val w1 = DoubleArray(inputs * hidden)
    private val b1 = DoubleArray(hidden)
    private val w2 = DoubleArray(hidden * outputs)
    private val b2 = DoubleArray(outputs)
    private var steps = 0
    private var lastLoss = 0.0

    init {
        resetWeights()
        restore()
    }

    @Synchronized
    fun learn(message: String): State {
        val label = teacherLabel(message)
        val x = features(message)
        if (x.all { it == 0.0 }) return status()
        val h = hiddenLayer(x)
        val probabilities = softmax(h)
        val target = label.ordinal
        val loss = -kotlin.math.ln(probabilities[target].coerceAtLeast(1e-9))
        val dOutput = DoubleArray(outputs) { index ->
            probabilities[index] - (if (index == target) 1.0 else 0.0)
        }
        // Compute both layer gradients against the SAME weights.
        val dHidden = DoubleArray(hidden) { row ->
            val signal = (0 until outputs).sumOf { column ->
                w2[column * hidden + row] * dOutput[column]
            }
            signal * (1.0 - h[row] * h[row])
        }
        val rate = 0.055 / (1.0 + steps / 800.0)
        for (column in 0 until outputs) {
            for (row in 0 until hidden) {
                w2[column * hidden + row] -= rate * dOutput[column] * h[row]
            }
            b2[column] -= rate * dOutput[column]
        }
        for (row in 0 until hidden) {
            for (index in 0 until inputs) {
                w1[row * inputs + index] -= rate * dHidden[row] * x[index]
            }
            b1[row] -= rate * dHidden[row]
        }
        steps++
        lastLoss = loss
        persist()
        return status()
    }

    @Synchronized
    fun predict(message: String): Prediction {
        val scores = softmax(hiddenLayer(features(message)))
        val index = scores.indices.maxByOrNull { scores[it] } ?: 2
        return Prediction(Topic.entries[index], scores[index])
    }

    @Synchronized
    fun status(): State = State(steps, lastLoss)

    @Synchronized
    fun reset() {
        resetWeights()
        steps = 0
        lastLoss = 0.0
        if (file.exists() && !file.delete()) {
            throw IllegalStateException("Unable to clear local neural adapter.")
        }
    }

    private fun hiddenLayer(x: DoubleArray): DoubleArray =
        DoubleArray(hidden) { row ->
            var sum = b1[row]
            for (index in 0 until inputs) sum += w1[row * inputs + index] * x[index]
            tanh(sum)
        }

    private fun softmax(h: DoubleArray): DoubleArray {
        val values = DoubleArray(outputs) { column ->
            var score = b2[column]
            for (row in 0 until hidden) score += w2[column * hidden + row] * h[row]
            score
        }
        val max = values.maxOrNull() ?: 0.0
        val exps = values.map { exp((it - max).coerceIn(-40.0, 40.0)) }
        val denom = exps.sum().coerceAtLeast(1e-9)
        return DoubleArray(outputs) { exps[it] / denom }
    }

    private fun features(text: String): DoubleArray {
        val x = DoubleArray(inputs)
        val words = Regex("""[\p{L}\p{N}]+""")
            .findAll(text.lowercase(Locale.ROOT)).take(90).map { it.value }.toList()
        var count = 0
        for (word in words) {
            val grams = sequenceOf("w:$word") +
                word.windowed(3, 1, partialWindows = false).asSequence()
                    .map { "c:$it" }
            for (gram in grams) {
                x[(gram.hashCode().toLong() and 0x7fffffffL).rem(inputs).toInt()] += 1.0
                count++
            }
        }
        val scale = sqrt(count.coerceAtLeast(1).toDouble())
        for (i in x.indices) x[i] /= scale
        return x
    }

    private fun teacherLabel(message: String): Topic {
        val lower = message.lowercase(Locale.ROOT)
        return when {
            Regex("""russian|russki|cyrillic|привет|здравствуйте|русск|\p{IsCyrillic}""")
                .containsMatchIn(lower) -> Topic.RUSSIAN
            Regex("""bond|yield|dividend|portfolio|equity|invest|financ|interest|convexity|duration|cash.?flow|compound""")
                .containsMatchIn(lower) -> Topic.FINANCE
            else -> Topic.GENERAL
        }
    }

    private fun resetWeights() {
        val random = Random(0xA57A2026L)
        for (i in w1.indices) w1[i] = (random.nextDouble() - 0.5) * 0.10
        for (i in w2.indices) w2[i] = (random.nextDouble() - 0.5) * 0.10
        b1.fill(0.0)
        b2.fill(0.0)
    }

    private fun persist() {
        val parent = file.parentFile ?: error("Missing private learning directory.")
        require(parent.isDirectory || parent.mkdirs()) {
            "Cannot create private learning directory."
        }
        val temp = File(parent, file.name + ".partial")
        try {
            FileOutputStream(temp).use { raw ->
                DataOutputStream(raw).use { stream ->
                    stream.writeUTF("ASTRA_NEURAL_TOPIC_V1")
                    stream.writeInt(steps)
                    stream.writeDouble(lastLoss)
                    for (values in arrayOf(w1, b1, w2, b2)) {
                        for (value in values) stream.writeDouble(value)
                    }
                    stream.flush()
                    raw.fd.sync()
                }
            }
            try {
                Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
    }

    private fun restore() {
        if (!file.isFile || file.length() > 50_000L) return
        runCatching {
            DataInputStream(file.inputStream().buffered()).use { stream ->
                check(stream.readUTF() == "ASTRA_NEURAL_TOPIC_V1")
                val loadedSteps = stream.readInt()
                val loss = stream.readDouble()
                require(loadedSteps in 0..10_000_000 && loss.isFinite())
                val read = arrayOf(w1, b1, w2, b2)
                for (values in read) {
                    for (i in values.indices) {
                        val value = stream.readDouble()
                        require(value.isFinite() && kotlin.math.abs(value) < 100.0)
                        values[i] = value
                    }
                }
                steps = loadedSteps
                lastLoss = loss
            }
        }.onFailure {
            resetWeights()
            steps = 0
            lastLoss = 0.0
        }
    }
}
