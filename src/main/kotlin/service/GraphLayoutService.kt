package service

import model.*
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.options.SizeConstraint
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkNode
import org.eclipse.elk.graph.util.ElkGraphUtil
import java.io.File
import java.net.URI
import java.util.*

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

        // Configure Layout: Top-Down, Orthogonal Routing with Strict Sizing and Increased Padding
        root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
        root.setProperty(CoreOptions.DIRECTION, Direction.DOWN)

        // INCREASE THESE VALUES significantly to prevent visual overlap
        root.setProperty(CoreOptions.SPACING_NODE_NODE, 100.0)
        root.setProperty(CoreOptions.SPACING_EDGE_NODE, 60.0)

        root.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, EnumSet.of(SizeConstraint.MINIMUM_SIZE))
        root.setProperty(
            CoreOptions.EDGE_ROUTING, org.eclipse.elk.core.options.EdgeRouting.ORTHOGONAL
        )

        // Maps to keep track of ELK nodes and actual Data Nodes
        val elkNodes = mutableMapOf<String, ElkNode>()
        val logicalNodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // 1. Create Snapshot Nodes (The Backbone)
        snapshots.sortedBy { it.timestampMs }.forEach { snap ->
            val sId = "snap_${snap.snapshotId}"
            elkNodes[sId] = createElkNode(root, sId, 220.0, 100.0)
            logicalNodes[sId] = GraphNode.SnapshotNode(sId, snap)

            // Link to parent
            if (snap.parentSnapshotId != null) {
                val pId = "snap_${snap.parentSnapshotId}"
                if (elkNodes.containsKey(pId)) {
                    ElkGraphUtil.createSimpleEdge(elkNodes[pId], elkNodes[sId])
                    edges.add(GraphEdge("e_$sId", pId, sId))
                }
            }

            // 2. Create Manifest List Node (One per snapshot)
            val mlId = "ml_${snap.snapshotId}"
            elkNodes[mlId] = createElkNode(root, mlId, 180.0, 60.0)
            logicalNodes[mlId] = GraphNode.ManifestListNode(mlId, snap.manifestList.orEmpty())
            ElkGraphUtil.createSimpleEdge(elkNodes[sId], elkNodes[mlId])
            edges.add(GraphEdge("e_ml_$sId", sId, mlId))

            // 3. Create Manifest Nodes
            // Filter to only manifests belonging to this snapshot
            val manifests = loadedManifestLists[snap.snapshotId.toString()] ?: emptyList()

            manifests.forEachIndexed { idx, manifest ->
                val mId = "man_${snap.snapshotId}_$idx"
                elkNodes[mId] = createElkNode(root, mId, 200.0, 80.0)
                logicalNodes[mId] = GraphNode.ManifestNode(mId, manifest)
                ElkGraphUtil.createSimpleEdge(elkNodes[mlId], elkNodes[mId])
                edges.add(GraphEdge("e_man_$mId", mlId, mId))

                // 4. Create File Nodes
                val manifestPath = manifest.manifestPath
                if (manifestPath != null) {
                    val fileEntries = loadedFiles[manifestPath] ?: emptyList()

                    // Limit visual clutter: only show first 10 files if too many
                    fileEntries.take(10).forEachIndexed { fIdx, entry ->
                        val fId = "file_${mId}_$fIdx"
                        elkNodes[fId] = createElkNode(root, fId, 200.0, 60.0)

                        // Safely extract DataFile and inject into logical node
                        val dataFile = entry.dataFile ?: DataFile(filePath = "unknown")
                        logicalNodes[fId] = GraphNode.FileNode(fId, dataFile)

                        ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[fId])
                        edges.add(GraphEdge("e_file_$fId", mId, fId))

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
                                        elkNodes[rId] = createElkNode(root, rId, 160.0, 40.0)
                                        logicalNodes[rId] = GraphNode.RowNode(rId, rowData)
                                        ElkGraphUtil.createSimpleEdge(elkNodes[fId], elkNodes[rId])
                                        edges.add(GraphEdge("e_row_$rId", fId, rId))
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
