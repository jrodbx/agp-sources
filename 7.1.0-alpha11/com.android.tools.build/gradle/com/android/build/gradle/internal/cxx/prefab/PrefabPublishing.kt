/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal.cxx.prefab

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.component.ComponentCreationConfig
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.minSdkVersion
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File
import java.io.StringWriter
import java.util.regex.Pattern

// TODO: Automatic importing of CMake/ndk-build modules?
// CMake doesn't appear to expose the data we need from CMake server (though that could be a
// missing request or an outdated server version). It would make sense to try using installed
// modules for this.
// ndk-build doesn't currently install headers.

// The following metadata classes are copied from
// https://github.com/google/prefab/tree/master/api/src/main/kotlin/com/google/prefab/api.
//
// The metadata is stable for a given schema version, but if we need to update to a newer schema
// version we'll need to update these.
//
// TODO: Depend on com.google.prefab:api.
// We can't currently do this because we can't use kotlinx.serialization.

/**
 * The v1 prefab.json schema.
 *
 * @property[name] The name of the module.
 * @property[schemaVersion] The version of the schema. Must be 1.
 * @property[dependencies] A list of other packages required by this package.
 * @property[version] The package version. For compatibility with CMake, this
 * *must* be formatted as major[.minor[.patch[.tweak]]] with all components
 * being numeric, even if that does not match the package's native version.
 */
data class PackageMetadataV1(
    val name: String,
    @SerializedName("schema_version")
    val schemaVersion: Int,
    val dependencies: List<String>,
    val version: String? = null
)

/**
 * The module metadata that is overridable per-platform in module.json.
 *
 * @property[exportLibraries] The list of libraries other than the library
 * described by this module that must be linked by users of this module.
 * @property[libraryName] The name (without the file extension) of the library
 * file. The file extension is automatically detected based on the contents of
 * the directory.
 */
data class PlatformSpecificModuleMetadataV1(
    @SerializedName("export_libraries")
    val exportLibraries: List<String>? = null,
    @SerializedName("library_name")
    val libraryName: String? = null
)

/**
 * The v1 module.json schema.
 *
 * @property[exportLibraries] The list of libraries other than the library
 * described by this module that must be linked by users of this module.
 * @property[libraryName] The name (without the file extension) of the library
 * file. The file extension is automatically detected based on the contents of
 * the directory.
 * @property[android] Android specific values that override the main values if
 * present.
 */
data class ModuleMetadataV1(
    @SerializedName("export_libraries")
    val exportLibraries: List<String>,
    @SerializedName("library_name")
    val libraryName: String? = null,
    // Allowing per-platform overrides before we support more than a single
    // platform might seem like overkill, but this makes it easier to maintain
    // compatibility for old packages when new platforms are added. If we added
    // this in schema version 2, there would be no way to reliably migrate v1
    // packages because we wouldn't know whether the exported libraries were
    // Android specific or not.
    val android: PlatformSpecificModuleMetadataV1 =
        PlatformSpecificModuleMetadataV1(
            null,
            null
        )
)

/**
 * The Android abi.json schema.
 *
 * @property[abi] The ABI name of the described library. These names match the tag field for
 * [com.android.build.gradle.internal.core.Abi].
 * @property[api] The minimum OS version supported by the library. i.e. the
 * library's `minSdkVersion`.
 * @property[ndk] The major version of the NDK that this library was built with.
 * @property[stl] The STL that this library was built with.
 * @property[static] If true then the library is .a, if false then .so,
 * otherwise it is header-only.
 */
data class AndroidAbiMetadata(
    val abi: String,
    val api: Int,
    val ndk: Int,
    val stl: String,
    val static: Boolean?
)

class JsonSerializer {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    fun toJson(obj: Any): String =
        StringWriter().also { writer -> gson.toJson(obj, writer) }.toString()
}

data class PrefabModuleTaskData(
    @get:Input
    val name: String,
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val headers: File?,
    @get:Optional
    @get:Input
    val libraryName: String?
)

/**
 * Return information about each Prefab package output model.
 */
fun ComponentCreationConfig.prefabPackageConfigurationData():
        List<PrefabModuleTaskData> {
    val extension = globalScope.extension as LibraryExtension
    val project = services.projectInfo.getProject()
    return extension.prefab.map { options ->
        val headers = options.headers?.let { headers ->
            project.layout
                .projectDirectory
                .dir(headers)
                .asFile
        }
        PrefabModuleTaskData(options.name, headers, options.libraryName)
    }
}

/**
 * This function creates prefab metadata structure inside a folder
 * and installs header files into it. The result is a configured
 * prefab package that doesn't yet have the .a or .so files (because
 * they haven't been built).
 *
 * For reference, this is the folder layout when this function is
 * finished:
 *      [installDir]/prefab.json
 *      [installDir]/modules/foo1/module.json
 *      [installDir]/modules/foo1/include/...
 *      [installDir]/modules/foo1/libs/android.x86/abi.json
 *      [installDir]/modules/foo2/module.json
 *      [installDir]/modules/foo2/include/...
 *      [installDir]/modules/foo2/libs/android.x86/abi.json
 *
 * The modules foo1 and foo2 are established in build.gradle by, for
 * example:
 *      android {
 *          prefab {
 *              foo1 {
 *                  headers "src/main/cpp/include"
 *                  libraryName "libfoo1"
 *              }
 *          }
 *      }
 */
