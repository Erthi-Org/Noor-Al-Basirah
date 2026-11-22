package org.erthi.noor

import android.app.Application
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.hifzai.learning.DifficultyTag
import com.nexvary.hifzai.learning.PracticeKind
import com.nexvary.hifzai.learning.PracticeSession
import com.nexvary.hifzai.learning.ReviewCard
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyProgressImportTest {
    @Test fun importIsRepeatableAndRejectsPartialInputWithoutChangingStoredData() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val prefs = app.getSharedPreferences("noor-learning-v1", 0)
        val archive = File(app.filesDir, "legacy-progress.csv")
        val document = File.createTempFile("hifzai-progress-", ".csv", app.cacheDir)
        val header = "surah,ayah,attempts,independent_recall,hinted,transition_misses,word_misses,similarity_misses"
        try {
            val repo = NoorRepository(app)
            val corpus = repo.loadCorpus()
            val session = PracticeSession(1, 2, PracticeKind.RECALL, 1, false, DifficultyTag.NONE)
            val cards = mapOf(1002 to ReviewCard(1002))
            assertTrue(repo.save(session, 5, cards))
            document.writeText("$header\r\n1,1,5,3,2,0,2,1\r\n")
            val uri = Uri.fromFile(document)
            assertEquals(1, repo.importLegacy(uri, corpus))
            val previous = archive.readText()
            assertEquals(0, repo.importLegacy(uri, corpus))
            document.writeText("$header\r\n1,1,9,3,2,0,2,1\r\n1,2,")
            try {
                repo.importLegacy(uri, corpus)
                fail("A truncated CSV must fail validation")
            } catch (_: IllegalArgumentException) {
                // The on-disk snapshot is checked below.
            }
            val reopened = NoorRepository(app)
            assertEquals(previous, archive.readText())
            assertEquals(1, reopened.legacyVerseCount(corpus))
            assertEquals(session, reopened.session())
            assertEquals(5, reopened.target())
            assertEquals(cards, reopened.cards())
        } finally {
            archive.delete()
            document.delete()
            prefs.edit().clear().commit()
        }
    }
}
