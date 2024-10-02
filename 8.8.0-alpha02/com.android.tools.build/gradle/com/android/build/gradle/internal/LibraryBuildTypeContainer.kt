/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal

import com.android.build.api.dsl.LibraryBuildType
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Configuring

class LibraryBuildTypeContainer (
    private val buildTypes: NamedDomainObjectContainer<LibraryBuildType>
) : NamedDomainObjectContainer<LibraryBuildType> by buildTypes {

    @Configuring
    fun debug(action: LibraryBuildType.() -> Unit) {
        getByName("debug", action)
    }

    @Configuring
    fun release(action: LibraryBuildType.() -> Unit) {
        getByName("release", action)
    }

    @Adding
    fun create(name: String, action: LibraryBuildType.() -> Unit): LibraryBuildType {
        val newObject = create(name)
        action(newObject)
        return newObject
    }
}
