package org.erthi.noor

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.nexvary.hifzai.learning.PracticeKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PracticeFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
        rule.onNodeWithText(label).performClick()
    }

    @Test fun chooseAyahRepeatRecallAndPreserveScheduleAcrossConfigurationChanges() {
        val repo = NoorRepository(rule.activity.application)
        val corpus = repo.loadCorpus()
        val chapter = rule.activity.getString(R.string.chapter)
        val firstChapter = chapter + ": " + corpus.info(repo.session().chapter).arabicName
        rule.waitUntil(30000) { rule.onAllNodesWithText(firstChapter).fetchSemanticsNodes().isNotEmpty() }
        click(firstChapter)
        val search = rule.activity.getString(R.string.search)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(search))
        rule.onNode(hasSetTextAction()).performTextInput("112:2")
        click(rule.activity.getString(R.string.verse_address, 112, 2))

        val nextStage = rule.activity.getString(R.string.next_stage)
        repeat(3) { click(nextStage) }
        click(rule.activity.getString(R.string.again))
        rule.waitUntil(10000) { repo.cards()[112002]?.intervalDays == 0 }
        repeat(3) { click(nextStage) }
        click(rule.activity.getString(R.string.good))
        rule.waitUntil(10000) { repo.cards()[112002]?.intervalDays == 1 }
        val scheduled = repo.cards().getValue(112002)
        assertEquals(LocalDate.now().toEpochDay() + 1, scheduled.dueEpochDay)
        click(nextStage)
        rule.waitUntil(10000) { repo.session().stage == PracticeKind.MASK_LIGHT }
        val session = repo.session()
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        val code = if (rule.activity.resources.configuration.locales[0].language == "en") "ar" else "en"
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
            }
            rule.waitUntil(30000) { rule.activity.resources.configuration.locales[0].language == code }
            rule.activityRule.scenario.recreate()
            val address = rule.activity.getString(R.string.verse_address, 112, 2)
            rule.waitUntil(30000) {
                rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(address))
            rule.onNodeWithText(address).assertIsDisplayed()
            assertEquals(session, repo.session())
            assertEquals(scheduled, repo.cards()[112002])
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                AppCompatDelegate.setApplicationLocales(previousLocales)
            }
        }
    }
}
