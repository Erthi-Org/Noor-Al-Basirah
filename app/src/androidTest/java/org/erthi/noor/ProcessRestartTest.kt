package org.erthi.noor

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.hifzai.learning.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Each phase runs in a separate instrumentation invocation on a disposable emulator. */
class ProcessRestartTest {
    @Test fun persistAcrossRestart() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val phase = InstrumentationRegistry.getArguments().getString("restartPhase")
        val app = instrumentation.targetContext.applicationContext as Application
        val repository = NoorRepository(app)
        val session = PracticeSession(112, 2, PracticeKind.RECALL, 2, true, DifficultyTag.NONE)
        val card = HifzScheduler.grade(ReviewCard(112002), RecallGrade.GOOD, 200L)
        when (phase) {
            "seed" -> assertTrue(repository.save(session, 7, mapOf(card.verseId to card)))
            "verify" -> {
                assertEquals(session, repository.session())
                assertEquals(7, repository.target())
                assertEquals(mapOf(card.verseId to card), repository.cards())
            }
            else -> error("Missing restart phase")
        }
    }
}
