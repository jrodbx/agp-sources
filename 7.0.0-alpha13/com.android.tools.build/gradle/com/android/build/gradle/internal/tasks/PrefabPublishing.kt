/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import com.android.build.api.variant.impl.LibraryVariantImpl
import com.android.build.gradle.internal.SdkComponentsBuildService
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.json.AndroidBuildGradleJsons
import com.android.build.gradle.internal.cxx.json.NativeLibraryValueMini
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.logging.infoln
import com.android.build.gradle.internal.cxx.logging.warnln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.minSdkVersion
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.services.getBuildService
import com.android.build.gradle.internal.tasks.factory.VariantTaskCreationAction
import com.android.build.gradle.internal.utils.setDisallowChanges
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.io.StringWriter

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
 */
data class AndroidAbiMetadata(
    val abi: String,
    val api: Int,
    val ndk: Int,
    val stl: String
)

private class JsonSerializer {
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

abstract class PrefabPackageTask : NonIncrementalTask() {
    @get:Internal
    abstract val sdkComponents: Property<SdkComponentsBuildService>

    private lateinit var configurationModel: CxxConfigurationModel

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    lateinit var packageName: String
        private set

    @get:Input
    lateinit var packageVersion: String
        private set

    @get:Nested
    lateinit var modules: List<PrefabModuleTaskData>
        private set

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    val jsonFiles get() = configurationModel.activeAbis.map { it.jsonFile }

    @get:Input
    val ndkAbiFilters get() = configurationModel.variant.validAbiList

    override fun doTaskAction() {
        val installDir = outputDirectory.get().asFile.apply { mkdirs() }
        createPackageMetadata(installDir)
        for (module in modules) {
            createModule(module, installDir)
        }
    }

    private fun createPackageMetadata(installDir: File) {
        installDir.resolve("prefab.json").writeText(
            JsonSerializer().toJson(
                PackageMetadataV1(
                    name = packageName,
                    schemaVersion = 1,
                    version = packageVersion.takeIf { it != "unspecified" },
                    dependencies = emptyList()
                )
            )
        )
    }

    private fun createModule(module: PrefabModuleTaskData, packageDir: File) {
        val installDir = packageDir.resolve("modules/${module.name}").apply { mkdirs() }
        createModuleMetadata(module, installDir)
        installHeaders(module, installDir)
        installLibs(module, installDir)
    }

    private fun createModuleMetadata(module: PrefabModuleTaskData, installDir: File) {
        installDir.resolve("module.json").writeText(
            JsonSerializer().toJson(
                ModuleMetadataV1(
                    exportLibraries = emptyList(),
                    libraryName = module.libraryName
                )
            )
        )
    }

    private fun installHeaders(module: PrefabModuleTaskData, installDir: File) {
        val includeDir = installDir.resolve("include").apply { deleteRecursively() }
        module.headers?.let {
            it.copyRecursively(includeDir)
            infoln("Installing $it to $includeDir")
        }
    }

    private fun findLibraryForAbi(moduleName: String, abi: CxxAbiModel): NativeLibraryValueMini {
        val config =
            AndroidBuildGradleJsons.getNativeBuildMiniConfig(abi, null)

        val matchingLibs = config.libraries.filterValues { it.artifactName == moduleName }.values
        if (matchingLibs.isEmpty()) {
            errorln("No libraries found for $moduleName")
        }
        return matchingLibs.single()
    }

    private fun installLibs(module: PrefabModuleTaskData, installDir: File) {
        val libsDir = installDir.resolve("libs").apply { deleteRecursively() }
        for (abiData in configurationModel.activeAbis) {
            val srcLibrary = findLibraryForAbi(module.name, abiData).output
            if (srcLibrary != null) {
                val libDir = libsDir.resolve("android.${abiData.abi.tag}")
                val dest = libDir.resolve(srcLibrary.name)
                infoln("Installing $srcLibrary to $dest")
                srcLibrary.copyTo(dest)
                createLibraryMetadata(libDir, abiData)
            } else {
                warnln("${module.name}} has no library output")
            }
        }
    }

    private fun createLibraryMetadata(libDir: File, abi: CxxAbiModel) {
        libDir.resolve("abi.json").writeText(
            JsonSerializer().toJson(
                AndroidAbiMetadata(
                    abi = abi.abi.tag,
                    api = abi.minSdkVersion,
                    ndk = abi.variant.module.ndkVersion.major,
                    stl = abi.variant.stlType
                )
            )
        )
    }

    class CreationAction(
            private val modules: List<PrefabModuleTaskData>,
            private val sdkComponentsBuildService: SdkComponentsBuildService,
            private val config : CxxConfigurationModel,
            componentProperties: LibraryVariantImpl
    ) : VariantTaskCreationAction<PrefabPackageTask, LibraryVariantImpl>(
        componentProperties
    ) {
        override val name: String
            get() = computeTaskName("prefab", "Package")

        override val type: Class<PrefabPackageTask>
            get() = PrefabPackageTask::class.java

        override fun handleProvider(taskProvider: TaskProvider<PrefabPackageTask>) {
            super.handleProvider(taskProvider)

            creationConfig.artifacts.setInitialProvider(
                taskProvider,
                PrefabPackageTask::outputDirectory
            ).withName("prefab").on(InternalArtifactType.PREFAB_PACKAGE)
        }

        override fun configure(task: PrefabPackageTask) {
            super.configure(task)
            task.description = "Creates a Prefab package for inclusion in an AAR"

            val project = creationConfig.services.projectInfo.getProject()
            task.packageName = project.name
            task.packageVersion = project.version.toString()
            task.modules = modules

            task.configurationModel = config
            task.sdkComponents.setDisallowChanges(
                    getBuildService(creationConfig.services.buildServiceRegistry)
            )
        }
    }
}
