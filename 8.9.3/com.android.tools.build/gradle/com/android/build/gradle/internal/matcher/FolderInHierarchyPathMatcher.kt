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

package com.android.build.gradle.internal.matcher

import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.Matcher
import java.util.regex.Pattern

class FolderInHierarchyPathMatcher(val matcher: Matcher) : PathMatcher {

    private val prefix: String = if ("*" != matcher.group(1)) matcher.group(1) else ""
    private val exactMatching= "*" != matcher.group(1) && !matcher.group(2).startsWith("*")
    private val suffix: String = if (matcher.group(2).startsWith("*"))
        matcher.group(2).substring(1) else matcher.group(2)
    private val exactMatchingString = "$prefix$suffix"

    init {
        if (!matcher.matches())
            throw IllegalArgumentException("matcher $matcher does not match this factory")
    }

    companion object {
        // **/foo/** **/.*/** **/blah*/** **/*foo/**
        val pattern: Pattern = Pattern.compile("\\*\\*/([^/*{}]*)([^/{}]*)/\\*\\*")
        fun factory(): GlobPathMatcherFactory {
            return object: GlobPathMatcherFactory {
                override fun pattern()= pattern
                override fun build(glob: Matcher)= FolderInHierarchyPathMatcher(glob)
            }
        }
    }

    override fun matches(path: Path?): Boolean {

        val pathAsString = path?.parent?.toString() ?: return false
        // if the complete path does not contain prefix and suffix anywhere, no need to continue
        if (!pathAsString.contains(prefix) || !pathAsString.contains(suffix)) return false

        var currentFolder = path
        while (currentFolder != null) {
            val folderAsString = currentFolder.fileName?.toString() ?: return false
            if (exactMatching) {
                if (folderAsString == exactMatchingString) {
                    return true
                }
            } else {
                if (folderAsString.startsWith(prefix) && folderAsString.endsWith(suffix)) {
                    // even though we are matching, we cannot be the top level folder.
                    return currentFolder.parent != null
                }
            }
            currentFolder = currentFolder.parent
        }
        return false
    }
}