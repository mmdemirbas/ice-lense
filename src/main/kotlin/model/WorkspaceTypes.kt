package model

import java.io.File

sealed class WorkspaceItem {
    abstract val path: String
    abstract val name: String

    data class Warehouse(
        override val path: String,
        override val name: String,
        val tables: List<String> = emptyList()
    ) : WorkspaceItem()

    data class SingleTable(
        override val path: String,
        override val name: String
    ) : WorkspaceItem()

    fun serialize(): String = when (this) {
        is Warehouse   -> "W|${path}"
        is SingleTable -> "T|${path}"
    }

    companion object {
        fun deserialize(s: String): WorkspaceItem? {
            val parts = s.split("|", limit = 2)
            if (parts.size != 2) return null
            val type = parts[0]
            val path = parts[1]
            val file = File(path)
            if (!file.exists()) return null
            return when (type) {
                "W" -> Warehouse(path, file.name)
                "T" -> SingleTable(path, file.name)
                else -> null
            }
        }
    }
}
