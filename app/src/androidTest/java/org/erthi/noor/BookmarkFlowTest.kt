package org.erthi.noor

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class BookmarkFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun bookmarkCanBeSavedReopenedAndRemoved() {
        val repo = NoorRepository(rule.activity.application)
        val session = repo.session()
        val id = session.chapter * 1000 + session.ayah
        val button = hasTestTag("toggle-bookmark")
        val list = hasScrollToIndexAction()
        val picker = rule.activity.getString(R.string.bookmarks)

        rule.waitUntil(30000) {
            rule.onAllNodes(hasContentDescription(picker) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(list).performScrollToNode(button)
        rule.onNode(button).assertIsOff().performClick()
        rule.waitUntil(10000) { id in repo.bookmarks() }

        rule.activityRule.scenario.recreate()
        rule.waitUntil(30000) {
            rule.onAllNodes(hasContentDescription(picker) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription(picker).assertIsEnabled().performClick()
        rule.onNodeWithTag("bookmark-$id").performClick()
        rule.onNode(list).performScrollToNode(button)
        rule.onNode(button).assertIsOn().performClick()
        rule.waitUntil(10000) { id !in repo.bookmarks() }
    }
}
