#if canImport(SwiftUI)
import SwiftUI
#if canImport(TagSpeakCore)
import TagSpeakCore
#endif

@main
public struct TagSpeakApp: App {
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var viewModel: MainScreenViewModel
  private let speechAnnouncer: IOSSpeechAnnouncer
  private let nfcCoordinator: CoreNfcSessionCoordinator
  @State private var hasHandledInitialLaunchScan = false

  public init() {
    let prefs = UserDefaultsTagSpeakRepository()
    let speaker = IOSSpeechAnnouncer()
    let coordinator = CoreNfcSessionCoordinator()
    let vm = MainScreenViewModel(
      preferencesRepository: prefs,
      speechSpeaker: speaker,
      nfcSessionDriver: coordinator
    )
    coordinator.viewModel = vm

    self.speechAnnouncer = speaker
    self.nfcCoordinator = coordinator
    self._viewModel = StateObject(wrappedValue: vm)
  }

  public var body: some Scene {
    WindowGroup {
      MainScreenView(viewModel: viewModel)
        .onChange(of: scenePhase) { _, newPhase in
          if newPhase == .active && !hasHandledInitialLaunchScan {
            hasHandledInitialLaunchScan = true
            viewModel.handleAppDidBecomeActive(isTriggeredByShortcut: false)
          }
        }
        #if canImport(AppIntents)
        .onReceive(
          NotificationCenter.default.publisher(for: .tagSpeakTriggerReadStickerIntent)
        ) { _ in
          viewModel.handleAppDidBecomeActive(isTriggeredByShortcut: true)
        }
        #endif
    }
  }
}
#endif
