package local.readapp.design
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

@Composable fun ReaderTheme(theme:String, content:@Composable ()->Unit) {
    val colors=when(if(theme=="system")if(isSystemInDarkTheme())"dark" else "light" else theme) {
        "dark" -> darkColorScheme(primary=Color(0xFFC9A468),secondary=Color(0xFFC9A468),background=Color(0xFF16191B),surface=Color(0xFF16191B),surfaceContainer=Color(0xFF232729),onSurface=Color(0xFFE8E3D8),onSurfaceVariant=Color(0xFF9BA09B),outlineVariant=Color(0xFF32383A))
        else -> lightColorScheme(primary=Color(0xFF22282B),secondary=Color(0xFFB08D57),primaryContainer=Color(0xFFE7EAE3),onPrimaryContainer=Color(0xFF22282B),secondaryContainer=Color(0xFFF0E4CE),onSecondaryContainer=Color(0xFF5C4630),background=Color(0xFFF6F2E9),surface=Color(0xFFF6F2E9),surfaceContainer=Color(0xFFEDE7DA),onSurface=Color(0xFF211E1A),onSurfaceVariant=Color(0xFF7A736A),outlineVariant=Color(0xFFE0D8C8))
    }
    MaterialTheme(colorScheme=colors,content=content)
}
