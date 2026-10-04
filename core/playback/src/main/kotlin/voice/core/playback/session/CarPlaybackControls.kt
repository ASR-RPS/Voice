package voice.core.playback.session

import android.content.Context
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import dev.zacsweers.metro.Inject
import voice.core.playback.misc.Decibel
import voice.core.playback.misc.VolumeGain
import voice.core.strings.R as StringsR

/** Safe, discrete controls for Android Auto. */
@Inject
internal class CarPlaybackControls(private val context: Context) {

  fun buttons(
    speed: Float,
    gain: Decibel,
  ): List<CommandButton> = listOf(
    button(
      command = CustomCommand.CyclePlaybackSpeed,
      displayName = context.getString(StringsR.string.playback_speed_title) + ": ${speed.formatSpeed()}",
      icon = CommandButton.ICON_PLAYBACK_SPEED,
    ),
    button(
      command = CustomCommand.CycleVolumeBoost,
      displayName = context.getString(StringsR.string.playback_option_volume_boost) + ": ${gain.formatGain()}",
      icon = CommandButton.ICON_VOLUME_UP,
    ),
  )

  fun updateButtons(
    session: MediaSession,
    controller: MediaSession.ControllerInfo,
    speed: Float,
    gain: Decibel,
  ) {
    val otherButtons = session.mediaButtonPreferences.filterNot { button ->
      button.sessionCommand?.customAction in carActions
    }
    session.setMediaButtonPreferences(controller, otherButtons + buttons(speed, gain))
  }

  fun nextSpeed(current: Float): Float = nextValue(current, speeds)

  fun nextGain(current: Decibel): Decibel = Decibel(nextValue(current.value, gains))

  private fun button(
    command: CustomCommand,
    displayName: String,
    icon: Int,
  ): CommandButton = CommandButton.Builder(icon)
    .setDisplayName(displayName)
    .setSessionCommand(SessionCommand(command.action, Bundle.EMPTY))
    .setSlots(CommandButton.SLOT_OVERFLOW)
    .build()

  private fun nextValue(
    current: Float,
    values: List<Float>,
  ): Float = values.firstOrNull { it > current + VALUE_EPSILON } ?: values.first()

  private fun Float.formatSpeed(): String {
    val value = "%.2f".format(java.util.Locale.ROOT, this).trimEnd('0').trimEnd('.')
    return "$value×"
  }

  private fun Decibel.formatGain(): String {
    val value = "%.0f".format(java.util.Locale.ROOT, value)
    return if (this == Decibel.Zero) "$value dB" else "+$value dB"
  }

  private companion object {
    const val VALUE_EPSILON = 0.001F
    val speeds = listOf(0.75F, 1F, 1.25F, 1.5F, 1.75F, 2F)
    val gains = listOf(0F, 3F, 6F, VolumeGain.MAX_GAIN.value)
    val carActions = setOf(
      CustomCommand.CYCLE_PLAYBACK_SPEED_ACTION,
      CustomCommand.CYCLE_VOLUME_BOOST_ACTION,
    )
  }
}

internal val CustomCommand.action: String
  get() = when (this) {
    CustomCommand.CyclePlaybackSpeed -> CustomCommand.CYCLE_PLAYBACK_SPEED_ACTION
    CustomCommand.CycleVolumeBoost -> CustomCommand.CYCLE_VOLUME_BOOST_ACTION
    else -> error("Only car controls have a standalone session action")
  }
