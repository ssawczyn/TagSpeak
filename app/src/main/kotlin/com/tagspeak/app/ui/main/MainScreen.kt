package com.tagspeak.app.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tagspeak.app.R
import com.tagspeak.app.data.StickerTypeProfile
import com.tagspeak.app.nfc.TagSpeakNdefCodec
import com.tagspeak.app.ui.theme.TagSpeakTheme

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel() },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  MainScreenContent(
    state = uiState,
    onSelectTab = viewModel::selectTab,
    onDraftLabelChanged = viewModel::updateDraftLabelText,
    onClearDraft = viewModel::clearDraftLabel,
    onToggleArmWrite = viewModel::toggleArmWrite,
    onToggleScanToEdit = viewModel::toggleScanToEditMode,
    onConfirmOverwrite = viewModel::confirmOverwriteAndArmWrite,
    onCancelOverwrite = viewModel::cancelOverwritePrompt,
    onSpeakScannedAgain = viewModel::speakLastScannedLabelAgain,
    onEditScannedLabel = viewModel::editLastScannedLabel,
    onSetConfirmBeforeOverwrite = viewModel::setConfirmBeforeOverwrite,
    onSetSpeakAfterWrite = viewModel::setSpeakAfterWrite,
    onSetSpeechRate = viewModel::setSpeechRate,
    onSetDefaultStickerType = viewModel::setDefaultStickerType,
    onTestSpeechOutput = viewModel::testSpeechOutput,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
  state: TagSpeakUiState,
  onSelectTab: (TagSpeakTab) -> Unit,
  onDraftLabelChanged: (String) -> Unit,
  onClearDraft: () -> Unit,
  onToggleArmWrite: () -> Unit,
  onToggleScanToEdit: () -> Unit,
  onConfirmOverwrite: () -> Unit,
  onCancelOverwrite: () -> Unit,
  onSpeakScannedAgain: () -> Unit,
  onEditScannedLabel: () -> Unit,
  onSetConfirmBeforeOverwrite: (Boolean) -> Unit,
  onSetSpeakAfterWrite: (Boolean) -> Unit,
  onSetSpeechRate: (Float) -> Unit,
  onSetDefaultStickerType: (StickerTypeProfile) -> Unit = {},
  onTestSpeechOutput: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
          )
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
      )
    },
    bottomBar = {
      NavigationBar {
        NavigationBarItem(
          selected = state.selectedTab == TagSpeakTab.WRITE,
          onClick = { onSelectTab(TagSpeakTab.WRITE) },
          icon = {},
          label = { Text(stringResource(R.string.tab_write)) },
          modifier = Modifier.testTag("tab_write"),
        )
        NavigationBarItem(
          selected = state.selectedTab == TagSpeakTab.INSPECT,
          onClick = { onSelectTab(TagSpeakTab.INSPECT) },
          icon = {},
          label = { Text(stringResource(R.string.tab_inspect)) },
          modifier = Modifier.testTag("tab_inspect"),
        )
        NavigationBarItem(
          selected = state.selectedTab == TagSpeakTab.SETTINGS,
          onClick = { onSelectTab(TagSpeakTab.SETTINGS) },
          icon = {},
          label = { Text(stringResource(R.string.tab_settings)) },
          modifier = Modifier.testTag("tab_settings"),
        )
      }
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.padding(innerPadding)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      StatusAnnouncementBanner(statusText = state.statusAnnouncement)

      when (state.selectedTab) {
        TagSpeakTab.WRITE ->
          WriteLabelSection(
            state = state,
            onDraftLabelChanged = onDraftLabelChanged,
            onClearDraft = onClearDraft,
            onToggleArmWrite = onToggleArmWrite,
            onToggleScanToEdit = onToggleScanToEdit,
            onConfirmOverwrite = onConfirmOverwrite,
            onCancelOverwrite = onCancelOverwrite,
          )
        TagSpeakTab.INSPECT ->
          InspectStickerSection(
            state = state,
            onSpeakScannedAgain = onSpeakScannedAgain,
            onEditScannedLabel = onEditScannedLabel,
          )
        TagSpeakTab.SETTINGS ->
          SettingsSection(
            state = state,
            onSetConfirmBeforeOverwrite = onSetConfirmBeforeOverwrite,
            onSetSpeakAfterWrite = onSetSpeakAfterWrite,
            onSetSpeechRate = onSetSpeechRate,
            onSetDefaultStickerType = onSetDefaultStickerType,
            onTestSpeechOutput = onTestSpeechOutput,
          )
      }
    }
  }
}

