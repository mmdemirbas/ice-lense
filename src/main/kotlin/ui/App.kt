package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.*
import service.GraphLayoutService
import java.awt.Cursor
import java.io.File
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.prefs.Preferences

// Access native OS preferences for this package
private val prefs = Preferences.userRoot().node("com.github.mmdemirbas.icelens")
private const val PREF_WAREHOUSE_PATH = "last_warehouse_path"
private const val PREF_SHOW_ROWS = "show_data_rows"
private const val PREF_LEFT_PANE_WIDTH = "left_pane_width"
private const val PREF_RIGHT_PANE_WIDTH = "right_pane_width"
private const val PREF_LEFT_PANE_VISIBLE = "left_pane_visible"
private const val PREF_RIGHT_PANE_VISIBLE = "right_pane_visible"
private const val PREF_ZOOM = "zoom"
private const val PREF_SHOW_METADATA = "show_metadata"
private const val PREF_SHOW_SNAPSHOTS = "show_snapshots"
private const val PREF_SHOW_MANIFESTS = "show_manifests"
private const val PREF_SHOW_DATA_FILES = "show_data_files"
private const val PREF_IS_SELECT_MODE = "is_select_mode"
private const val PREF_WORKSPACE_ITEMS = "workspace_items"

// Data class to hold cached table sessions
data class TableSession(
    val table: UnifiedTableModel,
    val graph: GraphModel,
    var selectedNodeIds: Set<String> = emptySet(),
)

private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    .withZone(ZoneId.systemDefault())

private fun formatTimestamp(ms: Long?): String {
    if (ms == null) return "N/A"
    return try {
        val formatted = timestampFormatter.format(Instant.ofEpochMilli(ms))
        "$formatted ($ms)"
    } catch (e: Exception) {
        "$ms (Error)"
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
fun DetailTable(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
    ) {
        content()
    }
}

@Composable
fun DetailRow(key: String, value: String, isHeader: Boolean = false) {
    val bgColor = if (isHeader) Color(0xFFF5F5F5) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = key,
            modifier = Modifier.weight(0.4f),
            fontSize = 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
            color = if (isHeader) Color.Black else Color.Gray
        )
        Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFE0E0E0)))
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            modifier = Modifier.weight(0.6f),
            fontSize = 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (isHeader) null else FontFamily.Monospace,
            color = Color.Black,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
}

@Composable
fun ToolbarGroup(content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, Color(0xFFCCCCCC)),
        color = Color(0xFFF8F8F8)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(32.dp).padding(horizontal = 2.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(Color(0xEE333333), RoundedCornerShape(4.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(text = tooltip, color = Color.White, fontSize = 12.sp)
            }
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.CursorPoint(
            alignment = Alignment.BottomEnd,
            offset = DpOffset(0.dp, 16.dp)
        )
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            colors = if (isSelected) IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF1976D2)
            ) else IconButtonDefaults.iconButtonColors()
        ) {
            Icon(icon, contentDescription = tooltip, modifier = Modifier.size(20.dp))
        }
    }
}

// Helper to detect if a directory is an Iceberg table
fun isIcebergTable(dir: File): Boolean {
    val metaDir = File(dir, "metadata")
    return metaDir.exists() && metaDir.isDirectory && metaDir.listFiles { f ->
        f.name.endsWith(".metadata.json")
    }?.isNotEmpty() == true
}

fun scanForTables(warehouseDir: File): List<String> {
    return warehouseDir.listFiles { file ->
        file.isDirectory && isIcebergTable(file)
    }?.map { it.name }?.sorted() ?: emptyList()
}

