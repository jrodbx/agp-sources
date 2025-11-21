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
import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.api.dsl.BaseFlavor
import com.android.build.api.dsl.DynamicFeatureBaseFlavor
import com.android.build.api.dsl.LibraryBaseFlavor
import com.android.build.api.dsl.MaxSdkSpec
import com.android.build.api.dsl.MaxSdkVersion
import com.android.build.api.dsl.MinSdkSpec
import com.android.build.api.dsl.MinSdkVersion
import com.android.build.api.dsl.Ndk
import com.android.build.api.dsl.Shaders
import com.android.build.api.dsl.TargetSdkSpec
import com.android.build.api.dsl.TargetSdkVersion
import com.android.build.api.dsl.TestBaseFlavor
import com.android.build.api.variant.impl.ResValueKeyImpl
import com.android.build.gradle.internal.services.DslServices
import com.android.build.gradle.internal.utils.updateIfChanged
import com.android.build.gradle.options.BooleanOption
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultApiVersion
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseConfig
import com.android.builder.model.ProductFlavor
import com.google.common.collect.Iterables
import java.io.File
import org.gradle.api.Action
import org.gradle.api.provider.Provider
import org.gradle.declarative.dsl.model.annotations.Adding
import org.gradle.declarative.dsl.model.annotations.Restricted

