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

import com.google.common.collect.ImmutableList
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Factory for [PathMatcher] instances that supports glob patterns.
 *
 * First, a list of simple pattern factories will be attempted for a potential match, and if nothing
 * matches, the default implementation will use the [FileSystem#getPathMatcher] implementation.
 *
 * The [FileSystem#getPathMatcher] uses a regular expression to match files against the pattern
 * which is correct but an overkill since glob patterns are a lot more simple than full regular
 * expressions.
 *
 * An implementation of this factory will declare a regular expression to match supported glob
 * patterns. If the glob sequence is supported, the [PathMatcher] returned by [build] can be used
 * to patch files against.
 */
interface GlobPathMatcherFactory {

    companion object {
        private val pathMatcherFactories = ImmutableList.of(
            NoWildcardPathMatcher.factory(),
            FolderInHierarchyPathMatcher.factory(),
            FileExtensionWithPrefixPathMatcher.factory(),
            FileNameWithSuffixPathMatcher.factory(),
            FileNameWithPrefixPathMatcher.factory()
        )

        fun create(pattern: CharSequence): PathMatcher {

            for (factory in pathMatcherFactories) {
                val matcher = factory.pattern().matcher(pattern)
                if (matcher.matches()) {
                    return factory.build(matcher)
                }
            }

            // everything else, go through the regex based path matcher.
            val fs = FileSystems.getDefault()

            return fs.getPathMatcher("glob:$pattern")
        }
    }

    /**
     * Returns a [PathMatcher] for the provided pattern matcher.
     * the passed [Matcher] instance must match the [#pattern()] or an [IllegalArgumentException]
     * will be thrown.
     *
     * @param glob the glob matching the factory pattern.
     * @return the [PathMatcher] instance if the passed glob pattern is supported or null if not.
     */
    @Throws(IllegalArgumentException::class)
    fun build(glob: Matcher): PathMatcher

    /**
     * Return the regular expression that will be used to match glob patterns.
     */
    fun pattern(): Pattern
}