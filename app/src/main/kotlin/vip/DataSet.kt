package vip

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import java.io.File

/**
 * Instance data as contributed: one Turtle file per entity and per case, all loaded into one [merged] model.
 * Each file is also kept on its own so checks can say which file a statement came from.
 */
class DataSet(val models: Map<File, Model>, val root: File? = null) {
    val files: List<File> get() = models.keys.toList()

    /** The file's path relative to the data root, e.g. `cases/milenijum-tim.ttl`. */
    fun relativePath(file: File): String = root?.let { file.relativeTo(it).invariantSeparatorsPath } ?: file.name

    /** The top-level folder a file sits in: `entities`, `cases`, or "" for a file at the root. */
    fun folderOf(file: File): String = relativePath(file).substringBefore('/', "")
    val merged: Model by lazy {
        ModelFactory.createDefaultModel().also { all -> models.values.forEach { all.add(it) } }
    }
}

/** Loads a directory tree of Turtle files (in path order), or a single file, as a [DataSet]. */
fun loadDataSet(path: File): DataSet {
    val files = if (path.isDirectory) {
        path.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .filter { it.isFile && it.name.endsWith(".ttl") && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
            .sortedBy { it.relativeTo(path).invariantSeparatorsPath }.toList()
    } else listOf(path)
    require(files.isNotEmpty()) { "No Turtle (.ttl) files found in ${path.path}" }
    return DataSet(files.associateWith { loadTurtle(it) }, root = if (path.isDirectory) path else null)
}

private fun loadTurtle(file: File): Model = try {
    RDFDataMgr.loadModel(file.path)
} catch (e: Exception) {
    throw IllegalArgumentException("${file.path}: ${e.message}", e)
}
