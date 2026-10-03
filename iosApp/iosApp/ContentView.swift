import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color(red: 0.04, green: 0.08, blue: 0.16)
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Image(systemName: "chart.pie.fill")
                    .font(.system(size: 52, weight: .semibold))
                    .foregroundStyle(Color(red: 0.36, green: 0.82, blue: 0.73))

                Text("Feniqo")
                    .font(.system(size: 36, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)

                Text("iOS uygulaması geliştirme aşamasında")
                    .font(.headline)
                    .foregroundStyle(.white.opacity(0.9))

                Text("Feniqo şu anda Android öncelikli ilerliyor. iOS sürümü güvenli oturum, yerel veritabanı ve ürün ekranları tamamlandıktan sonra kullanıma açılacak.")
                    .font(.body)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.white.opacity(0.68))
                    .padding(.horizontal, 24)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
