package model

import service.IcebergReader
import service.ParquetReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.streams.asSequence

fun UnifiedWarehouseModel(warehousePath: Path): UnifiedWarehouseModel {
    return UnifiedWarehouseModel(
        path = warehousePath,
        tables = Files
            .list(warehousePath)
            .filter { Files.isDirectory(it) }
            .toList()
            .associate { tablePath ->
                val table = UnifiedTableModel(tablePath)
                table.name to table
            },
    )
}

fun UnifiedTableModel(tablePath: Path): UnifiedTableModel {
    val metadataDir = tablePath.resolve("metadata")
    val parsedMetadata = Files
        .list(metadataDir)
        .asSequence()
        .filter { Files.isRegularFile(it) }
        .filter { it.fileName.toString().endsWith(".metadata.json") }
        .associateWith { IcebergReader.readTableMetadata(it.toString()) }

    // Parse snapshots once to avoid duplicate parsing of the same snapshots
    val parsedSnapshots = parsedMetadata.values
        .flatMap { it.snapshots }
        .distinctBy { it.snapshotId }
        .associate { snapshot ->
            snapshot.snapshotId to UnifiedSnapshot(
                resolveForceRelative(metadataDir, snapshot.manifestList),
                snapshot,
            )
        }

    return UnifiedTableModel(
        path = tablePath,
        name = tablePath.fileName.toString(),
        versionHint = metadataDir.resolve("version-hint.text").readText().trim(),
        metadatas = parsedMetadata.map { (path, metadata) ->
            UnifiedMetadata(
                path = path,
                metadata = metadata,
                snapshots = metadata.snapshots
                    .map { parsedSnapshots[it.snapshotId]!! }
                    .sortedBy { it.metadata.timestampMs },
            )
        }.sortedBy {
            it.path.fileName.toString().removePrefix("v").removeSuffix(".metadata.json").toInt()
        },
    )
}

fun resolveForceRelative(start: Path, pathToTakeOnlyLastPart: String?): Path {
    // Get the last part of the "pathToTakeOnlyLastPart" string, and resolve it relative to the "tablePath"
    return start.resolve(pathToTakeOnlyLastPart!!.removeSuffix("/").substringAfterLast("/"))
}

fun UnifiedSnapshot(snapshotPath: Path, snapshot: Snapshot): UnifiedSnapshot {
    val manifestList = IcebergReader.readManifestList(snapshotPath.toString())
    return UnifiedSnapshot(
        path = snapshotPath,
        metadata = snapshot,
        manifestLists = manifestList.map { manifest ->
            val metadataDir = snapshotPath.parent
            UnifiedManifest(
                resolveForceRelative(metadataDir, manifest.manifestPath),
                manifest,
            )
        },
    )
}

fun UnifiedManifest(manifestPath: Path, manifest: ManifestListEntry): UnifiedManifest {
    val dataFiles = IcebergReader.readManifestFile(manifestPath.toString())
    return UnifiedManifest(
        path = manifestPath,
        metadata = manifest,
        manifests = dataFiles.map { dataFile ->
            val metadataDirPrefix = manifest.manifestPath.orEmpty().substringBeforeLast('/')
            val tableDirPrefix = metadataDirPrefix.substringBeforeLast('/')
            val dataFilePathInFile = dataFile.dataFile?.filePath.orEmpty()
            val dataFilePathRelative =
                dataFilePathInFile.removePrefix(tableDirPrefix).removePrefix("/")
            val dataFilePathResolved = manifestPath.parent.parent.resolve(dataFilePathRelative)
            UnifiedDataFile(path = dataFilePathResolved, metadata = dataFile)
        },
    )
}

data class UnifiedWarehouseModel(
    val path: Path,
    val tables: Map<String, UnifiedTableModel>,
)

data class UnifiedTableModel(
    val path: Path,
    val name: String,
    val versionHint: String,
    val metadatas: List<UnifiedMetadata>,
)

data class UnifiedMetadata(
    val path: Path,
    val metadata: TableMetadata,
    val snapshots: List<UnifiedSnapshot>,
)

data class UnifiedSnapshot(
    val path: Path,
    val metadata: Snapshot,
    val manifestLists: List<UnifiedManifest>,
)

data class UnifiedManifest(
    val path: Path,
    val metadata: ManifestListEntry,
    val manifests: List<UnifiedDataFile>,
)

data class UnifiedDataFile(
    val path: Path,
    val metadata: ManifestEntry,
    private val rowsLoader: () -> List<UnifiedRow> = {
        ParquetReader.queryParquet(path.toString()).map { UnifiedRow(it) }
    },
) {
    val rows: List<UnifiedRow> by lazy { rowsLoader() }
}

data class UnifiedRow(
    val cells: Map<String, Any>,
)
