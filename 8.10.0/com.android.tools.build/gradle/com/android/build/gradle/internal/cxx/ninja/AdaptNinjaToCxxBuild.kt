

package com.android.build.gradle.internal.cxx.ninja

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.build.gradle.internal.cxx.collections.ancestors
import com.android.build.gradle.internal.cxx.collections.breadthFirst
import com.android.utils.cxx.io.filenameStartsWithIgnoreCase
import com.android.utils.cxx.io.hasExtensionIgnoreCase
import com.android.utils.cxx.io.removeExtensionIfPresent
import com.android.utils.cxx.ninja.streamNinjaBuildCommands
import com.android.utils.cxx.ninja.writeCompileCommandsJsonBin
import com.android.utils.cxx.ninja.NinjaBuildUnexpandedCommand
import com.android.build.gradle.internal.cxx.json.NativeBuildConfigValueMini
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.string.StringTable
import com.google.common.annotations.VisibleForTesting
import java.io.File

/**
 * Given a user-generated custom build.ninja, produce the build information needed to integrate
 * with Android Gradle Plugin's build system.
 *
 * Emits a file named compile_commands.json.bin that has information about source files and flags.
 *
 * @return [NativeBuildConfigValueMini] which has information about libraries that will be built.
 */
fun adaptNinjaToCxxBuild(
    ninjaBuildFile: File,
    abi: String,
    cxxBuildFolder: File,
    createNinjaCommand: (List<String>) -> List<String>,
    compileCommandsJsonBin: File,
    buildFileFilter: (File) -> Boolean = { _ -> true },
    platform: Int = CURRENT_PLATFORM
) : NativeBuildConfigValueMini {
    val adapter = NinjaToCxxBuildAdapter(
        abi,
        cxxBuildFolder,
        createNinjaCommand,
        buildFileFilter
    )

    // Create the graph of build outputs and inputs
    val graph = adapter.createBuildGraph(ninjaBuildFile)

    // Generate compile_commands.json.bin
    writeCompileCommandsJsonBin(
        ninjaBuildFile,
        cxxBuildFolder,
        compileCommandsJsonBin,
        platform
    )

    // Create the libraries configuration
    return adapter.createAndroidGradleBuildMini(graph)
}

/**
 * Helper class holds state for Ninja adaption including the string table [strings] and
 * [commandLineBuffer] which is a shared space for parsing command-lines.
 */
