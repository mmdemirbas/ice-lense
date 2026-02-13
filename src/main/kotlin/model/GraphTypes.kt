package model

// The logical graph model used by the UI
data class GraphModel(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val width: Double,
    val height: Double
)

sealed class GraphNode(
    open val id: String,
    open val x: Double,
    open val y: Double,
    open val width: Double,
    open val height: Double
) {
    data class SnapshotNode(
        override val id: String,
        val data: Snapshot,
        override val x: Double = 0.0,
        override val y: Double = 0.0
    ) : GraphNode(id, x, y, 220.0, 100.0)

    data class ManifestListNode(
        override val id: String,
        val path: String,
        override val x: Double = 0.0,
        override val y: Double = 0.0
    ) : GraphNode(id, x, y, 180.0, 60.0)

    data class ManifestNode(
        override val id: String,
        val data: ManifestListEntry,
        override val x: Double = 0.0,
        override val y: Double = 0.0
    ) : GraphNode(id, x, y, 200.0, 80.0)

    data class FileNode(
        override val id: String,
        val data: DataFile,
        override val x: Double = 0.0,
        override val y: Double = 0.0
    ) : GraphNode(id, x, y, 200.0, 60.0)
}

data class GraphEdge(
    val id: String,
    val fromId: String,
    val toId: String,
    // Calculated route points from ELK
    val sections: List<EdgeSection> = emptyList()
)

data class EdgeSection(val startX: Double, val startY: Double, val endX: Double, val endY: Double)