fun configurePrefab(
    fileOperations: FileOperations,
    installDir : File,
    packageName : String,
    packageVersion : String?,
    modules: List<PrefabModuleTaskData>,
    configurationModel: CxxConfigurationModel
) {
    installDir.mkdirs()
    createPrefabJson(installDir, packageName, packageVersion)
    for (module in modules) {
        createModule(fileOperations, module, installDir, configurationModel)
    }
}

/**
 * Creates the prefab.json file which defines the overall package.
 */
private fun createPrefabJson(
    installDir: File,
    packageName: String,
    packageVersion : String?
) {
    installDir.resolve("prefab.json").writeText(
        JsonSerializer().toJson(
            PackageMetadataV1(
                name = packageName,
                schemaVersion = 2,
                version = packageVersion,
                dependencies = emptyList()
            )
        )
    )
}

/**
 * Create, for example, the modules/foo1 and modules/foo2 folders and their
 * contents.
 */
private fun createModule(
    fileOperations: FileOperations,
    module: PrefabModuleTaskData,
    packageDir: File,
    configurationModel: CxxConfigurationModel
) {
    val moduleDir = packageDir.resolve("modules/${module.name}").apply { mkdirs() }
    createModuleJson(module, moduleDir)
    installHeaders(fileOperations, module.headers, moduleDir)
    createLibs(moduleDir, module.name, configurationModel)
}

/**
 * Create the module.json file which is metadata about a single prefab module.
 */
private fun createModuleJson(
    module: PrefabModuleTaskData,
    moduleDir: File) {
    moduleDir.resolve("module.json").writeText(
        JsonSerializer().toJson(
            ModuleMetadataV1(
                exportLibraries = emptyList(),
                libraryName = module.libraryName
            )
        )
    )
}

/**
 * Create the per-module includes folder and sync content into it.
 */
private fun installHeaders(
    fileOperations: FileOperations,
    headers: File?,
    moduleDir: File
) {
    val includeDir = moduleDir.resolve("include")
    if (headers == null) {
        // Remove include folder if user has removed prefab headers spec.
        includeDir.deleteRecursively()
        return
    }
    includeDir.mkdirs()
    infoln("Installing header $headers to $includeDir")
    // Use 'sync' to cause only changed files to be copied and to
    // delete files that were removed at the source.
    fileOperations.sync { spec ->
        spec.from(headers)
        spec.into(includeDir)
    }
}

/**
 * Create per-module libs folder and contents.
 */
private fun createLibs(
    moduleDir: File,
    moduleName: String,
    configurationModel: CxxConfigurationModel
) {
    val libsDir = moduleDir.resolve("libs").apply { deleteRecursively() }
    for (abiData in configurationModel.activeAbis) {
        val libDir = libsDir.resolve("android.${abiData.abi.tag}")
        createAbiJson(libDir, moduleName, abiData)
    }
}

/**
 * Create per-module per-ABI abi.json file.
 */
private fun createAbiJson(
    libDir: File,
    moduleName : String,
    abi: CxxAbiModel
) {

    // Figure out whether it's static or shared (or null for header-only)
    val config = AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, null)
    val library = config.libraries.values.singleOrNull { it.artifactName == moduleName}
    val static = library?.output?.path?.endsWith(".a")

    libDir.mkdirs()
    libDir.resolve("abi.json").writeText(
        JsonSerializer().toJson(
            AndroidAbiMetadata(
                abi = abi.abi.tag,
                api = abi.minSdkVersion,
                ndk = abi.variant.module.ndkVersion.major,
                stl = abi.variant.stlType,
                static = static
            )
        )
    )
}

/**
 * Return the name of the prefab[Variant]Package task.
 */
fun ComponentCreationConfig.prefabPackageTaskName() =
    computeTaskName("prefab", "Package")

/**
 * Return the name of the prefab[ConfigurePackage]Package task.
 */
fun ComponentCreationConfig.prefabConfigurePackageTaskName() =
    computeTaskName("prefab", "ConfigurePackage")

/**
 * Return the folder location of the prefab package output.
 */
fun ComponentCreationConfig.prefabPackageLocation() : String {
    return services.projectInfo.getIntermediatesDir()
        .resolve("prefab_package")
        .resolve(name)
        .absolutePath
}

private val EXTRACT_VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+(\\.\\d+(\\.\\d+)?)?)?")

/**
 * Extract a version number from [version] if possible.
 * "unspecified" is a special value coming from AGP 'packageVersion' for Prefab
 * purposes, we turn it in to null which causes the eventual prefab.json to
 * have no version number (which is legal).
 */
fun versionOrError(version : String) : String? {
    val m = EXTRACT_VERSION_PATTERN.matcher(version)
    return when {
        m.find() -> m.group()
        version == "unspecified" -> null
        else -> {
            // Emit a build error but allow progress to continue so that downstream
            // error messages and warnings will also be emitted.
            errorln("The version package version '$version' is incompatible with CMake")
            version
        }
    }
}




