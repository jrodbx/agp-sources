package com.android.build.gradle

import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.InternalTestExtension
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.TestExtensionImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.tasks.factory.BootClasspathConfig
import com.android.builder.core.LibraryRequest
import com.android.repository.Revision
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.internal.DefaultDomainObjectSet

/** {@code android} extension for {@code com.android.test} projects. */
open class TestExtension(
    dslServices: DslServices,
    bootClasspathConfig: BootClasspathConfig,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: TestExtensionImpl
) : BaseExtension(
    dslServices,
    bootClasspathConfig,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    false
), TestAndroidConfig,
    InternalTestExtension by publicExtensionImpl {

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

    private val applicationVariantList: DomainObjectSet<ApplicationVariant> =
        dslServices.domainObjectSet(ApplicationVariant::class.java)

    /**
     * The list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's `all` iterator to process future items.
     */
    val applicationVariants: DefaultDomainObjectSet<ApplicationVariant>
        get() = applicationVariantList as DefaultDomainObjectSet<ApplicationVariant>

    override fun addVariant(variant: BaseVariant) {
        applicationVariantList.add(variant as ApplicationVariant)
    }

    open fun targetProjectPath(targetProjectPath: String?) {
        checkWritability()
        publicExtensionImpl.targetProjectPath = targetProjectPath
    }

    /**
     * The variant of the tested project.
     *
     * Default is 'debug'
     *
     * @deprecated This is deprecated, test module can now test all flavors.
     */
    override var targetVariant: String
        get() = ""
        set(value) = targetVariant(value)

    open fun targetVariant(targetVariant: String) {
        checkWritability()
        System.err.println("android.targetVariant is deprecated, all variants are now tested.")
    }

    override val flavorDimensionList: MutableList<String>
        get() = flavorDimensions

    override val buildToolsRevision: Revision
        get() = Revision.parseRevision(buildToolsVersion, Revision.Precision.MICRO)

    override val libraryRequests: MutableCollection<LibraryRequest>
        get() = publicExtensionImpl.libraryRequests

    override val testBuildType: String?
        get() = null
}
