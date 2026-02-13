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
import model.GraphModel
import model.GraphNode
import model.ManifestEntry
import service.GraphLayoutService
import service.IcebergReader
import java.io.File
import java.util.prefs.Preferences

// Access native OS preferences for this package
private val prefs = Preferences.userRoot().node("com.github.mmdemirbas.icelens")
private const val PREF_WAREHOUSE_PATH = "last_warehouse_path"

@Composable
fun App() {
    // Global State
    var warehousePath by remember { mutableStateOf(prefs.get(PREF_WAREHOUSE_PATH, null)) }
    var availableTables by remember { mutableStateOf<List<String>>(emptyList()) }

    // Local Table State
    var selectedTable by remember { mutableStateOf<String?>(null) }
    var graphModel by remember { mutableStateOf<GraphModel?>(null) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var tableData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

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
    fun loadTable(tableName: String) {
        if (warehousePath == null) return
        val path = "$warehousePath/$tableName"

        try {
            val metaDir = File(path, "metadata")
            val metaFile = metaDir
                               .listFiles()
                               ?.filter { it.name.endsWith(".metadata.json") }
                               ?.maxByOrNull { it.name }
                           ?: throw Exception("No metadata file found in $path/metadata")

            val metadata = IcebergReader.readTableMetadata(metaFile.absolutePath)

            val loadedManifestLists = metadata.snapshots.associate {
                it.snapshotId.toString() to IcebergReader.readManifestList(
                    path + "/metadata/" + it.manifestList.orEmpty().substringAfterLast("/")
                )
            }

            val loadedFiles = mutableMapOf<String, List<ManifestEntry>>()
            loadedManifestLists.values.flatten().forEach { ml ->
                try {
                    val fPath =
                        path + "/metadata/" + ml.manifestPath.orEmpty().substringAfterLast("/")
                    loadedFiles[ml.manifestPath.orEmpty()] = IcebergReader.readManifestFile(fPath)
                } catch (e: Exception) {
                    println("Could not load manifest ${ml.manifestPath}: ${e.message}")
                }
            }

            graphModel =
                GraphLayoutService.layoutGraph(metadata.snapshots, loadedManifestLists, loadedFiles)
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
                Sidebar(
                    warehousePath = warehousePath,
                    tables = availableTables,
                    selectedTable = selectedTable,
                    onTableSelect = { loadTable(it) },
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
                    GraphCanvas(graphModel!!) { node ->
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
                                is GraphNode.SnapshotNode -> {
                                    Text("Snapshot ID: ${node.data.snapshotId}", fontSize = 12.sp)
                                    Text("Timestamp: ${node.data.timestampMs}", fontSize = 12.sp)
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
                                    Button(onClick = { /* DuckDB Logic */ }) {
                                        Text("Preview Data", fontSize = 12.sp)
                                    }
                                }

                                else                      -> Text(
                                    "Manifest List Node", fontSize = 12.sp
                                )
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