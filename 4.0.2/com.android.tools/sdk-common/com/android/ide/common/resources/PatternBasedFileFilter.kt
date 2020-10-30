/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *            http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.common.resources

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import java.io.File

/** Implements AAPT-compatible file filtering logic. */
class PatternBasedFileFilter {
    private val ignoredPatterns: List<String>

    val aaptStyleIgnoredPattern: String
        get() = Joiner.on(':').join(ignoredPatterns)

    /**
     * Creates default resource file filter. The default ignored patterns are the same as used by
     * aapt but can be customized via the `ANDROID_AAPT_IGNORE` environment variable.
     */
    constructor() {
        var patterns: String? = System.getenv("ANDROID_AAPT_IGNORE")
        if (patterns == null || patterns.isEmpty()) {
            // Matches aapt: frameworks/base/tools/aapt/AaptAssets.cpp:gDefaultIgnoreAssets
            patterns = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~"
        }

        ignoredPatterns = Splitter.on(':').splitToList(patterns)
    }

    /**
     * Creates a resource file filter using the given pattens of for ignored files.
     *
     * Patterns syntax:
     * * Individual patterns are separated by colon (:)
     * * Entry can start with the flag `!` to avoid printing a warning about the file being ignored.
     * * Entry can have the flag `<dir>` to match only directories or `<file>` to match only files.
     *   Default is to match both.
     * * Entry can be a simplified glob `<prefix>*` or `*<suffix>` where prefix/suffix must have at
     *   least 1 character (so that we don't match a '*' catch-all pattern.)
     * * The special filenames "." and ".." are always ignored.
     * * Otherwise the full string is matched.
     * * Match is not case-sensitive.
     *
     * @param ignoredPatterns the file name patterns to be ignored
     */
    constructor(ignoredPatterns: String) {
        this.ignoredPatterns = Splitter.on(':').splitToList(ignoredPatterns)
    }

    /**
     * Checks whether the given file should be ignored.
     *
     * @param file the file to check
     * @return true if the file should be ignored
     */
    fun isIgnored(file: File): Boolean {
        val filePath = file.path

        if (filePath == "." || filePath == "..") {
            return true
        }

        val isDirectory = file.isDirectory
        return isIgnored(filePath, isDirectory)
    }

    /**
     * Checks whether the given file should be ignored.
     *
     * @param filePath the path of the file to check
     * @param isDirectory indicates whether the file is a directory or not
     * @return true if the file should be ignored
     */
    fun isIgnored(filePath: String, isDirectory: Boolean): Boolean {
        if (filePath == "." || filePath == "..") {
            return true
        }

        var ignore = false

        val nameIndex = filePath.lastIndexOf(File.separatorChar) + 1
        val nameLength = filePath.length - nameIndex
        for (token in ignoredPatterns) {
            if (token.isEmpty()) {
                continue
            }
            var tokenIndex = 0
            if (token[tokenIndex] == '!') {
                tokenIndex++ // Skip.
            }

            if (token.regionMatches(tokenIndex, "<dir>", 0, 5)) {
                if (!isDirectory) {
                    continue
                }
                tokenIndex += 5
            }
            if (token.regionMatches(tokenIndex, "<file>", 0, 6)) {
                if (isDirectory) {
                    continue
                }
                tokenIndex += 6
            }

            var n = token.length - tokenIndex

            if (token[tokenIndex] == '*') {
                // Match *suffix such as *.scc or *~
                tokenIndex++
                n--
                if (n <= nameLength) {
                    ignore = token.regionMatches(tokenIndex, filePath, nameIndex + nameLength - n, n, ignoreCase = true)
                }
            }
            else if (n > 1 && token[token.length - 1] == '*') {
                // Match prefix* such as .* or _*
                ignore = token.regionMatches(tokenIndex, filePath, nameIndex, n - 1, ignoreCase = true)
            }
            else {
                // Match exactly, such as thumbs.db, .git, etc.
                ignore = token.length - tokenIndex == filePath.length - nameIndex && token.regionMatches(
                    tokenIndex,
                    filePath,
                    nameIndex,
                    filePath.length - nameIndex, ignoreCase = true)
            }

            if (ignore) {
                break
            }
        }

        return ignore
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val otherFilter = other as PatternBasedFileFilter?
        return ignoredPatterns == otherFilter!!.ignoredPatterns
    }

    override fun hashCode(): Int {
        return ignoredPatterns.hashCode()
    }
}
