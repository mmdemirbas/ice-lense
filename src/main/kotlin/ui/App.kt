package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode
import model.UnifiedTableModel
import service.GraphLayoutService
import java.awt.Cursor
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
fun DetailTable(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        content()
    }
}

@Composable
fun DetailRow(key: String, value: String, isHeader: Boolean = false) {
    val bgColor = if (isHeader) Color(0xFFF5F5F5) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bgColor).border(0.5.dp, Color(0xFFE0E0E0)).padding(8.dp)
    ) {
        Text(
            text = key,
            modifier = Modifier.weight(0.35f),
            fontSize = 11.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            fontSize = 11.sp,
            fontFamily = if (isHeader) null else androidx.compose.ui.text.font.FontFamily.Monospace,
            color = Color.Black
        )
    }
}

@Composable
fun DraggableDivider(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(8.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }) {
        VerticalDivider(modifier = Modifier.align(Alignment.Center))
    }
}

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

    // UI Configuration State
    var leftPaneWidth by remember { mutableStateOf(250.dp) }
    var rightPaneWidth by remember { mutableStateOf(300.dp) }
    var isLeftPaneVisible by remember { mutableStateOf(true) }
    var isRightPaneVisible by remember { mutableStateOf(true) }
    val density = LocalDensity.current

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
        Column(Modifier.fillMaxSize()) {

            // NEW: Top Toolbar for UI Controls
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { isLeftPaneVisible = !isLeftPaneVisible }) {
                    Text(if (isLeftPaneVisible) "◀ Hide Sidebar" else "Sidebar ▶", fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { isRightPaneVisible = !isRightPaneVisible }) {
                    Text(
                        if (isRightPaneVisible) "Hide Inspector ▶" else "◀ Inspector",
                        fontSize = 12.sp
                    )
                }
            }
            HorizontalDivider()

            Row(Modifier.weight(1f)) {

                // 1. Resizable Left Sidebar
                if (isLeftPaneVisible) {
                    Box(Modifier.width(leftPaneWidth).fillMaxHeight()) {
                        Sidebar(
                            warehousePath = warehousePath,
                            tables = availableTables,
                            selectedTable = selectedTable,
                            showRows = showRows,
                            onShowRowsChange = {
                                showRows = it
                                prefs.putBoolean(PREF_SHOW_ROWS, it)
                                if (selectedTable != null) loadTable(selectedTable!!, it)
                            },
                            onTableSelect = { newTable ->
                                if (selectedTable != null && graphModel != null) {
                                    val oldKey = "$selectedTable-rows_$showRows"
                                    sessionCache[oldKey]?.selectedNodeId = selectedNode?.id
                                }
                                loadTable(newTable)
                            },
                            onWarehouseChange = { newPath ->
                                warehousePath = newPath
                                prefs.put(PREF_WAREHOUSE_PATH, newPath)
                                scanWarehouse(newPath)
                            })
                    }
                    DraggableDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        leftPaneWidth = (leftPaneWidth + deltaDp).coerceIn(150.dp, 500.dp)
                    })
                }

                // 2. Main Canvas
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
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

                // 3. Resizable Right Inspector Panel
                if (isRightPaneVisible) {
                    DraggableDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        // Subtracting delta because dragging left increases the right pane's width
                        rightPaneWidth = (rightPaneWidth - deltaDp).coerceIn(200.dp, 600.dp)
                    })

                    Column(Modifier.width(rightPaneWidth).fillMaxHeight().padding(8.dp)) {
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

                        Text("Node Details", style = MaterialTheme.typography.headlineSmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Box(Modifier.weight(0.5f).fillMaxWidth()) {
                            selectedNode?.let { node ->
                                Column(
                                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                ) {
                                    when (node) {
                                        is GraphNode.MetadataNode -> DetailTable {
                                            DetailRow("Property", "Value", isHeader = true)
                                            DetailRow("File Name", node.fileName)
                                            DetailRow(
                                                "Format Version", "${node.data.formatVersion}"
                                            )
                                            DetailRow("Table UUID", "${node.data.tableUuid}")
                                            DetailRow("Last Updated", "${node.data.lastUpdatedMs}")
                                            DetailRow(
                                                "Last Seq. Num.", "${node.data.lastSequenceNumber}"
                                            )
                                            DetailRow("Last Column ID", "${node.data.lastColumnId}")
                                            DetailRow(
                                                "Current Snap.",
                                                "${node.data.currentSnapshotId ?: "None"}"
                                            )
                                            DetailRow("Total Snaps.", "${node.data.snapshots.size}")
                                            DetailRow("Location", "${node.data.location}")
                                        }

                                        is GraphNode.SnapshotNode -> DetailTable {
                                            DetailRow("Property", "Value", isHeader = true)
                                            DetailRow("Snapshot ID", "${node.data.snapshotId}")
                                            DetailRow(
                                                "Parent ID",
                                                "${node.data.parentSnapshotId ?: "None"}"
                                            )
                                            DetailRow("Timestamp", "${node.data.timestampMs}")
                                            node.data.summary.forEach { (k, v) ->
                                                DetailRow("Summary: $k", v)
                                            }
                                            DetailRow(
                                                "Manifest List", "${
                                                    node.data.manifestList?.substringAfterLast(
                                                        "/"
                                                    )
                                                }"
                                            )
                                        }

                                        is GraphNode.ManifestNode -> DetailTable {
                                            val contentType =
                                                if (node.data.content == 1) "Delete" else "Data"
                                            DetailRow("Property", "Value", isHeader = true)
                                            DetailRow("Content Type", contentType)
                                            DetailRow(
                                                "Sequence Num.", "${node.data.sequenceNumber}"
                                            )
                                            DetailRow(
                                                "Min Sequence Num.",
                                                "${node.data.cominSequenceNumber}"
                                            )
                                            DetailRow(
                                                "Partition Spec ID", "${node.data.partitionSpecId}"
                                            )
                                            DetailRow(
                                                "Added Snapshot", "${node.data.addedSnapshotId}"
                                            )
                                            DetailRow("Added Files", "${node.data.addedFilesCount}")
                                            DetailRow(
                                                "Existing Files", "${node.data.existingFilesCount}"
                                            )
                                            DetailRow(
                                                "Deleted Files", "${node.data.deletedFilesCount}"
                                            )
                                            DetailRow("Added Rows", "${node.data.addedRowsCount}")
                                            DetailRow(
                                                "Existing Rows", "${node.data.existingRowsCount}"
                                            )
                                            DetailRow(
                                                "Deleted Rows", "${node.data.deletedRowsCount}"
                                            )
                                            DetailRow(
                                                "Path", "${
                                                    node.data.manifestPath?.substringAfterLast(
                                                        "/"
                                                    )
                                                }"
                                            )
                                        }

                                        is GraphNode.FileNode     -> DetailTable {
                                            val contentType = when (node.data.content ?: 0) {
                                                1 -> "Position Delete"
                                                2 -> "Equality Delete"
                                                else -> "Data"
                                            }
                                            DetailRow("Property", "Value", isHeader = true)
                                            DetailRow("Content Type", contentType)
                                            DetailRow("File Format", "${node.data.fileFormat}")
                                            DetailRow("Record Count", "${node.data.recordCount}")
                                            DetailRow(
                                                "File Size", "${node.data.fileSizeInBytes} bytes"
                                            )
                                            DetailRow("Sort Order ID", "${node.data.sorderOrderId}")
                                            DetailRow(
                                                "Split Offsets",
                                                node.data.splitOffsets.joinToString(", ")
                                            )
                                            DetailRow(
                                                "Path",
                                                "${node.data.filePath?.substringAfterLast("/")}"
                                            )
                                        }

                                        is GraphNode.RowNode      -> DetailTable {
                                            val typeStr =
                                                if (node.isDelete) "Delete Row" else "Data Row"
                                            DetailRow("Column", "Value ($typeStr)", isHeader = true)
                                            node.data.forEach { (k, v) ->
                                                DetailRow(k, "$v")
                                            }
                                        }
                                    }
                                }
                            } ?: Text(
                                "Select a node to view details.",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}
