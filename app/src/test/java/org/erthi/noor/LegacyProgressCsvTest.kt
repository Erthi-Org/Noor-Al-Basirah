package org.erthi.noor

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyProgressCsvTest {
    private val header =
        "surah,ayah,attempts,independent_recall,hinted,transition_misses,word_misses,similarity_misses"

    private fun read(vararg rows: String) = LegacyProgressCsv.read(
        (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n").byteInputStream(),
    ) { chapter, ayah -> chapter == 1 && ayah in 1..7 }

    @Test fun mergePreservesNewerAndUnrelatedRecordsAndCanBeRepeated() {
        val previous = read("1,1,5,3,2,0,2,1", "1,2,3,1,2,1,3,0")
        val incoming = read("1,1,4,3,1,0,0,0", "1,3,2,1,1,0,1,0")
        val merged = LegacyProgressCsv.merge(previous, incoming)
        assertEquals(previous[1001], merged[1001])
        assertEquals(previous[1002], merged[1002])
        assertEquals(incoming[1003], merged[1003])
        assertEquals(merged, LegacyProgressCsv.merge(merged, incoming))
        val stream = ByteArrayInputStream(LegacyProgressCsv.encode(merged))
        assertEquals(merged, LegacyProgressCsv.read(stream) { _, _ -> true })
    }

    @Test fun rejectsInvalidAndDuplicateRows() {
        for (row in listOf("1,8,1,0,0,0,0,0", "1,1,1,2,0,0,0,0",
            "1,1,99999999999,0,0,0,0,0", "1,1,2,1")) {
            assertThrows(IllegalArgumentException::class.java) { read(row) }
        }
        val duplicate = "1,1,1,0,0,0,0,0"
        assertThrows(IllegalArgumentException::class.java) { read(duplicate, duplicate) }
    }

    @Test fun rejectsOversizedDocuments() {
        assertThrows(IllegalArgumentException::class.java) {
            LegacyProgressCsv.read(ByteArray(512 * 1024 + 1).inputStream()) { _, _ -> true }
        }
    }
}
