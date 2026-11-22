package org.erthi.noor

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

internal enum class RecitationStatus { EMPTY, LOADING, READY, PLAYING, PAUSED, FINISHED, ERROR }

internal data class RecitationState(
    val status: RecitationStatus = RecitationStatus.EMPTY,
    val position: Int = 0,
    val duration: Int = 0,
    val speed: Float = 1f,
    val repeat: Boolean = false,
    val focusDenied: Boolean = false,
)

internal class LocalRecitationModel(private val app: Application) : AndroidViewModel(app) {
    private val audio = requireNotNull(app.getSystemService(AudioManager::class.java))
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { if (it != AudioManager.AUDIOFOCUS_GAIN) pause() }
        .build()
    private val mutable = MutableStateFlow(RecitationState())
    val state = mutable.asStateFlow()
    private var player: MediaPlayer? = null
    private var loading: Job? = null
    private var holdingFocus = false

    fun open(uri: Uri) {
        loading?.cancel()
        release()
        mutable.value = mutable.value.copy(status = RecitationStatus.LOADING, position = 0, duration = 0, focusDenied = false)
        loading = viewModelScope.launch {
            try {
                val candidate = MediaPlayer()
                player = candidate
                candidate.setAudioAttributes(attributes)
                // Retain only the descriptor duplicated by MediaPlayer. Never pass a URL to it.
                val source = withContext(Dispatchers.IO) {
                    app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                        withContext(Dispatchers.Main) { candidate.setDataSource(descriptor) }
                        true
                    } ?: false
                }
                if (!source) throw IOException("Could not open audio document")
                candidate.setOnPreparedListener {
                    if (it !== player) return@setOnPreparedListener
                    if (it.duration <= 0) {
                        fail()
                        return@setOnPreparedListener
                    }
                    it.isLooping = mutable.value.repeat
                    mutable.value = mutable.value.copy(status = RecitationStatus.READY, duration = it.duration)
                }
                candidate.setOnCompletionListener {
                    if (it === player) {
                        abandonFocus()
                        mutable.value = mutable.value.copy(status = RecitationStatus.FINISHED,
                            position = mutable.value.duration)
                    }
                }
                candidate.setOnErrorListener { active, _, _ ->
                    if (active === player) fail()
                    true
                }
                candidate.prepareAsync()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail()
            }
        }
    }

    fun play() {
        if (mutable.value.status !in setOf(RecitationStatus.READY, RecitationStatus.PAUSED,
                RecitationStatus.FINISHED)) return
        val active = player ?: return
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mutable.value = mutable.value.copy(focusDenied = true)
            return
        }
        holdingFocus = true
        runCatching {
            active.playbackParams = PlaybackParams().setSpeed(mutable.value.speed).setPitch(1f)
            active.start()
            mutable.value = mutable.value.copy(status = RecitationStatus.PLAYING, focusDenied = false)
        }.onFailure { fail() }
    }

    fun pause() {
        if (mutable.value.status != RecitationStatus.PLAYING) return
        val active = player ?: return
        runCatching {
            active.pause()
            mutable.value = mutable.value.copy(status = RecitationStatus.PAUSED, position = active.currentPosition)
            abandonFocus()
        }.onFailure { fail() }
    }

    fun seekBy(milliseconds: Int) {
        if (mutable.value.duration <= 0) return
        val active = player ?: return
        runCatching {
            val state = mutable.value
            val position = (state.position.toLong() + milliseconds).coerceIn(0L, state.duration.toLong()).toInt()
            active.seekTo(position.toLong(), MediaPlayer.SEEK_CLOSEST)
            val status = if (state.status == RecitationStatus.FINISHED) RecitationStatus.PAUSED else state.status
            mutable.value = state.copy(status = status, position = position)
        }.onFailure { fail() }
    }

    fun setSpeed(speed: Float) {
        mutable.value = mutable.value.copy(speed = speed)
        if (mutable.value.status == RecitationStatus.PLAYING) {
            runCatching { player?.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f) }
                .onFailure { fail() }
        }
    }

    fun setRepeat(repeat: Boolean) {
        mutable.value = mutable.value.copy(repeat = repeat)
        if (mutable.value.duration > 0) {
            runCatching { player?.isLooping = repeat }.onFailure { fail() }
        }
    }

    fun refreshPosition() {
        if (mutable.value.status != RecitationStatus.PLAYING) return
        runCatching { player?.currentPosition }.onSuccess { position ->
            if (position != null) mutable.value = mutable.value.copy(position = position)
        }.onFailure { fail() }
    }

    private fun fail() {
        release()
        mutable.value = mutable.value.copy(status = RecitationStatus.ERROR, position = 0, duration = 0)
    }

    private fun abandonFocus() {
        if (holdingFocus) audio.abandonAudioFocusRequest(focus)
        holdingFocus = false
    }

    private fun release() {
        abandonFocus()
        val old = player
        player = null
        old?.release()
    }

    override fun onCleared() {
        loading?.cancel()
        release()
    }
}
