package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.WorkspaceItem
import java.io.File
import javax.swing.JFileChooser

@Composable
fun FiltersPanel(
    showRows: Boolean,
    onShowRowsChange: (Boolean) -> Unit,
    showMetadata: Boolean,
    onShowMetadataChange: (Boolean) -> Unit,
    showSnapshots: Boolean,
    onShowSnapshotsChange: (Boolean) -> Unit,
    showManifests: Boolean,
    onShowManifestsChange: (Boolean) -> Unit,
    showDataFiles: Boolean,
    onShowDataFilesChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(8.dp)
    ) {
        Text("FILTER NODES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showMetadata, onCheckedChange = onShowMetadataChange, modifier = Modifier.size(32.dp))
                Text("Metadata", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showSnapshots, onCheckedChange = onShowSnapshotsChange, modifier = Modifier.size(32.dp))
                Text("Snapshots", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showManifests, onCheckedChange = onShowManifestsChange, modifier = Modifier.size(32.dp))
                Text("Manifests", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showDataFiles, onCheckedChange = onShowDataFilesChange, modifier = Modifier.size(32.dp))
                Text("Data Files", fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showRows, onCheckedChange = onShowRowsChange, modifier = Modifier.size(32.dp))
                Text("Data Rows", fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun WorkspacePanel(
    workspaceItems: List<WorkspaceItem>,
    selectedTablePath: String?,
    expandedPaths: Set<String>,
    onExpandedPathsChange: (Set<String>) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTableSelect: (String) -> Unit,
    onAddRoot: (String) -> Unit,
    onRemoveRoot: (WorkspaceItem) -> Unit,
    onMoveRoot: (WorkspaceItem, Int) -> Unit,
) {
    var draggingItemPath by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    var dragAccumulatedOffset by remember { mutableStateOf(0f) }

    val currentWorkspaceItems by rememberUpdatedState(workspaceItems)
    val currentOnMoveRoot by rememberUpdatedState(onMoveRoot)

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)).padding(8.dp)
    ) {
        Button(
            onClick = {
                val chooser = JFileChooser()
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.dialogTitle = "Add Warehouse or Table"
                chooser.currentDirectory = File(System.getProperty("user.home"))

                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    onAddRoot(chooser.selectedFile.absolutePath)
                }
            }, modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add to Workspace", fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "WORKSPACE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            placeholder = { Text("Search tables...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(16.dp))
                    }
                }
            } else null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White
            )
        )

        val filteredWorkspaceItems = remember(workspaceItems, searchQuery) {
            if (searchQuery.isBlank()) workspaceItems
            else {
                workspaceItems.filter { item ->
                    when (item) {
                        is WorkspaceItem.SingleTable -> item.name.contains(searchQuery, ignoreCase = true)
                        is WorkspaceItem.Warehouse -> {
                            item.name.contains(searchQuery, ignoreCase = true) ||
                                    item.tables.any { it.contains(searchQuery, ignoreCase = true) }
                        }
                    }
                }
            }
        }

        val listState = rememberLazyListState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                filteredWorkspaceItems.forEachIndexed { index, item ->
                    item(key = item.path) {
                        val isSelected = item is WorkspaceItem.SingleTable && item.path == selectedTablePath
                        val isDragging = draggingItemPath == item.path

                        val effectivelyExpanded = expandedPaths.contains(item.path) || searchQuery.isNotBlank()

                        WorkspaceRootItem(
                            item = item,
                            isSelected = isSelected,
                            isExpanded = effectivelyExpanded,
                            onToggleExpand = {
                                onExpandedPathsChange(if (expandedPaths.contains(item.path)) {
                                    expandedPaths - item.path
                                } else {
                                    expandedPaths + item.path
                                })
                            },
                            onSelect = {
                                if (item is WorkspaceItem.SingleTable) {
                                    onTableSelect(item.path)
                                }
                            },
                            onRemove = { onRemoveRoot(item) },
                            modifier = Modifier
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffset
                                        alpha = 0.8f
                                    }
                                }
                                .pointerInput(item) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingItemPath = item.path
                                            dragOffset = 0f
                                            dragAccumulatedOffset = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount.y
                                            dragAccumulatedOffset += dragAmount.y
                                            val threshold = 32.dp.toPx()
                                            val currentIdx = currentWorkspaceItems.indexOf(item)
                                            if (dragAccumulatedOffset > threshold && currentIdx < currentWorkspaceItems.size - 1) {
                                                currentOnMoveRoot(item, 1)
                                                dragAccumulatedOffset -= threshold
                                                dragOffset -= threshold
                                            } else if (dragAccumulatedOffset < -threshold && currentIdx > 0) {
                                                currentOnMoveRoot(item, -1)
                                                dragAccumulatedOffset += threshold
                                                dragOffset += threshold
                                            }
                                        },
                                        onDragEnd = {
                                            draggingItemPath = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            draggingItemPath = null
                                            dragOffset = 0f
                                        }
                                    )
                                }
                        )
                    }

                    if (item is WorkspaceItem.Warehouse && (expandedPaths.contains(item.path) || searchQuery.isNotBlank())) {
                        val filteredTables = if (searchQuery.isBlank()) item.tables
                        else item.tables.filter { it.contains(searchQuery, ignoreCase = true) || item.name.contains(searchQuery, ignoreCase = true) }

                        items(filteredTables) { tableName ->
                            val tablePath = "${item.path}/$tableName"
                            val isSelected = tablePath == selectedTablePath
                            val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
                            val textColor = if (isSelected) Color(0xFF1976D2) else Color.Black

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor)
                                    .clickable { onTableSelect(tablePath) }
                                    .padding(vertical = 4.dp, horizontal = 24.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.TableChart, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = tableName,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )
        }
    }
}

@Composable
fun WorkspaceRootItem(
    item: WorkspaceItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefix = when (item) {
        is WorkspaceItem.Warehouse   -> "warehouse: "
        is WorkspaceItem.SingleTable -> "table: "
    }
    val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
    val textColor = if (isSelected) Color(0xFF1976D2) else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable {
                if (item is WorkspaceItem.Warehouse) onToggleExpand() else onSelect()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item is WorkspaceItem.Warehouse) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(24.dp)) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.TableChart, null, modifier = Modifier.size(14.dp), tint = if (isSelected) Color(0xFF1976D2) else Color.Gray)
            }
        }

        Text(
            text = "$prefix${item.name}",
            fontSize = 13.sp,
            color = textColor,
            fontWeight = if (isSelected || item is WorkspaceItem.Warehouse) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp), tint = Color.Red.copy(alpha = 0.7f))
        }
    }
}
