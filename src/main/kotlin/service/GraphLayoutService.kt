package service

import model.*
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkNode
import org.eclipse.elk.graph.util.ElkGraphUtil
import java.io.File
import java.net.URI

object GraphLayoutService {

    init {
        // Register ELK Layered Algorithm manually for standalone usage
        LayoutMetaDataService
            .getInstance()
            .registerLayoutMetaDataProviders(LayeredMetaDataProvider())
    }

    fun layoutGraph(
        snapshots: List<Snapshot>,
        loadedManifestLists: Map<String, List<ManifestListEntry>>, // Map snapshotId -> Manifests
        loadedFiles: Map<String, List<ManifestEntry>>, // Map manifestPath -> Entries
        warehousePath: String,
        tableName: String,
        showRows: Boolean,
    ): GraphModel {

        val root = ElkGraphUtil.createGraph()

        // 1. Shift to Rightward Flow and enforce column spacing
        root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
        root.setProperty(CoreOptions.DIRECTION, Direction.RIGHT)
        root.setProperty(CoreOptions.SPACING_NODE_NODE, 100.0)

        val elkNodes = mutableMapOf<String, ElkNode>()
        val logicalNodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        snapshots.sortedBy { it.timestampMs }.forEach { snap ->
            val sId = "snap_${snap.snapshotId}"
            elkNodes[sId] = createElkNode(root, sId, 220.0, 100.0)
            logicalNodes[sId] = GraphNode.SnapshotNode(sId, snap)

            // 2. Add sibling edge to logical model ONLY. Do not pass to ELK.
            // This treats each snapshot as an independent tree root, forcing them all into Column 1.
            if (snap.parentSnapshotId != null) {
                val pId = "snap_${snap.parentSnapshotId}"
                if (elkNodes.containsKey(pId)) {
                    edges.add(GraphEdge("e_$sId", pId, sId, isSibling = true))
                }
            }

            val mlId = "ml_${snap.snapshotId}"
            elkNodes[mlId] = createElkNode(root, mlId, 180.0, 60.0)
            logicalNodes[mlId] = GraphNode.ManifestListNode(mlId, snap.manifestList.orEmpty())

            // 3. Add hierarchy edges to ELK normally
            ElkGraphUtil.createSimpleEdge(elkNodes[sId], elkNodes[mlId])
            edges.add(GraphEdge("e_ml_$sId", sId, mlId, isSibling = false))

            val manifests = loadedManifestLists[snap.snapshotId.toString()] ?: emptyList()

            manifests.forEachIndexed { idx, manifest ->
                val mId = "man_${snap.snapshotId}_$idx"
                elkNodes[mId] = createElkNode(root, mId, 200.0, 80.0)
                logicalNodes[mId] = GraphNode.ManifestNode(mId, manifest)
                ElkGraphUtil.createSimpleEdge(elkNodes[mlId], elkNodes[mId])
                edges.add(GraphEdge("e_man_$mId", mlId, mId, isSibling = false))

                val manifestPath = manifest.manifestPath
                if (manifestPath != null) {
                    val fileEntries = loadedFiles[manifestPath] ?: emptyList()

                    fileEntries.take(10).forEachIndexed { fIdx, entry ->
                        val fId = "file_${mId}_$fIdx"
                        elkNodes[fId] = createElkNode(root, fId, 200.0, 60.0)

                        val dataFile = entry.dataFile ?: DataFile(filePath = "unknown")
                        logicalNodes[fId] = GraphNode.FileNode(fId, dataFile)

                        ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[fId])
                        edges.add(GraphEdge("e_file_$fId", mId, fId, isSibling = false))

                        // --- ROW EXTRACTION LOGIC ---
                        if (showRows) {
                            val rawPath = dataFile.filePath.orEmpty()

                            // Safe URI stripping to match App.kt logic
                            val pathWithoutScheme = if (rawPath.startsWith("file:")) {
                                URI(rawPath).path
                            } else {
                                rawPath
                            }

                            val tableMarker = "/$tableName/"

                            val localFile = if (pathWithoutScheme.contains(tableMarker)) {
                                val relativePart = pathWithoutScheme.substringAfter(tableMarker)
                                File("$warehousePath/$tableName/$relativePart")
                            } else {
                                File(pathWithoutScheme)
                            }

                            if (localFile.exists()) {
                                try {
                                    val rows = DuckDbService.queryParquet(localFile.canonicalPath)
                                    // Safety limit: 5 rows max
                                    rows.take(5).forEachIndexed { rIdx, rowData ->
                                        val rId = "row_${fId}_$rIdx"
                                        elkNodes[rId] = createElkNode(root, rId, 200.0, 80.0)
                                        logicalNodes[rId] = GraphNode.RowNode(rId, rowData)
                                        ElkGraphUtil.createSimpleEdge(elkNodes[fId], elkNodes[rId])
                                        edges.add(
                                            GraphEdge(
                                                "e_row_$rId", fId, rId, isSibling = false
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    println("DuckDB Graph Read Error: ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Run Layout Engine
        val engine = RecursiveGraphLayoutEngine()
        engine.layout(root, BasicProgressMonitor())

        // Sync calculated ELK coordinates back to the actual logical models
        val finalNodes = logicalNodes.values.map { node ->
            val elkNode = elkNodes[node.id]
            if (elkNode != null) {
                node.x = elkNode.x
                node.y = elkNode.y
            }
            node
        }

        return GraphModel(finalNodes, edges, root.width, root.height)
    }

    private fun createElkNode(parent: ElkNode, id: String, w: Double, h: Double): ElkNode {
        val node = ElkGraphUtil.createNode(parent)
        node.identifier = id
        node.width = w
        node.height = h
        return node
    }
}
