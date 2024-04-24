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

import com.android.build.gradle.internal.component.LibraryCreationConfig
import com.android.build.gradle.internal.component.features.NativeBuildCreationConfig
import com.android.build.gradle.internal.cxx.configure.getPrefabExperimentalPackagingOptions
import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.jsonFile
import com.android.build.gradle.internal.cxx.model.minSdkVersion
import com.android.build.gradle.internal.cxx.model.name
import com.android.utils.cxx.CxxDiagnosticCode.PREFAB_GRADLE_VERSION_NOT_COMPATIBLE_WITH_PREFAB
import java.util.regex.Pattern

/**
 * Create a [PrefabPublication] from Gradle configuration structures.
 *
 * A [PrefabPublication] is a mapping from Gradle configuration information to
 * a Prefab package on disk.
 *
 * The model created by this function does not yet have library name (libfoo.so)
 * plugged in because that information is not available at configuration time.
 */
fun createPrefabPublication(
    configurationModel: CxxConfigurationModel,
    libraryVariant : LibraryCreationConfig,
    nativeBuildCreationConfig: NativeBuildCreationConfig
) : PrefabPublication {

    val abis = configurationModel.activeAbis.map { abi ->
        PrefabAbiPublication(
            abiName = abi.name,
            abiApi = abi.minSdkVersion,
            abiStl = abi.variant.stlType,
            abiNdkMajor = abi.variant.module.ndkVersion.major,
            abiLibrary = null,
            abiAndroidGradleBuildJsonFile = abi.jsonFile.absoluteFile
        )
    }
    val modules = libraryVariant.global.prefab.map { options ->
        val experimentalSettings = nativeBuildCreationConfig.getPrefabExperimentalPackagingOptions(options.name)

        PrefabModulePublication(
            moduleName = options.name,
            moduleLibraryName = options.libraryName,
            moduleHeaders = options.headers?.let { headers ->
                libraryVariant.services.projectInfo.projectDirectory.dir(headers).asFile.absoluteFile
            },
            moduleExportLibraries = experimentalSettings.exportLibraries ?: listOf(),
            abis = abis
        )
    }
    return PrefabPublication(
        installationFolder = libraryVariant.services.projectInfo.getIntermediatesDir()
            .resolve(PREFAB_PACKAGE)
            .resolve(libraryVariant.name).resolve("prefab").absoluteFile,
        gradlePath = configurationModel.variant.module.gradleModulePathName,
        packageInfo = PrefabPackagePublication(
            packageName = libraryVariant.services.projectInfo.name,
            packageVersion = gradleVersionToPrefabPackageVersion(libraryVariant.services.projectInfo.version),
            packageSchemaVersion = 2,
            packageDependencies = listOf(),
            modules = modules
        )
    )
}

private val EXTRACT_VERSION_PATTERN = Pattern.compile("^\\d+(\\.\\d+(\\.\\d+(\\.\\d+)?)?)?")

/**
 * Extract a version number from [version] if possible.
 * "unspecified" is a special value coming from AGP 'packageVersion' for Prefab
 * purposes, we turn it in to null which causes the eventual prefab.json to
 * have no version number (which is legal).
 */
private fun gradleVersionToPrefabPackageVersion(version : String) : String? {
    val m = EXTRACT_VERSION_PATTERN.matcher(version)
    return when {
        m.find() -> m.group()
        version == "unspecified" -> null
        else -> {
            // Emit a build error but allow progress to continue so that downstream
            // error messages and warnings will also be emitted.
            errorln(
                PREFAB_GRADLE_VERSION_NOT_COMPATIBLE_WITH_PREFAB,
                "The package version '$version' is incompatible with Prefab")
            version
        }
    }
}