private class NinjaToCxxBuildAdapter(
    private val targetAbi : String,
    private val cxxBuildFolder : File,
    private val createNinjaCommand : (List<String>) -> List<String>,
    private val buildFileFilter : (File) -> Boolean
) {
    // Maintain string to int mapping
    val strings = StringTable()
    // Shared buffer for parsing command-lines. These are indices into the command-line being
    // parsed. See [TokenizedCommandLine]
    var commandLineBuffer = intArrayOf()

    /**
     * Make a single pass over build.ninja building the graph of inputs to outputs and gathering
     * the IDs of various interesting targets
     */
    fun createBuildGraph(ninjaBuildFile : File) : BuildGraph {
        // Keys are outputs, values are inputs
        val edges = mutableMapOf<Int, IntArray>()
        // Passthroughs are aliases to targets build targets. So for example, libfoo.so.passthrough
        // could be a target that invokes MSBuild.exe to produce libfoo.so rather than using
        // clang.exe call directly by Ninja.
        val passthroughs = mutableSetOf<Int>()
        // Build files like Teapots.sln
        val buildFiles = mutableSetOf<Int>()
        // Executables, like .so, that may be packaged into APK
        val packageables = mutableSetOf<Int>()
        // Static libraries (.a)
        val archives = mutableSetOf<Int>()

        streamNinjaBuildCommands(ninjaBuildFile) {
            val outputs = explicitOutputs + implicitOutputs
            val inputs = explicitInputs + implicitInputs
            val outIDs = idSetOf(outputs)
            val inIDs = idSetOf(inputs)
            for(output in outIDs) {
                edges[output] = inIDs.toIntArray()
            }

            when {
                // A generator target is one that is used to rebuild build.ninja itself. It is
                // special to Ninja. The inputs to 'build.ninja' should be the build files like
                // MyApp.vcxproj or Teapots.sln. Ninja uses "generator=1" to designate these.
                generator == "1" -> buildFiles += inIDs
                outputHasExtension("so", "") -> {
                    // In a statement like:
                    //   build lib.so : CLANG source.o /path/to/ndk/libc++_shared.so
                    // outIDs is lib.so
                    // and inIDs is [source.o, /path/to/ndk/libc++_shared.so]
                    // The inIDs need to be filtered down to just packagable files
                    packageables += outIDs
                    packageables += inIDs.filter(::isPackageable)
                }
                outputHasExtension("a") -> archives += outIDs
                outputHasExtension("passthrough") -> passthroughs += outIDs
            }
        }

        return BuildGraph(
            edges = edges,
            packageableIds = packageables.toSortedSet(),
            buildFileIds = buildFiles.toSortedSet(),
            passthroughs = passthroughs.toSortedSet(),
            archiveIds = archives.toSortedSet()
        )
    }

    /**
     * Creates [NativeBuildConfigValueMini] which has information about libraries.
     */
    fun createAndroidGradleBuildMini(graph : BuildGraph) = with(graph) {
        // Build a map where:
        // - Key is a build output like .so or .a
        // - Value is the list of passthrough targets that should be built to create that output
        // If a target maps to more than one passthrough, it is discarded.
        val passthroughMap = passthroughs
            .flatMap { passthrough ->
                edges.breadthFirst(removeExtension(passthrough, "passthrough"))
                    .map { child -> child to passthrough} }
            .groupBy( { it.first }, { it.second })
            // Remove targets covered by more than one passthrough target
            // See test `target with multiple passthroughs`
            .filter { it.value.size == 1 }
            .map { it.key to it.value.single() }
            .toMap()

        NativeBuildConfigValueMini().apply {
            cleanCommandsComponents = listOf(ninjaCommand("-t", "clean"))
            buildFiles = buildFileIds
                .map { resolveFile(it) }
                .filter { buildFile -> buildFileFilter(buildFile) }
            libraries = edges.ancestors(packageableIds + archiveIds)
                .filter { (_, libraries) ->
                    // We don't want targets that aggregate multiple other targets
                    // See test `'all' target may have another name'`
                    libraries.size == 1
                }.filter { (ancestor, _) ->
                    // Don't allow targets with ".passthrough" extension
                    !passthroughs.contains(ancestor)}
                .map { (ancestor, library) ->
                    assignTargetName(stringOf(ancestor)) to (ancestor to library.first())
                }
                // "all" target is eliminated after names are assigned because, for example,
                // CMake creates "subfolder/all" targets
                // See test `'all' target may be in a subfolder'`
                .filter { (name, _) -> name != "all" }
                .groupBy({ (name, _) -> name }, { it.second })
                .map { (name, targets) ->
                    val (targetId, outputId) = targets.singleOrNull()
                        // If there are multiple then choose the one that has a passthrough
                        ?: targets.singleOrNull { passthroughMap.containsKey(it.first) }
                        // Otherwise, choose the one that is packageable (.so)
                        ?: targets.singleOrNull { packageableIds.contains(it.first) }
                        // Otherwise, choose the one that is an archive (.a)
                        ?: targets.singleOrNull { archiveIds.contains(it.first) }
                        // Otherwise, take the last one.
                        ?: targets.last()
                    createLibrary(
                        name = name,
                        targetId = targetId,
                        outputId = outputId,
                        passthroughId = passthroughMap[outputId]
                    )
                }
                .associateBy { it.artifactName }
        }
    }

    /**
     * Create a [NativeLibraryValueMini].
     */
    private fun BuildGraph.createLibrary(
        name : String,
        targetId : Int,
        outputId : Int,
        passthroughId : Int?
    ) = NativeLibraryValueMini().apply {
        artifactName = name
        abi = targetAbi
        output = resolveFile(outputId)
        buildCommandComponents = ninjaCommand(passthroughId ?: targetId)
        runtimeFiles = edges.breadthFirst(targetId)
            .filter { it != targetId && packageableIds.contains(it) }
            .map { resolveFile(it) }
            .toList()
    }

    /**
     * Return true if the single output in [NinjaBuildUnexpandedCommand] has the given [extension].
     */
    private fun NinjaBuildUnexpandedCommand.outputHasExtension(vararg extensions : String) =
        ruleName != "phony" &&
                explicitOutputs.size == 1 &&
                // This check helps get rid of utility targets like 'clean' and 'help'.
                //   See test `utility targets are discarded'`
                // This is a safe check because we're only interested in targets that eventually
                // lead to source files. This isn't possible if there are no inputs.
                explicitInputs.isNotEmpty() &&
                explicitOutputs[0].hasExtensionIgnoreCase(*extensions)
    private fun idOf(value : String) = strings.encode(value)
    private fun idSetOf(values : List<String>) = values.map(::idOf).toSortedSet()
    private fun stringOf(id : Int) = strings.decode(id)
    private fun resolveFile(id : Int)  = resolveFile(stringOf(id))
    private fun resolveFile(path : String)  = cxxBuildFolder.resolve(path).normalize()
    private fun removeExtension(value : Int, ext : String) =
        idOf(stringOf(value).removeExtensionIfPresent(ext))
    private fun ninjaCommand(vararg args : String) = createNinjaCommand(args.toList())
    private fun ninjaCommand(vararg args : Int) = createNinjaCommand(args.map(::stringOf).toList())
    private fun isPackageable(id : Int) = isPackageable(stringOf(id))
}

