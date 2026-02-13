package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import model.GraphModel
import model.GraphNode
import model.ManifestEntry
import service.GraphLayoutService
import service.IcebergReader
import java.io.File
import javax.swing.JFileChooser

@Composable
fun App() {
    var graphModel by remember { mutableStateOf<GraphModel?>(null) }
    var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
    var tableData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Logic to load table
    fun loadTable(path: String) {
        try {
            // 1. Locate Metadata File (naive assumption: latest vX.metadata.json)
            val metaDir = File(path, "metadata")
            val metaFile = metaDir
                               .listFiles()
                               ?.filter { it.name.endsWith(".metadata.json") }
                               ?.maxByOrNull { it.name }
                           ?: throw Exception("No metadata file found in $path/metadata")

            // 2. Read Metadata
            val metadata = IcebergReader.readTableMetadata(metaFile.absolutePath)

            // 3. For visual demo, load manifest lists for snapshots
            val loadedManifestLists = metadata.snapshots.associate {
                it.snapshotId.toString() to IcebergReader.readManifestList(
                    path + "/metadata/" + it.manifestList.orEmpty().substringAfterLast(
                        "/"
                    )
                )
            }

            // 4. Load Manifests (lazy load logic simulation)
            val loadedFiles = mutableMapOf<String, List<ManifestEntry>>()
            loadedManifestLists.values.flatten().forEach { ml ->
                try {
                    val fPath = path + "/metadata/" + ml.manifestPath.orEmpty().substringAfterLast("/")
                    loadedFiles[ml.manifestPath.orEmpty()] = IcebergReader.readManifestFile(fPath)
                } catch (e: Exception) {
                    println("Could not load manifest ${ml.manifestPath}: ${e.message}")
                }
            }

            // 5. Layout Graph
            graphModel =
                GraphLayoutService.layoutGraph(metadata.snapshots, loadedManifestLists, loadedFiles)
            errorMsg = null
        } catch (e: Exception) {
            errorMsg = e.message
            e.printStackTrace()
        }
    }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {
            // Main Canvas
            Box(Modifier.weight(0.75f).fillMaxHeight()) {
                if (graphModel != null) {
                    GraphCanvas(graphModel!!) { node ->
                        selectedNode = node
                        // If file node, try to inspect data
                        if (node is GraphNode.FileNode) {
                            // Resolve relative path for DuckDB
                            val absPath =
                                node.data.filePath // This needs proper path resolution in real app
                            // For this demo, we assume the user picks the warehouse root and files are relative
                            // Note: Real Iceberg paths are absolute (s3:// or file:/). We need to handle that.
                            // We skip logic here for brevity.
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            chooser.currentDirectory = File(System.getProperty("user.home") + "/code/spark-kit/iceberg/data/db")
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                loadTable(chooser.selectedFile.absolutePath)
                            }
                        }, modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("Open Iceberg Table Directory")
                    }
                }

                if (errorMsg != null) {
                    Text(
                        "Error: $errorMsg",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Right Inspector Panel
            Column(Modifier.weight(0.25f).fillMaxHeight().padding(8.dp)) {
                Text("Inspector", style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()

                selectedNode?.let { node ->
                    when (node) {
                        is GraphNode.SnapshotNode -> {
                            Text("Snapshot ID: ${node.data.snapshotId}")
                            Text("Timestamp: ${node.data.timestampMs}")
                            Text("Operation: ${node.data.summary["operation"]}")
                        }

                        is GraphNode.ManifestNode -> {
                            Text("Manifest Entry")
                            Text("Snapshot: ${node.data.addedSnapshotId}")
                            Text("Content: ${if (node.data.content == 1) "Delete" else "Data"}")
                        }

                        is GraphNode.FileNode     -> {
                            Text("File Path: ${node.data.filePath}")
                            Text("Rows: ${node.data.recordCount}")
                            Button(onClick = {
                                // Trigger DuckDB (Simulation)
                                // In real app, resolve path correctly
                                // tableData = DuckDbService.queryParquet(node.data.filePath)
                            }) {
                                Text("Preview Data")
                            }
                        }

                        else                      -> Text("Unknown Node")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Data Grid (Simple)
                if (tableData.isNotEmpty()) {
                    LazyColumn {
                        items(tableData) { row ->
                            Card(Modifier.padding(4.dp).fillMaxWidth()) {
                                Column(Modifier.padding(4.dp)) {
                                    row.forEach { (k, v) ->
                                        Text("$k: $v", fontSize = 10.sp)
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