@Composable
private fun StatusAnnouncementBanner(
  statusText: String,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .testTag("status_banner")
        .semantics(mergeDescendants = true) {
          heading()
          liveRegion = LiveRegionMode.Polite
          contentDescription = "Current Status: $statusText"
        },
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "Current Status",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = statusText,
        style = MaterialTheme.typography.bodyLarge,
      )
    }
  }
}

@Composable
private fun WriteLabelSection(
  state: TagSpeakUiState,
  onDraftLabelChanged: (String) -> Unit,
  onClearDraft: () -> Unit,
  onToggleArmWrite: () -> Unit,
  onToggleScanToEdit: () -> Unit,
  onConfirmOverwrite: () -> Unit,
  onCancelOverwrite: () -> Unit,
) {
  val capacityAnnouncement = state.capacitySummaryText
  val stickerProfile = state.settings.defaultStickerType

  Text(
    text = stringResource(R.string.write_heading),
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )

  val armWriteButtonFocusRequester = remember { FocusRequester() }
  val confirmButtonFocusRequester = remember { FocusRequester() }

  // Overwrite confirmation modal dialog when an existing sticker label is detected
  val prompt = state.pendingOverwritePrompt
  if (prompt != null) {
    LaunchedEffect(prompt) {
      // Direct accessibility/keyboard focus to the Confirm Overwrite button immediately
      confirmButtonFocusRequester.requestFocus()
    }

    AlertDialog(
      onDismissRequest = {
        onCancelOverwrite()
        armWriteButtonFocusRequester.requestFocus()
      },
      title = {
        Text(
          text = stringResource(R.string.overwrite_warning_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.semantics { heading() }
        )
      },
      text = {
        Text(
          text = "This sticker currently says: \"${prompt.existingLabel}\". Do you want to replace it with \"${prompt.newLabel}\"?",
          style = MaterialTheme.typography.bodyLarge
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onConfirmOverwrite()
            armWriteButtonFocusRequester.requestFocus()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
          ),
          modifier = Modifier
            .testTag("confirm_overwrite_button")
            .focusRequester(confirmButtonFocusRequester)
        ) {
          Text(stringResource(R.string.confirm_overwrite_button))
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = {
            onCancelOverwrite()
            armWriteButtonFocusRequester.requestFocus()
          },
          modifier = Modifier.testTag("cancel_overwrite_button")
        ) {
          Text(stringResource(R.string.cancel_overwrite_button))
        }
      },
      modifier = Modifier
        .testTag("overwrite_confirmation_dialog")
        .testTag("overwrite_confirmation_card")
    )
  }

  OutlinedTextField(
    value = state.draftLabelText,
    onValueChange = onDraftLabelChanged,
    label = {
      Text(
        "${stringResource(R.string.label_input_title)} (up to ${state.maxRecommendedTextBytes} characters)"
      )
    },
    placeholder = { Text(stringResource(R.string.label_input_placeholder)) },
    isError = state.isDraftOverRecommendedSize,
    supportingText = {
      Text(
        text =
          if (state.isDraftOverRecommendedSize) {
            "$capacityAnnouncement. Exceeds ${stickerProfile.shortLabel} capacity; use a higher-capacity sticker or shorten the label."
          } else {
            capacityAnnouncement
          }
      )
    },
    modifier =
      Modifier.fillMaxWidth()
        .testTag("label_text_input")
        .semantics {
          if (state.isDraftOverRecommendedSize) {
            error(
              "Label exceeds ${state.maxRecommendedTextBytes} characters (${state.maxRecommendedTextBytes} bytes) for ${stickerProfile.shortLabel}s."
            )
          }
        },
    minLines = 3,
    maxLines = 6,
  )

  val isArmedToWrite = state.nfcMode == NfcOperationMode.ARMED_TO_WRITE
  Button(
    onClick = onToggleArmWrite,
    enabled = isArmedToWrite || state.canArmWrite,
    modifier =
      Modifier.fillMaxWidth()
        .testTag("arm_write_button")
        .focusRequester(armWriteButtonFocusRequester)
        .semantics {
          stateDescription =
            if (isArmedToWrite) {
              "Waiting for NFC sticker tap"
            } else {
              "Not currently writing"
            }
        },
  ) {
    Text(
      text =
        if (isArmedToWrite) {
          stringResource(R.string.cancel_write_button)
        } else {
          stringResource(R.string.write_to_sticker_button)
        }
    )
  }

  val isArmedScanToEdit = state.nfcMode == NfcOperationMode.ARMED_TO_LOAD_FOR_EDIT
  OutlinedButton(
    onClick = onToggleScanToEdit,
    modifier =
      Modifier.fillMaxWidth()
        .testTag("scan_to_edit_button")
        .semantics {
          stateDescription =
            if (isArmedScanToEdit) {
              "Waiting to scan existing sticker into editor"
            } else {
              "Inactive"
            }
        },
  ) {
    Text(
      text =
        if (isArmedScanToEdit) {
          stringResource(R.string.cancel_scan_to_edit_button)
        } else {
          stringResource(R.string.scan_to_edit_button)
        }
    )
  }

  if (state.draftLabelText.isNotEmpty()) {
    OutlinedButton(
      onClick = onClearDraft,
      modifier = Modifier.fillMaxWidth().testTag("clear_label_button"),
    ) {
      Text(stringResource(R.string.clear_label_button))
    }
  }
}

