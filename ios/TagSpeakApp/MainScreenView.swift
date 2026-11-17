#if canImport(SwiftUI)
import SwiftUI
#if canImport(TagSpeakCore)
import TagSpeakCore
#endif
#if canImport(UIKit)
import UIKit
#endif

public struct MainScreenView: View {
  @ObservedObject var viewModel: MainScreenViewModel

  public init(viewModel: MainScreenViewModel) {
    self.viewModel = viewModel
  }

  public var body: some View {
    NavigationStack {
      TabView(
        selection: Binding(
          get: { viewModel.uiState.selectedTab },
          set: { viewModel.selectTab($0) }
        )
      ) {
        ScrollView {
          VStack(alignment: .leading, spacing: 16) {
            StatusAnnouncementBanner(statusText: viewModel.uiState.statusAnnouncement)
            WriteLabelSection(viewModel: viewModel)
          }
          .padding(16)
        }
        .tabItem {
          Label("Write Label", systemImage: "square.and.pencil")
        }
        .tag(TagSpeakTab.write)
        .accessibilityIdentifier("tab_write")

        ScrollView {
          VStack(alignment: .leading, spacing: 16) {
            StatusAnnouncementBanner(statusText: viewModel.uiState.statusAnnouncement)
            InspectStickerSection(viewModel: viewModel)
          }
          .padding(16)
        }
        .tabItem {
          Label("Inspect Sticker", systemImage: "wave.3.right")
        }
        .tag(TagSpeakTab.inspect)
        .accessibilityIdentifier("tab_inspect")

        ScrollView {
          VStack(alignment: .leading, spacing: 16) {
            StatusAnnouncementBanner(statusText: viewModel.uiState.statusAnnouncement)
            SettingsSection(viewModel: viewModel)
          }
          .padding(16)
        }
        .tabItem {
          Label("Settings", systemImage: "gearshape")
        }
        .tag(TagSpeakTab.settings)
        .accessibilityIdentifier("tab_settings")
      }
      .navigationTitle("TagSpeak")
      #if os(iOS)
      .navigationBarTitleDisplayMode(.inline)
      #endif
      .onChange(of: viewModel.uiState.statusAnnouncement) { _, newAnnouncement in
        postVoiceOverAnnouncement(newAnnouncement)
      }
    }
  }

  private func postVoiceOverAnnouncement(_ message: String) {
    #if os(iOS)
    guard UIAccessibility.isVoiceOverRunning else { return }
    UIAccessibility.post(notification: .announcement, argument: message)
    #endif
  }
}

// MARK: - Status Banner

private struct StatusAnnouncementBanner: View {
  let statusText: String

  var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      Text("Current Status")
        .font(.caption.weight(.bold))
        .foregroundStyle(.secondary)
      Text(statusText)
        .font(.body)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    .padding(16)
    .background(
      RoundedRectangle(cornerRadius: 12)
        .fill(Color.secondary.opacity(0.14))
    )
    .accessibilityElement(children: .combine)
    .accessibilityLabel("Status: \(statusText)")
    .accessibilityIdentifier("status_banner")
  }
}

// MARK: - Write Label Tab

private struct WriteLabelSection: View {
  @ObservedObject var viewModel: MainScreenViewModel

  var body: some View {
    let state = viewModel.uiState
    let capacityAnnouncement =
      "\(state.draftTextUtf8Bytes) of \(state.maxRecommendedTextBytes) bytes used for standard NTAG213 sticker"

    VStack(alignment: .leading, spacing: 16) {
      // Quick Read Action at top so iOS users can always trigger a read scan with one tap
      Button {
        viewModel.startReadStickerScan()
      } label: {
        Label("Read Sticker Aloud", systemImage: "wave.3.right.circle.fill")
          .font(.headline)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 8)
      }
      .buttonStyle(.borderedProminent)
      .tint(.accentColor)
      .accessibilityHint("Opens the NFC scanner to read and speak any TagSpeak sticker aloud.")
      .accessibilityIdentifier("read_sticker_button")

      Text("Write a Spoken Sticker Label")
        .font(.title3.weight(.bold))
        .accessibilityAddTraits(.isHeader)

      if let prompt = state.pendingOverwritePrompt {
        OverwriteConfirmationCard(
          prompt: prompt,
          onConfirm: { viewModel.confirmOverwriteAndArmWrite() },
          onCancel: { viewModel.cancelOverwritePrompt() }
        )
      }

      VStack(alignment: .leading, spacing: 8) {
        Text("Spoken label text")
          .font(.subheadline.weight(.semibold))

        TextEditor(
          text: Binding(
            get: { state.draftLabelText },
            set: { viewModel.updateDraftLabelText($0) }
          )
        )
        .frame(minHeight: 96, maxHeight: 150)
        .padding(8)
        .overlay(
          RoundedRectangle(cornerRadius: 8)
            .stroke(
              state.isDraftOverRecommendedSize ? Color.red : Color.secondary.opacity(0.4),
              lineWidth: 1.5
            )
        )
        .accessibilityLabel("Spoken label text")
        .accessibilityValue(
          state.draftLabelText.isEmpty ? "Empty" : state.draftLabelText
        )
        .accessibilityHint(
          state.isDraftOverRecommendedSize
            ? "Warning: Label exceeds 96 bytes for standard NTAG213 stickers. \(capacityAnnouncement)"
            : capacityAnnouncement
        )
        .accessibilityIdentifier("label_text_input")

        Text(
          state.isDraftOverRecommendedSize
            ? "\(capacityAnnouncement). Requires a higher-capacity sticker such as NTAG215."
            : capacityAnnouncement
        )
        .font(.footnote)
        .foregroundStyle(state.isDraftOverRecommendedSize ? .red : .secondary)
        .accessibilityIdentifier("capacity_counter_text")
      }

      let isArmedToWrite = (state.nfcMode == .armedToWrite)
      Button {
        viewModel.toggleArmWrite()
      } label: {
        Text(isArmedToWrite ? "Cancel Waiting for Sticker" : "Write Label to Sticker")
          .font(.headline)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 6)
      }
      .buttonStyle(.borderedProminent)
      .disabled(!(isArmedToWrite || state.canArmWrite))
      .accessibilityValue(
        isArmedToWrite ? "Waiting for NFC sticker tap" : "Not currently writing"
      )
      .accessibilityIdentifier("arm_write_button")

