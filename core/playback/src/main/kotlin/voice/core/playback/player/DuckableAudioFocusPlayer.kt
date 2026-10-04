package voice.core.playback.player

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.datastore.core.DataStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.media.AudioAttributes as PlatformAudioAttributes

/**
 * Keeps Media3's default audio-focus handling unless the user explicitly opts into continuing
 * playback for duckable focus losses (for example short navigation alerts).
 *
 * In opt-in mode, speech audio attributes are preserved for playback while this class handles
 * audio focus itself. Duckable losses reduce volume without pausing; transient and permanent
 * losses keep their normal pause behavior.
 */
internal class DuckableAudioFocusPlayer(
  private val player: ExoPlayer,
  context: Context,
  scope: CoroutineScope,
  continuePlaybackOnDuckStore: DataStore<Boolean>,
  private val audioAttributes: AudioAttributes,
) : ForwardingPlayer(player) {

  private val audioManager = checkNotNull(context.getSystemService(AudioManager::class.java))
  private val focusHandler = Handler(player.applicationLooper)
  private val focusListener = AudioManager.OnAudioFocusChangeListener(::handleAudioFocusChange)
  private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(
      PlatformAudioAttributes.Builder()
        .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
        .setContentType(PlatformAudioAttributes.CONTENT_TYPE_SPEECH)
        .build(),
    )
    // Force a focus callback for MAY_DUCK so speech can be ducked explicitly instead of paused.
    .setWillPauseWhenDucked(true)
    .setOnAudioFocusChangeListener(focusListener, focusHandler)
    .build()

  private var useCustomAudioFocus = false
  private var desiredPlayWhenReady = player.playWhenReady
  private var focusState = FocusState.None
  private var requestedVolume = player.volume
  private var focusVolumeMultiplier = 1F

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      if (!useCustomAudioFocus) return
      if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
        abandonAudioFocus()
      } else if (desiredPlayWhenReady && focusState == FocusState.None) {
        applyDesiredPlaybackState()
      }
    }
  }

  private val settingJob: Job

  init {
    player.addListener(playerListener)
    settingJob = scope.launch {
      continuePlaybackOnDuckStore.data
        .distinctUntilChanged()
        .collect(::setUseCustomAudioFocus)
    }
  }

  override fun play() {
    playWhenReady = true
  }

  override fun pause() {
    playWhenReady = false
  }

  override fun getPlayWhenReady(): Boolean {
    return if (useCustomAudioFocus) desiredPlayWhenReady else player.playWhenReady
  }

  override fun getPlaybackSuppressionReason(): Int {
    return if (useCustomAudioFocus && focusState == FocusState.TransientLoss && desiredPlayWhenReady) {
      Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
    } else {
      player.playbackSuppressionReason
    }
  }

  override fun getVolume(): Float = requestedVolume

  override fun setVolume(volume: Float) {
    requestedVolume = volume
    updatePlayerVolume()
  }

  override fun setPlayWhenReady(playWhenReady: Boolean) {
    desiredPlayWhenReady = playWhenReady
    if (useCustomAudioFocus) {
      applyDesiredPlaybackState()
    } else {
      player.playWhenReady = playWhenReady
    }
  }

  override fun release() {
    settingJob.cancel()
    player.removeListener(playerListener)
    abandonAudioFocus()
    player.release()
  }

  internal val audioSessionId: Int
    get() = player.audioSessionId

  internal fun setSkipSilenceEnabled(enabled: Boolean) {
    player.skipSilenceEnabled = enabled
  }

  private fun setUseCustomAudioFocus(enabled: Boolean) {
    if (useCustomAudioFocus == enabled) return

    if (enabled) {
      desiredPlayWhenReady = player.playWhenReady
    }
    useCustomAudioFocus = enabled

    if (enabled) {
      player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
      applyDesiredPlaybackState()
    } else {
      abandonAudioFocus()
      player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      player.playWhenReady = desiredPlayWhenReady
    }
  }

  private fun applyDesiredPlaybackState() {
    if (!desiredPlayWhenReady) {
      restoreVolumeAfterDucking()
      player.playWhenReady = false
      return
    }

    if (player.playbackState == Player.STATE_IDLE) {
      // Match ExoPlayer's normal flow: focus is requested after playback leaves idle.
      player.playWhenReady = true
      return
    }

    when (focusState) {
      FocusState.HaveFocus, FocusState.Ducked -> player.playWhenReady = true
      FocusState.TransientLoss -> player.playWhenReady = false
      FocusState.None -> {
        when (audioManager.requestAudioFocus(audioFocusRequest)) {
          AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
            focusState = FocusState.HaveFocus
            player.playWhenReady = true
          }
          else -> {
            desiredPlayWhenReady = false
            player.playWhenReady = false
          }
        }
      }
    }
  }

  internal fun handleAudioFocusChange(focusChange: Int) {
    if (!useCustomAudioFocus) return

    when (focusChange) {
      AudioManager.AUDIOFOCUS_GAIN -> {
        restoreVolumeAfterDucking()
        focusState = FocusState.HaveFocus
        if (desiredPlayWhenReady) {
          player.playWhenReady = true
        }
      }
      AudioManager.AUDIOFOCUS_LOSS -> {
        restoreVolumeAfterDucking()
        focusState = FocusState.None
        desiredPlayWhenReady = false
        player.playWhenReady = false
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
        restoreVolumeAfterDucking()
        focusState = FocusState.TransientLoss
        player.playWhenReady = false
      }
      AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
        duckVolume()
        focusState = FocusState.Ducked
        player.playWhenReady = desiredPlayWhenReady
      }
    }
  }

  private fun duckVolume() {
    focusVolumeMultiplier = DUCK_VOLUME_MULTIPLIER
    updatePlayerVolume()
  }

  private fun restoreVolumeAfterDucking() {
    focusVolumeMultiplier = 1F
    updatePlayerVolume()
  }

  private fun updatePlayerVolume() {
    player.volume = requestedVolume * focusVolumeMultiplier
  }

  private fun abandonAudioFocus() {
    restoreVolumeAfterDucking()
    if (focusState == FocusState.None) return
    audioManager.abandonAudioFocusRequest(audioFocusRequest)
    focusState = FocusState.None
  }

  private enum class FocusState {
    None,
    HaveFocus,
    TransientLoss,
    Ducked,
  }

  private companion object {
    const val DUCK_VOLUME_MULTIPLIER = 0.2F
  }
}
