/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either excodess or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.build.gradle.internal

import com.android.build.api.dsl.CompileOptions
import com.google.common.base.Charsets
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.jetbrains.annotations.VisibleForTesting

/** Java compilation options. */
abstract class CompileOptions : CompileOptions {

    /**
     * Whether Java compilation should be incremental or not.
     *
     * Note that even if this option is set to `true`, Java compilation may still be non-incremental
     * (e.g., if incremental annotation processing is not yet possible in the project).
     */
    var incremental: Boolean? = null

    override var sourceCompatibility: JavaVersion
        get() = run {
            check(sourceAndTargetFinalized) { "sourceCompatibility is not yet finalized" }
            _sourceCompatibility!!
        }
        set(value) {
            check(!sourceAndTargetFinalized) { "sourceCompatibility has been finalized" }
            _sourceCompatibility = value
        }

    override var targetCompatibility: JavaVersion
        get() = run {
            check(sourceAndTargetFinalized) { "targetCompatibility is not yet finalized" }
            _targetCompatibility!!
        }
        set(value) {
            check(!sourceAndTargetFinalized) { "targetCompatibility has been finalized" }
            _targetCompatibility = value
        }

    override var encoding: String = Charsets.UTF_8.name()
    override var isCoreLibraryDesugaringEnabled: Boolean = false

    protected var _sourceCompatibility: JavaVersion? = null
    protected var _targetCompatibility: JavaVersion? = null

    private var sourceAndTargetFinalized: Boolean = false

    override fun sourceCompatibility(sourceCompatibility: Any) {
        _sourceCompatibility = parseJavaVersion(sourceCompatibility)
    }

    fun setSourceCompatibility(sourceCompatibility: Any) {
        sourceCompatibility(sourceCompatibility)
    }

    override fun targetCompatibility(targetCompatibility: Any) {
        _targetCompatibility = parseJavaVersion(targetCompatibility)
    }

    fun setTargetCompatibility(targetCompatibility: Any) {
        targetCompatibility(targetCompatibility)
    }

    /**
     * Computes the final sourceCompatibility and targetCompatibility versions based on the
     * following precedence order (first one wins):
     *   - Version set from the DSL
     *   - Toolchain version
     *   - [DEFAULT_JAVA_VERSION]
     */
    fun finalizeSourceAndTargetCompatibility(toolchainVersion: JavaVersion?) {
        check(!sourceAndTargetFinalized) { "sourceCompatibility and targetCompatibility have already been finalized" }

        _sourceCompatibility = _sourceCompatibility ?: toolchainVersion ?: DEFAULT_JAVA_VERSION
        _targetCompatibility = _targetCompatibility ?: toolchainVersion ?: DEFAULT_JAVA_VERSION
        sourceAndTargetFinalized = true
    }

    fun finalizeSourceAndTargetCompatibility(project: Project) {
        val toolchainVersion = project.extensions.getByType(JavaPluginExtension::class.java).toolchain.languageVersion.let {
            // Finalizing a property's value at configuration time is usually not recommended, but
            // we have to do it because AGP `CompileOptions` properties are currently not lazy
            // (bug 271841527).
            it.finalizeValue()
            it.orNull?.run { JavaVersion.toVersion(asInt()) }
        }
        finalizeSourceAndTargetCompatibility(toolchainVersion)
    }

    companion object {

        val DEFAULT_JAVA_VERSION = JavaVersion.VERSION_1_8

        private const val VERSION_PREFIX = "VERSION_"

        /** Converts a user-provided Java version of type [Any] to [JavaVersion]. */
        @VisibleForTesting
        internal fun parseJavaVersion(version: Any): JavaVersion {
            // for backward version reasons, we support setting strings like 'Version_1_6'
            val normalizedVersion =
                if (version is String && version.uppercase().startsWith(VERSION_PREFIX)) {
                    version.substring(VERSION_PREFIX.length).replace('_', '.')
                } else version

            return JavaVersion.toVersion(normalizedVersion)
        }
    }
}