      let isArmedScanToEdit = (state.nfcMode == .armedToLoadForEdit)
      Button {
        viewModel.toggleScanToEditMode()
      } label: {
        Text(
          isArmedScanToEdit
            ? "Cancel Scan to Edit"
            : "Scan Existing Sticker to Edit"
        )
        .frame(maxWidth: .infinity)
        .padding(.vertical, 6)
      }
      .buttonStyle(.bordered)
      .accessibilityValue(
        isArmedScanToEdit ? "Waiting to scan existing sticker into editor" : "Inactive"
      )
      .accessibilityIdentifier("scan_to_edit_button")

      if !state.draftLabelText.isEmpty {
        Button(role: .destructive) {
          viewModel.clearDraftLabel()
        } label: {
          Text("Clear Label Text")
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier("clear_label_button")
      }
    }
  }
}

private struct OverwriteConfirmationCard: View {
  let prompt: PendingOverwritePrompt
  let onConfirm: () -> Void
  let onCancel: () -> Void

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text("Sticker Already Has a Label")
        .font(.headline)
        .foregroundStyle(.red)
        .accessibilityAddTraits(.isHeader)

      Text(
        "This sticker currently says: \"\(prompt.existingLabel)\". Do you want to replace it with \"\(prompt.newLabel)\"?"
      )
      .font(.body)

      HStack(spacing: 12) {
        Button(role: .destructive, action: onConfirm) {
          Text("Confirm Overwrite")
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(.red)
        .accessibilityIdentifier("confirm_overwrite_button")

        Button(action: onCancel) {
          Text("Cancel")
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier("cancel_overwrite_button")
      }
    }
    .padding(16)
    .background(
      RoundedRectangle(cornerRadius: 12)
        .fill(Color.red.opacity(0.12))
    )
    .accessibilityIdentifier("overwrite_confirmation_card")
  }
}

// MARK: - Inspect Sticker Tab

private struct InspectStickerSection: View {
  @ObservedObject var viewModel: MainScreenViewModel

  var body: some View {
    let state = viewModel.uiState

    VStack(alignment: .leading, spacing: 16) {
      Text("Last Scanned Sticker")
        .font(.title3.weight(.bold))
        .accessibilityAddTraits(.isHeader)

      Button {
        viewModel.startReadStickerScan()
      } label: {
        Label("Scan Sticker Now", systemImage: "wave.3.right.circle.fill")
          .font(.headline)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 8)
      }
      .buttonStyle(.borderedProminent)
      .accessibilityIdentifier("inspect_scan_button")

      if let scanned = state.lastScannedLabel {
        VStack(alignment: .leading, spacing: 8) {
          Text("Spoken Label")
            .font(.subheadline.weight(.bold))
            .foregroundStyle(.secondary)

          Text(scanned.text)
            .font(.title3.weight(.semibold))
            .accessibilityAddTraits(.isHeader)
            .accessibilityIdentifier("inspected_label_text")

          let capacitySummary =
            if let maxCap = scanned.maxCapacityBytes {
              "NDEF Size: \(scanned.usedBytes) of \(maxCap) bytes used"
            } else {
              "NDEF Size: \(scanned.usedBytes) bytes"
            }
          Text(capacitySummary)
            .font(.subheadline)

          let writableSummary =
            scanned.isWritable
            ? "Sticker Status: Writable (can be edited or rewritten)"
            : "Sticker Status: Read-only"
          Text(writableSummary)
            .font(.subheadline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
          RoundedRectangle(cornerRadius: 12)
            .fill(Color.secondary.opacity(0.12))
        )
        .accessibilityIdentifier("inspected_label_card")

        Button {
          viewModel.speakLastScannedLabelAgain()
        } label: {
          Text("Speak Label Again")
            .font(.headline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
        }
        .buttonStyle(.borderedProminent)
        .accessibilityIdentifier("speak_again_button")

        if scanned.isWritable {
          Button {
            viewModel.editLastScannedLabel()
          } label: {
            Text("Edit or Rewrite This Label")
              .frame(maxWidth: .infinity)
              .padding(.vertical, 6)
          }
          .buttonStyle(.bordered)
          .accessibilityIdentifier("edit_scanned_label_button")
        }
      } else {
        Text(
          "No sticker scanned yet in this session. Activate Scan Sticker Now to read and inspect any TagSpeak sticker."
        )
        .font(.body)
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
          RoundedRectangle(cornerRadius: 12)
            .fill(Color.secondary.opacity(0.12))
        )
        .accessibilityIdentifier("inspect_empty_card")
      }
    }
  }
}

