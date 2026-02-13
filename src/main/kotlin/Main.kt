import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import ui.App

fun main() = application {
    Window(title = "Iceberg Lens", onCloseRequest = ::exitApplication) {
        App()
    }
}