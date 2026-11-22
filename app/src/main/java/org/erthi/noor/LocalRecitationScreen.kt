package org.erthi.noor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@Composable
internal fun LocalRecitationScreen(back: () -> Unit, model: LocalRecitationModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri?.scheme == "content") model.open(uri)
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) model.pause()
        }
        val noisy = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) model.pause()
            }
        }
        lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(noisy)
            lifecycle.removeObserver(observer)
            model.pause()
        }
    }
    val playing = state.status == RecitationStatus.PLAYING
    LaunchedEffect(playing) {
        while (playing) {
            delay(500)
            model.refreshPosition()
        }
    }

    val status = when (state.status) {
        RecitationStatus.EMPTY -> R.string.audio_help
        RecitationStatus.LOADING -> R.string.loading
        RecitationStatus.READY -> R.string.audio_ready
        RecitationStatus.PLAYING -> R.string.audio_playing
        RecitationStatus.PAUSED -> R.string.audio_paused
        RecitationStatus.FINISHED -> R.string.audio_finished
        RecitationStatus.ERROR -> R.string.audio_error
    }
    val control = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = back) { Text(stringResource(R.string.back)) }
        PageTitle(stringResource(R.string.audio_title))
        Button(onClick = { picker.launch("audio/*") }, modifier = control) {
            Text(stringResource(R.string.audio_choose))
        }
        Text(stringResource(if (state.focusDenied) R.string.audio_focus else status),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if (state.duration > 0) {
            Button(onClick = { if (playing) model.pause() else model.play() }, modifier = control) {
                Text(stringResource(if (playing) R.string.audio_pause else R.string.audio_play))
            }
            Text(stringResource(R.string.audio_time, state.position / 1000, state.duration / 1000))
            OutlinedButton(onClick = { model.seekBy(-15_000) }, modifier = control) {
                Text(stringResource(R.string.audio_back))
            }
            OutlinedButton(onClick = { model.seekBy(15_000) }, modifier = control) {
                Text(stringResource(R.string.audio_forward))
            }
            OutlinedButton(onClick = {
                model.setSpeed(when (state.speed) { 1f -> 0.75f; 0.75f -> 1.25f; else -> 1f })
            }, modifier = control) {
                Text(stringResource(R.string.audio_speed, (state.speed * 100).toInt()))
            }
            androidx.compose.foundation.layout.Row(
                control.toggleable(state.repeat, role = Role.Switch, onValueChange = model::setRepeat),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.audio_repeat), Modifier.weight(1f))
                Switch(state.repeat, onCheckedChange = null)
            }
        }
    }
}
