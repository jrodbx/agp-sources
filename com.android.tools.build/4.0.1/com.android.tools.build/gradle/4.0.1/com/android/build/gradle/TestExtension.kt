package com.android.build.gradle

import com.android.build.api.dsl.TestBuildFeatures
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantProperties
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.BaseVariantOutput
import com.android.build.gradle.api.ViewBindingOptions
import com.android.build.gradle.internal.CompileOptions
import com.android.build.gradle.internal.ExtraModelInfo
import com.android.build.gradle.internal.api.dsl.DslScope
import com.android.build.gradle.internal.coverage.JacocoOptions
import com.android.build.gradle.internal.dependency.SourceSetManager
import com.android.build.gradle.internal.dsl.ActionableVariantObjectOperationsExecutor
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.DataBindingOptions
import com.android.build.gradle.internal.dsl.CmakeOptions
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.android.build.gradle.internal.dsl.ExternalNativeBuild
import com.android.build.gradle.internal.dsl.NdkBuildOptions
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.build.gradle.internal.dsl.TestExtensionImpl
import com.android.build.gradle.internal.dsl.ViewBindingOptionsImpl
import com.android.build.gradle.internal.dsl.TestOptions
import com.android.build.gradle.internal.scope.GlobalScope
import com.android.build.gradle.internal.scope.VariantScope
import com.android.build.gradle.options.ProjectOptions
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectSet

/** {@code android} extension for {@code com.android.test} projects. */
open class TestExtension(
    dslScope: DslScope,
    projectOptions: ProjectOptions,
    globalScope: GlobalScope,
    buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
    sourceSetManager: SourceSetManager,
    extraModelInfo: ExtraModelInfo,
    private val publicExtensionImpl: TestExtensionImpl
) : BaseExtension(
    dslScope,
    projectOptions,
    globalScope,
    buildOutputs,
    sourceSetManager,
    extraModelInfo,
    false
), TestAndroidConfig,
    com.android.build.api.dsl.TestExtension<
            BuildType,
            CmakeOptions,
            CompileOptions,
            DefaultConfig,
            ExternalNativeBuild,
            JacocoOptions,
            NdkBuildOptions,
            ProductFlavor,
            SigningConfig,
            TestOptions,
            TestOptions.UnitTestOptions> by publicExtensionImpl,
    ActionableVariantObjectOperationsExecutor<TestVariant, TestVariantProperties> by publicExtensionImpl {

    private val applicationVariantList: DomainObjectSet<ApplicationVariant> =
        dslScope.objectFactory.domainObjectSet(ApplicationVariant::class.java)

    private var _targetProjectPath: String? = null

    override val dataBinding: DataBindingOptions =
        dslScope.objectFactory.newInstance(
            DataBindingOptions::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    override val viewBinding: ViewBindingOptions =
        dslScope.objectFactory.newInstance(
            ViewBindingOptionsImpl::class.java,
            publicExtensionImpl.buildFeatures,
            projectOptions,
            globalScope.dslScope
        )

    // this is needed because the impl class needs this but the interface does not,
    // so CommonExtension does not define it, which means, that even though it's part of
    // TestExtensionImpl, the implementation by delegate does not bring it.
    fun buildFeatures(action: Action<TestBuildFeatures>) {
        publicExtensionImpl.buildFeatures(action)
    }

    /**
     * The list of Application variants. Since the collections is built after evaluation, it
     * should be used with Gradle's `all` iterator to process future items.
     */
    val applicationVariants: DefaultDomainObjectSet<ApplicationVariant>
        get() = applicationVariantList as DefaultDomainObjectSet<ApplicationVariant>

    override fun addVariant(variant: BaseVariant, variantScope: VariantScope) {
        applicationVariantList.add(variant as ApplicationVariant)
    }

    /**
     * The Gradle path of the project that this test project tests.
     */
    override var targetProjectPath: String?
        get() = _targetProjectPath
        set(value) = targetProjectPath(value)

    open fun targetProjectPath(targetProjectPath: String?) {
        checkWritability()
        _targetProjectPath = targetProjectPath
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

    override val testBuildType: String?
        get() = null
}
