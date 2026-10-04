package voice.core.playback.session

import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import voice.core.playback.misc.Decibel

@Serializable
internal sealed interface CustomCommand {

  @Serializable
  data object ForceSeekToNext : CustomCommand

  @Serializable
  data object ForceSeekToPrevious : CustomCommand

  @Serializable
  data class SetSkipSilence(val skipSilence: Boolean) : CustomCommand

  @Serializable
  data class SetGain(val gain: Decibel) : CustomCommand

  /**
   * Car surfaces intentionally expose a small set of discrete, glanceable controls.
   * Repeating either command cycles through the available values.
   */
  @Serializable
  data object CyclePlaybackSpeed : CustomCommand

  @Serializable
  data object CycleVolumeBoost : CustomCommand

  companion object {

    const val CUSTOM_COMMAND_ACTION = "voiceCommandAction"
    internal const val CUSTOM_COMMAND_EXTRA = "voiceCommandExtra"
    internal const val CYCLE_PLAYBACK_SPEED_ACTION = "voice.car.CYCLE_PLAYBACK_SPEED"
    internal const val CYCLE_VOLUME_BOOST_ACTION = "voice.car.CYCLE_VOLUME_BOOST"
    internal fun parse(
      command: SessionCommand,
      args: Bundle,
    ): CustomCommand? {
      when (command.customAction) {
        CYCLE_PLAYBACK_SPEED_ACTION -> return CyclePlaybackSpeed
        CYCLE_VOLUME_BOOST_ACTION -> return CycleVolumeBoost
      }
      if (command.customAction != CUSTOM_COMMAND_ACTION) {
        return null
      }
      val json = args.getString(CUSTOM_COMMAND_EXTRA) ?: return null
      return Json.decodeFromString(serializer(), json)
    }
  }
}

internal fun MediaController.sendCustomCommand(command: CustomCommand) {
  val json = Json.encodeToString(CustomCommand.serializer(), command)
  sendCustomCommand(
    SessionCommand(CustomCommand.CUSTOM_COMMAND_ACTION, Bundle.EMPTY),
    Bundle().apply {
      putString(CustomCommand.CUSTOM_COMMAND_EXTRA, json)
    },
  )
}
