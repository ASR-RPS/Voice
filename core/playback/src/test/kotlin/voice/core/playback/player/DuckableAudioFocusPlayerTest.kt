package voice.core.playback.player

import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.playback.MemoryDataStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DuckableAudioFocusPlayerTest {

  private val scope = TestScope()
  private val internalPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build()
  private val continuePlaybackOnDuckStore = MemoryDataStore(true)
  private val player = DuckableAudioFocusPlayer(
    player = internalPlayer,
    context = ApplicationProvider.getApplicationContext(),
    scope = scope,
    continuePlaybackOnDuckStore = continuePlaybackOnDuckStore,
    audioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
      .setUsage(C.USAGE_MEDIA)
      .build(),
  )

  @Test
  fun `duckable loss keeps playing at reduced volume and gain restores volume`() = scope.runTest {
    runCurrent()
    player.volume = 0.8F
    player.play()

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

    assertTrue(player.playWhenReady)
    assertTrue(internalPlayer.playWhenReady)
    assertEquals(expected = 0.8F, actual = player.volume, absoluteTolerance = 0.001F)
    assertEquals(expected = 0.16F, actual = internalPlayer.volume, absoluteTolerance = 0.001F)

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

    assertTrue(player.playWhenReady)
    assertTrue(internalPlayer.playWhenReady)
    assertEquals(expected = 0.8F, actual = internalPlayer.volume, absoluteTolerance = 0.001F)

    player.release()
  }

  @Test
  fun `volume changes while ducked keep the focus multiplier`() = scope.runTest {
    runCurrent()
    player.volume = 0.8F
    player.play()
    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

    player.volume = 0.5F

    assertEquals(expected = 0.5F, actual = player.volume, absoluteTolerance = 0.001F)
    assertEquals(expected = 0.1F, actual = internalPlayer.volume, absoluteTolerance = 0.001F)

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

    assertEquals(expected = 0.5F, actual = player.volume, absoluteTolerance = 0.001F)
    assertEquals(expected = 0.5F, actual = internalPlayer.volume, absoluteTolerance = 0.001F)

    player.release()
  }

  @Test
  fun `ordinary transient loss pauses underlying player and resumes on gain`() = scope.runTest {
    runCurrent()
    player.play()

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)

    assertTrue(player.playWhenReady)
    assertFalse(internalPlayer.playWhenReady)

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)

    assertTrue(player.playWhenReady)
    assertTrue(internalPlayer.playWhenReady)

    player.release()
  }

  @Test
  fun `permanent focus loss clears play intent`() = scope.runTest {
    runCurrent()
    player.play()

    player.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

    assertFalse(player.playWhenReady)
    assertFalse(internalPlayer.playWhenReady)

    player.release()
  }
}
