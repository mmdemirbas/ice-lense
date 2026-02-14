package service

import model.*
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.math.KVector
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

    fun layoutGraph(tableModel: UnifiedTableModel, showRows: Boolean): GraphModel {
        val root = ElkGraphUtil.createGraph()

        // 1. Shift to Rightward Flow and enforce column spacing
        root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
        root.setProperty(CoreOptions.DIRECTION, Direction.RIGHT)
        root.setProperty(CoreOptions.SPACING_NODE_NODE, 100.0)
        root.setProperty(
            org.eclipse.elk.alg.layered.options.LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS,
            300.0
        )

        val elkNodes = mutableMapOf<String, ElkNode>()
        val logicalNodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // 1. COLUMN 1: Lineage of Metadata Files
        tableModel.metadatas.forEach { metadata ->
            val fileName = metadata.path.fileName.toString()
            val meta = metadata.metadata
            val mId = "meta_${fileName}"
            elkNodes[mId] = createElkNode(root, mId, 260.0, 120.0)
            logicalNodes[mId] = GraphNode.MetadataNode(mId, fileName, meta)

            metadata.snapshots.forEach { snapshot ->
                val snap = snapshot.metadata
                val sId = "snap_${snap.snapshotId}"
                elkNodes[sId] = createElkNode(root, sId, 220.0, 100.0)
                logicalNodes[sId] = GraphNode.SnapshotNode(sId, snap)

                ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[sId])
                edges.add(GraphEdge("e_snap_$sId", mId, sId))

                // Snapshot Timeline Sibling Edge (Top to Bottom)
//                if (snap.parentSnapshotId != null) {
//                    val pId = "snap_${snap.parentSnapshotId}"
//                    if (elkNodes.containsKey(pId)) {
//                        edges.add(GraphEdge("e_$sId", pId, sId, isSibling = true))
//                    }
//                }

                // 4. COLUMN 4: Manifest Files
                val manifests = snapshot.manifestLists
                manifests.forEachIndexed { idx, unifiedManifest ->
                    val manifest = unifiedManifest.metadata
                    val mId = "man_${snap.snapshotId}_$idx"
                    elkNodes[mId] = createElkNode(root, mId, 200.0, 80.0)
                    logicalNodes[mId] = GraphNode.ManifestNode(mId, manifest)

                    // Hierarchy Edge (Column 3 -> Column 4)
                    ElkGraphUtil.createSimpleEdge(elkNodes[sId], elkNodes[mId])
                    edges.add(GraphEdge("e_man_$mId", sId, mId))

                    // 5. COLUMN 5: Data/Delete Files
                    val manifestPath = manifest.manifestPath
                    if (manifestPath != null) {
                        val unifiedDataFiles = unifiedManifest.manifests
                        unifiedDataFiles.take(10).forEachIndexed { fIdx, unifiedDataFile ->
                            val entry = unifiedDataFile.metadata
                            val fId = "file_${mId}_$fIdx"
                            elkNodes[fId] = createElkNode(root, fId, 200.0, 60.0)
                            val dataFile = entry.dataFile ?: DataFile(filePath = "unknown")
                            logicalNodes[fId] = GraphNode.FileNode(fId, dataFile)

                            // Hierarchy Edge (Column 4 -> Column 5)
                            ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[fId])
                            edges.add(GraphEdge("e_file_$fId", mId, fId))

                            // 6. COLUMN 6: Row Data
                            if (showRows) {
                                val rawPath = dataFile.filePath.orEmpty()
                                val pathWithoutScheme =
                                    if (rawPath.startsWith("file:")) URI(rawPath).path else rawPath
                                val tableMarker = "/${tableModel.name}/"

                                val localFile = if (pathWithoutScheme.contains(tableMarker)) {
                                    val relativePart = pathWithoutScheme.substringAfter(tableMarker)
                                    File("${tableModel.path}/$relativePart")
                                } else {
                                    File(pathWithoutScheme)
                                }

                                if (localFile.exists()) {
                                    try {
                                    val isDeleteFile = (entry.dataFile?.content ?: 0) > 0

                                        unifiedDataFile.rows
                                            .take(5)
                                            .forEachIndexed { rIdx, rowData ->
                                                val rId = "row_${fId}_$rIdx"
                                                elkNodes[rId] =
                                                    createElkNode(root, rId, 200.0, 80.0)
                                                logicalNodes[rId] =
                                                    GraphNode.RowNode(rId, rowData.cells, isDeleteFile)

                                                // Hierarchy Edge (Column 5 -> Column 6)
                                                ElkGraphUtil.createSimpleEdge(
                                                    elkNodes[fId], elkNodes[rId]
                                                )
                                                edges.add(
                                                    GraphEdge(
                                                        "e_row_$rId", fId, rId
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

        // Lock the dimensions so ELK reserves the exact bounding box size during routing
        node.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, EnumSet.of(SizeConstraint.MINIMUM_SIZE))
        node.setProperty(CoreOptions.NODE_SIZE_MINIMUM, KVector(w, h))

        node.width = w
        node.height = h
        return node
    }
}
