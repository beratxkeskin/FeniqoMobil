import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color(red: 0.04, green: 0.08, blue: 0.16)
                .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    Image(systemName: "chart.pie.fill")
                        .font(.system(size: 52, weight: .semibold))
                        .foregroundStyle(Color(red: 0.36, green: 0.82, blue: 0.73))
                        .accessibilityHidden(true)

                    Text("Feniqo")
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .accessibilityLabel(Text("ios_shell_brand_accessibility_label"))

                    Text("ios_shell_title")
                        .font(.headline)
                        .foregroundStyle(.white.opacity(0.9))

                    Text("ios_shell_description")
                        .font(.body)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.white.opacity(0.68))
                        .padding(.horizontal, 24)
                }
                .padding(.vertical, 32)
                .frame(maxWidth: .infinity)
                .accessibilityElement(children: .combine)
                .accessibilityHint(Text("ios_shell_status_accessibility_hint"))
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            ContentView()
                .environment(\.locale, .init(identifier: "tr"))
                .previewDisplayName("Türkçe")

            ContentView()
                .environment(\.locale, .init(identifier: "en"))
                .previewDisplayName("English")
        }
    }
}
