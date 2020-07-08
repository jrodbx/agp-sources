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
}
