package voice.features.settings.views

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import voice.core.ui.icons.VoiceIcons
import voice.core.strings.R as StringsR

@Composable
internal fun ContinuePlaybackOnDuckRow(
  enabled: Boolean,
  onEnabledChange: (Boolean) -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable { onEnabledChange(!enabled) },
    leadingContent = {
      Icon(
        imageVector = VoiceIcons.AudioFile,
        contentDescription = stringResource(StringsR.string.settings_playback_continue_on_duck_title),
      )
    },
    supportingContent = {
      Text(stringResource(StringsR.string.settings_playback_continue_on_duck_summary))
    },
    trailingContent = {
      Switch(
        checked = enabled,
        onCheckedChange = onEnabledChange,
      )
    },
  ) {
    Text(stringResource(StringsR.string.settings_playback_continue_on_duck_title))
  }
}
