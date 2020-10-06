/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.lint.gradle.api

import java.net.URL
import java.net.URLClassLoader
import java.util.Enumeration

class DelegatingClassLoader(urls: Array<URL>) :
    URLClassLoader(urls, null /* no parent class loader!*/) {
    private val delegate = this.javaClass.classLoader

    @Throws(ClassNotFoundException::class)
    override fun findClass(name: String): Class<*> {
        if (mustBeLoadedByDelegate(name)) {
            return delegate.loadClass(name)
        }

        return try {
            super.findClass(name)
        } catch (e: ClassNotFoundException) {
            delegate.loadClass(name)
        }
    }

    override fun findResource(name: String?): URL? {
        val resource = super.findResource(name)
        if (resource != null) {
            return resource
        }
        return delegate.getResource(name)
    }

    override fun findResources(name: String?): Enumeration<URL> {
        val resources = super.findResources(name)
        // TODO: Merge in values from delegate?`
        if (resources != null && resources.hasMoreElements()) {
            return resources
        }
        return delegate.getResources(name)
    }

    // Lint bundles the Kotlin compiler, which requires a very recent version of Kotlin
    // stdlib. Thus Lint sometimes needs to bundle a newer version of the Kotlin stdlib than the
    // one that is used by Gradle. However, Lint calls Gradle/AGP APIs, and some of those APIs
    // reference Kotlin stdlib classes (loaded by Gradle's classloader). This leads to LinkageErrors
    // at runtime (loader constraint violations). To fix this, we load a subset of Kotlin stdlib
    // classes (the ones appearing in API signatures) using the Gradle classloader.
    // We assume that these classes don't change between Kotlin versions. Yes, this is hacky.
    // It is inspired by PluginClassLoader.mustBeLoadedByPlatform in IntelliJ.
    // TODO: This is a workaround for b/166661949. Remove this once Lint runs out-of-process.
    private fun mustBeLoadedByDelegate(name: String): Boolean {
        if (!name.startsWith("kotlin.")) {
            return false
        }

        if (name.startsWith("kotlin.jvm.functions.") ||
            name.startsWith("kotlin.reflect.") && name.indexOf('.', 15) < 0
        ) {
            return true
        }

        when (name) {
            "kotlin.sequences.Sequence",
            "kotlin.Lazy",
            "kotlin.Unit",
            "kotlin.Pair",
            "kotlin.Triple",
            "kotlin.jvm.internal.DefaultConstructorMarker",
            "kotlin.jvm.internal.ClassBasedDeclarationContainer",
            "kotlin.properties.ReadWriteProperty",
            "kotlin.properties.ReadOnlyProperty" -> return true
        }

        return false
    }
}
