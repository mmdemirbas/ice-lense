package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode
import model.UnifiedTableModel
import service.GraphLayoutService
import java.io.File
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
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) { // NEW: Scrollable details
                            when (node) {
                                is GraphNode.MetadataNode -> {
                                    DetailRow("Type", "Metadata File")
                                    DetailRow("Format Version", "${node.data.formatVersion}")
                                    DetailRow("Table UUID", "${node.data.tableUuid}")
                                    DetailRow("Location", "${node.data.location}")
                                    DetailRow("Current Snapshot", "${node.data.currentSnapshotId}")
                                    DetailRow("Total Snapshots", "${node.data.snapshots.size}")
                                }

                                is GraphNode.SnapshotNode -> {
                                    DetailRow("Type", "Snapshot")
                                    DetailRow("ID", "${node.data.snapshotId}")
                                    DetailRow(
                                        "Parent ID", "${node.data.parentSnapshotId ?: "None"}"
                                    )
                                    DetailRow("Timestamp", "${node.data.timestampMs}")
                                    DetailRow(
                                        "Operation",
                                        "${node.data.summary["operation"] ?: "unknown"}"
                                    )
                                    DetailRow(
                                        "Manifest List",
                                        "${node.data.manifestList?.substringAfterLast("/")}"
                                    )

                                    Spacer(Modifier.height(8.dp))
                                    Text("Summary:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    node.data.summary
                                        .filterKeys { it != "operation" }
                                        .forEach { (k, v) ->
                                            DetailRow(k, v)
                                        }
                                }

                                is GraphNode.ManifestNode -> {
                                    val contentType =
                                        if (node.data.content == 1) "Delete" else "Data"
                                    DetailRow("Type", "Manifest Entry ($contentType)")
                                    DetailRow("Snapshot ID", "${node.data.addedSnapshotId}")
                                    DetailRow("Added Files", "${node.data.addedDataFilesCount}")
                                    DetailRow("Added Rows", "${node.data.addedRowsCount}")
                                    DetailRow(
                                        "Path", "${node.data.manifestPath?.substringAfterLast("/")}"
                                    )
                                }

                                is GraphNode.FileNode     -> {
                                    val contentType = when (node.data.content ?: 0) {
                                        1 -> "Position Delete"
                                        2 -> "Equality Delete"
                                        else -> "Data"
                                    }
                                    DetailRow("Type", "Parquet File ($contentType)")
                                    DetailRow("Format", "${node.data.fileFormat}")
                                    DetailRow("Record Count", "${node.data.recordCount}")
                                    DetailRow("File Size", "${node.data.fileSizeInBytes} bytes")
                                    DetailRow(
                                        "Path", "${node.data.filePath?.substringAfterLast("/")}"
                                    )
                                }

                                is GraphNode.RowNode      -> {
                                    DetailRow(
                                        "Type", if (node.isDelete) "Delete Row" else "Data Row"
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Record Data:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    node.data.forEach { (k, v) ->
                                        DetailRow(k, "$v")
                                    }
                                }

                                else                      -> Text("Unknown Node", fontSize = 12.sp)
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

@Composable
fun DetailRow(key: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(key, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 12.sp)
    }
}