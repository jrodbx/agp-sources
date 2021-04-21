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

import com.android.build.api.dsl.ApplicationBaseFlavor
import com.android.build.api.dsl.DynamicFeatureBaseFlavor
import com.android.build.api.dsl.LibraryBaseFlavor
import com.android.build.api.dsl.Ndk
import com.android.build.api.dsl.Shaders
import com.android.build.api.dsl.TestBaseFlavor
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.AbstractProductFlavor
import com.android.builder.core.BuilderConstants
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultVectorDrawablesOptions
import com.android.builder.internal.ClassFieldImpl
import com.android.builder.model.ApiVersion
import com.android.builder.model.BaseConfig
import com.android.builder.model.ProductFlavor
import com.google.common.base.Strings
import java.io.File
import org.gradle.api.Action

/** Base DSL object used to configure product flavors.  */
abstract class BaseFlavor(name: String, private val dslServices: DslServices) :
    AbstractProductFlavor(name),
    CoreProductFlavor,
    ApplicationBaseFlavor<SigningConfig>,
    DynamicFeatureBaseFlavor,
    LibraryBaseFlavor<SigningConfig>,
    TestBaseFlavor<SigningConfig> {

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters.  */
    override val ndk: NdkOptions = dslServices.newInstance(NdkOptions::class.java)

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
        get() = maxSdkVersion
        set(value) {
            maxSdkVersion = value
        }
    override var minSdk: Int?
        get() = minSdkVersion?.apiLevel
        set(value) {
            if (value == null) minSdkVersion = null
            else setMinSdkVersion(value)
        }
    override var minSdkPreview: String?
        get() = minSdkVersion?.codename
        set(value) {
            setMinSdkVersion(value)
        }

    override var targetSdk: Int?
        get() = targetSdkVersion?.apiLevel
        set(value) {
            if (value == null) targetSdkVersion = null
            else setTargetSdkVersion(value)
        }
    override var targetSdkPreview: String?
        get() = targetSdkVersion?.codename
        set(value) {
            setTargetSdkVersion(value)
        }

    fun setMinSdkVersion(minSdkVersion: Int) {
        setMinSdkVersion(DefaultApiVersion(minSdkVersion))
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun minSdkVersion(minSdkVersion: Int) {
        setMinSdkVersion(minSdkVersion)
    }

    open fun setMinSdkVersion(minSdkVersion: String?) {
        setMinSdkVersion(getApiVersion(minSdkVersion))
    }

    /**
     * Sets minimum SDK version.
     *
     * See [uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun minSdkVersion(minSdkVersion: String?) {
        setMinSdkVersion(minSdkVersion)
    }

    fun setTargetSdkVersion(targetSdkVersion: Int): ProductFlavor {
        setTargetSdkVersion(DefaultApiVersion(targetSdkVersion))
        return this
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun targetSdkVersion(targetSdkVersion: Int) {
        setTargetSdkVersion(targetSdkVersion)
    }

    fun setTargetSdkVersion(targetSdkVersion: String?) {
        setTargetSdkVersion(getApiVersion(targetSdkVersion))
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun targetSdkVersion(targetSdkVersion: String?) {
        setTargetSdkVersion(targetSdkVersion)
    }

    /**
     * Sets the maximum SDK version to the given value.
     *
     * See [
 * uses-sdk element documentation](http://developer.android.com/guide/topics/manifest/uses-sdk-element.html).
     */
    open fun maxSdkVersion(maxSdkVersion: Int) {
        setMaxSdkVersion(maxSdkVersion)
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
    fun testInstrumentationRunnerArgument(key: String, value: String) {
        testInstrumentationRunnerArguments[key] = value
    }

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
    open fun testInstrumentationRunnerArguments(args: Map<String, String>) {
        testInstrumentationRunnerArguments.putAll(args)
    }

    /** Signing config used by this product flavor.  */
    override var signingConfig: SigningConfig?
        get() = super.signingConfig as SigningConfig?
        set(value) { super.setSigningConfig(value) }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.
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
                        "DefaultConfig: buildConfigField '{}' value is being replaced: {} -> {}",
                        name,
                        alreadyPresent.value,
                        value
                    )
            } else {
                dslServices.logger
                    .info(
                        "ProductFlavor({}): buildConfigField '{}' " +
                                "value is being replaced: {} -> {}",
                        flavorName,
                        name,
                        alreadyPresent.value,
                        value
                    )
            }
        }
        addBuildConfigField(ClassFieldImpl(type, name, value))
    }

    override fun resValue(type: String, name: String, value: String) {
        val alreadyPresent = resValues[name]
        if (alreadyPresent != null) {
            val flavorName = getName()
            if (BuilderConstants.MAIN == flavorName) {
                dslServices.logger
                    .info(
                        "DefaultConfig: resValue '{}' value is being replaced: {} -> {}",
                        name,
                        alreadyPresent.value,
                        value
                    )
            } else {
                dslServices.logger
                    .info(
                        "ProductFlavor({}): resValue '{}' value is being replaced: {} -> {}",
                        flavorName,
                        name,
                        alreadyPresent.value,
                        value
                    )
            }
        }
        addResValue(ClassFieldImpl(type, name, value))
    }

    override val proguardFiles: MutableList<File>
        get() = super.proguardFiles

    override fun proguardFile(proguardFile: Any) {
        proguardFiles.add(dslServices.file(proguardFile))
    }

    override fun proguardFiles(vararg files: Any) {
        for (file in files) {
            proguardFile(file)
        }
    }

    fun setProguardFiles(proguardFileIterable: Iterable<Any>) {
        proguardFiles.clear()
        for (file in proguardFileIterable) {
            proguardFile(file)
        }
    }

    override var testProguardFiles: MutableList<File>
        get() = super.testProguardFiles
        set(value) {
            // Override to handle the testProguardFiles = ['string'] case.
            setTestProguardFiles(value)
        }

    override fun testProguardFile(proguardFile: Any) {
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

    override fun externalNativeBuild(action: com.android.build.api.dsl.ExternalNativeBuildOptions.() -> Unit) {
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
    fun resConfig(config: String) {
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
    fun resConfigs(vararg config: String) {
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
    fun resConfigs(config: Collection<String>) {
        addResourceConfigurations(config)
    }

    override val javaCompileOptions: JavaCompileOptions =
        dslServices.newInstance(JavaCompileOptions::class.java, dslServices)

    override fun javaCompileOptions(action: com.android.build.api.dsl.JavaCompileOptions.() -> Unit) {
        action.invoke(javaCompileOptions)
    }

    fun javaCompileOptions(action: Action<JavaCompileOptions>) {
        action.execute(javaCompileOptions)
    }

    override val shaders: ShaderOptions =
        dslServices.newInstance(ShaderOptions::class.java)

    /** Configure the shader compiler options for this product flavor.  */
    fun shaders(action: Action<ShaderOptions>) {
        action.execute(shaders)
    }

    override fun shaders(action: Shaders.() -> Unit) {
        action.invoke(shaders)
    }

    override val aarMetadata: AarMetadata = dslServices.newInstance(AarMetadata::class.java)

    override fun aarMetadata(action: com.android.build.api.dsl.AarMetadata.() -> Unit) {
        action.invoke(aarMetadata)
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

    private fun getApiVersion(value: String?): ApiVersion? {
        return if (!Strings.isNullOrEmpty(value)) {
            if (Character.isDigit(value!![0])) {
                try {
                    val apiLevel = Integer.valueOf(value)
                    DefaultApiVersion(apiLevel)
                } catch (e: NumberFormatException) {
                    throw RuntimeException("'$value' is not a valid API level. ", e)
                }
            } else DefaultApiVersion(value)
        } else null
    }

    override fun _initWith(that: BaseConfig) {
        super._initWith(that)
        if (that is ProductFlavor) {
            _vectorDrawables =
                DefaultVectorDrawablesOptions.copyOf(that.vectorDrawables) as VectorDrawablesOptions
        }
    }
}
