/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.gradle

import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.dsl.LibraryExtensionImpl
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet
import java.util.Collections

/**
 * The {@code android} extension for {@code com.android.library} projects.
 *
 * <p>Apply this plugin to your project to <a
 * href="https://developer.android.com/studio/projects/android-library.html">create an Android
 * library</a>.
 */
open class LibraryExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: LibraryExtensionImpl
) : TestedExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    false
),
   InternalLibraryExtension by publicExtensionImpl {

    // Overrides to make the parameterized types match, due to BaseExtension being part of
    // the previous public API and not wanting to paramerterize that.
    override val buildTypes: NamedDomainObjectContainer<BuildType>
        get() = publicExtensionImpl.buildTypes as NamedDomainObjectContainer<BuildType>
    override val defaultConfig: DefaultConfig
        get() = publicExtensionImpl.defaultConfig as DefaultConfig
    override val productFlavors: NamedDomainObjectContainer<ProductFlavor>
        get() = publicExtensionImpl.productFlavors as NamedDomainObjectContainer<ProductFlavor>
    override val sourceSets: NamedDomainObjectContainer<AndroidSourceSet>
        get() = publicExtensionImpl.sourceSets

    private val libraryVariantList: DomainObjectSet<LibraryVariant> =
        dslServices.domainObjectSet(LibraryVariant::class.java)

    /**
     * Returns a collection of
     * [build variants](https://developer.android.com/studio/build/build-variants.html)
     * that the library project includes.
     *
     * To process elements in this collection, you should use
     * [`all`](https://docs.gradle.org/current/javadoc/org/gradle/api/DomainObjectCollection.html#all-org.gradle.api.Action-).
     * That's because the plugin populates this collection only after
     * the project is evaluated. Unlike the `each` iterator, using `all`
     * processes future elements as the plugin creates them.
     *
     * The following sample iterates through all `libraryVariants` elements to
     * [inject a build variable into the manifest](https://developer.android.com/studio/build/manifest-build-variables.html):
     *
     * ```
     * android.libraryVariants.all { variant ->
     *     def mergedFlavor = variant.getMergedFlavor()
     *     // Defines the value of a build variable you can use in the manifest.
     *     mergedFlavor.manifestPlaceholders = [hostName:"www.example.com"]
     * }
     * ```
     */
    val libraryVariants: DefaultDomainObjectSet<LibraryVariant>
        get() = libraryVariantList as DefaultDomainObjectSet<LibraryVariant>

    override fun addVariant(variant: BaseVariant) {
        libraryVariantList.add(variant as LibraryVariant)
    }

    @Suppress("WrongTerminology")
    @Deprecated("Use aidlPackagedList instead", ReplaceWith("aidlPackagedList"))
    fun aidlPackageWhiteList(vararg aidlFqcns: String) {
        Collections.addAll(publicExtensionImpl.aidlPackagedList, *aidlFqcns)
    }

    fun aidlPackagedList(vararg aidlFqcns: String) {
        Collections.addAll(publicExtensionImpl.aidlPackagedList, *aidlFqcns)
    }

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests
}
