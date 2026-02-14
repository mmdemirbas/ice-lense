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
private const val PREF_ACTIVE_LEFT_WINDOW = "active_left_window"
private const val PREF_ACTIVE_RIGHT_WINDOW = "active_right_window"
private const val PREF_WINDOW_ANCHORS = "tool_window_anchors"
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
    val density = LocalDensity.current

    var activeLeftToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_LEFT_WINDOW, "workspace")) }
    var activeRightToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_RIGHT_WINDOW, "structure")) }

    var windowAnchors by remember {
        val saved = prefs.get(PREF_WINDOW_ANCHORS, "workspace:LEFT;filters:LEFT;structure:RIGHT;inspector:RIGHT")
        val map = saved.split(";").associate {
            val (id, anchor) = it.split(":")
            id to ToolWindowAnchor.valueOf(anchor)
        }.toMutableMap()
        mutableStateOf(map)
    }

    val toolWindows = listOf(
        ToolWindowConfig("workspace", "Workspace", Icons.Default.Storage, windowAnchors["workspace"] ?: ToolWindowAnchor.LEFT),
        ToolWindowConfig("filters", "Filters", Icons.Default.FilterList, windowAnchors["filters"] ?: ToolWindowAnchor.LEFT),
        ToolWindowConfig("structure", "Structure", Icons.Default.AccountTree, windowAnchors["structure"] ?: ToolWindowAnchor.RIGHT),
        ToolWindowConfig("inspector", "Inspector", Icons.Default.Info, windowAnchors["inspector"] ?: ToolWindowAnchor.RIGHT)
    )

    fun saveWorkspace(items: List<WorkspaceItem>) {
        workspaceItems = items
        prefs.put(PREF_WORKSPACE_ITEMS, items.joinToString(";") { it.serialize() })
    }

    fun toggleToolWindow(id: String, side: ToolWindowAnchor) {
        if (side == ToolWindowAnchor.LEFT) {
            activeLeftToolWindowId = if (activeLeftToolWindowId == id) "" else id
            prefs.put(PREF_ACTIVE_LEFT_WINDOW, activeLeftToolWindowId)
        } else {
            activeRightToolWindowId = if (activeRightToolWindowId == id) "" else id
            prefs.put(PREF_ACTIVE_RIGHT_WINDOW, activeRightToolWindowId)
        }
    }

    fun moveToolWindow(id: String) {
        val currentAnchor = windowAnchors[id] ?: ToolWindowAnchor.LEFT
        val newAnchor = if (currentAnchor == ToolWindowAnchor.LEFT) ToolWindowAnchor.RIGHT else ToolWindowAnchor.LEFT
        
        val newAnchors = windowAnchors.toMutableMap()
        newAnchors[id] = newAnchor
        windowAnchors = newAnchors
        prefs.put(PREF_WINDOW_ANCHORS, newAnchors.entries.joinToString(";") { "${it.key}:${it.value}" })

        // Clear active states if they were pointing to this ID
        if (currentAnchor == ToolWindowAnchor.LEFT && activeLeftToolWindowId == id) activeLeftToolWindowId = ""
        if (currentAnchor == ToolWindowAnchor.RIGHT && activeRightToolWindowId == id) activeRightToolWindowId = ""
        
        // Auto-activate on new side
        if (newAnchor == ToolWindowAnchor.LEFT) activeLeftToolWindowId = id
        else activeRightToolWindowId = id
        
        prefs.put(PREF_ACTIVE_LEFT_WINDOW, activeLeftToolWindowId)
        prefs.put(PREF_ACTIVE_RIGHT_WINDOW, activeRightToolWindowId)
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
                    tooltip = if (activeLeftToolWindowId.isNotEmpty()) "Hide Left Tool Windows" else "Show Left Tool Windows",
                    onClick = {
                        activeLeftToolWindowId = if (activeLeftToolWindowId.isNotEmpty()) "" else toolWindows.firstOrNull { windowAnchors[it.id] == ToolWindowAnchor.LEFT }?.id ?: ""
                        prefs.put(PREF_ACTIVE_LEFT_WINDOW, activeLeftToolWindowId)
                    },
                    isSelected = activeLeftToolWindowId.isNotEmpty()
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
                    tooltip = if (activeRightToolWindowId.isNotEmpty()) "Hide Right Tool Windows" else "Show Right Tool Windows",
                    onClick = {
                        activeRightToolWindowId = if (activeRightToolWindowId.isNotEmpty()) "" else toolWindows.firstOrNull { windowAnchors[it.id] == ToolWindowAnchor.RIGHT }?.id ?: ""
                        prefs.put(PREF_ACTIVE_RIGHT_WINDOW, activeRightToolWindowId)
                    },
                    isSelected = activeRightToolWindowId.isNotEmpty(),
                    modifier = Modifier.graphicsLayer { scaleX = -1f }
                )
            }
            HorizontalDivider()

            Row(Modifier.weight(1f)) {
                // Left Tool Window Bar
                ToolWindowBar(
                    anchor = ToolWindowAnchor.LEFT,
                    windows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.LEFT }.map { it.id to it.icon },
                    activeWindowId = activeLeftToolWindowId,
                    onWindowClick = { id ->
                        activeLeftToolWindowId = if (activeLeftToolWindowId == id) "" else id
                        prefs.put(PREF_ACTIVE_LEFT_WINDOW, activeLeftToolWindowId)
                    }
                )

                if (activeLeftToolWindowId.isNotEmpty()) {
                    Box(Modifier.width(leftPaneWidth).fillMaxHeight()) {
                        val onClose = { activeLeftToolWindowId = "" }
                        val onMove = { moveToolWindow(activeLeftToolWindowId) }
                        when (activeLeftToolWindowId) {
                            "workspace" -> ToolWindowPane("Workspace", onClose, onMove) {
                                WorkspacePanel(
                                    workspaceItems = workspaceItems,
                                    selectedTablePath = selectedTablePath,
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
                            "filters"   -> ToolWindowPane("Filters", onClose, onMove) {
                                FiltersPanel(
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
                                    }
                                )
                            }
                            "structure" -> ToolWindowPane("Structure", onClose, onMove) {
                                if (graphModel != null) {
                                    NavigationTree(
                                        graph = graphModel!!,
                                        selectedNodeIds = selectedNodeIds,
                                        onNodeSelect = { selectedNodeIds = setOf(it.id) })
                                } else {
                                    Text("No graph loaded.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
                                }
                            }
                            "inspector" -> ToolWindowPane("Inspector", onClose, onMove) {
                                NodeDetailsContent(graphModel, selectedNodeIds)
                            }
                        }
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

                if (activeRightToolWindowId.isNotEmpty()) {
                    DraggableDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        rightPaneWidth = (rightPaneWidth - deltaDp).coerceIn(200.dp, 600.dp)
                        prefs.putFloat(PREF_RIGHT_PANE_WIDTH, rightPaneWidth.value)
                    })
                    Box(Modifier.width(rightPaneWidth).fillMaxHeight()) {
                        val onClose = { activeRightToolWindowId = "" }
                        val onMove = { moveToolWindow(activeRightToolWindowId) }
                        when (activeRightToolWindowId) {
                            "workspace" -> ToolWindowPane("Workspace", onClose, onMove) {
                                WorkspacePanel(
                                    workspaceItems = workspaceItems,
                                    selectedTablePath = selectedTablePath,
                                    onTableSelect = { loadTable(it) },
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
                            "filters"   -> ToolWindowPane("Filters", onClose, onMove) {
                                FiltersPanel(
                                    showRows = showRows, onShowRowsChange = { showRows = it },
                                    showMetadata = showMetadata, onShowMetadataChange = { showMetadata = it },
                                    showSnapshots = showSnapshots, onShowSnapshotsChange = { showSnapshots = it },
                                    showManifests = showManifests, onShowManifestsChange = { showManifests = it },
                                    showDataFiles = showDataFiles, onShowDataFilesChange = { showDataFiles = it }
                                )
                            }
                            "structure" -> ToolWindowPane("Structure", onClose, onMove) {
                                if (graphModel != null) {
                                    NavigationTree(
                                        graph = graphModel!!,
                                        selectedNodeIds = selectedNodeIds,
                                        onNodeSelect = { selectedNodeIds = setOf(it.id) })
                                } else {
                                    Text("No graph loaded.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
                                }
                            }
                            "inspector" -> ToolWindowPane("Inspector", onClose, onMove) {
                                NodeDetailsContent(graphModel, selectedNodeIds)
                            }
                        }
                    }
                }

                // Right Tool Window Bar
                ToolWindowBar(
                    anchor = ToolWindowAnchor.RIGHT,
                    windows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.RIGHT }.map { it.id to it.icon },
                    activeWindowId = activeRightToolWindowId,
                    onWindowClick = { id ->
                        activeRightToolWindowId = if (activeRightToolWindowId == id) "" else id
                        prefs.put(PREF_ACTIVE_RIGHT_WINDOW, activeRightToolWindowId)
                    }
                )
            }
        }
    }
}