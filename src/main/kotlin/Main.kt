import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ui.App
import java.util.prefs.Preferences

private val prefs = Preferences.userRoot().node("com.github.mmdemirbas.icelens.window")
private const val PREF_WINDOW_X = "window_x"
private const val PREF_WINDOW_Y = "window_y"
private const val PREF_WINDOW_WIDTH = "window_width"
private const val PREF_WINDOW_HEIGHT = "window_height"
private const val PREF_WINDOW_MAXIMIZED = "window_maximized"

fun main() = application {
    val isMaximized = prefs.getBoolean(PREF_WINDOW_MAXIMIZED, false)
    val windowState = rememberWindowState(
        position = if (prefs.get(PREF_WINDOW_X, null) != null) {
            WindowPosition(
                prefs.getInt(PREF_WINDOW_X, 100).dp,
                prefs.getInt(PREF_WINDOW_Y, 100).dp
            )
        } else {
            WindowPosition.PlatformDefault
        },
        size = DpSize(
            prefs.getInt(PREF_WINDOW_WIDTH, 1200).dp,
            prefs.getInt(PREF_WINDOW_HEIGHT, 800).dp
        ),
        placement = if (isMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
    )

    Window(
        onCloseRequest = {
            // Persist window state before closing
            val isCurrentlyMaximized = windowState.placement == WindowPlacement.Maximized
            prefs.putBoolean(PREF_WINDOW_MAXIMIZED, isCurrentlyMaximized)
            if (!isCurrentlyMaximized) {
                prefs.putInt(PREF_WINDOW_X, windowState.position.x.value.toInt())
                prefs.putInt(PREF_WINDOW_Y, windowState.position.y.value.toInt())
                prefs.putInt(PREF_WINDOW_WIDTH, windowState.size.width.value.toInt())
                prefs.putInt(PREF_WINDOW_HEIGHT, windowState.size.height.value.toInt())
            }
            exitApplication()
        },
        title = "Iceberg Lens",
        state = windowState
    ) {
        App()
    }
}