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
import com.android.build.gradle.internal.dsl.decorator.androidPluginDslDecorator
import org.gradle.api.DomainObjectSet
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import java.io.File

class DslServicesImpl constructor(
    projectServices: ProjectServices,
    override val sdkComponents: Provider<SdkComponentsBuildService>
) : BaseServicesImpl(projectServices), DslServices {

    override fun <T> domainObjectSet(type: Class<T>): DomainObjectSet<T> =
        projectServices.objectFactory.domainObjectSet(type)

    override fun <T> domainObjectContainer(
        type: Class<T>,
        factory: NamedDomainObjectFactory<T>
    ): NamedDomainObjectContainer<T> =
        projectServices.objectFactory.domainObjectContainer(type, factory)

    override fun <T> polymorphicDomainObjectContainer(
        type: Class<T>
    ): ExtensiblePolymorphicDomainObjectContainer<T> =
        projectServices.objectFactory.polymorphicDomainObjectContainer(type)

    override fun <T> property(type: Class<T>): Property<T> = projectServices.objectFactory.property(type)

    override fun <T> provider(type: Class<T>, value: T?): Provider<T> =
        projectServices.objectFactory.property(type).also {
            it.set(value)
        }

    override val buildDirectory: DirectoryProperty
        get() = projectServices.projectLayout.buildDirectory

    override val logger: Logger
        get() = projectServices.logger

    override fun file(file: Any): File = projectServices.fileResolver(file)

    override fun <T: Any> newDecoratedInstance(dslClass: Class<T>, vararg args: Any) : T {
        return newInstance(androidPluginDslDecorator.decorate(dslClass), *args)
    }
}
