package com.feniqo.mobile.demo

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feniqo.mobile.BuildConfig

@Composable
fun DemoGate(content: @Composable () -> Unit) {
    if (!BuildConfig.DEMO) { content(); return }
    val viewModel: DemoViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetFailed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Demo · Yalnız bu cihazda", style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { menu = true }) { Text("Demo seçenekleri") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)) {
            if (state == DemoState.READY) content()
            else Column(Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (state == DemoState.LOADING) {
                    CircularProgressIndicator()
                    Text("Örnek hesabın hazırlanıyor…")
                } else {
                    Text("Demo hazırlanamadı. Demoyu sıfırlayıp uygulamayı yeniden açabilirsin.")
                    Button(onClick = { confirmReset = true }) { Text("Demoyu sıfırla") }
                }
            }
        }
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, title = { Text("Örnek hesabın") },
        text = { Text("Veriler kurgusaldır. İşlemleri düzenleyebilir, yeni kayıt ekleyebilirsin. Bulut, davet ve canlı piyasa bağlantıları kapalıdır. Çıkış yaptıysan demo hesabını yeniden açabilirsin.") },
        confirmButton = { TextButton(onClick = { menu = false; viewModel.open() }, enabled = state != DemoState.LOADING) { Text("Hesabı aç") } },
        dismissButton = { TextButton(onClick = { menu = false; confirmReset = true }) { Text("Demoyu sıfırla") } })
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Demo sıfırlansın mı?") },
        text = { Text("Yalnız Feniqo Demo içindeki denemelerin ve ayarların temizlenecek. Uygulama kapanacak; yeniden açtığında güncel tarihli örnek veriler hazırlanacak. Normal Feniqo uygulaman etkilenmez.") },
        confirmButton = { TextButton(onClick = {
            confirmReset = false
            if (BuildConfig.DEMO && context.packageName == "com.feniqo.mobile.demo") {
                resetFailed = !(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).clearApplicationUserData()
            }
        }) { Text("Sıfırla ve kapat") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Vazgeç") } })
    if (resetFailed) AlertDialog(onDismissRequest = { resetFailed = false }, title = { Text("Sıfırlama başlatılamadı") },
        text = { Text("Android Ayarlar → Uygulamalar → Feniqo Demo → Depolama → Verileri temizle yolunu kullanabilirsin.") },
        confirmButton = { TextButton(onClick = { resetFailed = false }) { Text("Tamam") } })
}
