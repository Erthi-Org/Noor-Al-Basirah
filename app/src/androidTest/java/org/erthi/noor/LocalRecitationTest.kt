package org.erthi.noor

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalRecitationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun openPlayerFromHome() {
        val title = rule.activity.getString(R.string.audio_title)
        val choose = rule.activity.getString(R.string.audio_choose)
        val list = hasScrollToIndexAction()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText(rule.activity.getString(R.string.chapter), substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(list).performScrollToNode(hasText(title))
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithText(choose).assertIsDisplayed().assertIsEnabled()
        val capture = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/erthi-screenshots/local-recitation.png")
        ParcelFileDescriptor.AutoCloseInputStream(capture).use { it.readBytes() }
    }

    @Test fun invalidDocumentCanBeReplacedAndAValidClipCanBePlayedAndPaused() {
        val model = rule.runOnIdle {
            ViewModelProvider(rule.activity)[LocalRecitationModel::class.java]
        }
        val file = File.createTempFile("recitation-test-", ".wav", rule.activity.cacheDir)
        try {
            file.writeText("invalid")
            rule.runOnIdle { model.open(Uri.fromFile(file)) }
            rule.waitUntil(15_000) { model.state.value.status == RecitationStatus.ERROR }

            // Three seconds of generated silence. No recordings or downloads are required.
            val samples = 24_000
            val wav = ByteBuffer.allocate(44 + 2 * samples).order(ByteOrder.LITTLE_ENDIAN)
            wav.put("RIFF".toByteArray()).putInt(36 + 2 * samples).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(2 * samples)
            file.writeBytes(wav.array())
            rule.runOnIdle { model.open(Uri.fromFile(file)) }
            rule.waitUntil(15_000) { model.state.value.status == RecitationStatus.READY }
            assertEquals(3_000, model.state.value.duration)
            rule.runOnIdle {
                model.setRepeat(true)
                model.setSpeed(0.75f)
                model.play()
                assertEquals(RecitationStatus.PLAYING, model.state.value.status)
                model.pause()
                assertEquals(RecitationStatus.PAUSED, model.state.value.status)
            }
        } finally {
            file.delete()
        }
    }
}
