/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.Ndk
import com.android.build.api.dsl.PostProcessing
import com.android.build.api.dsl.Shaders
import com.android.build.api.dsl.TestBuildType
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.AbstractBuildType
import com.android.builder.core.BuilderConstants
import com.android.builder.core.ComponentType
import com.android.builder.errors.IssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.BaseConfig
import com.google.common.base.Preconditions
import com.google.common.collect.Iterables
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import java.io.File
import java.io.Serializable
import javax.inject.Inject

/** DSL object to configure build types.  */
abstract class BuildType @Inject @WithLazyInitialization(methodName="lazyInit") constructor(
    private val name: String,
    private val dslServices: DslServices,
    private val componentType: ComponentType
) :
    AbstractBuildType(), CoreBuildType, Serializable,
    VariantDimensionBinaryCompatibilityFix,
    ApplicationBuildType,
    LibraryBuildType,
    DynamicFeatureBuildType,
    TestBuildType,
    InternalBuildType {

    fun lazyInit() {
        renderscriptOptimLevel = 3
        isEmbedMicroApp = true
        enableUnitTestCoverage = dslServices.projectInfo.hasPlugin(JacocoPlugin.PLUGIN_EXTENSION_NAME)
    }

    /**
     * Name of this build type.
     */
    override fun getName(): String {
        return name
    }

    abstract override var enableAndroidTestCoverage: Boolean

    abstract override var enableUnitTestCoverage: Boolean

    abstract var _isDebuggable: Boolean

    override var isDebuggable: Boolean
        get() = // Accessing coverage data requires a debuggable package.
            _isDebuggable || isTestCoverageEnabled
        set(value) { _isDebuggable = value }

    abstract override var isTestCoverageEnabled: Boolean

    abstract override var isPseudoLocalesEnabled: Boolean

    abstract override var isJniDebuggable: Boolean

    abstract override var isRenderscriptDebuggable: Boolean

    abstract override var renderscriptOptimLevel: Int

    abstract override var isProfileable: Boolean

    @Deprecated("This property is deprecated. Changing its value has no effect.")
    override var isZipAlignEnabled: Boolean
        get() = true
        set(_) { }

    /**
     * Whether to enable the checks that the both the old and new way of configuring
     * bytecode postProcessing are not used at the same time.
     *
     * The checks are disabled during [.initWith].
     */
    private var dslChecksEnabled = true

    /**
     * Describes how code postProcessing is configured. We don't allow mixing the old and new DSLs.
     */
    enum class PostProcessingConfiguration {
        POSTPROCESSING_BLOCK, OLD_DSL
    }

    override val ndkConfig: NdkOptions = dslServices.newInstance(NdkOptions::class.java)

    override val externalNativeBuild: ExternalNativeBuildOptions =
        dslServices.newInstance(ExternalNativeBuildOptions::class.java, dslServices)

    override val externalNativeBuildOptions: ExternalNativeBuildOptions
        get() {
            return this.externalNativeBuild
        }

    val _postProcessing: PostProcessingBlock = dslServices.newInstance(
        PostProcessingBlock::class.java,
        dslServices,
        componentType
    )

    private var _shrinkResources = false

    /*
     * (Non javadoc): Whether png crunching should be enabled if not explicitly overridden.
     *
     * Can be removed once the AaptOptions crunch method is removed.
     */
    override var isCrunchPngsDefault = true

    // FIXME remove: b/149431538
    @Suppress("DEPRECATION")
    private val _isDefaultProperty: Property<Boolean> =
        dslServices.property(Boolean::class.java).convention(false)

    abstract override val matchingFallbacks: MutableList<String>

    abstract override fun setMatchingFallbacks(fallbacks: List<String>)

    override fun setMatchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks.clear()
        for (fallback in fallbacks) {
            matchingFallbacks.add(fallback)
        }
    }

    fun setMatchingFallbacks(fallback: String) {
        matchingFallbacks.clear()
        matchingFallbacks.add(fallback)
    }

    abstract override val javaCompileOptions: JavaCompileOptions

    override val shaders: ShaderOptions = dslServices.newInstance(ShaderOptions::class.java)

    /**
     * Initialize the DSL object with the debug signingConfig. Not meant to be used from the build
     * scripts.
     */
    fun init(debugSigningConfig: SigningConfig?) {
        init()
        if (BuilderConstants.DEBUG == name) {
            Preconditions.checkNotNull(debugSigningConfig)
            setSigningConfig(debugSigningConfig)
        }
    }

    /**
     * Initialize the DSL object without the signingConfig. Not meant to be used from the build
     * scripts.
     */
    fun init() {
        if (BuilderConstants.DEBUG == name) {
            setDebuggable(true)
            isEmbedMicroApp = false
            isCrunchPngsDefault = false
        }
    }

    /** The signing configuration. e.g.: `signingConfig signingConfigs.myConfig`  */
    override var signingConfig: ApkSigningConfig? = null

    override fun setSigningConfig(signingConfig: com.android.builder.model.SigningConfig?): com.android.builder.model.BuildType {
        this.signingConfig = signingConfig as SigningConfig?
        return this
    }

    fun setSigningConfig(signingConfig: SigningConfig?) {
        this.signingConfig = signingConfig
    }

    fun setSigningConfig(signingConfig: InternalSigningConfig?) {
        this.signingConfig = signingConfig
    }

    fun setSigningConfig(signingConfig: Any?) {
        this.signingConfig = signingConfig as SigningConfig?
    }

    override fun _internal_getSigingConfig(): ApkSigningConfig? {
        return signingConfig
    }

    abstract override var isEmbedMicroApp: Boolean

    override fun getIsDefault(): Property<Boolean> {
        return _isDefaultProperty
    }

    override var isDefault: Boolean
        get() = _isDefaultProperty.get()
        set(value) {
            _isDefaultProperty.set(value)
        }

    fun setIsDefault(isDefault: Boolean) {
        this.isDefault = isDefault
    }

    override fun _initWith(that: BaseConfig) {
        super._initWith(that)
        val thatBuildType =
            that as BuildType
        ndkConfig._initWith(thatBuildType.ndkConfig)
        javaCompileOptions.annotationProcessorOptions._initWith(
            thatBuildType.javaCompileOptions.annotationProcessorOptions
        )
        _shrinkResources = thatBuildType._shrinkResources
        shaders._initWith(thatBuildType.shaders)
        enableUnitTestCoverage = thatBuildType.enableUnitTestCoverage
        enableAndroidTestCoverage = thatBuildType.enableAndroidTestCoverage
        externalNativeBuildOptions._initWith(thatBuildType.externalNativeBuildOptions)
        postProcessingBlockUsed = thatBuildType.postProcessingBlockUsed
        if (postProcessingBlockUsed) {
            _postProcessing.initWith(that._postProcessing)
        }
        isCrunchPngs = thatBuildType.isCrunchPngs
        isCrunchPngsDefault = thatBuildType.isCrunchPngsDefault
        setMatchingFallbacks(thatBuildType.matchingFallbacks)
        // we don't want to dynamically link these values. We just want to copy the current value.
        isProfileable = thatBuildType.isProfileable
        aarMetadata.minCompileSdk = thatBuildType.aarMetadata.minCompileSdk
        (optimization as OptimizationImpl).initWith(that.optimization as OptimizationImpl)
        androidTest.enableMinification = thatBuildType.androidTest.enableMinification
        (vcsInfo as VcsInfoImpl).initWith(that.vcsInfo as VcsInfoImpl)
    }

    /** Override as DSL objects have no reason to be compared for equality.  */
    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

    /** Override as DSL objects have no reason to be compared for equality.  */
    override fun equals(o: Any?): Boolean {
        return this === o
    }
    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    override fun buildConfigField(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = buildConfigFields[name]
        if (alreadyPresent != null) {
            val message = String.format(
                "BuildType(%s): buildConfigField '%s' value is being replaced.",
                getName(), name
            )
            dslServices.logger.debug(message)
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    override fun resValue(
        type: String,
        name: String,
        value: String
    ) {
        val resValueKey = ResValueKeyImpl(type, name)
        val alreadyPresent = resValues[resValueKey.toString()]
        if (alreadyPresent != null) {
            val message = String.format(
                "BuildType(%s): resValue '%s' value is being replaced.",
                getName(), resValueKey.toString()
            )
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                message
            )
        }
        addResValue(resValueKey.toString(), ClassFieldImpl(type, name, value))
    }

    override var proguardFiles: MutableList<File>
        get() = super.proguardFiles
        set(value) {
            // Override to handle the proguardFiles = ['string'] case (see PluginDslTest.testProguardFiles_*)
            setProguardFiles(value)
        }

    override fun proguardFile(proguardFile: Any): BuildType {
        proguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun proguardFiles(vararg files: Any): BuildType {
        for (file in files) {
            proguardFile(file)
        }
        return this
    }

    /**
     * Sets the ProGuard configuration files.
     *
     *
     * There are 2 default rules files
     *
     *  * proguard-android.txt
     *  * proguard-android-optimize.txt
     *
     *
     * They are located in the SDK. Using `getDefaultProguardFile(String filename)` will return the
     * full path to the files. They are identical except for enabling optimizations.
     */
    override fun setProguardFiles(proguardFileIterable: Iterable<*>): BuildType {
        val replacementFiles = Iterables.toArray(proguardFileIterable, Any::class.java)
        proguardFiles.clear()
        proguardFiles(
            *replacementFiles
        )
        return this
    }

    override val testProguardFiles: MutableList<File>
        get() = super.testProguardFiles


    override fun testProguardFile(proguardFile: Any): BuildType {
        testProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun testProguardFiles(vararg proguardFiles: Any): BuildType {
        for (proguardFile in proguardFiles) {
            testProguardFile(proguardFile)
        }
        return this
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun setTestProguardFiles(files: Iterable<*>): BuildType {
        testProguardFiles.clear()
        testProguardFiles(
            *Iterables.toArray(
                files,
                Any::class.java
            )
        )
        return this
    }

    override val consumerProguardFiles: MutableList<File>
        get() = super.consumerProguardFiles

    override fun consumerProguardFile(proguardFile: Any): BuildType {
        consumerProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun consumerProguardFiles(vararg proguardFiles: Any): BuildType {
        for (proguardFile in proguardFiles) {
            consumerProguardFile(proguardFile)
        }
        return this
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun setConsumerProguardFiles(proguardFileIterable: Iterable<*>): BuildType {
        consumerProguardFiles.clear()
        consumerProguardFiles(
            *Iterables.toArray(
                proguardFileIterable,
                Any::class.java
            )
        )
        return this
    }

    fun ndk(action: Action<NdkOptions>) {
        action.execute(ndkConfig)
    }

    override val ndk: Ndk
        get() = ndkConfig

    override fun ndk(action: Ndk.() -> Unit) {
        action.invoke(ndk)
    }

    /**
     * Configure native build options.
     */
    fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>): ExternalNativeBuildOptions {
        action.execute(externalNativeBuildOptions)
        return externalNativeBuildOptions
    }

    override fun externalNativeBuild(action: com.android.build.api.dsl.ExternalNativeBuildFlags.() -> Unit) {
        action.invoke(externalNativeBuild)
    }

    /**
     * Configure shader compiler options for this build type.
     */
    fun shaders(action: Action<ShaderOptions>) {
        action.execute(shaders)
    }

    override fun shaders(action: Shaders.() -> Unit) {
        action.invoke(shaders)
    }

    override var isMinifyEnabled: Boolean
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            _postProcessing.isRemoveUnusedCode ||
                    _postProcessing.isObfuscate ||
                    _postProcessing.isOptimizeCode

        set(value) {
            _postProcessing.isRemoveUnusedCode = value
            _postProcessing.isObfuscate = value
            _postProcessing.isOptimizeCode = value
        }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    override var isShrinkResources: Boolean
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            _postProcessing.isRemoveUnusedResources
        set(value) {
            checkShrinkResourceEligibility(componentType, dslServices, value)
            _postProcessing.isRemoveUnusedResources = value
        }

    /**
     * Always returns [false].
     *
     * When you enable code shrinking by setting
     * [`minifyEnabled`](com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:minifyEnabled) to `true`, the Android plugin uses R8.
     *
     * To learn more, read
     * [Shrink, obfuscate, and optimize your app](https://developer.android.com/studio/build/shrink-code.html).
     */
    override val isUseProguard: Boolean?
        get() {
            dslServices.deprecationReporter.reportObsoleteUsage(
                    "useProguard",
                    DeprecationReporter.DeprecationTarget.VERSION_8_0
            )
            return false
        }

    abstract override var isCrunchPngs: Boolean?

    var postProcessingBlockUsed = false

    /** This DSL is incubating and subject to change.  */
    @get:Internal
    @get:Incubating
    override val postprocessing: PostProcessingBlock
        get() {
            checkPostProcessingConfiguration()
            return _postProcessing
        }

    /** This DSL is incubating and subject to change.  */
    @Incubating
    @Internal
    fun postprocessing(action: Action<PostProcessingBlock>) {
        checkPostProcessingConfiguration()
        action.execute(_postProcessing)
    }

    override fun postprocessing(action: PostProcessing.() -> Unit) {
        postprocessing(Action { action.invoke(it) })
    }

    private fun checkPostProcessingConfiguration() {
        if (!postProcessingBlockUsed) {
            postProcessingBlockUsed = true
            _postProcessing.isRemoveUnusedCode = true
        }
    }

    override fun initWith(that: com.android.build.api.dsl.BuildType) {
        if (that !is BuildType) {
            throw RuntimeException("Unexpected implementation type")
        }
        initWith(that as com.android.builder.model.BuildType)
    }

    override fun initWith(that: com.android.builder.model.BuildType): BuildType {
        // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (that === this) {
            return this
        }
        dslChecksEnabled = false
        if (that is BuildType) {
            that.dslChecksEnabled = false
        }
        try {
            if(that is ExtensionAware) {
                initExtensions(from=that, to=this)
            }
            return super.initWith(that) as BuildType
        } finally {
            dslChecksEnabled = true
            if (that is BuildType) {
                that.dslChecksEnabled = true
            }
        }
    }

    /** Internal implementation detail, See comment on [InternalBuildType] */
    fun initWith(that: InternalBuildType) {
         initWith(that as com.android.builder.model.BuildType)
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
