package local.readapp
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import local.readapp.data.LocalServices
import local.readapp.feature.IncomingFile
import local.readapp.feature.ReadApp
import local.readapp.txt.IndexedTxtEngine
import local.readapp.epub.SafeEpubEngine

class ReadApplication:Application() {
    val services by lazy { LocalServices(this,IndexedTxtEngine(),SafeEpubEngine()) }
}
class MainActivity:ComponentActivity() {
    private val incoming=Channel<IncomingFile>(Channel.UNLIMITED)
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        attachI18n(applicationContext)
        consume(intent)
        val services=(application as ReadApplication).services
        setContent {
            var splash by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxSize()) {
                ReadApp(services.books,services.preferences,remember { incoming.receiveAsFlow() })
                AnimatedVisibility(visible=splash, exit=fadeOut(tween(380))) {
                    ReaderLaunchSplash(onFinished={ splash=false })
                }
            }
        }
    }
    override fun onTrimMemory(level:Int) {
        super.onTrimMemory(level)
        // 国内 4GB 内存机型常见内存紧张：主动释放已解出的媒体缓存，避免被系统杀进程
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            java.io.File(cacheDir,"reader-assets").deleteRecursively()
        }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); consume(intent) }
    private fun consume(value:Intent?) {
        if(value?.action==Intent.ACTION_VIEW && value.data!=null) {
            incoming.trySend(IncomingFile(value.data.toString(),value.flags))
            setIntent(Intent(this,MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        }
    }
}
