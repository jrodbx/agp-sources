/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.internal.services

import com.android.build.gradle.internal.SdkComponentsBuildService
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File
import kotlin.properties.ReadWriteProperty

/**
 * Services for the DSL objects.
 *
 * This contains whatever is needed by all the DSL objects.
 *
 * This is meant to be transient and only available by the DSL objects. Other stages of the
 * plugin will use different services objects.
 */
interface DslServices: BaseServices {

    val logger: Logger
    val buildDirectory: DirectoryProperty
    val sdkComponents: Provider<SdkComponentsBuildService>

    fun <T> domainObjectSet(type: Class<T>): DomainObjectSet<T>
    fun <T> domainObjectContainer(
        type: Class<T>,
        factory: NamedDomainObjectFactory<T>
    ): NamedDomainObjectContainer<T>

    @Deprecated("do not use. DSL elements should not use Property<T> objects")
    fun <T> property(type: Class<T>): Property<T>

    fun <T> newVar(initialValue: T): ReadWriteProperty<Any?, T>

    fun file(file: Any): File
}