// MARK: - Settings Tab

private struct SettingsSection: View {
  @ObservedObject var viewModel: MainScreenViewModel

  private let speedOptions: [(Float, String)] = [
    (0.75, "0.75x (Slower)"),
    (1.00, "1.0x (Normal)"),
    (1.25, "1.25x (Fast)"),
    (1.50, "1.5x (Faster)"),
    (2.00, "2.0x (Maximum)"),
  ]

  var body: some View {
    let settings = viewModel.uiState.settings

    VStack(alignment: .leading, spacing: 16) {
      Text("Accessibility & NFC Settings")
        .font(.title3.weight(.bold))
        .accessibilityAddTraits(.isHeader)

      AccessibleSwitchRow(
        title: "Confirm before overwriting an existing label",
        description:
          "Warn me and ask for confirmation when writing to a sticker that already contains a label.",
        isOn: settings.confirmBeforeOverwrite,
        onToggle: { viewModel.setConfirmBeforeOverwrite($0) }
      )
      .accessibilityIdentifier("switch_confirm_overwrite")

      AccessibleSwitchRow(
        title: "Speak label aloud after writing",
        description: "Automatically speak a confirmation when a sticker is written.",
        isOn: settings.speakAfterWrite,
        onToggle: { viewModel.setSpeakAfterWrite($0) }
      )
      .accessibilityIdentifier("switch_speak_after_write")

      AccessibleSwitchRow(
        title: "Automatically start scanning on app launch",
        description:
          "Immediately open the NFC scanner when TagSpeak opens (ideal when launching TagSpeak from the Action Button or Home Screen).",
        isOn: settings.autoScanOnLaunch,
        onToggle: { viewModel.setAutoScanOnLaunch($0) }
      )
      .accessibilityIdentifier("switch_auto_scan_on_launch")

      Text("Speech Speed")
        .font(.headline)
        .padding(.top, 8)
        .accessibilityAddTraits(.isHeader)

      VStack(alignment: .leading, spacing: 8) {
        ForEach(speedOptions, id: \.0) { rateValue, rateLabel in
          let isSelected = abs(settings.speechRate - rateValue) < 0.05
          Button {
            viewModel.setSpeechRate(rateValue)
          } label: {
            HStack(spacing: 12) {
              Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                .accessibilityHidden(true)
              Text(rateLabel)
                .font(.body)
                .foregroundStyle(.primary)
              Spacer()
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 12)
            .background(
              RoundedRectangle(cornerRadius: 8)
                .fill(isSelected ? Color.accentColor.opacity(0.12) : Color.clear)
            )
          }
          .buttonStyle(.plain)
          .accessibilityElement(children: .combine)
          .accessibilityLabel(rateLabel)
          .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
        }
      }

      Button {
        viewModel.testSpeechOutput()
      } label: {
        Text("Test Speech Output")
          .font(.headline)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 6)
      }
      .buttonStyle(.borderedProminent)
      .accessibilityIdentifier("test_speech_button")
    }
  }
}

private struct AccessibleSwitchRow: View {
  let title: String
  let description: String
  let isOn: Bool
  let onToggle: (Bool) -> Void

  var body: some View {
    Toggle(
      isOn: Binding(
        get: { isOn },
        set: { onToggle($0) }
      )
    ) {
      VStack(alignment: .leading, spacing: 4) {
        Text(title)
          .font(.headline)
        Text(description)
          .font(.subheadline)
          .foregroundStyle(.secondary)
      }
    }
    .padding(16)
    .background(
      RoundedRectangle(cornerRadius: 12)
        .fill(Color.secondary.opacity(0.12))
    )
    .accessibilityElement(children: .combine)
  }
}
#endif
