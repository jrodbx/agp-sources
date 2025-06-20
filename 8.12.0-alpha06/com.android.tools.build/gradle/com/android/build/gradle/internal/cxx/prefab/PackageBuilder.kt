/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants.CURRENT_PLATFORM
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.android.build.gradle.internal.cxx.io.synchronizeFile
import com.android.build.gradle.internal.cxx.io.writeTextIfDifferent
import com.android.build.gradle.internal.cxx.json.writeJsonFileIfDifferent
import com.android.build.gradle.internal.cxx.logging.infoln
import org.gradle.api.file.FileSystemOperations
import java.io.File

/**
 * This function creates prefab metadata structure inside a folder.
 *
 * If [payloadIndirection] is false, then header files and library
 * files are also copied into the structure.
 *
 * If [payloadIndirection] is true, then header files and library
 * files are NOT copied into the structure but a list of payload
 * mappings is returned. These mappings describe the copies that
 * would have happened.
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
fun buildPrefabPackage(
    fileOperations: FileSystemOperations? = null,
    payloadIndirection: Boolean = false,
    publication: PrefabPublication
) : List<PayloadMapping> {
    if (!payloadIndirection && fileOperations == null) {
        error("Must supply file operations")
    }
    return PrefabPackageBuilder(fileOperations, payloadIndirection)
        .configurePrefab(publication)
}

/**
 * Describes a mapping of origin payload folder and file (.so, .a, .h, etc) paths to the analog
 * path in the package that is being generated.
 */
data class PayloadMapping(
    // The path in the origin (:lib) module.
    // Example, lib/build/intermediates/cxx/Debug/6g3q1ba3/obj/arm64-v8a/libfoo.so
    val originModulePath : String,
    // Hypothetical path in the generated (:app) package. However, [buildPrefabPackage] does not
    // actually copy the file there.
    // For example,
    //   app/build/intermediates/cxx/refs/lib/2u45445o/modules/foo/libs/android.arm64-v8a/libfoo.so
    val builtPackagePath : String
)

private class PrefabPackageBuilder(
    private val fileOperations: FileSystemOperations?,
    private val payloadIndirection: Boolean
) {
    private val payloadMappings = mutableListOf<PayloadMapping>()

    fun configurePrefab(publication: PrefabPublication) : List<PayloadMapping> {
        publication.installationFolder.mkdirs()
        createPrefabJson(publication.installationFolder, publication.packageInfo)
        for (module in publication.packageInfo.modules) {
            createModule(module, publication.installationFolder)
        }
        return payloadMappings
    }

    /**
     * Creates the prefab.json file which defines the overall package.
     */
    private fun createPrefabJson(
        installDir: File,
        packageInfo: PrefabPackagePublication
    ) {
        writeJsonFileIfDifferent(
            installDir.resolve("prefab.json"),
            PackageMetadataV1(
                name = packageInfo.packageName,
                schemaVersion = packageInfo.packageSchemaVersion,
                version = packageInfo.packageVersion,
                dependencies = packageInfo.packageDependencies
            )
        )
    }

    /**
     * Create, for example, the modules/foo1 and modules/foo2 folders and their
     * contents.
     */
    private fun createModule(
        module: PrefabModulePublication,
        packageDir: File
    ) {
        val moduleDir = packageDir.resolve("modules/${module.moduleName}").apply { mkdirs() }
        createModuleJson(module, moduleDir)
        installHeaders(module.moduleHeaders, moduleDir)
        createLibs(moduleDir, module)
    }

    /**
     * Create the module.json file which is metadata about a single prefab module.
     */
    private fun createModuleJson(
        module: PrefabModulePublication,
        moduleDir: File
    ) {
        writeJsonFileIfDifferent(
            moduleDir.resolve("module.json"),
            ModuleMetadataV1(
                exportLibraries = module.moduleExportLibraries,
                libraryName = module.moduleLibraryName
            )
        )
    }

    /**
     * Create the per-module includes folder and sync content into it.
     */
    private fun installHeaders(
        headers: File?,
        moduleDir: File
    ) {
        val includeDir = moduleDir.resolve("include")

        if (headers == null) {
            // Remove content of include folder if user has removed prefab headers spec.
            includeDir.deleteRecursively()
            // But leave the empty folder since Prefab cli expects it.
            includeDir.mkdirs()
            return
        }
        includeDir.mkdirs()
        if (payloadIndirection) {
            addPayloadMapping(
                originModulePath = headers,
                builtPackagePath = includeDir)
            // Prefab requires a file to be in the include folder.
            if (headers.isDirectory) {
                includeDir.resolve("placeholder.txt")
                    .writeTextIfDifferent("")
            }
            return
        }
        infoln("Installing header $headers to $includeDir")
        // Use 'sync' to cause only changed files to be copied and to
        // delete files that were removed at the source.
        fileOperations!!.sync { spec ->
            spec.from(headers)
            spec.into(includeDir)
        }
    }

    /**
     * Create per-module libs folder and contents.
     */
    private fun createLibs(
        moduleDir: File,
        module: PrefabModulePublication
    ) {
        val libsDir = moduleDir.resolve("libs").apply { deleteRecursively() }
        for (abi in module.abis) {
            val libDir = libsDir.resolve("android.${abi.abiName}")
            createAbiJson(libDir, abi)
        }
    }

    /**
     * Create per-module per-ABI abi.json file.
     */
    private fun createAbiJson(
        libDir: File,
        abi: PrefabAbiPublication
    ) {
        libDir.mkdirs()
        writeJsonFileIfDifferent(
            libDir.resolve("abi.json"),
            AndroidAbiMetadata(
                abi = abi.abiName,
                api = abi.abiApi,
                ndk = abi.abiNdkMajor,
                stl = abi.abiStl,
                static = abi.abiLibrary!!.name.endsWith(".a")
            )
        )

        val source = abi.abiLibrary
        val destination = libDir.resolve(source.name)
        if (payloadIndirection) {
            addPayloadMapping(
                originModulePath = source,
                builtPackagePath = destination)
            return
        }
        infoln("Installing $source to $destination")
        synchronizeFile(
            source = source,
            destination = destination
        )
    }

    private fun addPayloadMapping(originModulePath : File, builtPackagePath : File) {
        payloadMappings.add(PayloadMapping(
            originModulePath = originModulePath.platformSlashes(),
            builtPackagePath = builtPackagePath.platformSlashes()))
    }

    private fun File.platformSlashes() = if (CURRENT_PLATFORM  == PLATFORM_WINDOWS) {
        path.replace("\\", "/")
    } else path
}


