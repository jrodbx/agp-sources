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

import com.android.build.gradle.internal.cxx.json.readJsonFile
import com.android.build.gradle.internal.cxx.json.writeJsonFileIfDifferent
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.Configuration
import com.android.build.gradle.internal.cxx.prefab.PrefabPublicationType.HeaderOnly
import java.io.File

/*************************************************************************************
 * Defines the project directory layout with respect to Prefab configuration and
 * publishing.
 *
 * There are three kinds of Prefab package representations. Each represents the
 * information that is available at a particular time during the Gradle lifecycle.
 * Two of the representations--PREFAB_PACKAGE_HEADER_ONLY and PREFAB_PACKAGE_CONFIGURATION
 * are "publications" and have a [PrefabPublicationType]. These don't carry a
 * payload of header files an source libraries. The third kind, PREFAB_PACKAGE, is a a
 * standard unzipped AAR Prefab folder.
 *
 * ---------------------------------------------------------------------------------
 * PREFAB_PACKAGE_HEADER_ONLY (Gradle configuration time)
 * Purpose: Allows IDE sync regardless of the order of C/C++ configuration.
 * Location: lib/build/intermediates/prefab_package_header_only
 * ---------------------------------------------------------------------------------
 * This is a file named 'prefab_publication.json' (schema [PrefabPublication]) that
 * views the Prefab package as header-only. This is enough information to allow the
 * IDE to sync successfully (see b/19473943) by allowing CMake to run _before_
 * dependent modules are C/C++ configured.
 *
 * ---------------------------------------------------------------------------------
 * PREFAB_PACKAGE_CONFIGURATION (C/C++ configuration time)
 * Purpose: Allows module :app to C/C++ configure by depending on module :lib's
 * C/C++ configuration.
 * Location: lib/build/intermediates/prefab_package_configuration
 * ---------------------------------------------------------------------------------
 * This is a file named 'prefab_publication.json' ([PrefabPublication]) that has
 * information about libraries (like libfoo.so) before having built those
 * libraries. Information about libraries is available after running CMake to
 * generate the project.
 *
 * ---------------------------------------------------------------------------------
 * PREFAB_PACKAGE (C/C++ build time)
 * Purpose: Allows module :app to link by depending on :lib's C/C++ build.
 * Location: lib/build/intermediates/prefab_package
 * ---------------------------------------------------------------------------------
 * This is an actual unzipped Prefab AAR directory. It contains headers and
 * libraries. To build this, we first needed to run CMake to generate the project,
 * and also we needed to successfully build the C/C++ project.
 ************************************************************************************/

const val PREFAB_PUBLICATION_FILE = "prefab_publication.json"
const val PREFAB_PACKAGE = "prefab_package"
const val PREFAB_PACKAGE_CONFIGURATION_SEGMENT = "prefab_package_configuration"
const val PREFAB_PACKAGE_HEADER_ONLY_SEGMENT = "prefab_package_header_only"

enum class PrefabPublicationType(val segment : String) {
    Configuration(PREFAB_PACKAGE_CONFIGURATION_SEGMENT),
    HeaderOnly(PREFAB_PACKAGE_HEADER_ONLY_SEGMENT);
}

/**
 * Read the [PrefabPublication] file (prefab_publication.json) of this type if it exists.
 * Return null otherwise.
 */
fun PrefabPublicationType.readPublicationFileOrNull(installationFolder: File) : PrefabPublication? {
    val file = publicationFileFrom(installationFolder) ?: return null
    if (!file.isFile) return null
    return readJsonFile<PrefabPublication>(file)
}

/**
 * Write the [PrefabPublication] of this type to disk.
 */
fun PrefabPublicationType.writePublicationFile(publication : PrefabPublication) {
    val file = publicationFileFrom(publication.installationFolder)!!
    val patched = when(this) {
        Configuration -> publication.copyWithLibraryInformationAdded()
        HeaderOnly -> publication.copyAsHeaderOnly()
        else -> error("$this")
    }
    writeJsonFileIfDifferent(file, patched)
}

/**
 * Get the name of the publication file of this type.
 */
private fun PrefabPublicationType.publicationFileFrom(installationFolder: File) : File? {
    val variantRoot = installationFolder.parentFile
    val variant = variantRoot.name
    val prefabRoot = variantRoot.parentFile
    if (prefabRoot.name != PREFAB_PACKAGE) return null
    return prefabRoot.parentFile
        .resolve(segment)
        .resolve(PREFAB_PUBLICATION_FILE)
        .resolve(variant)
}



