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

import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.DynamicFeatureBuildType
import com.android.build.api.dsl.LibraryBuildType
import com.android.build.api.dsl.Ndk
import com.android.build.api.dsl.Shaders
import com.android.build.api.dsl.TestBuildType
import com.android.build.gradle.internal.errors.DeprecationReporter
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.AbstractBuildType
import com.android.builder.core.BuilderConstants
import com.android.builder.errors.IssueReporter
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.BaseConfig
import com.android.builder.model.CodeShrinker
import com.google.common.collect.Iterables
import java.io.File
import java.io.Serializable
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Incubating
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

/** DSL object to configure build types.  */
open class BuildType @Inject constructor(
    private val name: String,
    private val dslServices: DslServices
) :
    AbstractBuildType(), CoreBuildType, Serializable,
    ApplicationBuildType<SigningConfig>,
    LibraryBuildType<SigningConfig>,
    DynamicFeatureBuildType,
    TestBuildType<SigningConfig> {

    /**
     * Name of this build type.
     */
    override fun getName(): String {
        return name
    }

    override var isDebuggable: Boolean = false
        get() = // Accessing coverage data requires a debuggable package.
            field || isTestCoverageEnabled

    override var isTestCoverageEnabled: Boolean = false

    override var isPseudoLocalesEnabled: Boolean = false

    override var isJniDebuggable: Boolean = false

    override var isRenderscriptDebuggable: Boolean = false

    override var renderscriptOptimLevel = 3

    override var isZipAlignEnabled: Boolean = true

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

    private val _postProcessing: PostProcessingBlock = dslServices.newInstance(
        PostProcessingBlock::class.java,
        dslServices
    )
    private var _postProcessingConfiguration: PostProcessingConfiguration? = null
    private var postProcessingDslMethodUsed: String? = null
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

    override val matchingFallbacks: MutableList<String> = mutableListOf()

    fun setMatchingFallbacks(fallbacks: List<String>) {
        val newFallbacks = ArrayList(fallbacks)
        matchingFallbacks.clear()
        matchingFallbacks.addAll(newFallbacks)
    }

    fun setMatchingFallbacks(vararg fallbacks: String) {
        matchingFallbacks.clear()
        for (fallback in fallbacks) {
            matchingFallbacks.add(fallback)
        }
    }

    fun setMatchingFallbacks(fallback: String) {
        matchingFallbacks.clear()
        matchingFallbacks.add(fallback)
    }

    override val javaCompileOptions: JavaCompileOptions =
        dslServices.newInstance(JavaCompileOptions::class.java, dslServices)

    override fun javaCompileOptions(action: com.android.build.api.dsl.JavaCompileOptions.() -> Unit) {
        action.invoke(javaCompileOptions)
    }

    override val shaders: ShaderOptions = dslServices.newInstance(ShaderOptions::class.java)

    override val aarMetadata: AarMetadata = dslServices.newInstance(AarMetadata::class.java)

    override fun aarMetadata(action: com.android.build.api.dsl.AarMetadata.() -> Unit) {
        action.invoke(aarMetadata)
    }

    /**
     * Initialize the DSL object with the debug signingConfig. Not meant to be used from the build
     * scripts.
     */
    fun init(debugSigningConfig: SigningConfig?) {
        init()
        if (BuilderConstants.DEBUG == name) {
            assert(debugSigningConfig != null)
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
    override var signingConfig: SigningConfig? = null

    override fun setSigningConfig(signingConfig: com.android.builder.model.SigningConfig?): com.android.builder.model.BuildType {
        this.signingConfig = signingConfig as SigningConfig?
        return this
    }

    fun setSigningConfig(signingConfig: Any?) {
        this.signingConfig = signingConfig as SigningConfig?
    }

    override var isEmbedMicroApp: Boolean = true

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
        externalNativeBuildOptions._initWith(thatBuildType.externalNativeBuildOptions)
        _postProcessing.initWith(that.postprocessing)
        isCrunchPngs = thatBuildType.isCrunchPngs
        isCrunchPngsDefault = thatBuildType.isCrunchPngsDefault
        setMatchingFallbacks(thatBuildType.matchingFallbacks)
        // we don't want to dynamically link these values. We just want to copy the current value.
        isDefault = thatBuildType.isDefault
        aarMetadata.minCompileSdk = thatBuildType.aarMetadata.minCompileSdk
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
                "BuildType(%s): buildConfigField '%s' value is being replaced: %s -> %s",
                getName(), name, alreadyPresent.value, value
            )
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                message
            )
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    override fun resValue(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = resValues[name]
        if (alreadyPresent != null) {
            val message = String.format(
                "BuildType(%s): resValue '%s' value is being replaced: %s -> %s",
                getName(), name, alreadyPresent.value, value
            )
            dslServices.issueReporter.reportWarning(
                IssueReporter.Type.GENERIC,
                message
            )
        }
        addResValue(ClassFieldImpl(type, name, value))
    }

    override var proguardFiles: MutableList<File>
        get() = super.proguardFiles
        set(value) {
            // Override to handle the proguardFiles = ['string'] case (see PluginDslTest.testProguardFiles_*)
            setProguardFiles(value)
        }

    override fun proguardFile(proguardFile: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFile")
        proguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun proguardFiles(vararg files: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "proguardFiles")
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
    fun setProguardFiles(proguardFileIterable: Iterable<*>): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setProguardFiles")
        proguardFiles.clear()
        proguardFiles(
            *Iterables.toArray(
                proguardFileIterable,
                Any::class.java
            )
        )
        return this
    }

    override val testProguardFiles: MutableList<File>
        get() = super.testProguardFiles


    override fun testProguardFile(proguardFile: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFile")
        testProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun testProguardFiles(vararg proguardFiles: Any): BuildType {
        checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "testProguardFiles")
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
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "setTestProguardFiles"
        )
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
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "consumerProguardFile"
        )
        consumerProguardFiles.add(dslServices.file(proguardFile))
        return this
    }

    override fun consumerProguardFiles(vararg proguardFiles: Any): BuildType {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "consumerProguardFiles"
        )
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
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.OLD_DSL, "setConsumerProguardFiles"
        )
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

    override fun externalNativeBuild(action: com.android.build.api.dsl.ExternalNativeBuildOptions.() -> Unit) {
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

    override var isMinifyEnabled: Boolean = false
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
                field
            } else {
                (_postProcessing.isRemoveUnusedCode ||
                        _postProcessing.isObfuscate ||
                        _postProcessing.isOptimizeCode)
            }
        set(value) {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL,
                "setMinifyEnabled"
            )
            field = value
        }

    /**
     * Whether shrinking of unused resources is enabled.
     *
     * Default is false;
     */
    override var isShrinkResources: Boolean
        get() =
            // Try to return a sensible value for the model and other Gradle plugins inspecting the DSL.
            if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
                _shrinkResources
            } else {
                _postProcessing.isRemoveUnusedResources
            }
        set(value) {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.OLD_DSL,
                "setShrinkResources"
            )
            this._shrinkResources = value
        }

    /**
     * Specifies whether to always use ProGuard for code and resource shrinking.
     *
     * By default, when you enable code shrinking by setting
     * [`minifyEnabled`](com.android.build.gradle.internal.dsl.BuildType.html#com.android.build.gradle.internal.dsl.BuildType:minifyEnabled) to `true`, the Android plugin uses R8. If you set
     * this property to `true`, the Android plugin uses ProGuard.
     *
     * To learn more, read
     * [Shrink, obfuscate, and optimize your app](https://developer.android.com/studio/build/shrink-code.html).
     */
    override var isUseProguard: Boolean?
        get() = if (_postProcessingConfiguration != PostProcessingConfiguration.POSTPROCESSING_BLOCK) {
            false
        } else {
            _postProcessing.codeShrinkerEnum == CodeShrinker.PROGUARD
        }
        set(_: Boolean?) {
            checkPostProcessingConfiguration(PostProcessingConfiguration.OLD_DSL, "setUseProguard")
            if (dslChecksEnabled.get()) {
                dslServices.deprecationReporter
                    .reportObsoleteUsage(
                        "useProguard", DeprecationReporter.DeprecationTarget.DSL_USE_PROGUARD
                    )
            }
    }

    fun setUseProguard(useProguard: Boolean) {
        isUseProguard = useProguard
    }

    override var isCrunchPngs: Boolean? = null

    /** This DSL is incubating and subject to change.  */
    @get:Internal
    @get:Incubating
    val postprocessing: PostProcessingBlock
        get() {
            checkPostProcessingConfiguration(
                PostProcessingConfiguration.POSTPROCESSING_BLOCK, "getPostProcessing"
            )
            return _postProcessing
        }

    /** This DSL is incubating and subject to change.  */
    @Incubating
    @Internal
    fun postprocessing(action: Action<PostProcessingBlock>) {
        checkPostProcessingConfiguration(
            PostProcessingConfiguration.POSTPROCESSING_BLOCK, "postProcessing"
        )
        action.execute(_postProcessing)
    }

    /** Describes how postProcessing was configured. Not to be used from the DSL.  */
    // TODO(b/140406102): Should be internal.
    val postProcessingConfiguration: PostProcessingConfiguration
        // If the user didn't configure anything, stick to the old DSL.
        get() = _postProcessingConfiguration ?: PostProcessingConfiguration.OLD_DSL

    /**
     * Checks that the user is consistently using either the new or old DSL for configuring bytecode
     * postProcessing.
     */
    private fun checkPostProcessingConfiguration(
        used: PostProcessingConfiguration,
        methodName: String
    ) {
        if (!dslChecksEnabled.get()) {
            return
        }
        if (_postProcessingConfiguration == null) {
            _postProcessingConfiguration = used
            postProcessingDslMethodUsed = methodName
        } else if (_postProcessingConfiguration != used) {
            assert(postProcessingDslMethodUsed != null)
            val message: String
            message = when (used) {
                PostProcessingConfiguration.POSTPROCESSING_BLOCK -> // TODO: URL with more details.
                    String.format(
                        "The `postProcessing` block cannot be used with together with the `%s` method.",
                        postProcessingDslMethodUsed
                    )
                PostProcessingConfiguration.OLD_DSL -> // TODO: URL with more details.
                    String.format(
                        "The `%s` method cannot be used with together with the `postProcessing` block.",
                        methodName
                    )
                else -> throw AssertionError("Unknown value $used")
            }
            dslServices.issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                message,
                methodName
            )
        }
    }

    override fun initWith(that: com.android.builder.model.BuildType): BuildType {
        // we need to avoid doing this because of Property objects that cannot
        // be set from themselves
        if (that === this) {
            return this
        }
        dslChecksEnabled.set(false)
        try {
            this.signingConfig = that.signingConfig as SigningConfig?
            return super.initWith(that) as BuildType
        } finally {
            dslChecksEnabled.set(true)
        }
    }

    companion object {
        private const val serialVersionUID = 1L
        /**
         * Whether the current thread should check that the both the old and new way of configuring
         * bytecode postProcessing are not used at the same time.
         *
         * The checks are disabled during [.initWith].
         */
        private val dslChecksEnabled =
            ThreadLocal.withInitial { true }
    }
}
