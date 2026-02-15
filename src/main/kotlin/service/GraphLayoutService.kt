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
        val processedManifests = mutableSetOf<String>()
        val processedFiles = mutableSetOf<String>()

        // Registry to map long Iceberg paths to simple IDs
        var nextFileId = 1
        val filePathToSimpleId = mutableMapOf<String, Int>()
        var nextSnapshotSimpleId = 1
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
                edges.add(GraphEdge("e_meta_seq_$mId", prevMetaId, mId, isSibling = true))
            }
            prevMetaId = mId

            val sortedSnapshots = metadata.snapshots.sortedWith(
                compareBy(
                    { it.metadata.timestampMs ?: Long.MAX_VALUE },
                    { it.metadata.sequenceNumber ?: Long.MAX_VALUE },
                    { it.metadata.snapshotId ?: Long.MAX_VALUE }
                )
            )
            var previousSnapshotId: String? = null
            sortedSnapshots.forEach { snapshot ->
                val snap = snapshot.metadata
                val sId = "snap_${snap.snapshotId}"
                if (!elkNodes.containsKey(sId)) {
                    val simpleSnapshotId = nextSnapshotSimpleId++
                    elkNodes[sId] = createElkNode(root, sId, 220.0, 100.0)
                    logicalNodes[sId] = GraphNode.SnapshotNode(sId, snap, simpleSnapshotId)
                }

                val snapEdgeId = "e_snap_${mId}_to_$sId"
                ElkGraphUtil.createSimpleEdge(elkNodes[mId], elkNodes[sId])
                edges.add(GraphEdge(snapEdgeId, mId, sId))

                if (previousSnapshotId != null) {
                    edges.add(GraphEdge("e_snap_seq_${previousSnapshotId}_$sId", previousSnapshotId!!, sId, isSibling = true))
                }
                previousSnapshotId = sId

                val manifests = snapshot.manifestLists.sortedWith(
                    compareBy(
                        { it.metadata.sequenceNumber ?: Int.MAX_VALUE },
                        { it.metadata.cominSequenceNumber ?: Int.MAX_VALUE },
                        { it.metadata.addedSnapshotId ?: Long.MAX_VALUE },
                        { it.metadata.content ?: Int.MAX_VALUE },
                        { it.metadata.manifestPath ?: "" }
                    )
                )
                var previousManifestId: String? = null
                manifests.forEach { unifiedManifest ->
                    val manifest = unifiedManifest.metadata
                    val rawManPath = manifest.manifestPath ?: "unknown_${UUID.randomUUID()}"
                    val manId = "man_${rawManPath.hashCode()}"
                    if (!elkNodes.containsKey(manId)) {
                        elkNodes[manId] = createElkNode(root, manId, 200.0, 80.0)
                        logicalNodes[manId] = GraphNode.ManifestNode(manId, manifest)
                    }

                    val manEdgeId = "e_man_${sId}_to_$manId"
                    if (edges.none { it.id == manEdgeId }) {
                        ElkGraphUtil.createSimpleEdge(elkNodes[sId], elkNodes[manId])
                        edges.add(GraphEdge(manEdgeId, sId, manId))
                    }

                    if (previousManifestId != null) {
                        edges.add(GraphEdge("e_man_seq_${sId}_${previousManifestId}_$manId", previousManifestId!!, manId, isSibling = true))
                    }
                    previousManifestId = manId

                    if (processedManifests.add(manId)) {
                        val manifestPath = manifest.manifestPath
                        if (manifestPath != null) {
                            val unifiedDataFiles = unifiedManifest.manifests.sortedWith(
                                compareBy(
                                    { it.metadata.dataFile?.dataSequenceNumber ?: it.metadata.sequenceNumber ?: (manifest.sequenceNumber?.toLong() ?: Long.MAX_VALUE) },
                                    { it.metadata.fileSequenceNumber ?: Long.MAX_VALUE },
                                    { contentRank(it.metadata.dataFile?.content) },
                                    { it.metadata.status },
                                    { it.metadata.dataFile?.filePath ?: "" }
                                )
                            )
                            var previousFileIdForManifest: String? = null
                            unifiedDataFiles.take(10).forEach { unifiedDataFile ->
                                val entry = unifiedDataFile.metadata
                                val dataFile = entry.dataFile ?: DataFile(filePath = "unknown")
                                val rawPath = dataFile.filePath.orEmpty()
                                val simpleId = filePathToSimpleId.getOrPut(rawPath) { nextFileId++ }
                                val fId = "file_$simpleId"

                                if (!elkNodes.containsKey(fId)) {
                                    elkNodes[fId] = createElkNode(root, fId, 200.0, 60.0)
                                    logicalNodes[fId] = GraphNode.FileNode(fId, entry, simpleId)
                                }

                                val edgeId = "e_file_${manId}_to_$fId"
                                ElkGraphUtil.createSimpleEdge(elkNodes[manId], elkNodes[fId])
                                edges.add(GraphEdge(edgeId, manId, fId))

                                if (previousFileIdForManifest != null) {
                                    edges.add(
                                        GraphEdge(
                                            "e_file_seq_${manId}_${previousFileIdForManifest}_$fId",
                                            previousFileIdForManifest!!,
                                            fId,
                                            isSibling = true
                                        )
                                    )
                                }
                                previousFileIdForManifest = fId

                                if (showRows && processedFiles.add(fId)) {
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
                                            unifiedDataFile.rows
                                                .take(5)
                                                .forEachIndexed { rIdx, rowData ->
                                                    val enrichedData = mutableMapOf<String, Any>()
                                                    val contentType = entry.dataFile?.content ?: 0
                                                    if (contentType > 0 && rowData.cells.containsKey("file_path")) {
                                                        val targetPath =
                                                            rowData.cells["file_path"].toString()
                                                        val targetId =
                                                            filePathToSimpleId[targetPath] ?: "?"
                                                        enrichedData["target_file"] = "File $targetId"
                                                    }
                                                    enrichedData.putAll(rowData.cells)

                                                    val rId = "row_${fId}_$rIdx"
                                                    if (!elkNodes.containsKey(rId)) {
                                                        elkNodes[rId] =
                                                            createElkNode(root, rId, 200.0, 80.0)
                                                        logicalNodes[rId] = GraphNode.RowNode(
                                                            rId,
                                                            enrichedData,
                                                            contentType
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

        enforceChronologicalVerticalOrder(logicalNodes, edges)

        return GraphModel(finalNodes, edges, root.width, root.height)
    }

    private fun enforceChronologicalVerticalOrder(
        nodesById: Map<String, GraphNode>,
        edges: List<GraphEdge>
    ) {
        fun reorder(nodes: List<GraphNode>, comparator: Comparator<GraphNode>) {
            if (nodes.size < 2) return
            val ySlots = nodes.map { it.y }.sorted()
            val desired = nodes.sortedWith(comparator)
            desired.forEachIndexed { index, node ->
                node.y = ySlots[index]
            }
        }

        val nonSiblingEdges = edges.filter { !it.isSibling }
        val childrenByParent = nonSiblingEdges.groupBy { it.fromId }
            .mapValues { (_, v) -> v.map { it.toId } }

        val metadataComparator = Comparator<GraphNode> { a, b ->
            val ma = a as? GraphNode.MetadataNode
            val mb = b as? GraphNode.MetadataNode
            val va = ma?.fileName?.removePrefix("v")?.removeSuffix(".metadata.json")?.toIntOrNull() ?: Int.MAX_VALUE
            val vb = mb?.fileName?.removePrefix("v")?.removeSuffix(".metadata.json")?.toIntOrNull() ?: Int.MAX_VALUE
            va.compareTo(vb)
        }

        val snapshotComparator = Comparator<GraphNode> { a, b ->
            val sa = (a as? GraphNode.SnapshotNode)?.data
            val sb = (b as? GraphNode.SnapshotNode)?.data
            compareValuesBy(sa, sb,
                { it?.timestampMs ?: Long.MAX_VALUE },
                { it?.sequenceNumber ?: Long.MAX_VALUE },
                { it?.snapshotId ?: Long.MAX_VALUE }
            )
        }

        val manifestComparator = Comparator<GraphNode> { a, b ->
            val ma = (a as? GraphNode.ManifestNode)?.data
            val mb = (b as? GraphNode.ManifestNode)?.data
            compareValuesBy(ma, mb,
                { it?.sequenceNumber ?: Int.MAX_VALUE },
                { it?.cominSequenceNumber ?: Int.MAX_VALUE },
                { it?.addedSnapshotId ?: Long.MAX_VALUE },
                { it?.content ?: Int.MAX_VALUE },
                { it?.manifestPath ?: "" }
            )
        }

        val fileComparator = Comparator<GraphNode> { a, b ->
            val fa = a as? GraphNode.FileNode
            val fb = b as? GraphNode.FileNode
            compareValuesBy(fa, fb,
                { it?.data?.dataSequenceNumber ?: it?.entry?.sequenceNumber ?: Long.MAX_VALUE },
                { it?.entry?.fileSequenceNumber ?: Long.MAX_VALUE },
                { contentRank(it?.data?.content) },
                { it?.entry?.status ?: Int.MAX_VALUE },
                { it?.data?.filePath ?: "" }
            )
        }

        val rowComparator = Comparator<GraphNode> { a, b ->
            val ra = (a as? GraphNode.RowNode)?.id ?: ""
            val rb = (b as? GraphNode.RowNode)?.id ?: ""
            ra.compareTo(rb)
        }

        val metadataNodes = nodesById.values.filterIsInstance<GraphNode.MetadataNode>()
        reorder(metadataNodes, metadataComparator)

        metadataNodes.forEach { parent ->
            val snapshotChildren = childrenByParent[parent.id].orEmpty()
                .mapNotNull { nodesById[it] as? GraphNode.SnapshotNode }
            reorder(snapshotChildren, snapshotComparator)
        }

        nodesById.values.filterIsInstance<GraphNode.SnapshotNode>().forEach { parent ->
            val manifestChildren = childrenByParent[parent.id].orEmpty()
                .mapNotNull { nodesById[it] as? GraphNode.ManifestNode }
            reorder(manifestChildren, manifestComparator)
        }

        nodesById.values.filterIsInstance<GraphNode.ManifestNode>().forEach { parent ->
            val fileChildren = childrenByParent[parent.id].orEmpty()
                .mapNotNull { nodesById[it] as? GraphNode.FileNode }
            reorder(fileChildren, fileComparator)
        }

        nodesById.values.filterIsInstance<GraphNode.FileNode>().forEach { parent ->
            val rowChildren = childrenByParent[parent.id].orEmpty()
                .mapNotNull { nodesById[it] as? GraphNode.RowNode }
            reorder(rowChildren, rowComparator)
        }
    }

    private fun contentRank(content: Int?): Int = when (content ?: 0) {
        2 -> 0 // Equality deletes first
        0 -> 1 // Data files second
        1 -> 2 // Position deletes last
        else -> 3
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
