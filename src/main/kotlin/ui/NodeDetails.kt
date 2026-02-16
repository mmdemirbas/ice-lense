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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import model.MetadataLogEntry
import model.SnapshotLogEntry

private fun nodeTitle(node: GraphNode): String = when (node) {
    is GraphNode.TableNode -> "TABLE ${node.summary.tableName}"
    is GraphNode.MetadataNode -> "METADATA ${node.simpleId}"
    is GraphNode.SnapshotNode -> "SNAPSHOT ${node.simpleId}"
    is GraphNode.ManifestNode -> "MANIFEST ${node.simpleId}: ${if (node.data.content == 1) "DELETE" else "DATA"}"
    is GraphNode.FileNode -> "FILE ${node.simpleId}: ${when (node.data.content ?: 0) {
        1 -> "POS DELETE"
        2 -> "EQ DELETE"
        else -> "DATA"
    }}"
    is GraphNode.RowNode -> when (node.content) {
        1 -> "POS DELETE ROW"
        2 -> "EQ DELETE ROW"
        else -> "DATA ROW"
    }
    is GraphNode.ErrorNode -> "ERROR ${node.title}"
}

private fun normalizeText(value: String?): String {
    if (value == null) return "N/A"
    return value
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun kvLongs(values: List<KeyValuePairLong>?): String =
    if (values.isNullOrEmpty()) "[]" else values.joinToString(", ") { "${it.key}:${it.value}" }

private fun ByteArray.toHexShort(maxBytes: Int = 24): String {
    val head = take(maxBytes).joinToString("") { byte -> "%02x".format(byte) }
    return if (size > maxBytes) "$head..." else head
}

private fun kvBytes(values: List<KeyValuePairBytes>?): String =
    if (values.isNullOrEmpty()) "[]" else values.joinToString(", ") { "${it.key}:${it.value.toHexShort()}" }

private fun longs(values: List<Long>?): String =
    if (values.isNullOrEmpty()) "[]" else values.joinToString(", ")

private fun currentSnapshotLabel(currentSnapshotId: Long?): String = when (currentSnapshotId) {
    null -> "None"
    -1L -> "None (-1)"
    else -> currentSnapshotId.toString()
}

private fun timelineContentRank(content: Int?): Int = when (content ?: 0) {
    2 -> 0
    0 -> 1
    1 -> 2
    else -> 3
}

private fun manifestContentRank(content: Int?): Int = when (content ?: 0) {
    1 -> 0
    0 -> 1
    else -> 2
}

private fun jsonToAnnotatedString(json: String): AnnotatedString {
    val stringStyle = SpanStyle(color = Color(0xFF2E7D32))
    val numberStyle = SpanStyle(color = Color(0xFF1565C0))
    val keywordStyle = SpanStyle(color = Color(0xFF8E24AA), fontWeight = FontWeight.SemiBold)
    val punctStyle = SpanStyle(color = Color(0xFF616161))
    val builder = AnnotatedString.Builder()

    var i = 0
    while (i < json.length) {
        val ch = json[i]
        when {
            ch == '"' -> {
                var j = i + 1
                var escaped = false
                while (j < json.length) {
                    val c = json[j]
                    if (c == '"' && !escaped) break
                    escaped = c == '\\' && !escaped
                    if (c != '\\') escaped = false
                    j++
                }
                val end = (j + 1).coerceAtMost(json.length)
                builder.pushStyle(stringStyle)
                builder.append(json.substring(i, end))
                builder.pop()
                i = end
            }
            ch == '-' || ch.isDigit() -> {
                var j = i + 1
                while (j < json.length && (json[j].isDigit() || json[j] in listOf('.', 'e', 'E', '+', '-'))) j++
                builder.pushStyle(numberStyle)
                builder.append(json.substring(i, j))
                builder.pop()
                i = j
            }
            json.startsWith("true", i) || json.startsWith("false", i) || json.startsWith("null", i) -> {
                val token = when {
                    json.startsWith("true", i) -> "true"
                    json.startsWith("false", i) -> "false"
                    else -> "null"
                }
                builder.pushStyle(keywordStyle)
                builder.append(token)
                builder.pop()
                i += token.length
            }
            ch in listOf('{', '}', '[', ']', ':', ',') -> {
                builder.pushStyle(punctStyle)
                builder.append(ch)
                builder.pop()
                i++
            }
            else -> {
                builder.append(ch)
                i++
            }
        }
    }
    return builder.toAnnotatedString()
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

private fun renderSnapshotLogRows(items: List<SnapshotLogEntry>): List<List<String>> =
    items.sortedBy { it.timestampMs ?: Long.MAX_VALUE }.map { entry ->
        listOf(
            formatTimestamp(entry.timestampMs),
            "${entry.snapshotId ?: "N/A"}"
        )
    }

private fun renderMetadataLogRows(items: List<MetadataLogEntry>): List<List<String>> =
    items.sortedBy { it.timestampMs ?: Long.MAX_VALUE }.map { entry ->
        listOf(
            formatTimestamp(entry.timestampMs),
            normalizeText(entry.metadataFile)
        )
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
                    is GraphNode.TableNode -> {
                        val summary = node.summary
                        val metadataChildren = children
                            .filterIsInstance<GraphNode.MetadataNode>()
                            .sortedBy { it.simpleId }
                        val metadataNodeByFileName = metadataChildren.associateBy { it.fileName }
                        val versionFileNames = summary.metadataVersions.map { it.fileName }.toSet()
                        val mergedMetadataRows = buildList {
                            summary.metadataVersions.forEachIndexed { index, version ->
                                val metadataNode = metadataNodeByFileName[version.fileName]
                                add(
                                    listOf(
                                        "${index + 1}",
                                        metadataNode?.id ?: "N/A",
                                        version.fileName,
                                        "${version.version ?: "N/A"}",
                                        formatTimestamp(version.fileLastModifiedMs),
                                        formatTimestamp(version.metadataLastUpdatedMs),
                                        "${version.snapshotCount}",
                                        currentSnapshotLabel(version.currentSnapshotId)
                                    )
                                )
                            }
                            metadataChildren
                                .filter { it.fileName !in versionFileNames }
                                .forEachIndexed { index, metadataNode ->
                                    add(
                                        listOf(
                                            "${summary.metadataVersions.size + index + 1}",
                                            metadataNode.id,
                                            metadataNode.fileName,
                                            "N/A",
                                            "N/A",
                                            formatTimestamp(metadataNode.data.lastUpdatedMs),
                                            "${metadataNode.data.snapshots.size}",
                                            currentSnapshotLabel(metadataNode.data.currentSnapshotId)
                                        )
                                    )
                                }
                        }

                        DetailTable {
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Name", summary.tableName)
                            DetailRow("Table Path", summary.tablePath)
                            DetailRow("Location", summary.location ?: "N/A")
                            DetailRow("Table UUID", summary.tableUuid ?: "N/A")
                            DetailRow("Format Version", "${summary.formatVersion ?: "N/A"}")
                            DetailRow("Current Snapshot ID", currentSnapshotLabel(summary.currentSnapshotId))
                            DetailRow("Current Metadata Version", "${summary.currentMetadataVersion ?: "N/A"}")
                            DetailRow("version-hint.text", summary.versionHintText.ifBlank { "N/A" })
                            DetailRow("Table Created (Inferred)", formatTimestamp(summary.tableCreationMs))
                            DetailRow("Table Last Updated (Inferred)", formatTimestamp(summary.tableLastUpdateMs))
                            DetailRow("Last Updated (UI)", formatTimestamp(summary.lastUpdatedMs))
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Metadata Files")
                        DetailTable {
                            DetailRow("Metric", "Value", isHeader = true)
                            DetailRow("File Count", "${summary.metadataFileCount}")
                            DetailRow("Known", "${summary.metadataFileTimes.knownCount}")
                            DetailRow("Missing", "${summary.metadataFileTimes.missingCount}")
                            DetailRow("Oldest", formatTimestamp(summary.metadataFileTimes.oldestMs))
                            DetailRow("Latest", formatTimestamp(summary.metadataFileTimes.newestMs))
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Snapshot Manifest Lists")
                        DetailTable {
                            DetailRow("Metric", "Value", isHeader = true)
                            DetailRow("Unique Snapshots", "${summary.snapshotCount}")
                            DetailRow("File Count", "${summary.snapshotManifestListFileCount}")
                            DetailRow("Known", "${summary.snapshotManifestListFileTimes.knownCount}")
                            DetailRow("Missing", "${summary.snapshotManifestListFileTimes.missingCount}")
                            DetailRow("Oldest", formatTimestamp(summary.snapshotManifestListFileTimes.oldestMs))
                            DetailRow("Latest", formatTimestamp(summary.snapshotManifestListFileTimes.newestMs))
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Manifest Files")
                        DetailTable {
                            DetailRow("Metric", "Value", isHeader = true)
                            DetailRow("File Count", "${summary.manifestCount}")
                            DetailRow("Known", "${summary.manifestFileTimes.knownCount}")
                            DetailRow("Missing", "${summary.manifestFileTimes.missingCount}")
                            DetailRow("Breakdown", "${summary.dataManifestCount} data / ${summary.deleteManifestCount} delete")
                            DetailRow("Oldest", formatTimestamp(summary.manifestFileTimes.oldestMs))
                            DetailRow("Latest", formatTimestamp(summary.manifestFileTimes.newestMs))
                            DetailRow("Manifest Entries", "${summary.manifestEntryCount}")
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Data Files")
                        DetailTable {
                            DetailRow("Metric", "Value", isHeader = true)
                            DetailRow("File Count", "${summary.uniqueDataFileCount}")
                            DetailRow("Known", "${summary.dataFileTimes.knownCount}")
                            DetailRow("Missing", "${summary.dataFileTimes.missingCount}")
                            DetailRow("Breakdown", "${summary.dataFileCount} data / ${summary.posDeleteFileCount} pos-del / ${summary.eqDeleteFileCount} eq-del")
                            DetailRow("Oldest", formatTimestamp(summary.dataFileTimes.oldestMs))
                            DetailRow("Latest", formatTimestamp(summary.dataFileTimes.newestMs))
                            DetailRow("Total Records", "${summary.totalRecordCount}")
                        }

                        if (mergedMetadataRows.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Metadata Nodes & Timeline")
                            WideTable(
                                headers = listOf(
                                    "Order",
                                    "Node",
                                    "File",
                                    "Version",
                                    "File Updated",
                                    "Metadata Last Updated",
                                    "Snapshots",
                                    "Current Snapshot ID"
                                ),
                                rows = mergedMetadataRows
                            )
                        }
                    }

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
                            DetailRow("Current Schema ID", "${node.data.currentSchemaId ?: "N/A"}")
                            DetailRow("Default Spec ID", "${node.data.defaultSpecId ?: "N/A"}")
                            DetailRow("Last Partition ID", "${node.data.lastPartitionId ?: "N/A"}")
                            DetailRow("Default Sort Order ID", "${node.data.defaultSortOrderId ?: "N/A"}")
                            DetailRow("Current Snapshot ID", currentSnapshotLabel(node.data.currentSnapshotId))
                            DetailRow("Total Snapshots", "${node.data.snapshots.size}")
                            DetailRow("Total Schemas", "${node.data.schemas.size}")
                            DetailRow("Total Partition Specs", "${node.data.partitionSpecs.size}")
                            DetailRow("Total Sort Orders", "${node.data.sortOrders.size}")
                            DetailRow("Total Refs", "${node.data.refs.size}")
                            DetailRow("Statistics Entries", "${node.data.statistics.size}")
                            DetailRow("Partition Statistics Entries", "${node.data.partitionStatistics.size}")
                            DetailRow("Snapshot Log Entries", "${node.data.snapshotLog.size}")
                            DetailRow("Metadata Log Entries", "${node.data.metadataLog.size}")
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

                        if (node.data.partitionSpecs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Partition Specs")
                            node.data.partitionSpecs
                                .sortedBy { it.specId ?: Int.MAX_VALUE }
                                .forEach { spec ->
                                    Text(
                                        "Spec ${spec.specId ?: "Unknown"}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    WideTable(
                                        headers = listOf("Source ID", "Field ID", "Name", "Transform"),
                                        rows = if (spec.fields.isEmpty()) listOf(listOf("N/A", "N/A", "N/A", "N/A")) else spec.fields.map { field ->
                                            listOf(
                                                "${field.sourceId ?: "N/A"}",
                                                "${field.fieldId ?: "N/A"}",
                                                field.name ?: "N/A",
                                                normalizeText(field.transform?.toString())
                                            )
                                        }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                        }

                        if (node.data.sortOrders.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Sort Orders")
                            node.data.sortOrders
                                .sortedBy { it.orderId ?: Int.MAX_VALUE }
                                .forEach { order ->
                                    Text(
                                        "Order ${order.orderId ?: "Unknown"}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    WideTable(
                                        headers = listOf("Source ID", "Transform", "Direction", "Null Order"),
                                        rows = if (order.fields.isEmpty()) listOf(listOf("N/A", "N/A", "N/A", "N/A")) else order.fields.map { field ->
                                            listOf(
                                                "${field.sourceId ?: "N/A"}",
                                                normalizeText(field.transform?.toString()),
                                                field.direction ?: "N/A",
                                                field.nullOrder ?: "N/A"
                                            )
                                        }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                        }

                        if (node.data.refs.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Refs")
                            WideTable(
                                headers = listOf("Name", "Type", "Snapshot ID", "Max Ref Age MS", "Max Snapshot Age MS", "Min Snapshots To Keep"),
                                rows = node.data.refs.toSortedMap().map { (name, ref) ->
                                    listOf(
                                        name,
                                        ref.type ?: "N/A",
                                        "${ref.snapshotId ?: "N/A"}",
                                        "${ref.maxRefAgeMs ?: "N/A"}",
                                        "${ref.maxSnapshotAgeMs ?: "N/A"}",
                                        "${ref.minSnapshotsToKeep ?: "N/A"}"
                                    )
                                }
                            )
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

                        if (node.data.snapshotLog.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Snapshot Log")
                            DetailTable {
                                DetailRow("Timestamp", "Snapshot ID", isHeader = true)
                                renderSnapshotLogRows(node.data.snapshotLog).forEach { row ->
                                    DetailRow(row.getOrElse(0) { "N/A" }, row.getOrElse(1) { "N/A" })
                                }
                            }
                        }

                        if (node.data.metadataLog.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Metadata Log")
                            DetailTable {
                                DetailRow("Timestamp", "Metadata File", isHeader = true)
                                renderMetadataLogRows(node.data.metadataLog).forEach { row ->
                                    DetailRow(row.getOrElse(0) { "N/A" }, row.getOrElse(1) { "N/A" })
                                }
                            }
                        }

                        if (node.data.statistics.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Statistics")
                            WideTable(
                                headers = listOf("Index", "Value"),
                                rows = node.data.statistics.mapIndexed { index, stat ->
                                    listOf("${index + 1}", normalizeText(stat.toString()))
                                }
                            )
                        }

                        if (node.data.partitionStatistics.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            SectionTitle("Partition Statistics")
                            WideTable(
                                headers = listOf("Index", "Value"),
                                rows = node.data.partitionStatistics.mapIndexed { index, stat ->
                                    listOf("${index + 1}", normalizeText(stat.toString()))
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        SectionTitle("Raw metadata.json")
                        val rawJson = node.rawJson
                        if (!rawJson.isNullOrBlank()) {
                            val rawJsonScrollX = rememberScrollState()
                            val rawJsonScrollY = rememberScrollState()
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 220.dp, max = 420.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.LightGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                    .background(Color(0xFFFAFAFA))
                                    .horizontalScroll(rawJsonScrollX)
                                    .verticalScroll(rawJsonScrollY)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = jsonToAnnotatedString(rawJson),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            DetailTable {
                                DetailRow("Raw JSON", "N/A", isHeader = true)
                            }
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
                                    { manifestContentRank(it.data.content) },
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
                                    { it.data.dataSequenceNumber ?: it.entry.sequenceNumber ?: Long.MAX_VALUE },
                                    { it.entry.fileSequenceNumber ?: Long.MAX_VALUE },
                                    { timelineContentRank(it.data.content) },
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
                                        longs(data.splitOffsets),
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
                            DetailRow("Split Offsets", longs(node.data.splitOffsets))
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

                    is GraphNode.ErrorNode -> {
                        DetailTable {
                            DetailRow("Property", "Value", isHeader = true)
                            DetailRow("Title", node.title)
                            DetailRow("Stage", node.stage)
                            DetailRow("Path", node.path)
                            DetailRow("Message", node.message)
                        }
                        Spacer(Modifier.height(12.dp))
                        SectionTitle("Stack Trace")
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .border(1.dp, Color.LightGray, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .background(Color(0xFFF7F7F7))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = node.stackTrace ?: "N/A",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
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
