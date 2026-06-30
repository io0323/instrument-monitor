import SwiftUI
import shared

@main
struct iOSApp: App {
    init() {
        IosKoinInitializerKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
