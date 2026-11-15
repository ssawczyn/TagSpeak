package com.tagspeak.app.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.tagspeak.app.data.InMemoryPreferencesRepository
import com.tagspeak.app.nfc.ParsedTagSpeakLabel
import com.tagspeak.app.ui.theme.TagSpeakTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Automated accessibility & Compose semantics tests for [MainScreen].
 */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private lateinit var viewModel: MainScreenViewModel

  @Before
  fun setup() {
    viewModel = MainScreenViewModel(InMemoryPreferencesRepository())
    composeTestRule.setContent {
      TagSpeakTheme {
        MainScreen(viewModel = viewModel)
      }
    }
  }

  @Test
  fun statusBanner_exposesPoliteLiveRegionAndHeadingSemantics() {
    composeTestRule
      .onNodeWithTag("status_banner")
      .assertIsDisplayed()
      .assert(
        SemanticsMatcher.expectValue(
          SemanticsProperties.LiveRegion,
          LiveRegionMode.Polite,
        )
      )

    composeTestRule
      .onNodeWithText("Create or Edit Sticker Label")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
  }

  @Test
  fun writeFlow_armsWriteAndDisplaysOverwriteConfirmationWhenExistingLabelFound() {
    composeTestRule.onNodeWithTag("label_text_input").performTextInput("Cinnamon")
    composeTestRule.onNodeWithTag("arm_write_button").performClick()

    composeTestRule
      .onNodeWithTag("arm_write_button")
      .assert(
        SemanticsMatcher.expectValue(
          SemanticsProperties.StateDescription,
          "Waiting for NFC sticker tap",
        )
      )

    // Simulate tapping a sticker that already contains "Nutmeg"
    composeTestRule.runOnIdle {
      viewModel.onHardwareTagDiscovered(
        tag = null,
        parsedFromIntent = ParsedTagSpeakLabel(text = "Nutmeg", hasTagSpeakAar = true),
      )
    }

    composeTestRule.onNodeWithTag("overwrite_confirmation_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("confirm_overwrite_button").performClick()

    // Tap again to complete the overwrite
    composeTestRule.runOnIdle {
      viewModel.onHardwareTagDiscovered(
        tag = null,
        parsedFromIntent = ParsedTagSpeakLabel(text = "Nutmeg", hasTagSpeakAar = true),
      )
    }

    composeTestRule
      .onNodeWithTag("status_banner")
      .assert(
        SemanticsMatcher.expectValue(
          SemanticsProperties.ContentDescription,
          listOf("Status: Label written to sticker: \"Cinnamon\"."),
        )
      )
  }

  @Test
  fun inspectToEditFlow_loadsScannedLabelIntoEditor() {
    composeTestRule.runOnIdle {
      viewModel.onHardwareTagDiscovered(
        tag = null,
        parsedFromIntent =
          ParsedTagSpeakLabel(
            text = "Apple Cider Vinegar",
            hasTagSpeakAar = true,
            usedBytes = 60,
            maxCapacityBytes = 137,
            isWritable = true,
          ),
      )
    }

    composeTestRule.onNodeWithTag("inspected_label_text").assertTextContains("Apple Cider Vinegar")
    composeTestRule.onNodeWithTag("edit_scanned_label_button").performClick()

    // Should switch back to Write tab with the scanned label pre-filled in the text input
    composeTestRule.onNodeWithTag("label_text_input").assertTextContains("Apple Cider Vinegar")
  }

  @Test
  fun settingsTab_exposesMergedSwitchRoleAndTogglesOverwriteProtection() {
    composeTestRule.onNodeWithTag("tab_settings").performClick()

    val confirmSwitch = composeTestRule.onNodeWithTag("switch_confirm_overwrite")
    confirmSwitch
      .assertIsDisplayed()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
      .assertIsOn()

    confirmSwitch.performClick()
    confirmSwitch.assertIsOff()
  }
}
