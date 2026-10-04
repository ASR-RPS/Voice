package voice.core.playback.session

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import voice.core.playback.misc.Decibel

class CarPlaybackControlsTest {

  private val controls = CarPlaybackControls(mockk<Context>())

  @Test
  fun `cycles playback speed through car-safe presets`() {
    assertEquals(1.25F, controls.nextSpeed(1F))
    assertEquals(0.75F, controls.nextSpeed(2F))
  }

  @Test
  fun `cycles volume boost through car-safe presets`() {
    assertEquals(Decibel(3F), controls.nextGain(Decibel.Zero))
    assertEquals(Decibel.Zero, controls.nextGain(Decibel(9F)))
  }
}
