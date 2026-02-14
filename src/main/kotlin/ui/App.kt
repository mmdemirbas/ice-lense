package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val PREF_TOP_PANE_HEIGHT = "top_pane_height"
private const val PREF_BOTTOM_PANE_HEIGHT = "bottom_pane_height"
private const val PREF_ACTIVE_LEFT_WINDOW = "active_left_window"
private const val PREF_ACTIVE_RIGHT_WINDOW = "active_right_window"
private const val PREF_ACTIVE_TOP_WINDOW = "active_top_window"
private const val PREF_ACTIVE_BOTTOM_WINDOW = "active_bottom_window"
private const val PREF_WINDOW_ANCHORS = "tool_window_anchors"
private const val PREF_ZOOM = "zoom"
private const val PREF_SHOW_METADATA = "show_metadata"
private const val PREF_SHOW_SNAPSHOTS = "show_snapshots"
private const val PREF_SHOW_MANIFESTS = "show_manifests"
private const val PREF_SHOW_DATA_FILES = "show_data_files"
private const val PREF_IS_SELECT_MODE = "is_select_mode"
private const val PREF_WORKSPACE_ITEMS = "workspace_items"
private const val PREF_WORKSPACE_EXPANDED_PATHS = "workspace_expanded_paths"
private const val PREF_AUTO_RELOAD_TABLE = "auto_reload_table"

// Data class to hold cached table sessions
data class TableSession(
    val table: UnifiedTableModel,
    val graph: GraphModel,
    var selectedNodeIds: Set<String> = emptySet(),
    val fingerprint: String = "",
)