@Composable
private fun InspectStickerSection(
  state: TagSpeakUiState,
  onSpeakScannedAgain: () -> Unit,
  onEditScannedLabel: () -> Unit,
) {
  Text(
    text = stringResource(R.string.inspect_heading),
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )

  val scanned = state.lastScannedLabel
  if (scanned == null) {
    Card(
      modifier = Modifier.fillMaxWidth().testTag("inspect_empty_card"),
    ) {
      Text(
        text = stringResource(R.string.inspect_empty_message),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(16.dp),
      )
    }
  } else {
    Card(
      modifier = Modifier.fillMaxWidth().testTag("inspected_label_card"),
      colors =
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Spoken Label",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = scanned.text,
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.testTag("inspected_label_text").semantics { heading() },
        )
        val capacitySummary =
          if (scanned.maxCapacityBytes != null) {
            val maxTextChars =
              TagSpeakNdefCodec.maxTextBytesForTagCapacity(scanned.maxCapacityBytes)
            "Sticker Capacity: ${scanned.text.length} of $maxTextChars characters used (${scanned.usedBytes} of ${scanned.maxCapacityBytes} NDEF bytes)"
          } else {
            "Label Length: ${scanned.text.length} characters (${scanned.usedBytes} NDEF bytes)"
          }
        Text(
          text = capacitySummary,
          style = MaterialTheme.typography.bodyMedium,
        )
        val writableSummary =
          if (scanned.isWritable) {
            "Sticker Status: Writable (can be edited or rewritten)"
          } else {
            "Sticker Status: Read-only"
          }
        Text(
          text = writableSummary,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    Button(
      onClick = onSpeakScannedAgain,
      modifier = Modifier.fillMaxWidth().testTag("speak_again_button"),
    ) {
      Text(stringResource(R.string.speak_label_again_button))
    }

    if (scanned.isWritable) {
      OutlinedButton(
        onClick = onEditScannedLabel,
        modifier = Modifier.fillMaxWidth().testTag("edit_scanned_label_button"),
      ) {
        Text(stringResource(R.string.edit_scanned_label_button))
      }
    }
  }
}

@Composable
private fun SettingsSection(
  state: TagSpeakUiState,
  onSetConfirmBeforeOverwrite: (Boolean) -> Unit,
  onSetSpeakAfterWrite: (Boolean) -> Unit,
  onSetSpeechRate: (Float) -> Unit,
  onSetDefaultStickerType: (StickerTypeProfile) -> Unit,
  onTestSpeechOutput: () -> Unit,
) {
  Text(
    text = stringResource(R.string.settings_heading),
    style = MaterialTheme.typography.headlineSmall,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )

  AccessibleSwitchRow(
    title = stringResource(R.string.setting_confirm_overwrite_title),
    description = stringResource(R.string.setting_confirm_overwrite_desc),
    checked = state.settings.confirmBeforeOverwrite,
    onCheckedChange = onSetConfirmBeforeOverwrite,
    modifier = Modifier.testTag("switch_confirm_overwrite"),
  )

  AccessibleSwitchRow(
    title = stringResource(R.string.setting_speak_after_write_title),
    description = stringResource(R.string.setting_speak_after_write_desc),
    checked = state.settings.speakAfterWrite,
    onCheckedChange = onSetSpeakAfterWrite,
    modifier = Modifier.testTag("switch_speak_after_write"),
  )

  Spacer(modifier = Modifier.height(8.dp))

  Text(
    text = stringResource(R.string.setting_default_sticker_type_heading),
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )
  Text(
    text = stringResource(R.string.setting_default_sticker_type_desc),
    style = MaterialTheme.typography.bodyMedium,
  )

  Column(
    modifier = Modifier.fillMaxWidth().selectableGroup(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    StickerTypeProfile.entries.forEach { profile ->
      val isSelected = state.settings.defaultStickerType == profile
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .testTag("sticker_type_${profile.id}")
            .selectable(
              selected = isSelected,
              role = Role.RadioButton,
              onClick = { onSetDefaultStickerType(profile) },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = isSelected,
          onClick = null,
        )
        Text(
          text = profile.displayName,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(start = 12.dp),
        )
      }
    }
  }

  Spacer(modifier = Modifier.height(8.dp))

  Text(
    text = stringResource(R.string.setting_speech_rate_heading),
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.semantics { heading() },
  )

  val speedOptions =
    listOf(
      0.75f to "0.75x (Slower)",
      1.0f to "1.0x (Normal)",
      1.25f to "1.25x (Fast)",
      1.5f to "1.5x (Faster)",
      2.0f to "2.0x (Maximum)",
    )

  Column(
    modifier = Modifier.fillMaxWidth().selectableGroup(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    speedOptions.forEach { (rateValue, rateLabel) ->
      val isSelected = kotlin.math.abs(state.settings.speechRate - rateValue) < 0.05f
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .selectable(
              selected = isSelected,
              role = Role.RadioButton,
              onClick = { onSetSpeechRate(rateValue) },
            )
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = isSelected,
          onClick = null,
        )
        Text(
          text = rateLabel,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(start = 12.dp),
        )
      }
    }
  }

  Button(
    onClick = onTestSpeechOutput,
    modifier = Modifier.fillMaxWidth().testTag("test_speech_button"),
  ) {
    Text(stringResource(R.string.test_speech_button))
  }
}

@Composable
private fun AccessibleSwitchRow(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .toggleable(
          value = checked,
          role = Role.Switch,
          onValueChange = onCheckedChange,
        ),
  ) {
    Row(
      modifier = Modifier.padding(16.dp).fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f).padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
      Switch(
        checked = checked,
        onCheckedChange = null,
      )
    }
  }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
  TagSpeakTheme {
    MainScreenContent(
      state = TagSpeakUiState(draftLabelText = "Oatmeal, expires November 2027"),
      onSelectTab = {},
      onDraftLabelChanged = {},
      onClearDraft = {},
      onToggleArmWrite = {},
      onToggleScanToEdit = {},
      onConfirmOverwrite = {},
      onCancelOverwrite = {},
      onSpeakScannedAgain = {},
      onEditScannedLabel = {},
      onSetConfirmBeforeOverwrite = {},
      onSetSpeakAfterWrite = {},
      onSetSpeechRate = {},
      onTestSpeechOutput = {},
    )
  }
}
