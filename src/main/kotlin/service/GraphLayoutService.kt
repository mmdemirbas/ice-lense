package service

import model.*
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.options.Direction
import org.eclipse.elk.core.options.EdgeRouting
import org.eclipse.elk.core.options.SizeConstraint
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.ElkNode
import org.eclipse.elk.graph.util.ElkGraphUtil
import java.util.EnumSet

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
    ): GraphModel {

        val root = ElkGraphUtil.createGraph()

        // Configure Layout: Top-Down, Orthogonal Routing with Strict Sizing
        root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
        root.setProperty(CoreOptions.DIRECTION, Direction.DOWN)
        root.setProperty(CoreOptions.SPACING_NODE_NODE, 60.0)
        root.setProperty(CoreOptions.SPACING_EDGE_NODE, 30.0)
        root.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, EnumSet.of(SizeConstraint.MINIMUM_SIZE))
        root.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL)

        // Maps to keep track of ELK nodes
        val nodeMap = mutableMapOf<String, ElkNode>()
        val edges = mutableListOf<GraphEdge>()

        // 1. Create Snapshot Nodes (The Backbone)
        snapshots.sortedBy { it.timestampMs }.forEach { snap ->
            val id = "snap_${snap.snapshotId}"
            val node = createElkNode(root, id, 220.0, 100.0)
            nodeMap[id] = node

            // Link to parent
            if (snap.parentSnapshotId != null) {
                val parentId = "snap_${snap.parentSnapshotId}"
                if (nodeMap.containsKey(parentId)) {
                    val edge = ElkGraphUtil.createSimpleEdge(nodeMap[parentId], node)
                    edges.add(GraphEdge("e_$id", parentId, id))
                }
            }

            // 2. Create Manifest List Node (One per snapshot)
            val mlId = "ml_${snap.snapshotId}"
            val mlNode = createElkNode(root, mlId, 180.0, 60.0)
            nodeMap[mlId] = mlNode
            ElkGraphUtil.createSimpleEdge(node, mlNode)
            edges.add(GraphEdge("e_ml_$id", id, mlId))

            // 3. Create Manifest Nodes (if loaded)
            val manifests = loadedManifestLists
            manifests.onEachIndexed { idx, manifest ->
                val mId = "man_${snap.snapshotId}_$idx"
                val mNode = createElkNode(root, mId, 200.0, 80.0)
                nodeMap[mId] = mNode
                ElkGraphUtil.createSimpleEdge(mlNode, mNode)
                edges.add(GraphEdge("e_man_$mId", mlId, mId))

                // 4. Create File Nodes (if loaded)
                val manifestPaths = manifest.value.map { it.manifestPath }.distinct()
                val fileEntries = manifestPaths.flatMap { path -> loadedFiles[path] ?: emptyList() }

                // Limit visual clutter: only show first 10 files if too many
                fileEntries.take(10).forEachIndexed { fIdx, entry ->
                    val fId = "file_${mId}_$fIdx"
                    val fNode = createElkNode(root, fId, 200.0, 60.0)
                    nodeMap[fId] = fNode
                    ElkGraphUtil.createSimpleEdge(mNode, fNode)
                    edges.add(GraphEdge("e_file_$fId", mId, fId))
                }
            }
        }

        // Run Layout
        val engine = RecursiveGraphLayoutEngine()
        engine.layout(root, BasicProgressMonitor())

        // Transform back to Kotlin Model
        val nodes = nodeMap.map { (id, elkNode) ->
            // Determine type based on ID prefix
            when {
                id.startsWith("snap_") -> {
                    val snapId = id.removePrefix("snap_").toLong()
                    val snap = snapshots.find { it.snapshotId == snapId }!!
                    GraphNode.SnapshotNode(id, snap, elkNode.x, elkNode.y)
                }

                id.startsWith("ml_")   -> GraphNode.ManifestListNode(
                    id,
                    "Manifest List",
                    elkNode.x,
                    elkNode.y
                )

                id.startsWith("man_")  -> {
                    // Simplifying lookup for demo
                    GraphNode.ManifestNode(
                        id,
                        ManifestListEntry("", 0, 0, 0, 0),
                        elkNode.x,
                        elkNode.y
                    )
                }

                id.startsWith("file_") -> {
                    GraphNode.FileNode(
                        id,
                        DataFile("file.parquet", "PARQUET", 0, 0),
                        elkNode.x,
                        elkNode.y
                    )
                }

                else                   -> GraphNode.SnapshotNode(
                    id,
                    snapshots.first(),
                    elkNode.x,
                    elkNode.y
                )
            }
        }

        // Collect routed edges
        val routedEdges = edges.map { edge ->
            // In a real app, extract bend points from ELK edges here
            // For now, we assume simple straight lines updated by UI logic or just start/end
            edge
        }

        return GraphModel(nodes, routedEdges, root.width, root.height)
    }

    private fun createElkNode(parent: ElkNode, id: String, w: Double, h: Double): ElkNode {
        val node = ElkGraphUtil.createNode(parent)
        node.identifier = id
        node.width = w
        node.height = h
        return node
    }
}