@Composable
fun DraggableVerticalDivider(onDrag: (Float) -> Unit) {
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
fun DraggableHorizontalDivider(onDrag: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }) {
        HorizontalDivider(modifier = Modifier.align(Alignment.Center))
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
    var graphModel by remember { mutableStateOf<GraphModel?>(null, neverEqualPolicy()) }
    var graphRevision by remember { mutableIntStateOf(0) }
    var selectedNodeIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var workspaceExpandedPaths by remember {
        val saved = prefs.get(PREF_WORKSPACE_EXPANDED_PATHS, "")
        val expanded = saved
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        mutableStateOf(expanded)
    }
    var workspaceSearchQuery by remember { mutableStateOf("") }
    var isLoadingTable by remember { mutableStateOf(false) }
    var loadRequestId by remember { mutableStateOf(0L) }
    var autoReloadTable by remember { mutableStateOf(prefs.getBoolean(PREF_AUTO_RELOAD_TABLE, true)) }

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
    var topPaneHeight by remember { mutableStateOf(prefs.getFloat(PREF_TOP_PANE_HEIGHT, 220f).dp) }
    var bottomPaneHeight by remember { mutableStateOf(prefs.getFloat(PREF_BOTTOM_PANE_HEIGHT, 220f).dp) }
    val density = LocalDensity.current

    var activeLeftToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_LEFT_WINDOW, "workspace")) }
    var activeRightToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_RIGHT_WINDOW, "structure")) }
    var activeTopToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_TOP_WINDOW, "")) }
    var activeBottomToolWindowId by remember { mutableStateOf(prefs.get(PREF_ACTIVE_BOTTOM_WINDOW, "")) }
    var hiddenToolWindowsSnapshot by remember { mutableStateOf<Map<ToolWindowAnchor, String>?>(null) }
    var draggingToolWindowId by remember { mutableStateOf<String?>(null) }
    var dragTargetAnchor by remember { mutableStateOf<ToolWindowAnchor?>(null) }
    var appWindowBounds by remember { mutableStateOf<Rect?>(null) }

    var windowAnchors by remember {
        val defaults = mapOf(
            "workspace" to ToolWindowAnchor.LEFT,
            "filters" to ToolWindowAnchor.LEFT,
            "structure" to ToolWindowAnchor.RIGHT,
            "inspector" to ToolWindowAnchor.RIGHT
        )
        val saved = prefs.get(PREF_WINDOW_ANCHORS, "")
        val parsed = saved.split(";").mapNotNull { part ->
            val idAndAnchor = part.split(":")
            if (idAndAnchor.size != 2) return@mapNotNull null
            val id = idAndAnchor[0]
            val anchor = runCatching { ToolWindowAnchor.valueOf(idAndAnchor[1]) }.getOrNull() ?: return@mapNotNull null
            id to anchor
        }.toMap()
        val map = defaults.toMutableMap().apply { putAll(parsed) }
        mutableStateOf(map)
    }

    val toolWindows = listOf(
        ToolWindowConfig("workspace", "Workspace", Icons.Default.Storage, windowAnchors["workspace"] ?: ToolWindowAnchor.LEFT),
        ToolWindowConfig("filters", "Filters", Icons.Default.FilterList, windowAnchors["filters"] ?: ToolWindowAnchor.LEFT),
        ToolWindowConfig("structure", "Structure", Icons.Default.AccountTree, windowAnchors["structure"] ?: ToolWindowAnchor.RIGHT),
        ToolWindowConfig("inspector", "Inspector", Icons.Default.Info, windowAnchors["inspector"] ?: ToolWindowAnchor.RIGHT)
    )

    fun setWorkspaceExpandedPaths(paths: Set<String>) {
        workspaceExpandedPaths = paths
        prefs.put(PREF_WORKSPACE_EXPANDED_PATHS, paths.joinToString(";"))
    }

    fun saveWorkspace(items: List<WorkspaceItem>) {
        workspaceItems = items
        prefs.put(PREF_WORKSPACE_ITEMS, items.joinToString(";") { it.serialize() })
        val validPaths = items.map { it.path }.toSet()
        setWorkspaceExpandedPaths(workspaceExpandedPaths.filter { it in validPaths }.toSet())
    }

    fun getActiveWindow(anchor: ToolWindowAnchor): String {
        return when (anchor) {
            ToolWindowAnchor.LEFT -> activeLeftToolWindowId
            ToolWindowAnchor.RIGHT -> activeRightToolWindowId
            ToolWindowAnchor.TOP -> activeTopToolWindowId
            ToolWindowAnchor.BOTTOM -> activeBottomToolWindowId
        }
    }

    fun setActiveWindow(anchor: ToolWindowAnchor, id: String) {
        when (anchor) {
            ToolWindowAnchor.LEFT -> {
                activeLeftToolWindowId = id
                prefs.put(PREF_ACTIVE_LEFT_WINDOW, id)
            }
            ToolWindowAnchor.RIGHT -> {
                activeRightToolWindowId = id
                prefs.put(PREF_ACTIVE_RIGHT_WINDOW, id)
            }
            ToolWindowAnchor.TOP -> {
                activeTopToolWindowId = id
                prefs.put(PREF_ACTIVE_TOP_WINDOW, id)
            }
            ToolWindowAnchor.BOTTOM -> {
                activeBottomToolWindowId = id
                prefs.put(PREF_ACTIVE_BOTTOM_WINDOW, id)
            }
        }
    }

    fun toggleToolWindow(id: String, anchor: ToolWindowAnchor) {
        val current = getActiveWindow(anchor)
        setActiveWindow(anchor, if (current == id) "" else id)
    }

    fun activeWindowMap(): Map<ToolWindowAnchor, String> = mapOf(
        ToolWindowAnchor.LEFT to activeLeftToolWindowId,
        ToolWindowAnchor.RIGHT to activeRightToolWindowId,
        ToolWindowAnchor.TOP to activeTopToolWindowId,
        ToolWindowAnchor.BOTTOM to activeBottomToolWindowId
    )

    fun setAllToolWindowsHidden() {
        setActiveWindow(ToolWindowAnchor.LEFT, "")
        setActiveWindow(ToolWindowAnchor.RIGHT, "")
        setActiveWindow(ToolWindowAnchor.TOP, "")
        setActiveWindow(ToolWindowAnchor.BOTTOM, "")
    }

    fun toggleAllToolWindows() {
        val current = activeWindowMap()
        val hasAnyVisible = current.values.any { it.isNotEmpty() }

        if (hasAnyVisible) {
            hiddenToolWindowsSnapshot = current
            setAllToolWindowsHidden()
            return
        }

        val snapshot = hiddenToolWindowsSnapshot ?: return
        ToolWindowAnchor.entries.forEach { anchor ->
            val savedId = snapshot[anchor].orEmpty()
            if (savedId.isNotEmpty() && windowAnchors[savedId] == anchor) {
                setActiveWindow(anchor, savedId)
            }
        }
    }

    fun moveToolWindow(id: String, newAnchor: ToolWindowAnchor) {
        val currentAnchor = windowAnchors[id] ?: ToolWindowAnchor.LEFT
        if (currentAnchor == newAnchor) return

        val newAnchors = windowAnchors.toMutableMap()
        newAnchors[id] = newAnchor
        windowAnchors = newAnchors
        prefs.put(PREF_WINDOW_ANCHORS, newAnchors.entries.joinToString(";") { "${it.key}:${it.value}" })

        if (getActiveWindow(currentAnchor) == id) {
            setActiveWindow(currentAnchor, "")
        }
        setActiveWindow(newAnchor, id)
    }

    fun updateDragTarget(positionInWindow: Offset?) {
        val bounds = appWindowBounds
        val edgeSizePx = with(density) { 120.dp.toPx() }
        dragTargetAnchor = if (positionInWindow == null || bounds == null) null else when {
            positionInWindow.y <= bounds.top + edgeSizePx -> ToolWindowAnchor.TOP
            positionInWindow.y >= bounds.bottom - edgeSizePx -> ToolWindowAnchor.BOTTOM
            positionInWindow.x <= bounds.left + edgeSizePx -> ToolWindowAnchor.LEFT
            positionInWindow.x >= bounds.right - edgeSizePx -> ToolWindowAnchor.RIGHT
            else -> null
        }
    }

    fun setGraphModelAndBump(model: GraphModel?) {
        graphModel = model
        graphRevision++
    }

    fun computeTableFingerprint(tablePath: String): String {
        val metadataDir = File(tablePath, "metadata")
        if (!metadataDir.exists() || !metadataDir.isDirectory) return "missing"
        val trackedFiles = metadataDir.listFiles()?.filter { file ->
            file.isFile && (file.name.endsWith(".metadata.json") || file.name == "version-hint.text")
        }?.sortedBy { it.name }.orEmpty()
        if (trackedFiles.isEmpty()) return "empty"
        val signature = trackedFiles.joinToString("|") { file ->
            "${file.name}:${file.length()}:${file.lastModified()}"
        }
        return signature.hashCode().toString()
    }

    // Logic to load a specific table
    fun loadTable(
        tablePath: String,
        withRows: Boolean = showRows,
        forceRelayout: Boolean = false,
        forceReloadFromFs: Boolean = false,
        preservePositions: Boolean = false
    ) {
        val cacheKey = "$tablePath-rows_$withRows"
        selectedTablePath = tablePath

        if (!forceRelayout && !forceReloadFromFs && sessionCache.containsKey(cacheKey)) {
            val session = sessionCache[cacheKey]!!
            setGraphModelAndBump(session.graph)
            selectedNodeIds = session.selectedNodeIds
            errorMsg = null
            isLoadingTable = false
            return
        }

        isLoadingTable = true
        errorMsg = null
        val requestId = loadRequestId + 1
        loadRequestId = requestId

        coroutineScope.launch {
            try {
                val previousSession = sessionCache[cacheKey]
                val reloaded = withContext(Dispatchers.Default) {
                    val fingerprint = computeTableFingerprint(tablePath)
                    if (forceReloadFromFs && !forceRelayout && previousSession != null && previousSession.fingerprint == fingerprint) {
                        return@withContext previousSession
                    }
                    val tableModel = UnifiedTableModel(Paths.get(tablePath))
                    val newGraph = GraphLayoutService.layoutGraph(tableModel, withRows)
                    if (preservePositions && previousSession != null) {
                        val oldById = previousSession.graph.nodes.associateBy { it.id }
                        newGraph.nodes.forEach { n ->
                            val old = oldById[n.id]
                            if (old != null) {
                                n.x = old.x
                                n.y = old.y
                            }
                        }
                    }
                    TableSession(
                        table = tableModel,
                        graph = newGraph,
                        selectedNodeIds = previousSession?.selectedNodeIds.orEmpty(),
                        fingerprint = fingerprint
                    )
                }

                if (requestId != loadRequestId) return@launch
                sessionCache[cacheKey] = reloaded
                setGraphModelAndBump(reloaded.graph)
                selectedNodeIds = if (preservePositions && !forceRelayout) {
                    selectedNodeIds.filter { id -> reloaded.graph.nodes.any { it.id == id } }.toSet()
                } else {
                    emptySet()
                }
                errorMsg = null
            } catch (e: Exception) {
                if (requestId != loadRequestId) return@launch
                errorMsg = e.message
                e.printStackTrace()
                setGraphModelAndBump(null)
            } finally {
                if (requestId == loadRequestId) {
                    isLoadingTable = false
                }
            }
        }
    }

    fun reapplyCurrentLayout() {
        val tablePath = selectedTablePath ?: return
        val cacheKey = "$tablePath-rows_$showRows"

        isLoadingTable = true
        errorMsg = null
        val requestId = loadRequestId + 1
        loadRequestId = requestId

        coroutineScope.launch {
            try {
                val existingTableModel = sessionCache[cacheKey]?.table
                val fingerprint = withContext(Dispatchers.Default) { computeTableFingerprint(tablePath) }
                val tableModel = existingTableModel ?: withContext(Dispatchers.Default) {
                    UnifiedTableModel(Paths.get(tablePath))
                }
                val newGraph = withContext(Dispatchers.Default) {
                    GraphLayoutService.layoutGraph(tableModel, showRows)
                }

                if (requestId != loadRequestId) return@launch
                val session = TableSession(tableModel, newGraph, fingerprint = fingerprint)
                sessionCache[cacheKey] = session
                setGraphModelAndBump(newGraph)
                selectedNodeIds = emptySet()
                errorMsg = null
            } catch (e: Exception) {
                if (requestId != loadRequestId) return@launch
                errorMsg = e.message
            } finally {
                if (requestId == loadRequestId) {
                    isLoadingTable = false
                }
            }
        }
    }

    fun reloadCurrentTableFromFilesystem(preserveLayout: Boolean = true) {
        val tablePath = selectedTablePath ?: return
        loadTable(
            tablePath = tablePath,
            withRows = showRows,
            forceReloadFromFs = true,
            preservePositions = preserveLayout
        )
    }

    @Composable
    fun RenderToolWindowContent(toolWindowId: String) {
        when (toolWindowId) {
            "workspace" -> WorkspacePanel(
                workspaceItems = workspaceItems,
                selectedTablePath = selectedTablePath,
                expandedPaths = workspaceExpandedPaths,
                onExpandedPathsChange = { setWorkspaceExpandedPaths(it) },
                searchQuery = workspaceSearchQuery,
                onSearchQueryChange = { workspaceSearchQuery = it },
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
            "filters" -> FiltersPanel(
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
            "structure" -> {
                if (graphModel != null) {
                    NavigationTree(
                        graph = graphModel!!,
                        selectedNodeIds = selectedNodeIds,
                        onNodeSelect = { selectedNodeIds = setOf(it.id) }
                    )
                } else {
                    Text("No graph loaded.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
                }
            }
            "inspector" -> NodeDetailsContent(graphModel, selectedNodeIds)
        }
    }

    val leftWindows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.LEFT }.map { it.id to it.icon }
    val rightWindows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.RIGHT }.map { it.id to it.icon }
    val topWindows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.TOP }.map { it.id to it.icon }
    val bottomWindows = toolWindows.filter { windowAnchors[it.id] == ToolWindowAnchor.BOTTOM }.map { it.id to it.icon }
    val filteredGraph = remember(graphRevision, showMetadata, showSnapshots, showManifests, showDataFiles, showRows) {
        graphModel?.let { currentGraph ->
            val filteredNodes = currentGraph.nodes.filter { node ->
                when (node) {
                    is GraphNode.MetadataNode -> showMetadata
                    is GraphNode.SnapshotNode -> showSnapshots
                    is GraphNode.ManifestNode -> showManifests
                    is GraphNode.FileNode -> showDataFiles
                    is GraphNode.RowNode -> showDataFiles && showRows
                }
            }
            val filteredNodeIds = filteredNodes.asSequence().map { it.id }.toHashSet()
            val filteredEdges = currentGraph.edges.filter { edge ->
                edge.fromId in filteredNodeIds && edge.toId in filteredNodeIds
            }
            currentGraph.copy(nodes = filteredNodes, edges = filteredEdges)
        }
    }

    fun toolWindowTitle(id: String): String = toolWindows.firstOrNull { it.id == id }?.title ?: id

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords -> appWindowBounds = coords.boundsInWindow() }
        ) {
            Column(Modifier.fillMaxSize()) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Color(0xFFEEEEEE))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        icon = Icons.Default.FilterCenterFocus,
                        tooltip = "Reset Zoom",
                        onClick = {
                            zoom = 1f
                            prefs.putFloat(PREF_ZOOM, zoom)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                ToolbarGroup {
                    ToolbarIconButton(
                        icon = Icons.Default.Refresh,
                        tooltip = "Re-apply Layout",
                        onClick = { reapplyCurrentLayout() },
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                ToolbarGroup {
                    ToolbarIconButton(
                        icon = Icons.Default.Sync,
                        tooltip = "Reload from Filesystem",
                        onClick = { reloadCurrentTableFromFilesystem(preserveLayout = true) },
                        modifier = Modifier.size(32.dp)
                    )
                    Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFCCCCCC)))
                    ToolbarIconButton(
                        icon = Icons.Default.Schedule,
                        tooltip = if (autoReloadTable) "Auto Reload: On" else "Auto Reload: Off",
                        onClick = {
                            autoReloadTable = !autoReloadTable
                            prefs.putBoolean(PREF_AUTO_RELOAD_TABLE, autoReloadTable)
                        },
                        isSelected = autoReloadTable,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(Modifier.weight(1f))
            }
            HorizontalDivider()

            if (topWindows.isNotEmpty()) {
                ToolWindowBar(
                    anchor = ToolWindowAnchor.TOP,
                    windows = topWindows,
                    activeWindowId = activeTopToolWindowId,
                    onWindowClick = { id -> toggleToolWindow(id, ToolWindowAnchor.TOP) },
                    isDropTarget = dragTargetAnchor == ToolWindowAnchor.TOP
                )
            }

            if (activeTopToolWindowId.isNotEmpty()) {
                val paneId = activeTopToolWindowId
                Box(Modifier.height(topPaneHeight).fillMaxWidth()) {
                    ToolWindowPane(
                        title = toolWindowTitle(paneId),
                        isBeingDragged = draggingToolWindowId == paneId,
                        onClose = { setActiveWindow(ToolWindowAnchor.TOP, "") },
                        onDragStart = { position ->
                            draggingToolWindowId = paneId
                            updateDragTarget(position)
                        },
                        onDragMove = { position -> updateDragTarget(position) },
                        onDragEnd = {
                            val draggedId = draggingToolWindowId
                            val target = dragTargetAnchor
                            if (draggedId != null && target != null) moveToolWindow(draggedId, target)
                            draggingToolWindowId = null
                            updateDragTarget(null)
                        },
                        onDragCancel = {
                            draggingToolWindowId = null
                            updateDragTarget(null)
                        }
                    ) {
                        RenderToolWindowContent(paneId)
                    }
                }
                DraggableHorizontalDivider(onDrag = { delta ->
                    val deltaDp = with(density) { delta.toDp() }
                    topPaneHeight = (topPaneHeight + deltaDp).coerceIn(120.dp, 500.dp)
                    prefs.putFloat(PREF_TOP_PANE_HEIGHT, topPaneHeight.value)
                })
            }

            Row(Modifier.weight(1f)) {
                if (leftWindows.isNotEmpty()) {
                    ToolWindowBar(
                        anchor = ToolWindowAnchor.LEFT,
                        windows = leftWindows,
                        activeWindowId = activeLeftToolWindowId,
                        onWindowClick = { id -> toggleToolWindow(id, ToolWindowAnchor.LEFT) },
                        isDropTarget = dragTargetAnchor == ToolWindowAnchor.LEFT
                    )
                }

                if (activeLeftToolWindowId.isNotEmpty()) {
                    val paneId = activeLeftToolWindowId
                    Box(Modifier.width(leftPaneWidth).fillMaxHeight()) {
                        ToolWindowPane(
                            title = toolWindowTitle(paneId),
                            isBeingDragged = draggingToolWindowId == paneId,
                            onClose = { setActiveWindow(ToolWindowAnchor.LEFT, "") },
                            onDragStart = { position ->
                                draggingToolWindowId = paneId
                                updateDragTarget(position)
                            },
                            onDragMove = { position -> updateDragTarget(position) },
                            onDragEnd = {
                                val draggedId = draggingToolWindowId
                                val target = dragTargetAnchor
                                if (draggedId != null && target != null) moveToolWindow(draggedId, target)
                                draggingToolWindowId = null
                                updateDragTarget(null)
                            },
                            onDragCancel = {
                                draggingToolWindowId = null
                                updateDragTarget(null)
                            }
                        ) {
                            RenderToolWindowContent(paneId)
                        }
                    }
                    DraggableVerticalDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        leftPaneWidth = (leftPaneWidth + deltaDp).coerceIn(150.dp, 500.dp)
                        prefs.putFloat(PREF_LEFT_PANE_WIDTH, leftPaneWidth.value)
                    })
                }

                Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    if (filteredGraph != null) {
                        key(graphRevision) {
                            GraphCanvas(
                                graph = filteredGraph,
                                graphRevision = graphRevision,
                                selectedNodeIds = selectedNodeIds,
                                isSelectMode = isSelectMode,
                                zoom = zoom,
                                onZoomChange = {
                                    zoom = it
                                    prefs.putFloat(PREF_ZOOM, it)
                                },
                                onSelectionChange = { selectedNodeIds = it },
                                onEmptyAreaDoubleClick = { toggleAllToolWindows() }
                            )
                        }
                    } else if (!isLoadingTable && workspaceItems.isNotEmpty()) {
                        Text(
                            "Select a table from the sidebar to view its structure.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (isLoadingTable) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Loading table...")
                        }
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
                    DraggableVerticalDivider(onDrag = { delta ->
                        val deltaDp = with(density) { delta.toDp() }
                        rightPaneWidth = (rightPaneWidth - deltaDp).coerceIn(200.dp, 600.dp)
                        prefs.putFloat(PREF_RIGHT_PANE_WIDTH, rightPaneWidth.value)
                    })

                    val paneId = activeRightToolWindowId
                    Box(Modifier.width(rightPaneWidth).fillMaxHeight()) {
                        ToolWindowPane(
                            title = toolWindowTitle(paneId),
                            isBeingDragged = draggingToolWindowId == paneId,
                            onClose = { setActiveWindow(ToolWindowAnchor.RIGHT, "") },
                            onDragStart = { position ->
                                draggingToolWindowId = paneId
                                updateDragTarget(position)
                            },
                            onDragMove = { position -> updateDragTarget(position) },
                            onDragEnd = {
                                val draggedId = draggingToolWindowId
                                val target = dragTargetAnchor
                                if (draggedId != null && target != null) moveToolWindow(draggedId, target)
                                draggingToolWindowId = null
                                updateDragTarget(null)
                            },
                            onDragCancel = {
                                draggingToolWindowId = null
                                updateDragTarget(null)
                            }
                        ) {
                            RenderToolWindowContent(paneId)
                        }
                    }
                }

                if (rightWindows.isNotEmpty()) {
                    ToolWindowBar(
                        anchor = ToolWindowAnchor.RIGHT,
                        windows = rightWindows,
                        activeWindowId = activeRightToolWindowId,
                        onWindowClick = { id -> toggleToolWindow(id, ToolWindowAnchor.RIGHT) },
                        isDropTarget = dragTargetAnchor == ToolWindowAnchor.RIGHT
                    )
                }
            }

            if (activeBottomToolWindowId.isNotEmpty()) {
                DraggableHorizontalDivider(onDrag = { delta ->
                    val deltaDp = with(density) { delta.toDp() }
                    bottomPaneHeight = (bottomPaneHeight - deltaDp).coerceIn(120.dp, 500.dp)
                    prefs.putFloat(PREF_BOTTOM_PANE_HEIGHT, bottomPaneHeight.value)
                })
                val paneId = activeBottomToolWindowId
                Box(Modifier.height(bottomPaneHeight).fillMaxWidth()) {
                    ToolWindowPane(
                        title = toolWindowTitle(paneId),
                        isBeingDragged = draggingToolWindowId == paneId,
                        onClose = { setActiveWindow(ToolWindowAnchor.BOTTOM, "") },
                        onDragStart = { position ->
                            draggingToolWindowId = paneId
                            updateDragTarget(position)
                        },
                        onDragMove = { position -> updateDragTarget(position) },
                        onDragEnd = {
                            val draggedId = draggingToolWindowId
                            val target = dragTargetAnchor
                            if (draggedId != null && target != null) moveToolWindow(draggedId, target)
                            draggingToolWindowId = null
                            updateDragTarget(null)
                        },
                        onDragCancel = {
                            draggingToolWindowId = null
                            updateDragTarget(null)
                        }
                    ) {
                        RenderToolWindowContent(paneId)
                    }
                }
            }

            if (bottomWindows.isNotEmpty()) {
                ToolWindowBar(
                    anchor = ToolWindowAnchor.BOTTOM,
                    windows = bottomWindows,
                    activeWindowId = activeBottomToolWindowId,
                    onWindowClick = { id -> toggleToolWindow(id, ToolWindowAnchor.BOTTOM) },
                    isDropTarget = dragTargetAnchor == ToolWindowAnchor.BOTTOM
                )
            }

            }

            if (draggingToolWindowId != null) {
                val dropTargetSize = 120.dp
                val baseColor = Color(0xFF90CAF9).copy(alpha = 0.12f)
                val activeColor = Color(0xFF64B5F6).copy(alpha = 0.32f)

                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(dropTargetSize)
                        .background(if (dragTargetAnchor == ToolWindowAnchor.TOP) activeColor else baseColor)
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(dropTargetSize)
                        .background(if (dragTargetAnchor == ToolWindowAnchor.BOTTOM) activeColor else baseColor)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(dropTargetSize)
                        .background(if (dragTargetAnchor == ToolWindowAnchor.LEFT) activeColor else baseColor)
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(dropTargetSize)
                        .background(if (dragTargetAnchor == ToolWindowAnchor.RIGHT) activeColor else baseColor)
                )
            }
        }
    }

    LaunchedEffect(selectedTablePath, showRows, autoReloadTable) {
        if (!autoReloadTable) return@LaunchedEffect
        while (isActive) {
            val tablePath = selectedTablePath
            if (tablePath != null && !isLoadingTable) {
                val cacheKey = "$tablePath-rows_$showRows"
                val session = sessionCache[cacheKey]
                if (session != null) {
                    val currentFingerprint = withContext(Dispatchers.Default) { computeTableFingerprint(tablePath) }
                    if (currentFingerprint != session.fingerprint) {
                        reloadCurrentTableFromFilesystem(preserveLayout = true)
                    }
                }
            }
            delay(3000)
        }
    }
}
