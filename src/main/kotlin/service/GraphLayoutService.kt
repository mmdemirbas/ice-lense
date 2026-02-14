package service

import model.*
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.math.KVector
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.options.EdgeRouting
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
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.SPLINES)
        root.setProperty(CoreOptions.SPACING_NODE_NODE, 100.0)
        root.setProperty(
            org.eclipse.elk.alg.layered.options.LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS,
            300.0
        )

        val elkNodes = mutableMapOf<String, ElkNode>()
        val logicalNodes = mutableMapOf<String, GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // Registry to map long Iceberg paths to simple IDs
        var nextFileId = 1
        val filePathToSimpleId = mutableMapOf<String, Int>()
        var prevMetaId: String? = null

        tableModel.metadatas.forEach { metadata ->
            val fileName = metadata.path.fileName.toString()
            val meta = metadata.metadata
            val mId = "meta_${fileName}"
            if (!elkNodes.containsKey(mId)) {
                elkNodes[mId] = createElkNode(root, mId, 260.0, 120.0)
                logicalNodes[mId] = GraphNode.MetadataNode(mId, fileName, meta)
            }

            // Link consecutive metadata files
            if (prevMetaId != null) {
                // We keep the logical edge for drawing, but NOT the ELK edge to avoid horizontal layout
                edges.add(GraphEdge("e_meta_seq_$mId", prevMetaId!!, mId, isSibling = true))
            }
            prevMetaId = mId

            metadata.snapshots.forEach { snapshot ->
                val snap = snapshot.metadata
                val sId = "snap_${snap.snapshotId}"
                if (!elkNodes.containsKey(sId)) {
                    elkNodes[sId] = createElkNode(root, sId, 220.0, 100.0)
                    logicalNodes[sId] = GraphNode.SnapshotNode(sId, snap)
                }

                val snapEdgeId = "e_snap_${mId}_to_$sId"
                ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[sId])
                edges.add(GraphEdge(snapEdgeId, mId, sId))

                val manifests = snapshot.manifestLists
                manifests.forEach { unifiedManifest ->
                    val manifest = unifiedManifest.metadata
                    val rawManPath = manifest.manifestPath ?: "unknown_${UUID.randomUUID()}"
                    val manId = "man_${rawManPath.hashCode()}"
                    if (!elkNodes.containsKey(manId)) {
                        elkNodes[manId] = createElkNode(root, manId, 200.0, 80.0)
                        logicalNodes[manId] = GraphNode.ManifestNode(manId, manifest)
                    }

                    val manEdgeId = "e_man_${sId}_to_$manId"
                    ElkGraphUtil.createSimpleEdge(elkNodes[sId], elkNodes[manId])
                    edges.add(GraphEdge(manEdgeId, sId, manId))

                    val manifestPath = manifest.manifestPath
                    if (manifestPath != null) {
                        val unifiedDataFiles = unifiedManifest.manifests
                        unifiedDataFiles.take(10).forEachIndexed { fIdx, unifiedDataFile ->
                            val entry = unifiedDataFile.metadata
                            val dataFile = entry.dataFile ?: DataFile(filePath = "unknown")
                            val rawPath = dataFile.filePath.orEmpty()
                            val simpleId = filePathToSimpleId.getOrPut(rawPath) { nextFileId++ }
                            val fId = "file_$simpleId"

                            if (!elkNodes.containsKey(fId)) {
                                elkNodes[fId] = createElkNode(root, fId, 200.0, 60.0)
                                logicalNodes[fId] = GraphNode.FileNode(fId, dataFile, simpleId)
                            }

                            val edgeId = "e_file_${manId}_to_$fId"
                            ElkGraphUtil.createSimpleEdge(elkNodes[manId], elkNodes[fId])
                            edges.add(GraphEdge(edgeId, manId, fId))

                            if (showRows) {
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
                                                val enrichedData = rowData.cells.toMutableMap()
                                                if (isDeleteFile && enrichedData.containsKey("file_path")) {
                                                    val targetPath =
                                                        enrichedData["file_path"].toString()
                                                    val targetId =
                                                        filePathToSimpleId[targetPath] ?: "?"
                                                    enrichedData["target_file"] = "File $targetId"
                                                    enrichedData.remove("file_path")
                                                }

                                                val rId = "row_${fId}_$rIdx"
                                                if (!elkNodes.containsKey(rId)) {
                                                    elkNodes[rId] =
                                                        createElkNode(root, rId, 200.0, 80.0)
                                                    logicalNodes[rId] = GraphNode.RowNode(
                                                        rId,
                                                        enrichedData,
                                                        isDeleteFile
                                                    )

                                                    ElkGraphUtil.createSimpleEdge(
                                                        elkNodes[fId],
                                                        elkNodes[rId]
                                                    )
                                                    edges.add(GraphEdge("e_row_$rId", fId, rId))
                                                }
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