/** Base DSL object used to configure product flavors.  */
abstract class BaseFlavor(name: String, private val dslServices: DslServices) :
    AbstractProductFlavor(name),
    CoreProductFlavor,
    ApplicationBaseFlavor,
    DynamicFeatureBaseFlavor,
    LibraryBaseFlavor,
    TestBaseFlavor {

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    override val ndk: NdkOptions = dslServices.newInstance(NdkOptions::class.java)

    private val disallowProviderInAndroidSourceSet =
        dslServices.projectOptions.get(BooleanOption.DISALLOW_PROVIDER_IN_ANDROID_SOURCE_SET)

    override val ndkConfig: CoreNdkOptions
        get() {
            return ndk
        }

    override val externalNativeBuild: ExternalNativeBuildOptions =
        dslServices.newInstance(ExternalNativeBuildOptions::class.java, dslServices)

    override val externalNativeBuildOptions: CoreExternalNativeBuildOptions
        get() {
            return this.externalNativeBuild
        }
    override var maxSdk: Int?
        get() = _maxSdk?.apiLevel
        set(value) {
            maxSdk { version = value?.let { release(it) } }
        }

    override var maxSdkVersion: Int?
        get() = _maxSdk?.apiLevel
        set(value) {
            maxSdk { version = value?.let { release(it) } }
        }

    protected abstract var _minSdk: MinSdkVersion?

    private val minSdkDelegate = MinSdkDelegate(
        getMinSdk = { _minSdk },
        setMinSdk = { _minSdk = it },
        dslServices = dslServices
    )

    @get:Restricted
    override var minSdk: Int?
        get() = minSdkDelegate.minSdk
        set(value) {
            minSdkDelegate.minSdk = value
        }

    override var minSdkVersion: ApiVersion?
        get() = minSdkDelegate.minSdkVersion
        set(value) {
            minSdkDelegate.minSdkVersion = value
        }

    override fun minSdk(action: MinSdkSpec.() -> Unit) {
        minSdkDelegate.minSdk(action)
    }

    open fun minSdk(action: Action<MinSdkSpec>) {
        minSdkDelegate.minSdk(action)
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `minSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("minSdk {}")
    )
    open fun minSdk(version: Int) {
        setMinSdkVersion(version)
    }

    override var minSdkPreview: String?
        get() =  minSdkDelegate.minSdkPreview
        set(value) {
            minSdkDelegate.minSdkPreview = value
        }

    protected abstract var _targetSdk: TargetSdkVersion?

    override fun targetSdk(action: TargetSdkSpec.() -> Unit) {
        createTargetSdkSpec().also {
            action.invoke(it)
            updateIfChanged(_targetSdk, it.version ) {
                _targetSdk = it
            }
        }
    }

    open fun targetSdk(action: Action<TargetSdkSpec>) {
        createTargetSdkSpec().also {
            action.execute(it)
            updateIfChanged(_targetSdk, it.version ) {
                _targetSdk = it
            }
        }
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `targetSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("targetSdk {}")
    )
    open fun targetSdk(version: Int) {
        setTargetSdkVersion(version)
    }

    override var targetSdkVersion: ApiVersion?
        get() = _targetSdk?.let { DefaultApiVersion(it.apiLevel, it.codeName) }
        set(value) {
            if (value == null) {
                _targetSdk = null
            } else {
                val codeName = value.getCodename()
                if (codeName != null) {
                    targetSdk { version = preview(codeName) }
                } else {
                    targetSdk { version = release(value.apiLevel)}
                }
            }
        }

    override var targetSdk: Int?
        get() = _targetSdk?.apiLevel
        set(value) {
            targetSdk { version = value?.let { release(it) } }
        }

    override var targetSdkPreview: String?
        get() = _targetSdk?.codeName
        set(value) {
            setTargetSdkVersion(value)
        }

    override fun setMinSdkVersion(minSdkVersion: Int) {
        minSdkDelegate.setMinSdkVersion(minSdkVersion)
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    override fun minSdkVersion(minSdkVersion: Int) {
        setMinSdkVersion(minSdkVersion)
    }

    override fun setMinSdkVersion(minSdkVersion: String?) {
        minSdkDelegate.setMinSdkVersion(minSdkVersion)
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    override fun minSdkVersion(minSdkVersion: String?) {
        setMinSdkVersion(minSdkVersion)
    }

    fun setTargetSdkVersion(targetSdkVersion: Int): ProductFlavor {
        targetSdk { version = release(targetSdkVersion) }
        return this
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    override fun targetSdkVersion(targetSdkVersion: Int) {
        setTargetSdkVersion(targetSdkVersion)
    }

    override fun setTargetSdkVersion(targetSdkVersion: String?) {
        targetSdk {
            version = targetSdkVersion?.let { targetSdkVersion ->
                val apiLevel = targetSdkVersion.apiVersionToInt()
                if (apiLevel != null) {
                    release(apiLevel)
                } else {
                    preview(targetSdkVersion)
                }
            }
        }
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    override fun targetSdkVersion(targetSdkVersion: String?) {
        setTargetSdkVersion(targetSdkVersion)
    }

    /**
     * Sets the maximum SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    override fun maxSdkVersion(maxSdkVersion: Int) {
        maxSdk { version = release(maxSdkVersion) }
    }

    protected abstract var _maxSdk: MaxSdkVersion?

    override fun maxSdk(action: MaxSdkSpec.() -> Unit) {
        createMaxSdkSpec().also {
            action.invoke(it)
            updateIfChanged(_maxSdk, it.version) {
                _maxSdk = it
            }
        }
    }

    open fun maxSdk(action: Action<MaxSdkSpec>) {
        createMaxSdkSpec().also {
            action.execute(it)
            updateIfChanged(_maxSdk, it.version) {
                _maxSdk = it
            }
        }
    }

    //TODO(b/421964815): remove the support for groovy space assignment(e.g `maxSdk 24`).
    @Deprecated(
        "To be removed after Gradle drops space assignment support",
        ReplaceWith("maxSdk {}")
    )
    open fun maxSdk(version: Int) {
        maxSdkVersion(version)
    }

    /**
     * Adds a custom argument to the test instrumentation runner, e.g:
     *
     * `testInstrumentationRunnerArgument "size", "medium"`
     *
     * Test runner arguments can also be specified from the command line
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    override fun testInstrumentationRunnerArgument(key: String, value: String) {
        testInstrumentationRunnerArguments[key] = value
    }

    @get:Restricted
    abstract override var testInstrumentationRunner: String?

    /**
     * Adds custom arguments to the test instrumentation runner, e.g:
     *
     * `testInstrumentationRunnerArguments(size: "medium", foo: "bar")`

     * Test runner arguments can also be specified from the command line:
     *
     * ```
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * ```
     */
    override fun testInstrumentationRunnerArguments(args: Map<String, String>) {
        testInstrumentationRunnerArguments.putAll(args)
    }

    /** Signing config used by this product flavor.  */
    override var signingConfig: ApkSigningConfig?
        get() = super.signingConfig
        set(value) { super.signingConfig = value }

    fun setSigningConfig(signingConfig: com.android.build.gradle.internal.dsl.SigningConfig?) {
        this.signingConfig = signingConfig
    }

    fun setSigningConfig(signingConfig: InternalSigningConfig?) {
        this.signingConfig = signingConfig
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.
    @Adding
    override fun buildConfigField(
        type: String,
        name: String,
        value: String
    ) {
        val alreadyPresent = buildConfigFields[name]
        if (alreadyPresent != null) {
            val flavorName = getName()
            if (BuilderConstants.MAIN == flavorName) {
                dslServices.logger
                    .info(
                        "DefaultConfig: buildConfigField '{}' value is being replaced.",
                        name,
                    )
            } else {
                dslServices.logger
                    .info(
                        "ProductFlavor({}): buildConfigField '{}' " +
                                "value is being replaced.",
                        flavorName,
                        name,
                    )
            }
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    override fun resValue(type: String, name: String, value: String) {
        val resValueKey = ResValueKeyImpl(type, name)
        val alreadyPresent = resValues[resValueKey.toString()]
        if (alreadyPresent != null) {
            val flavorName = getName()
            if (BuilderConstants.MAIN == flavorName) {
                dslServices.logger
                    .info(
                        "DefaultConfig: resValue '{}' value is being replaced.",
                        resValueKey.toString(),
                    )
            } else {
                dslServices.logger
                    .info(
                        "ProductFlavor({}): resValue '{}' value is being replaced.",
                        flavorName,
                        resValueKey.toString(),
                    )
            }
        }
        addResValue(resValueKey.toString(), ClassFieldImpl(type, name, value))
    }

    private fun checkNoProvider(target: Any) {
        if (target is Provider<*>) {
            if (disallowProviderInAndroidSourceSet) {
                throw RuntimeException(
                    "Error : You cannot add Provider instances to the Proguard files APIs.\n" +
                            "It is not possible for Android Studio to determine if the Provider points\n" +
                            "to a directory that contains generated (read-only) or static (read-write) files. \n\n" +
                            "Instead you should use the Variant API, in particular\n" +
                            "Variant.proguardFiles and CanProduceConsumerProguardFiles.addStaticDirectories.\n" +
                            "\n" +
                            "You can re-enable the behavior by setting the " +
                            "`${BooleanOption.DISALLOW_PROVIDER_IN_ANDROID_SOURCE_SET.propertyName}=false` to gradle.properties.\n" +
                            "However, be aware that any Gradle Task dependency will not be automatically carried."
                )
            }
        }
    }

    override val proguardFiles: MutableList<File>
        get() = super.proguardFiles

    override fun proguardFile(proguardFile: Any) {
        checkNoProvider(proguardFile)
        proguardFiles.add(dslServices.file(proguardFile))
    }

    override fun proguardFiles(vararg files: Any) {
        for (file in files) {
            proguardFile(file)
        }
    }

    override fun setProguardFiles(proguardFileIterable: Iterable<*>) {
        val replacementFiles = Iterables.toArray(proguardFileIterable, Any::class.java)
        proguardFiles.clear()
        proguardFiles(*replacementFiles)
    }

    override var testProguardFiles: MutableList<File>
        get() = super.testProguardFiles
        set(value) {
            // Override to handle the testProguardFiles = ['string'] case.
            setTestProguardFiles(value)
        }

    override fun testProguardFile(proguardFile: Any) {
        checkNoProvider(proguardFile)
        testProguardFiles.add(dslServices.file(proguardFile))
    }

    override fun testProguardFiles(vararg proguardFiles: Any) {
        for (proguardFile in proguardFiles) {
            testProguardFile(proguardFile)
        }
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     * Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    fun setTestProguardFiles(files: Iterable<Any>) {
        testProguardFiles.clear()
        for (proguardFile in files) {
            testProguardFile(proguardFile)
        }
    }

    override val consumerProguardFiles: MutableList<File>
        get() = super.consumerProguardFiles

    override fun consumerProguardFile(proguardFile: Any) {
        checkNoProvider(proguardFile)
        consumerProguardFiles.add(dslServices.file(proguardFile))
    }

    override fun consumerProguardFiles(vararg proguardFiles: Any) {
        for (proguardFile in proguardFiles) {
            consumerProguardFile(proguardFile)
        }
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * This is only valid for Library project. This is ignored in Application project.
     */
    fun setConsumerProguardFiles(proguardFileIterable: Iterable<Any>) {
        consumerProguardFiles.clear()
        for (proguardFile in proguardFileIterable) {
            consumerProguardFile(proguardFile)
        }
    }

    fun ndk(action: Action<NdkOptions>) {
        action.execute(ndk)
    }

    override fun ndk(action: Ndk.() -> Unit) {
        action.invoke(ndk)
    }

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * To learn more, see
     * [Add C and C++ Code to Your Project](http://developer.android.com/studio/projects/add-native-code.html#).
     */
    fun externalNativeBuild(action: Action<ExternalNativeBuildOptions>) {
        action.execute(externalNativeBuild)
    }

    override fun externalNativeBuild(action: com.android.build.api.dsl.ExternalNativeBuildFlags.() -> Unit) {
        action.invoke(externalNativeBuild)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    override fun resConfig(config: String) {
        addResourceConfiguration(config)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    override fun resConfigs(vararg config: String) {
        addResourceConfigurations(*config)
    }

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * to keep.
     *
     * For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the `resConfigs` property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * ````
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * ````
     *
     * You can also use this property to filter resources for screen densities. For example,
     * specifying `hdpi` removes all other screen density resources (such as `mdpi`,
     * `xhdpi`, etc) from the final APK.
     *
     * **Note:** `auto` is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the `
     * auto` argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * To learn more, see
     * [Remove unused alternative resources](https://d.android.com/studio/build/shrink-code.html#unused-alt-resources).
     */
    override fun resConfigs(config: Collection<String>) {
        addResourceConfigurations(config)
    }

    abstract override val javaCompileOptions: JavaCompileOptions

    override val shaders: ShaderOptions =
        dslServices.newInstance(ShaderOptions::class.java)

    /** Configure the shader compiler options for this product flavor.  */
    fun shaders(action: Action<ShaderOptions>) {
        action.execute(shaders)
    }

    override fun shaders(action: Shaders.() -> Unit) {
        action.invoke(shaders)
    }

    /**
     * Deprecated equivalent of `vectorDrawablesOptions.generatedDensities`.
     */
    @Deprecated("Replace with vectorDrawablesOptions.generatedDensities")
    var generatedDensities: Set<String>?
        get() = vectorDrawables.generatedDensities
        set(densities) {
            vectorDrawables.setGeneratedDensities(densities)
        }

    fun setGeneratedDensities(generatedDensities: Iterable<String>) {
        vectorDrawables.setGeneratedDensities(generatedDensities)
    }

    private var _vectorDrawables: VectorDrawablesOptions =
        dslServices.newInstance(VectorDrawablesOptions::class.java)

    /** Configures [VectorDrawablesOptions].  */
    fun vectorDrawables(action: Action<VectorDrawablesOptions>) {
        action.execute(vectorDrawables)
    }

    override fun vectorDrawables(action: com.android.build.api.dsl.VectorDrawables.() -> Unit) {
        action.invoke(vectorDrawables)
    }

    override val vectorDrawables: VectorDrawablesOptions
        get() = _vectorDrawables

    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     * If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     */
    open fun wearAppUnbundled(wearAppUnbundled: Boolean?) {
        this.wearAppUnbundled = wearAppUnbundled
    }

    private fun createTargetSdkSpec(): TargetSdkSpecImpl {
        return dslServices.newDecoratedInstance(TargetSdkSpecImpl::class.java, dslServices).also {
            it.version = _targetSdk
        }
    }

    private fun createMaxSdkSpec(): MaxSdkSpecImpl {
        return dslServices.newDecoratedInstance(MaxSdkSpecImpl::class.java, dslServices).also {
            it.version = _maxSdk
        }
    }

    private fun createMinSdkSpec(): MinSdkSpecImpl {
        return dslServices.newDecoratedInstance(MinSdkSpecImpl::class.java, dslServices).also {
            it.version = _minSdk
        }
    }

    override fun initWith(that: BaseFlavor) {
        if (that !is com.android.build.gradle.internal.dsl.BaseFlavor) {
            throw RuntimeException("Unexpected implementation type")
        }
        _initWith(that)
    }

    override fun _initWith(that: BaseConfig) {
        super._initWith(that)
        if (that is ProductFlavor) {
            _vectorDrawables = VectorDrawablesOptions.copyOf(that.vectorDrawables)
        }
    }
}
