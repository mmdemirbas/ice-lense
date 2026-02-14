package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode

private fun inspectorNodeLabel(node: GraphNode): String = when (node) {
    is GraphNode.MetadataNode -> "Metadata ${node.fileName}"
    is GraphNode.SnapshotNode -> "Snapshot ${node.simpleId}"
    is GraphNode.ManifestNode -> "Manifest"
    is GraphNode.FileNode -> "File ${node.simpleId}"
    is GraphNode.RowNode -> when (node.content) {
        1 -> "Position Delete Row"
        2 -> "Equality Delete Row"
        else -> "Data Row"
    }
}

private fun inspectorEdgeType(isSibling: Boolean): String =
    if (isSibling) "Sibling Link" else "Parent/Child Link"

@Composable
fun NodeDetailsContent(graphModel: GraphModel?, selectedNodeIds: Set<String>) {
    SelectionContainer {
        if (selectedNodeIds.isEmpty()) {
            Text(
                "Select a node to view details.",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
        } else if (selectedNodeIds.size == 1) {
            val node = graphModel?.nodes?.find { it.id == selectedNodeIds.first() }
            if (node != null) {
                val scrollState = rememberScrollState()
                Box(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(scrollState).padding(8.dp)
                    ) {
                        when (node) {
                            is GraphNode.MetadataNode  -> {
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

                            is GraphNode.SnapshotNode  -> {
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

                            is GraphNode.ManifestNode  -> DetailTable {
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

                            is GraphNode.FileNode      -> DetailTable {
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

                            is GraphNode.RowNode       -> DetailTable {
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

                        val currentGraph = graphModel
                        val parentEdges = currentGraph.edges.filter { it.toId == node.id }
                        val childEdges = currentGraph.edges.filter { it.fromId == node.id }
                        val nodeById = currentGraph.nodes.associateBy { it.id }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Relations",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        DetailTable {
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Parent Count", "${parentEdges.size}")
                            DetailRow("Children Count", "${childEdges.size}")
                        }

                        if (parentEdges.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Parents", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            DetailTable {
                                DetailRow("Parent", "Edge Type", isHeader = true)
                                parentEdges.forEach { edge ->
                                    val parentLabel = nodeById[edge.fromId]?.let { inspectorNodeLabel(it) } ?: edge.fromId
                                    DetailRow(parentLabel, inspectorEdgeType(edge.isSibling))
                                }
                            }
                        }

                        if (childEdges.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Children", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(4.dp))
                            DetailTable {
                                DetailRow("Child", "Edge Type", isHeader = true)
                                childEdges.forEach { edge ->
                                    val childLabel = nodeById[edge.toId]?.let { inspectorNodeLabel(it) } ?: edge.toId
                                    DetailRow(childLabel, inspectorEdgeType(edge.isSibling))
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState)
                    )
                }
            }
        } else {
            Column(Modifier.padding(8.dp)) {
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
