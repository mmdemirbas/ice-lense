package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode

@Composable
fun NavigationTree(
    graph: GraphModel,
    selectedNodeIds: Set<String>,
    onNodeSelect: (GraphNode) -> Unit,
) {
    var expandedNodeIds by remember { mutableStateOf(setOf<String>()) }

    val flattenedTree = remember(graph, expandedNodeIds) {
        flattenGraph(graph, expandedNodeIds)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedNodeIds) {
        if (selectedNodeIds.size == 1) {
            val selectedId = selectedNodeIds.first()
            val path = findPathToNode(graph, selectedId)
            expandedNodeIds = expandedNodeIds + path

            val index = flattenedTree.indexOfFirst { it.first.id == selectedId }
            if (index >= 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val isItemVisible = visibleItems.any { it.index == index }
                if (!isItemVisible) {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(flattenedTree) { (node, depth, hasChildren) ->
            val isSelected = selectedNodeIds.contains(node.id)
            val isExpanded = expandedNodeIds.contains(node.id)
            val bgColor = if (isSelected) Color(0xFFE3F2FD) else Color.Transparent
            val textColor = if (isSelected) Color(0xFF1976D2) else Color.DarkGray

            Row(
                modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .clickable { onNodeSelect(node) }
                .padding(vertical = 4.dp, horizontal = 8.dp)
                .padding(start = (depth * 16).dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(16.dp).clickable {
                            if (hasChildren) {
                                expandedNodeIds = if (isExpanded) {
                                    expandedNodeIds - node.id
                                } else {
                                    expandedNodeIds + node.id
                                }
                            }
                        }) {
                    if (hasChildren) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = "Toggle Expand",
                            tint = Color.Gray,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Box(
                    modifier = Modifier.size(10.dp).background(
                        getGraphNodeColor(node), androidx.compose.foundation.shape.CircleShape
                    ).border(
                        1.dp,
                        getGraphNodeBorderColor(node),
                        androidx.compose.foundation.shape.CircleShape
                    )
                )
                Spacer(Modifier.width(8.dp))

                Text(
                    text = getNodeLabel(node),
                    fontSize = 11.sp,
                    color = textColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun flattenGraph(
    graph: GraphModel,
    expandedIds: Set<String>,
): List<Triple<GraphNode, Int, Boolean>> {
    val result = mutableListOf<Triple<GraphNode, Int, Boolean>>()
    val edgesBySource = graph.edges.groupBy { it.fromId }
    val visited = mutableSetOf<String>()

    fun traverse(nodeId: String, depth: Int) {
        if (visited.contains(nodeId)) return
        visited.add(nodeId)

        val node = graph.nodes.find { it.id == nodeId } ?: return
        val children = edgesBySource[nodeId]?.map { it.toId } ?: emptyList()

        result.add(Triple(node, depth, children.isNotEmpty()))

        if (expandedIds.contains(nodeId)) {
            children.forEach { traverse(it, depth + 1) }
        }

        visited.remove(nodeId)
    }

    val childIds = graph.edges.map { it.toId }.toSet()
    val roots = graph.nodes.filter { it.id !in childIds }

    roots.forEach { traverse(it.id, 0) }
    return result
}

private fun findPathToNode(graph: GraphModel, targetId: String): List<String> {
    val edgesByTarget = graph.edges.groupBy { it.toId }
    val path = mutableListOf<String>()
    var currentId = targetId

    while (true) {
        val parentEdge = edgesByTarget[currentId]?.firstOrNull() ?: break
        currentId = parentEdge.fromId
        path.add(currentId)
    }
    return path
}

private fun getNodeLabel(node: GraphNode): String {
    return when (node) {
        is GraphNode.MetadataNode -> "Meta: ${node.fileName}"
        is GraphNode.SnapshotNode -> "Snap: ${node.data.snapshotId}"
        is GraphNode.ManifestNode -> "Manifest (${node.data.addedFilesCount} adds)"
        is GraphNode.FileNode     -> "File ${node.simpleId}: ${
            node.data.filePath?.substringAfterLast("/")
        }"

        is GraphNode.RowNode      -> "Row: ${node.data.values.firstOrNull() ?: "..."}"
    }
}