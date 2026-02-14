package ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode
import model.KeyValuePairBytes
import model.KeyValuePairLong

private fun nodeTitle(node: GraphNode): String = when (node) {
    is GraphNode.MetadataNode -> node.fileName
    is GraphNode.SnapshotNode -> "Snapshot ${node.simpleId}"
    is GraphNode.ManifestNode -> if (node.data.content == 1) "Delete Manifest" else "Data Manifest"
    is GraphNode.FileNode -> when (node.data.content ?: 0) {
        1 -> "Pos Delete ${node.simpleId}"
        2 -> "Eq Delete ${node.simpleId}"
        else -> "Data File ${node.simpleId}"
    }
    is GraphNode.RowNode -> when (node.content) {
        1 -> "Position Delete Row"
        2 -> "Equality Delete Row"
        else -> "Data Row"
    }
}

private fun normalizeText(value: String?): String {
    if (value == null) return "N/A"
    return value
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun kvLongs(values: List<KeyValuePairLong>): String =
    if (values.isEmpty()) "[]" else values.joinToString(", ") { "${it.key}:${it.value}" }

private fun ByteArray.toHexShort(maxBytes: Int = 24): String {
    val head = take(maxBytes).joinToString("") { byte -> "%02x".format(byte) }
    return if (size > maxBytes) "$head..." else head
}

private fun kvBytes(values: List<KeyValuePairBytes>): String =
    if (values.isEmpty()) "[]" else values.joinToString(", ") { "${it.key}:${it.value.toHexShort()}" }

private fun currentSnapshotLabel(currentSnapshotId: Long?): String = when (currentSnapshotId) {
    null -> "None"
    -1L -> "None (-1)"
    else -> currentSnapshotId.toString()
}

private fun childNodes(node: GraphNode, graphModel: GraphModel): List<GraphNode> {
    val nodeById = graphModel.nodes.associateBy { it.id }
    return graphModel.edges
        .asSequence()
        .filter { it.fromId == node.id }
        .mapNotNull { nodeById[it.toId] }
        .toList()
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun WideTable(
    headers: List<String>,
    rows: List<List<String>>,
    columnWidth: Dp = 180.dp,
) {
    val horizontalState = rememberScrollState()
    val tableWidth = (headers.size * columnWidth.value).dp + ((headers.size - 1).coerceAtLeast(0) * 9).dp + 16.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalState)
    ) {
        Column(
            Modifier
                .width(tableWidth)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .border(1.dp, Color.LightGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        ) {
            WideTableRow(headers, headers.size, columnWidth, isHeader = true)
            rows.forEach { row -> WideTableRow(row, headers.size, columnWidth, isHeader = false) }
        }
    }
}

@Composable
private fun WideTableRow(cells: List<String>, columns: Int, columnWidth: Dp, isHeader: Boolean) {
    val bgColor = if (isHeader) Color(0xFFF5F5F5) else Color.Transparent
    val normalizedCells = if (cells.size < columns) {
        cells + List(columns - cells.size) { "" }
    } else {
        cells.take(columns)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        normalizedCells.forEachIndexed { index, cell ->
            if (index > 0) {
                Box(Modifier.width(1.dp).height(16.dp).background(Color(0xFFE0E0E0)))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = cell,
                modifier = Modifier.width(columnWidth),
                fontSize = 11.sp,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                fontFamily = if (isHeader) null else FontFamily.Monospace,
                maxLines = if (isHeader) 2 else 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
}

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
            return@SelectionContainer
        }
        if (selectedNodeIds.size > 1) {
            Column(Modifier.padding(8.dp)) {
                Text("${selectedNodeIds.size} Nodes Selected", fontWeight = FontWeight.Bold)
                Text(
                    "Drag any selected node to move the group together.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            return@SelectionContainer
        }

        val currentGraph = graphModel ?: return@SelectionContainer
        val node = currentGraph.nodes.find { it.id == selectedNodeIds.first() } ?: return@SelectionContainer
        val children = remember(node.id, currentGraph.nodes, currentGraph.edges) { childNodes(node, currentGraph) }

        val scrollState = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                Text(nodeTitle(node), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Node ID: ${node.id}", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))

                when (node) {
                    is GraphNode.MetadataNode -> {
                        DetailTable {
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("File Name", node.fileName)
                            DetailRow("Format Version", "${node.data.formatVersion}")
                            DetailRow("Table UUID", "${node.data.tableUuid ?: "N/A"}")
                            DetailRow("Location", "${node.data.location ?: "N/A"}")
                            DetailRow("Last Seq. Num.", "${node.data.lastSequenceNumber ?: "N/A"}")
                            DetailRow("Last Updated", formatTimestamp(node.data.lastUpdatedMs))
                            DetailRow("Last Column ID", "${node.data.lastColumnId ?: "N/A"}")
                            DetailRow("Current Snapshot ID", currentSnapshotLabel(node.data.currentSnapshotId))
                            DetailRow("Total Snapshots", "${node.data.snapshots.size}")
                            DetailRow("Total Schemas", "${node.data.schemas.size}")
                        }

                        if (node.data.properties.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Properties")
                            DetailTable {
                                DetailRow("Key", "Value", isHeader = true)
                                node.data.properties.toSortedMap().forEach { (k, v) ->
                                    DetailRow(k, v)
                                }
                            }
                        }

                        if (node.data.schemas.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Schemas")
                            node.data.schemas
                                .sortedBy { it.schemaId ?: Int.MAX_VALUE }
                                .forEach { schema ->
                                    Text(
                                        "Schema ${schema.schemaId ?: "Unknown"}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    WideTable(
                                        headers = listOf(
                                            "Field ID",
                                            "Field Name",
                                            "Required",
                                            "Type",
                                            "Is Identifier Field"
                                        ),
                                        rows = schema.fields
                                            .sortedBy { it.id ?: Int.MAX_VALUE }
                                            .map { field ->
                                                val isIdentifier = field.id != null && field.id in schema.identifierFieldIds
                                                listOf(
                                                    "${field.id ?: "N/A"}",
                                                    field.name ?: "N/A",
                                                    "${field.required ?: false}",
                                                    normalizeText(field.type?.toString()?.trim('"')),
                                                    if (isIdentifier) "Yes" else "No"
                                                )
                                            }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                        }

                        if (node.data.snapshots.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Snapshots")
                            val snapshots = node.data.snapshots.sortedBy { it.timestampMs ?: Long.MAX_VALUE }
                            WideTable(
                                headers = listOf(
                                    "Snapshot ID",
                                    "Parent Snapshot ID",
                                    "Sequence Number",
                                    "Schema ID",
                                    "Timestamp",
                                    "Manifest List",
                                    "Operation",
                                    "Summary"
                                ),
                                rows = snapshots.map { snapshot ->
                                    listOf(
                                        "${snapshot.snapshotId ?: "N/A"}",
                                        "${snapshot.parentSnapshotId ?: "None"}",
                                        "${snapshot.sequenceNumber ?: "N/A"}",
                                        "${snapshot.schemaId ?: "N/A"}",
                                        formatTimestamp(snapshot.timestampMs),
                                        normalizeText(snapshot.manifestList),
                                        snapshot.summary["operation"] ?: "N/A",
                                        if (snapshot.summary.isEmpty()) "N/A"
                                        else snapshot.summary.toSortedMap().entries.joinToString(", ") { "${it.key}=${it.value}" }
                                    )
                                }
                            )
                        }
                    }

                    is GraphNode.SnapshotNode -> {
                        DetailTable {
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Snapshot ID", "${node.data.snapshotId}")
                            DetailRow("Parent ID", "${node.data.parentSnapshotId ?: "None"}")
                            DetailRow("Sequence Number", "${node.data.sequenceNumber ?: "N/A"}")
                            DetailRow("Schema ID", "${node.data.schemaId ?: "N/A"}")
                            DetailRow("Timestamp", formatTimestamp(node.data.timestampMs))
                            val manifestList = node.data.manifestList
                            val manifestListLabel = if (manifestList == null) "N/A" else "${manifestList.substringAfterLast("/")} ($manifestList)"
                            DetailRow("Manifest List", manifestListLabel)
                        }

                        if (node.data.summary.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Summary")
                            DetailTable {
                                DetailRow("Key", "Value", isHeader = true)
                                node.data.summary.toSortedMap().forEach { (k, v) ->
                                    DetailRow(k, v)
                                }
                            }
                        }

                        val manifestChildren = children
                            .filterIsInstance<GraphNode.ManifestNode>()
                            .sortedWith(
                                compareBy(
                                    { it.data.sequenceNumber ?: Int.MAX_VALUE },
                                    { it.data.cominSequenceNumber ?: Int.MAX_VALUE },
                                    { it.data.manifestPath ?: "" }
                                )
                            )

                        if (manifestChildren.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Manifest List Rows")
                            WideTable(
                                headers = listOf(
                                    "Apply Order",
                                    "Manifest Path",
                                    "Content",
                                    "Manifest Length",
                                    "Partition Spec ID",
                                    "Sequence Number",
                                    "Min Sequence Number",
                                    "Added Snapshot ID",
                                    "Added Files",
                                    "Existing Files",
                                    "Deleted Files",
                                    "Added Rows",
                                    "Existing Rows",
                                    "Deleted Rows"
                                ),
                                rows = manifestChildren.mapIndexed { index, manifestNode ->
                                    val manifest = manifestNode.data
                                    listOf(
                                        "${index + 1}",
                                        normalizeText(manifest.manifestPath),
                                        if (manifest.content == 1) "Deletes (1)" else "Data (0)",
                                        "${manifest.manifestLength ?: "N/A"}",
                                        "${manifest.partitionSpecId ?: "N/A"}",
                                        "${manifest.sequenceNumber ?: "N/A"}",
                                        "${manifest.cominSequenceNumber ?: "N/A"}",
                                        "${manifest.addedSnapshotId ?: "N/A"}",
                                        "${manifest.addedFilesCount ?: 0}",
                                        "${manifest.existingFilesCount ?: 0}",
                                        "${manifest.deletedFilesCount ?: 0}",
                                        "${manifest.addedRowsCount ?: 0}",
                                        "${manifest.existingRowsCount ?: 0}",
                                        "${manifest.deletedRowsCount ?: 0}"
                                    )
                                }
                            )
                        }
                    }

                    is GraphNode.ManifestNode -> {
                        DetailTable {
                            val contentType = when (val c = node.data.content) {
                                1 -> "Deletes ($c)"
                                0 -> "Data ($c)"
                                else -> "Unknown ($c)"
                            }
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Content Type", contentType)
                            DetailRow("Sequence Num.", "${node.data.sequenceNumber ?: "N/A"}")
                            DetailRow("Min Sequence Num.", "${node.data.cominSequenceNumber ?: "N/A"}")
                            DetailRow("Partition Spec ID", "${node.data.partitionSpecId ?: "N/A"}")
                            DetailRow("Added Snapshot", "${node.data.addedSnapshotId ?: "N/A"}")
                            DetailRow("Added Files", "${node.data.addedFilesCount ?: 0}")
                            DetailRow("Existing Files", "${node.data.existingFilesCount ?: 0}")
                            DetailRow("Deleted Files", "${node.data.deletedFilesCount ?: 0}")
                            DetailRow("Added Rows", "${node.data.addedRowsCount ?: 0}")
                            DetailRow("Existing Rows", "${node.data.existingRowsCount ?: 0}")
                            DetailRow("Deleted Rows", "${node.data.deletedRowsCount ?: 0}")
                            DetailRow("Manifest Length", "${node.data.manifestLength ?: 0} bytes")
                            val manifestPath = node.data.manifestPath
                            val manifestPathLabel = if (manifestPath == null) "N/A" else "${manifestPath.substringAfterLast("/")} ($manifestPath)"
                            DetailRow("Path", manifestPathLabel)
                        }

                        val fileChildren = children
                            .filterIsInstance<GraphNode.FileNode>()
                            .sortedWith(
                                compareBy(
                                    { it.entry.sequenceNumber ?: Long.MAX_VALUE },
                                    { it.entry.fileSequenceNumber ?: Long.MAX_VALUE },
                                    { it.simpleId }
                                )
                            )

                        if (fileChildren.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Manifest Entries (Data File Details)")
                            WideTable(
                                headers = listOf(
                                    "Apply Order",
                                    "Simple ID",
                                    "Status",
                                    "Content",
                                    "Snapshot ID",
                                    "Sequence Number",
                                    "File Sequence Number",
                                    "File Path",
                                    "Format",
                                    "Record Count",
                                    "File Size Bytes",
                                    "Partition",
                                    "Column Sizes",
                                    "Value Counts",
                                    "Null Value Counts",
                                    "NaN Value Counts",
                                    "Lower Bounds",
                                    "Upper Bounds",
                                    "Key Metadata",
                                    "Split Offsets",
                                    "Equality IDs",
                                    "Sort Order ID"
                                ),
                                rows = fileChildren.mapIndexed { index, fileNode ->
                                    val data = fileNode.data
                                    val status = when (fileNode.entry.status) {
                                        0 -> "EXISTING (0)"
                                        1 -> "ADDED (1)"
                                        2 -> "DELETED (2)"
                                        else -> "Unknown (${fileNode.entry.status})"
                                    }
                                    val content = when (data.content ?: 0) {
                                        1 -> "Position Delete (1)"
                                        2 -> "Equality Delete (2)"
                                        else -> "Data (0)"
                                    }
                                    listOf(
                                        "${index + 1}",
                                        "${fileNode.simpleId}",
                                        status,
                                        content,
                                        "${fileNode.entry.snapshotId ?: "N/A"}",
                                        "${fileNode.entry.sequenceNumber ?: "N/A"}",
                                        "${fileNode.entry.fileSequenceNumber ?: "N/A"}",
                                        normalizeText(data.filePath),
                                        data.fileFormat ?: "N/A",
                                        "${data.recordCount ?: 0}",
                                        "${data.fileSizeInBytes ?: 0}",
                                        "N/A",
                                        kvLongs(data.columnSizes),
                                        kvLongs(data.valueCounts),
                                        kvLongs(data.nullValueCounts),
                                        kvLongs(data.nanValueCounts),
                                        kvBytes(data.lowerBounds),
                                        kvBytes(data.upperBounds),
                                        data.keyMetadata?.toHexShort() ?: "N/A",
                                        data.splitOffsets.joinToString(", "),
                                        data.equalityIds?.joinToString(", ") ?: "N/A",
                                        "${data.sorderOrderId ?: "N/A"}"
                                    )
                                }
                            )
                        }
                    }

                    is GraphNode.FileNode -> {
                        DetailTable {
                            val contentType = when (val c = node.data.content ?: 0) {
                                1 -> "Position Delete ($c)"
                                2 -> "Equality Delete ($c)"
                                else -> "Data ($c)"
                            }
                            val status = when (val s = node.entry.status) {
                                0 -> "EXISTING ($s)"
                                1 -> "ADDED ($s)"
                                2 -> "DELETED ($s)"
                                else -> "Unknown ($s)"
                            }
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Simple ID", "${node.simpleId}")
                            DetailRow("Content Type", contentType)
                            DetailRow("Status", status)
                            DetailRow("Snapshot ID", "${node.entry.snapshotId ?: "N/A"}")
                            DetailRow("Sequence Num.", "${node.entry.sequenceNumber ?: "N/A"}")
                            DetailRow("File Seq. Num.", "${node.entry.fileSequenceNumber ?: "N/A"}")
                            DetailRow("File Format", "${node.data.fileFormat ?: "N/A"}")
                            DetailRow("Record Count", "${node.data.recordCount ?: 0}")
                            DetailRow("File Size", "${node.data.fileSizeInBytes ?: 0} bytes")
                            DetailRow("Sort Order ID", "${node.data.sorderOrderId ?: "N/A"}")
                            DetailRow("Split Offsets", node.data.splitOffsets.joinToString(", "))
                            DetailRow("Equality IDs", node.data.equalityIds?.joinToString(", ") ?: "N/A")
                            DetailRow("Partition", "N/A")
                            DetailRow("Column Sizes", kvLongs(node.data.columnSizes))
                            DetailRow("Value Counts", kvLongs(node.data.valueCounts))
                            DetailRow("Null Value Counts", kvLongs(node.data.nullValueCounts))
                            DetailRow("NaN Value Counts", kvLongs(node.data.nanValueCounts))
                            DetailRow("Lower Bounds", kvBytes(node.data.lowerBounds))
                            DetailRow("Upper Bounds", kvBytes(node.data.upperBounds))
                            DetailRow("Path", "${node.data.filePath ?: "N/A"}")
                        }
                    }

                    is GraphNode.RowNode -> {
                        DetailTable {
                            val typeStr = when (node.content) {
                                1 -> "Position Delete Row"
                                2 -> "Equality Delete Row"
                                else -> "Data Row"
                            }
                            DetailRow("Column", "Value ($typeStr)", isHeader = true)
                            node.data.entries
                                .sortedBy { it.key }
                                .forEach { (k, v) -> DetailRow(k, "$v") }
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
}
