@file:OptIn(com.github.avrokotlin.avro4k.ExperimentalAvro4kApi::class)

package service

import com.github.avrokotlin.avro4k.Avro
import com.github.avrokotlin.avro4k.decodeFromGenericData
import com.github.avrokotlin.avro4k.schema
import kotlinx.serialization.json.Json
import model.ManifestEntry
import model.ManifestListEntry
import model.TableMetadata
import org.apache.avro.file.DataFileReader
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import java.io.File
import java.net.URI

object IcebergReader {
    private val json = Json { ignoreUnknownKeys = true }

    // 1. Read Metadata JSON
    fun readTableMetadata(localPath: String): TableMetadata {
        val file = File(localPath)
        if (!file.exists()) throw IllegalArgumentException("File not found: $localPath")
        return json.decodeFromString(TableMetadata.serializer(), file.readText())
    }

    // 2. Read Manifest List (Avro)
    fun readManifestList(localPath: String): List<ManifestListEntry> {
        return readAvro(localPath)
    }

    // 3. Read Manifest File (Avro)
    fun readManifestFile(localPath: String): List<ManifestEntry> {
        return readAvro(localPath)
    }

    private inline fun <reified T : Any> readAvro(localPath: String): List<T> {
        val file = when {
            localPath.startsWith("file:") -> File(URI(localPath))
            else                          -> File(localPath)
        }

        // Parse file blocks into schema-aware GenericRecords
        return DataFileReader(file, GenericDatumReader<GenericRecord>()).use { reader ->
            val entries = mutableListOf<T>()
            val schema = Avro.schema<T>()

            reader.forEach { record ->
                try {
                    @Suppress("DEPRECATION") entries.add(Avro.decodeFromGenericData(schema, record))
                } catch (e: Exception) {
                    System.err.println("Error parsing Avro record")
                    System.err.println("File: $file")
                    System.err.println("Schema: $schema")
                    System.err.println("Record: $record")
                    throw e
                }
            }

            entries
        }
    }
}