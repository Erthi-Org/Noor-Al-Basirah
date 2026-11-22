package org.erthi.noor

import java.io.InputStream

internal data class LegacyCounters(
    val attempts: Int,
    val recalled: Int,
    val hinted: Int,
    val transitionMisses: Int,
    val wordMisses: Int,
    val similarityMisses: Int,
)

internal object LegacyProgressCsv {
    private const val HEADER =
        "surah,ayah,attempts,independent_recall,hinted,transition_misses,word_misses,similarity_misses"
    private const val MAX_BYTES = 512 * 1024

    fun read(input: InputStream, isVerse: (Int, Int) -> Boolean): Map<Int, LegacyCounters> {
        val bytes = input.readNBytesCompat()
        val content = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = content.lineSequence().iterator()
        require(lines.hasNext() && lines.next() == HEADER)
        val result = linkedMapOf<Int, LegacyCounters>()
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isEmpty() && !lines.hasNext()) break
            val fields = line.split(',')
            require(fields.size == 8)
            val numbers = fields.map { field ->
                require(field.isNotEmpty() && field.all { it in '0'..'9' })
                requireNotNull(field.toIntOrNull())
            }
            val (chapter, ayah, attempts, recalled, hinted) = numbers
            require(chapter in 1..114 && ayah in 1..286 && isVerse(chapter, ayah))
            require(attempts > 0 && recalled <= attempts && hinted <= attempts)
            val id = chapter * 1000 + ayah
            require(id !in result)
            result[id] = LegacyCounters(attempts, recalled, hinted, numbers[5], numbers[6], numbers[7])
        }
        return result
    }

    fun encode(records: Map<Int, LegacyCounters>): ByteArray = buildString {
        appendLine(HEADER)
        for ((id, row) in records.toSortedMap()) {
            appendLine(listOf(id / 1000, id % 1000, row.attempts, row.recalled, row.hinted,
                row.transitionMisses, row.wordMisses, row.similarityMisses).joinToString(","))
        }
    }.toByteArray(Charsets.UTF_8)

    fun merge(
        previous: Map<Int, LegacyCounters>,
        imported: Map<Int, LegacyCounters>,
    ): Map<Int, LegacyCounters> = previous.toMutableMap().apply {
        for ((id, row) in imported) {
            if (row.attempts > (get(id)?.attempts ?: 0)) put(id, row)
        }
    }

    private fun InputStream.readNBytesCompat(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count == -1) break
            require(output.size() + count <= MAX_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