/**
 * Heuristic to check whether name could be a packageable library (like .so or executable) that is
 * *not* an Android system library.
 */
@VisibleForTesting
fun isPackageable(name: String) : Boolean {
    return name.hasExtensionIgnoreCase("") ||
            (name.hasExtensionIgnoreCase("so") &&
                    (!name.contains("ndk") || !NDK_SYSTEM_LIBS.any { name.endsWith(it) }))
}

// Libraries known to be installed on the device. When referenced by build.ninja, these should
// not be considered as libraries that should be packaged in the APK.
private val NDK_SYSTEM_LIBS = listOf(
    "libEGL.so",
    "libGLESv1_CM.so",
    "libGLESv2.so",
    "libGLESv3.so",
    "libOpenMAXAL.so",
    "libOpenSLES.so",
    "libaaudio.so",
    "libamidi.so",
    "libandroid.so",
    "libbinder_ndk.so",
    "libc.so",
    "libcamera2ndk.so",
    "libdl.so",
    "libjnigraphics.so",
    "liblog.so",
    "libm.so",
    "libmediandk.so",
    "libnativewindow.so",
    "libneuralnetworks.so",
    "libstdc++.so",
    "libsync.so",
    "libvulkan.so",
    "libz.so")

/**
 * Assign a name to a target. Strip "lib" from front and ".a" or ".so" from the end.
 * Preserve case.
 */
@VisibleForTesting
fun assignTargetName(target : String) : String {
    val targetFile = File(target)
    if (!target.hasExtensionIgnoreCase("so", "a")) return targetFile.name
    val name = targetFile.nameWithoutExtension
    return if (target.filenameStartsWithIgnoreCase("lib") &&
                !target.filenameStartsWithIgnoreCase("lib.")) name.substring(3)
            else name
}

/**
 * Graph of build inputs and outputs along with the IDs of some interesting targets.
 */
private class BuildGraph(
    val edges : Map<Int, IntArray>,
    val packageableIds : Set<Int>,
    val buildFileIds : Set<Int>,
    val passthroughs : Set<Int>,
    val archiveIds: Set<Int>
)


