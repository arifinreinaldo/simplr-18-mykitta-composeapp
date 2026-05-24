import SwiftUI
import Shared

@main
struct iOSApp: App {
    // SwiftUI App.init() runs once at process start, before any body is
    // computed — perfect spot for one-time Koin bootstrap. Without this,
    // the first koinViewModel<...>() call inside Compose throws
    // "KoinApplication has not been started".
    init() {
        AppModuleKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
