package model

import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolWindowAnchor {
    LEFT, RIGHT, TOP, BOTTOM
}

data class ToolWindowConfig(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val anchor: ToolWindowAnchor,
    val isVisible: Boolean = true,
    val order: Int = 0
)
