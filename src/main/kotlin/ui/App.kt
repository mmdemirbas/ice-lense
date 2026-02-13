package ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import model.GraphModel
import model.GraphNode
import model.UnifiedTableModel
import service.GraphLayoutService
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.prefs.Preferences

// Access native OS preferences for this package
private val prefs = Preferences.userRoot().node("com.github.mmdemirbas.icelens")
private const val PREF_WAREHOUSE_PATH = "last_warehouse_path"
private const val PREF_SHOW_ROWS = "show_data_rows"

// Data class to hold cached table sessions
data class TableSession(
    val table: UnifiedTableModel,
    val graph: GraphModel,
    var selectedNodeId: String? = null,
)

@Composable
fun App() {
    // Global State
    var warehousePath by remember { mutableStateOf(prefs.get(PREF_WAREHOUSE_PATH, null)) }
    var availableTables by remember { mutableStateOf<List<String>>(emptyList()) }

    // Local Table State
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var graphModel by remember { mutableStateOf<GraphModel?>(null) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Persistent Toggle State
    var showRows by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_ROWS, false)) }

    // In-Memory navigation state
    val sessionCache = remember { mutableMapOf<String, TableSession>() }

    // Coroutine scope for async DB queries
    val coroutineScope = rememberCoroutineScope()

    // Logic to scan a warehouse for valid Iceberg tables
    fun scanWarehouse(path: String) {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) {
            errorMsg = "Invalid warehouse path: $path"
            return
        }

        val discovered = root.listFiles { file ->
            if (!file.isDirectory) return@listFiles false
            val metaDir = File(file, "metadata")
            metaDir.exists() && metaDir.isDirectory && metaDir.listFiles { f ->
                f.name.endsWith(".metadata.json")
            }?.isNotEmpty() == true
        }?.map { it.name }?.sorted() ?: emptyList()

        availableTables = discovered
        errorMsg = null

        // Reset local state
        selectedTable = null
        graphModel = null
        selectedNode = null
    }

    // Run initial scan if a persisted path exists
    LaunchedEffect(warehousePath) {
        warehousePath?.let { scanWarehouse(it) }
    }

    // Logic to load a specific table
    fun loadTable(tableName: String, withRows: Boolean = showRows) {
        if (warehousePath == null) return
        val cacheKey = "$tableName-rows_$withRows"

        if (sessionCache.containsKey(cacheKey)) {
            val session = sessionCache[cacheKey]!!
            graphModel = session.graph
            selectedTable = tableName
            selectedNode = session.graph.nodes.find { it.id == session.selectedNodeId }
            errorMsg = null
            return
        }

        val path = "$warehousePath/$tableName"
        try {
            val tableModel = UnifiedTableModel(Paths.get(path))
            val newGraph = GraphLayoutService.layoutGraph(tableModel, withRows)

            sessionCache[cacheKey] = TableSession(tableModel, newGraph)
            graphModel = newGraph
            selectedTable = tableName
            selectedNode = null
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = e.message
            e.printStackTrace()
            graphModel = null
        }
    }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {

            // 1. Left Sidebar (20% width)
            Box(Modifier.weight(0.2f).fillMaxHeight()) {
                // Sidebar callback, save the preference and selection
                Sidebar(
                    warehousePath = warehousePath,
                    tables = availableTables,
                    selectedTable = selectedTable,
                    showRows = showRows,
                    onShowRowsChange = {
                        showRows = it
                        prefs.putBoolean(PREF_SHOW_ROWS, it) // Save to OS
                        if (selectedTable != null) loadTable(selectedTable!!, it)
                    },
                    onTableSelect = { newTable ->
                        // Save current selection before leaving
                        if (selectedTable != null && graphModel != null) {
                            val oldKey = "$selectedTable-rows_$showRows"
                            sessionCache[oldKey]?.selectedNodeId = selectedNode?.id
                        }
                        loadTable(newTable)
                    },
                    onWarehouseChange = { newPath ->
                        warehousePath = newPath
                        prefs.put(PREF_WAREHOUSE_PATH, newPath) // Persist to OS
                        scanWarehouse(newPath)
                    })
            }

            VerticalDivider()

            // 2. Main Canvas (60% width)
            Box(Modifier.weight(0.6f).fillMaxHeight().clipToBounds()) {
                if (graphModel != null) {
                    GraphCanvas(graphModel!!, selectedNode) { node ->
                        selectedNode = node
                    }
                } else if (warehousePath != null && availableTables.isEmpty()) {
                    Text(
                        "No Iceberg tables found in this warehouse.",
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (errorMsg != null) {
                    Text(
                        "Error: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                    )
                }
            }

            VerticalDivider()

            // 3. Right Inspector Panel (20% width)
            Column(Modifier.weight(0.2f).fillMaxHeight().padding(8.dp)) {

                // Top Half: Navigation Tree
                Text("Structure Tree", style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(Modifier.weight(0.5f).fillMaxWidth()) {
                    if (graphModel != null) {
                        NavigationTree(
                            graph = graphModel!!,
                            selectedNode = selectedNode,
                            onNodeSelect = { selectedNode = it })
                    } else {
                        Text("No graph loaded.", fontSize = 12.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bottom Half: Node Details
                Text("Node Details", style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Box(Modifier.weight(0.5f).fillMaxWidth()) {
                    selectedNode?.let { node ->
                        Column {
                            when (node) {
                                is GraphNode.MetadataNode -> {
                                    Text("Metadata Node", fontSize = 12.sp)
                                    Text("Format Version: ${node.data.formatVersion}")
                                    Text("Table UUID: ${node.data.tableUuid}")
                                    Text("Location: ${node.data.location}")
                                    Text("Current Snapshot: ${node.data.currentSnapshotId}")
                                    Text("Snapshot Count: ${node.data.snapshots.size}")
                                }

                                is GraphNode.SnapshotNode -> {
                                    Text("Snapshot ID: ${node.data.snapshotId}", fontSize = 12.sp)
                                    Text(
                                        "Parent Snapshot ID: ${node.data.parentSnapshotId}",
                                        fontSize = 12.sp
                                    )
                                    Text("Timestamp: ${node.data.timestampMs}", fontSize = 12.sp)
                                    Text(
                                        "Manifest List: ${node.data.manifestList}", fontSize = 12.sp
                                    )
                                    Text(
                                        "Operation: ${node.data.summary["operation"]}",
                                        fontSize = 12.sp
                                    )
                                }

                                is GraphNode.ManifestNode -> {
                                    Text(
                                        "Manifest Entry",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Snapshot: ${node.data.addedSnapshotId}", fontSize = 12.sp)
                                    Text(
                                        "Content: ${if (node.data.content == 1) "Delete" else "Data"}",
                                        fontSize = 12.sp
                                    )
                                }

                                is GraphNode.FileNode     -> {
                                    Text(
                                        "File Path:", fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                    Text("${node.data.filePath}", fontSize = 10.sp)
                                    Text("Rows: ${node.data.recordCount}", fontSize = 12.sp)
                                    Spacer(Modifier.height(8.dp))

                                    Button(onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val rawPath = node.data.filePath.orEmpty()

                                                // 1. Strip URI scheme if present (e.g., "file://")
                                                val pathWithoutScheme =
                                                    if (rawPath.startsWith("file:")) {
                                                        URI(rawPath).path
                                                    } else {
                                                        rawPath
                                                    }

                                                // 2. Rebase path from Container OS to Host OS
                                                val tableMarker = "/$selectedTable/"
                                                val localFile =
                                                    if (pathWithoutScheme.contains(tableMarker) && warehousePath != null) {
                                                        val relativePart =
                                                            pathWithoutScheme.substringAfter(
                                                                tableMarker
                                                            )
                                                        File("$warehousePath/$selectedTable/$relativePart")
                                                    } else {
                                                        File(pathWithoutScheme)
                                                    }

                                                // 3. Validate existence before passing to DuckDB
                                                if (!localFile.exists()) {
                                                    throw Exception("Mapped file not found on host: ${localFile.absolutePath}")
                                                }

                                                errorMsg = null
                                            } catch (e: Exception) {
                                                errorMsg = "DuckDB Error: ${e.message}"
                                                e.printStackTrace()
                                            }
                                        }
                                    }) {
                                        Text("Preview Data", fontSize = 12.sp)
                                    }
                                }

                                is GraphNode.RowNode      -> {
                                    Text(
                                        "Row Data:", fontSize = 12.sp, fontWeight = FontWeight.Bold
                                    )
                                    node.data.forEach { (k, v) ->
                                        Text("$k: $v", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    } ?: Text(
                        "Select a node to view details.", fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
    }
}