@Composable
fun App() {
    var workspaceItems by remember {
        val saved = prefs.get(PREF_WORKSPACE_ITEMS, "")
        val items = saved.split(";").mapNotNull { WorkspaceItem.deserialize(it) }
        val refreshed = items.map { item ->
            if (item is WorkspaceItem.Warehouse) {
                item.copy(tables = scanForTables(File(item.path)))
            } else item
        }
        mutableStateOf(refreshed)
    }

    var selectedTablePath by remember { mutableStateOf<String?>(null) }
    var graphModel by remember { mutableStateOf<GraphModel?>(null) }
    var selectedNodeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    var showRows by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_ROWS, true)) }
    var isSelectMode by remember { mutableStateOf(prefs.getBoolean(PREF_IS_SELECT_MODE, false)) }
    var zoom by remember { mutableStateOf(prefs.getFloat(PREF_ZOOM, 1f)) }

    // Filtering states
    var showMetadata by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_METADATA, true)) }
    var showSnapshots by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_SNAPSHOTS, true)) }
    var showManifests by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_MANIFESTS, true)) }
    var showDataFiles by remember { mutableStateOf(prefs.getBoolean(PREF_SHOW_DATA_FILES, true)) }

    val sessionCache = remember { mutableMapOf<String, TableSession>() }
    val coroutineScope = rememberCoroutineScope()

    var leftPaneWidth by remember { mutableStateOf(prefs.getFloat(PREF_LEFT_PANE_WIDTH, 250f).dp) }
    var rightPaneWidth by remember { mutableStateOf(prefs.getFloat(PREF_RIGHT_PANE_WIDTH, 300f).dp) }
    var isLeftPaneVisible by remember { mutableStateOf(prefs.getBoolean(PREF_LEFT_PANE_VISIBLE, true)) }
    var isRightPaneVisible by remember { mutableStateOf(prefs.getBoolean(PREF_RIGHT_PANE_VISIBLE, true)) }
    val density = LocalDensity.current

    fun saveWorkspace(items: List<WorkspaceItem>) {
        workspaceItems = items
        prefs.put(PREF_WORKSPACE_ITEMS, items.joinToString(";") { it.serialize() })
    }

    // Logic to load a specific table
    fun loadTable(tablePath: String, withRows: Boolean = showRows) {
        val cacheKey = "$tablePath-rows_$withRows"

        if (sessionCache.containsKey(cacheKey)) {
            val session = sessionCache[cacheKey]!!
            graphModel = session.graph
            selectedTablePath = tablePath
            selectedNodeIds = session.selectedNodeIds
            errorMsg = null
            return
        }

        try {
            val tableModel = UnifiedTableModel(Paths.get(tablePath))
            val newGraph = GraphLayoutService.layoutGraph(tableModel, withRows)

            sessionCache[cacheKey] = TableSession(tableModel, newGraph)
            graphModel = newGraph
            selectedTablePath = tablePath
            selectedNodeIds = emptySet()
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = e.message
            e.printStackTrace()
            graphModel = null
        }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize()) {

            // Top Toolbar for UI Controls
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.ViewSidebar,
                    tooltip = if (isLeftPaneVisible) "Hide Sidebar" else "Show Sidebar",
                    onClick = {
                        isLeftPaneVisible = !isLeftPaneVisible
                        prefs.putBoolean(PREF_LEFT_PANE_VISIBLE, isLeftPaneVisible)
                    },
                    isSelected = isLeftPaneVisible
                )

                Spacer(Modifier.width(8.dp))

                // Selection Mode Toggle Group
                ToolbarGroup {
                    ToolbarIconButton(
                        icon = Icons.Default.AdsClick,
                        tooltip = "Selection Mode",
                        onClick = {
                            isSelectMode = true
                            prefs.putBoolean(PREF_IS_SELECT_MODE, isSelectMode)
                        },
                        isSelected = isSelectMode,
                        modifier = Modifier.size(32.dp)
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFCCCCCC)))
                    ToolbarIconButton(
                        icon = Icons.Default.PanTool,
                        tooltip = "Pan Mode",
                        onClick = {
                            isSelectMode = false
                            prefs.putBoolean(PREF_IS_SELECT_MODE, isSelectMode)
                        },
                        isSelected = !isSelectMode,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                // Zoom Controls Group
                ToolbarGroup {
                    ToolbarIconButton(
                        icon = Icons.Default.ZoomIn,
                        tooltip = "Zoom In",
                        onClick = {
                            zoom = (zoom * 1.2f).coerceAtMost(3f)
                            prefs.putFloat(PREF_ZOOM, zoom)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFCCCCCC)))
                    Text(
                        "${(zoom * 100).toInt()}%",
                        fontSize = 11.sp,
                        modifier = Modifier.width(45.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFCCCCCC)))
                    ToolbarIconButton(
                        icon = Icons.Default.ZoomOut,
                        tooltip = "Zoom Out",
                        onClick = {
                            zoom = (zoom / 1.2f).coerceAtLeast(0.1f)
                            prefs.putFloat(PREF_ZOOM, zoom)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFCCCCCC)))
                    ToolbarIconButton(
                        icon = Icons.Default.RestartAlt,
                        tooltip = "Reset Zoom",
                        onClick = {
                            zoom = 1f
                            prefs.putFloat(PREF_ZOOM, zoom)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                ToolbarIconButton(
                    icon = Icons.AutoMirrored.Filled.ViewSidebar,
                    tooltip = if (isRightPaneVisible) "Hide Inspector" else "Show Inspector",
                    onClick = {
                        isRightPaneVisible = !isRightPaneVisible
                        prefs.putBoolean(PREF_RIGHT_PANE_VISIBLE, isRightPaneVisible)
                    },
                    isSelected = isRightPaneVisible,
                    modifier = Modifier.graphicsLayer { scaleX = -1f }
                )
            }
            HorizontalDivider()

            Row(Modifier.weight(1f)) {

                // 1. Resizable Left Sidebar
                if (isLeftPaneVisible) {
                    Box(Modifier.width(leftPaneWidth).fillMaxHeight()) {
                        Sidebar(
                            workspaceItems = workspaceItems,
                            selectedTablePath = selectedTablePath,
                            showRows = showRows,
                            onShowRowsChange = {
                                showRows = it
                                prefs.putBoolean(PREF_SHOW_ROWS, it)
                                if (selectedTablePath != null) loadTable(selectedTablePath!!, it)
                            },
                            showMetadata = showMetadata,
                            onShowMetadataChange = {
                                showMetadata = it
                                prefs.putBoolean(PREF_SHOW_METADATA, it)
                            },
                            showSnapshots = showSnapshots,
                            onShowSnapshotsChange = {
                                showSnapshots = it
                                prefs.putBoolean(PREF_SHOW_SNAPSHOTS, it)
                            },
                            showManifests = showManifests,
                            onShowManifestsChange = {
                                showManifests = it
                                prefs.putBoolean(PREF_SHOW_MANIFESTS, it)
                            },
                            showDataFiles = showDataFiles,
                            onShowDataFilesChange = {
                                showDataFiles = it
                                prefs.putBoolean(PREF_SHOW_DATA_FILES, it)
                            },
                            onTableSelect = { tablePath ->
                                if (selectedTablePath != null && graphModel != null) {
                                    val oldKey = "$selectedTablePath-rows_$showRows"
                                    sessionCache[oldKey]?.selectedNodeIds = selectedNodeIds
                                }
                                loadTable(tablePath)
                            },
                            onAddRoot = { path ->
                                val file = File(path)
                                if (file.exists() && file.isDirectory) {
                                    val newItem = if (isIcebergTable(file)) {
                                        WorkspaceItem.SingleTable(path, file.name)
                                    } else {
                                        WorkspaceItem.Warehouse(path, file.name, scanForTables(file))
                                    }
                                    saveWorkspace(workspaceItems + newItem)
                                }
                            },
                            onRemoveRoot = { item ->
                                saveWorkspace(workspaceItems.filter { it != item })
                            },
                            onMoveRoot = { item, delta ->
                                val index = workspaceItems.indexOf(item)
                                if (index != -1) {
                                    val newIndex = (index + delta).coerceIn(0, workspaceItems.size - 1)
                                    if (newIndex != index) {
                                        val newList = workspaceItems.toMutableList()
                                        newList.removeAt(index)
                                        newList.add(newIndex, item)
                                        saveWorkspace(newList)
                                    }
                                }
                            }
                        )
                    }
                    DraggableDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        leftPaneWidth = (leftPaneWidth + deltaDp).coerceIn(150.dp, 500.dp)
                        prefs.putFloat(PREF_LEFT_PANE_WIDTH, leftPaneWidth.value)
                    })
                }

                // 2. Main Canvas
                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    if (graphModel != null) {
                        val filteredNodes = graphModel!!.nodes.filter { node ->
                            when (node) {
                                is GraphNode.MetadataNode -> showMetadata
                                is GraphNode.SnapshotNode -> showSnapshots
                                is GraphNode.ManifestNode -> showManifests
                                is GraphNode.FileNode     -> showDataFiles
                                is GraphNode.RowNode      -> showDataFiles && showRows
                            }
                        }
                        val filteredNodeIds = filteredNodes.map { it.id }.toSet()
                        val filteredEdges = graphModel!!.edges.filter { edge ->
                            edge.fromId in filteredNodeIds && edge.toId in filteredNodeIds
                        }
                        val filteredGraph = graphModel!!.copy(nodes = filteredNodes, edges = filteredEdges)

                        GraphCanvas(
                            graph = filteredGraph,
                            selectedNodeIds = selectedNodeIds,
                            isSelectMode = isSelectMode,
                            zoom = zoom,
                            onZoomChange = {
                                zoom = it
                                prefs.putFloat(PREF_ZOOM, it)
                            },
                            onSelectionChange = { selectedNodeIds = it }
                        )
                    } else if (workspaceItems.isNotEmpty()) {
                        Text(
                            "Select a table from the sidebar to view its structure.",
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
                        prefs.putFloat(PREF_RIGHT_PANE_WIDTH, rightPaneWidth.value)
                    })

                    Column(Modifier.width(rightPaneWidth).fillMaxHeight().padding(8.dp)) {
                        Text("Structure Tree", style = MaterialTheme.typography.headlineSmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Box(Modifier.weight(0.5f).fillMaxWidth()) {
                            if (graphModel != null) {
                                NavigationTree(
                                    graph = graphModel!!,
                                    selectedNodeIds = selectedNodeIds,
                                    onNodeSelect = { selectedNodeIds = setOf(it.id) })
                            } else {
                                Text("No graph loaded.", fontSize = 12.sp, color = Color.Gray)
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Node Details", style = MaterialTheme.typography.headlineSmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Box(Modifier.weight(0.5f).fillMaxWidth()) {
                            SelectionContainer {
                                if (selectedNodeIds.isEmpty()) {
                                    Text(
                                        "Select a node to view details.",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                } else if (selectedNodeIds.size == 1) {
                                    val node = graphModel?.nodes?.find { it.id == selectedNodeIds.first() }
                                    if (node != null) {
                                        val scrollState = rememberScrollState()
                                        Box(Modifier.fillMaxSize()) {
                                            Column(
                                                Modifier.fillMaxSize().verticalScroll(scrollState)
                                            ) {
                                                when (node) {
                                                is GraphNode.MetadataNode -> {
                                                    DetailTable {
                                                        DetailRow("Property", "Value", isHeader = true)
                                                        DetailRow("File Name", node.fileName)
                                                        DetailRow(
                                                            "Format Version",
                                                            "${node.data.formatVersion}"
                                                        )
                                                        DetailRow(
                                                            "Table UUID",
                                                            "${node.data.tableUuid ?: "N/A"}"
                                                        )
                                                        DetailRow(
                                                            "Location",
                                                            "${node.data.location ?: "N/A"}"
                                                        )
                                                        DetailRow(
                                                            "Last Seq. Num.",
                                                            "${node.data.lastSequenceNumber ?: "N/A"}"
                                                        )
                                                        DetailRow(
                                                            "Last Updated",
                                                            formatTimestamp(node.data.lastUpdatedMs)
                                                        )
                                                        DetailRow(
                                                            "Last Column ID",
                                                            "${node.data.lastColumnId ?: "N/A"}"
                                                        )
                                                        DetailRow(
                                                            "Current Snap.",
                                                            "${node.data.currentSnapshotId ?: "None"}"
                                                        )
                                                        DetailRow(
                                                            "Total Snaps.",
                                                            "${node.data.snapshots.size}"
                                                        )
                                                    }
                                                    if (node.data.properties.isNotEmpty()) {
                                                        Spacer(Modifier.height(16.dp))
                                                        Text(
                                                            "Properties",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        DetailTable {
                                                            DetailRow("Key", "Value", isHeader = true)
                                                            node.data.properties.forEach { (k, v) ->
                                                                DetailRow(k, v)
                                                            }
                                                        }
                                                    }
                                                }

                                                is GraphNode.SnapshotNode -> {
                                                    DetailTable {
                                                        DetailRow("Property", "Value", isHeader = true)
                                                        DetailRow("Snapshot ID", "${node.data.snapshotId}")
                                                        DetailRow(
                                                            "Parent ID",
                                                            "${node.data.parentSnapshotId ?: "None"}"
                                                        )
                                                        DetailRow(
                                                            "Timestamp",
                                                            formatTimestamp(node.data.timestampMs)
                                                        )
                                                        val manifestList = node.data.manifestList
                                                        val manifestListLabel =
                                                            if (manifestList == null) "N/A" else "${
                                                                manifestList.substringAfterLast("/")
                                                            } ($manifestList)"
                                                        DetailRow("Manifest List", manifestListLabel)
                                                    }
                                                    if (node.data.summary.isNotEmpty()) {
                                                        Spacer(Modifier.height(16.dp))
                                                        Text(
                                                            "Summary",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        DetailTable {
                                                            DetailRow("Key", "Value", isHeader = true)
                                                            node.data.summary.forEach { (k, v) ->
                                                                DetailRow(k, v)
                                                            }
                                                        }
                                                    }
                                                }

                                                is GraphNode.ManifestNode -> DetailTable {
                                                    val contentType = when (val c = node.data.content) {
                                                        1    -> "Deletes ($c)"
                                                        0    -> "Data ($c)"
                                                        else -> "Unknown ($c)"
                                                    }
                                                    DetailRow("Property", "Value", isHeader = true)
                                                    DetailRow("Content Type", contentType)
                                                    DetailRow(
                                                        "Sequence Num.",
                                                        "${node.data.sequenceNumber ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "Min Sequence Num.",
                                                        "${node.data.cominSequenceNumber ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "Partition Spec ID",
                                                        "${node.data.partitionSpecId ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "Added Snapshot",
                                                        "${node.data.addedSnapshotId ?: "N/A"}"
                                                    )
                                                    DetailRow("Added Files", "${node.data.addedFilesCount ?: 0}")
                                                    DetailRow(
                                                        "Existing Files",
                                                        "${node.data.existingFilesCount ?: 0}"
                                                    )
                                                    DetailRow(
                                                        "Deleted Files",
                                                        "${node.data.deletedFilesCount ?: 0}"
                                                    )
                                                    DetailRow("Added Rows", "${node.data.addedRowsCount ?: 0}")
                                                    DetailRow(
                                                        "Existing Rows",
                                                        "${node.data.existingRowsCount ?: 0}"
                                                    )
                                                    DetailRow(
                                                        "Deleted Rows",
                                                        "${node.data.deletedRowsCount ?: 0}"
                                                    )
                                                    DetailRow(
                                                        "Manifest Length",
                                                        "${node.data.manifestLength ?: 0} bytes"
                                                    )
                                                    val manifestPath = node.data.manifestPath
                                                    val manifestPathLabel =
                                                        if (manifestPath == null) "N/A" else "${
                                                            manifestPath.substringAfterLast("/")
                                                        } ($manifestPath)"
                                                    DetailRow("Path", manifestPathLabel)
                                                }

                                                is GraphNode.FileNode -> DetailTable {
                                                    val contentType = when (val c = node.data.content ?: 0) {
                                                        1    -> "Position Delete ($c)"
                                                        2    -> "Equality Delete ($c)"
                                                        else -> "Data ($c)"
                                                    }
                                                    val status = when (val s = node.entry.status) {
                                                        0    -> "EXISTING ($s)"
                                                        1    -> "ADDED ($s)"
                                                        2    -> "DELETED ($s)"
                                                        else -> "Unknown ($s)"
                                                    }
                                                    DetailRow("Property", "Value", isHeader = true)
                                                    DetailRow("Simple ID", "${node.simpleId}")
                                                    DetailRow("Content Type", contentType)
                                                    DetailRow("Status", status)
                                                    DetailRow(
                                                        "Snapshot ID",
                                                        "${node.entry.snapshotId ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "Sequence Num.",
                                                        "${node.entry.sequenceNumber ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "File Seq. Num.",
                                                        "${node.entry.fileSequenceNumber ?: "N/A"}"
                                                    )
                                                    DetailRow("File Format", "${node.data.fileFormat ?: "N/A"}")
                                                    DetailRow("Record Count", "${node.data.recordCount ?: 0}")
                                                    DetailRow(
                                                        "File Size",
                                                        "${node.data.fileSizeInBytes ?: 0} bytes"
                                                    )
                                                    DetailRow(
                                                        "Sort Order ID",
                                                        "${node.data.sorderOrderId ?: "N/A"}"
                                                    )
                                                    DetailRow(
                                                        "Split Offsets",
                                                        node.data.splitOffsets.joinToString(", ")
                                                    )
                                                    DetailRow("Path", "${node.data.filePath ?: "N/A"}")
                                                }

                                                is GraphNode.RowNode -> DetailTable {
                                                    val typeStr = when (node.content) {
                                                        1    -> "Position Delete Row"
                                                        2    -> "Equality Delete Row"
                                                        else -> "Data Row"
                                                    }
                                                    DetailRow("Column", "Value ($typeStr)", isHeader = true)
                                                    node.data.forEach { (k, v) ->
                                                        DetailRow(k, "$v")
                                                    }
                                                }
                                            }
                                        }
                                        VerticalScrollbar(
                                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                            adapter = rememberScrollbarAdapter(scrollState)
                                        )
                                    }
                                } else {
                                    Column {
                                        Text("${selectedNodeIds.size} Nodes Selected", fontWeight = FontWeight.Bold)
                                        Text(
                                            "Drag any selected node to move the group together.",
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
        }
    }
}
}