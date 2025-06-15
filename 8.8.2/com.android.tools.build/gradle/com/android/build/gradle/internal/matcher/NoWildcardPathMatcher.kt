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

import java.io.File
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.regex.Matcher
import java.util.regex.Pattern

class NoWildcardPathMatcher(matcher: Matcher): PathMatcher {

    val path: String

    init {
        if (!matcher.matches())
            throw IllegalArgumentException("matcher $matcher does not match this factory")
        path = matcher.group(1)
    }

    companion object {
        val pattern: Pattern= Pattern.compile("(/[^*{}]*)")
        fun factory() = object: GlobPathMatcherFactory {
            override fun build(glob: Matcher) = NoWildcardPathMatcher(glob)
            override fun pattern()= pattern
        }
    }

    override fun matches(p0: Path?): Boolean {
        val pathAsString = p0?.toString()?.replace(File.separatorChar, '/')
        return path == pathAsString
    }
}
