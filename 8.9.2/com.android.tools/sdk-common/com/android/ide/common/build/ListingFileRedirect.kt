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
package com.android.ide.common.build

import java.io.File
import java.io.PrintWriter
import java.io.StringReader
import java.util.Properties

object ListingFileRedirect {

    /**
     * Redirect file will have this marker as the first line as comment.
     */
    const val REDIRECT_MARKER = "#- File Locator -"

    /**
     * Property name in a [Properties] for the metadata file location.
     */
    const val REDIRECT_PROPERTY_NAME = "listingFile"

    /**
     * Redirect file name used when for the artifact.
     */
    const val REDIRECT_FILE_NAME = "redirect.txt"

    fun writeRedirect(listingFile: File, into: File) {
        val path = try {
                into.parentFile.toPath().relativize(listingFile.toPath()).toString()
            } catch(ex: IllegalArgumentException) {
                listingFile.canonicalPath
            }
        PrintWriter(into).use {
            it.println(REDIRECT_MARKER)
            it.println("${REDIRECT_PROPERTY_NAME}=${path.replace("\\", "/")}")
        }
    }

    fun maybeExtractRedirectedFile(redirectFile: File, redirectFileContent: String? = null): File? {
        val fileContent = redirectFileContent ?: redirectFile.readText()
        return if (fileContent.startsWith(REDIRECT_MARKER)) {
            val fileLocator = Properties().also {
                it.load(StringReader(fileContent))
            }
            val file = File(fileLocator.getProperty(REDIRECT_PROPERTY_NAME))
            if(!file.isAbsolute())
                redirectFile.parentFile.resolve(file)
            else
                file
        } else null
    }

    fun getListingFile(inputFile: File) =
        maybeExtractRedirectedFile(inputFile) ?: inputFile
}
