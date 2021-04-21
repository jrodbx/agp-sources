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

import com.android.build.api.dsl.LibraryBuildFeatures
import com.android.build.api.dsl.PrefabPackagingOptions
import com.android.build.api.variant.LibraryVariantProperties
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.ActionableVariantObjectOperationsExecutor
import com.android.build.gradle.internal.dsl.InternalLibraryExtension
import com.android.build.gradle.internal.dsl.LibraryExtensionImpl
import com.android.build.gradle.internal.dsl.PrefabModuleFactory
import com.android.build.gradle.internal.dsl.ViewBindingOptionsImpl
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import org.gradle.api.Action
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
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: LibraryExtensionImpl
) : TestedExtension(
    dslServices,
    globalScope,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    false
),
   InternalLibraryExtension by publicExtensionImpl,
    ActionableVariantObjectOperationsExecutor<com.android.build.api.variant.LibraryVariant<LibraryVariantProperties>, LibraryVariantProperties> by publicExtensionImpl {

    private val libraryVariantList: DomainObjectSet<LibraryVariant> =
        dslServices.domainObjectSet(LibraryVariant::class.java)

    override val viewBinding: ViewBindingOptions =
        dslServices.newInstance(
            ViewBindingOptionsImpl::class.java,
            publicExtensionImpl.buildFeatures,
            dslServices
        )

    // this is needed because the impl class needs this but the interface does not,
    // so CommonExtension does not define it, which means, that even though it's part of
    // LibraryExtensionImpl, the implementation by delegate does not bring it.
    fun buildFeatures(action: Action<LibraryBuildFeatures>) {
        publicExtensionImpl.buildFeatures(action)
    }

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

    fun aidlPackageWhiteList(vararg aidlFqcns: String) {
        Collections.addAll(publicExtensionImpl.aidlPackageWhiteList, *aidlFqcns)
    }

    override var compileSdkVersion: String?
        get() = publicExtensionImpl.compileSdkPreview
        set(value) {
            publicExtensionImpl.compileSdkPreview = value
        }
    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests
}
