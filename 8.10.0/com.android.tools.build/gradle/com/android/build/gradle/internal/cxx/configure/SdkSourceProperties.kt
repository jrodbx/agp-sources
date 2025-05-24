/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.configure

import com.android.SdkConstants.FN_SOURCE_PROP
import com.android.repository.Revision
import java.io.File

/**
 * Android SDK packages have a file named source.properties in the root of the installation
 * folder. It looks like:
 *
 *   Pkg.Desc = Android NDK
 *   Pkg.Revision = 17.2.4988734
 *
 * This class is for reading that file.
 */
data class SdkSourceProperties(private val map : Map<String, String>) {

    /**
     * Get a value by key.
     * Returns null if the key didn't exist.
     */
    fun getValue(key : SdkSourceProperty) : String? {
        return map[key.key]
    }

    /**
     * Return the Pkg.Revision value.
     */
    val revision : Revision
        get() = Revision.parseRevision(getValue(SdkSourceProperty.SDK_PKG_REVISION)!!)

    companion object {
        private const val SOURCE_PROPERTIES = "source.properties"

        /**
         * Enum of known properties.
         */
        enum class SdkSourceProperty(val key : String) {
            SDK_PKG_DESC("Pkg.Desc"),
            SDK_PKG_REVISION("Pkg.Revision");
        }

        private fun sourcePropertiesFile(folder : File) = File(folder, SOURCE_PROPERTIES)

        /**
         * Read a source properties file.
         *
         * Throws FileNotFoundException if the source.properties file didn't exist.
         */
        fun fromInstallFolder(folder : File) : SdkSourceProperties {
            val map = mutableMapOf<String, String>()
            val sourceProperties = pathFromInstallFolder(folder)
            for (line in sourceProperties.readLines()) {
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                map[key] = value
            }
            return SdkSourceProperties(map)
        }

        /**
         * Try to read the Pkg.Revision from the source.properties file. Returns null
         * if the folder or file can't be found or if there is no Pkg.Revision in the
         * file.
         */
        fun tryReadPackageRevision(folder : File) : String? {
            if (!folder.isDirectory || !sourcePropertiesFile(folder).isFile) {
                return null
            }
            val properties = fromInstallFolder(folder)
            return properties.getValue(SdkSourceProperty.SDK_PKG_REVISION)
        }

        /**
         * Return the path to source.properties inside a given SDK component.
         */
        private fun pathFromInstallFolder(folder : File) : File {
            return File(folder, FN_SOURCE_PROP)

        }
    }
}
