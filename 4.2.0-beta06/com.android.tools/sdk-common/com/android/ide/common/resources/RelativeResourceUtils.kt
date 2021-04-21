@file:JvmName("RelativeResourceUtils")

package com.android.ide.common.resources

import java.io.File
import java.io.IOException
import java.lang.IllegalStateException

/**
 * Determines a resource file path relative to the source set containing the resource.
 *
 * The absolute path to the module source set is identified by the source set ordering of a module.
 * Format of the returned String is `<package name - source set module order>:<path to source set>`.
 */
fun produceRelativeSourceSetPath(
        resourceFile: File,
        packageName: String,
        moduleSourceSets: Collection<Collection<File>>
) : String {
    // Allows for relocatablity as various file system formats streamline to the same path format.
    val invariantFilePath = resourceFile.invariantSeparatorsPath
    for (sourceSet in moduleSourceSets) {
        for ((index, sourceSetFile) in sourceSet.withIndex()) {
            if (invariantFilePath.startsWith(sourceSetFile.invariantSeparatorsPath)) {
                val resIndex = sourceSetFile.invariantSeparatorsPath.length
                val relativePathToSourceSet = invariantFilePath.substring(resIndex)
                return "$packageName-$index:$relativePathToSourceSet"
            }
        }
    }

    // TODO(lukeedgar) Improve handling of these edge case source sets.
    //Handle cases where resources do not originate from source-sets.
    val dirs = invariantFilePath.split('/')
    if ("generated" in dirs && "pngs" in dirs){
        val variant = dirs[dirs.indexOf("pngs") + 1]
        val relativePathToSourceSet =
                invariantFilePath.substringAfterLast(variant)
        return "$packageName-generated-pngs-$variant:$relativePathToSourceSet"
    } else if ("incremental" in dirs && "merged.dir" in dirs) {
        val variant = dirs[dirs.indexOf("incremental") + 1]
        val relativePathToSourceSet =
                invariantFilePath.substringAfterLast("merged.dir")
        return "$packageName-incremental-$variant-merged.dir:$relativePathToSourceSet"
    }

    throw IllegalArgumentException(
            "Unable to locate resourceFile (${resourceFile.absolutePath}) in source-sets.")
}

/**
 * Converts a source set identified relative resource path to an absolute path.
 *
 * The source set identifier before the ':' separator is replaced with the absolute source set
 * path and then concatenated with the path after the ':' separator.
 */
fun relativeResourcePathToAbsolutePath(
        relativePath: String,
        sourceSetPathMap: Map<String, String>): String {
    if (sourceSetPathMap.none()) {
        throw IllegalStateException(
                """Unable to get absolute path from $relativePath
                   because no relative root paths are present.""")
    }
    val separatorIndex = relativePath.indexOf(':')
    if (separatorIndex == -1) {
        throw IllegalArgumentException(
                """Source set identifier and relative path must be separated by a ':'character.
                   Relative path: $relativePath""")
    }
    val sourceSetPrefix = relativePath.substring(0, separatorIndex)
    val resourcePathFromSourceSet = relativePath.substring(separatorIndex + 1, relativePath.length)
    val absolutePath = sourceSetPathMap[sourceSetPrefix]
            ?: throw NoSuchElementException(
                    """Unable to get absolute path from $relativePath
                       because $sourceSetPrefix is not key in sourceSetPathMap.""")

    return "$absolutePath$resourcePathFromSourceSet"
}

/**
 * Parses identifier and file path into a map from a file
 * in the format '<Int source set id> <String file path>`.
 */
fun readFromSourceSetPathsFile(artifactFile: File) : Map<String, String> {
    if (!artifactFile.exists() || !artifactFile.isFile) {
        throw IOException("$artifactFile does not exist or is not a file.")
    }
    return artifactFile.bufferedReader().lineSequence().associate {
        it.substringBefore(" ") to it.substringAfter(" ")
    }
}
