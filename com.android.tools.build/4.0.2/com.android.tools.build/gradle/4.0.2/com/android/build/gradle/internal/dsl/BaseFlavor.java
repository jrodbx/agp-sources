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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.api.dsl.DslScope;
import com.android.builder.core.AbstractProductFlavor;
import com.android.builder.core.BuilderConstants;
import com.android.builder.core.DefaultApiVersion;
import com.android.builder.internal.ClassFieldImpl;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.ClassField;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

/** Base DSL object used to configure product flavors. */
@SuppressWarnings("deprecation")
public abstract class BaseFlavor extends AbstractProductFlavor implements CoreProductFlavor {

    @NonNull private DslScope dslScope;

    @NonNull private final NdkOptions ndkConfig;

    @NonNull private final ExternalNativeBuildOptions externalNativeBuildOptions;

    @NonNull private final JavaCompileOptions javaCompileOptions;

    @NonNull private final ShaderOptions shaderOptions;

    public BaseFlavor(@NonNull String name, @NonNull DslScope dslScope) {
        super(name, dslScope.getObjectFactory().newInstance(VectorDrawablesOptions.class));
        this.dslScope = dslScope;
        ObjectFactory objectFactory = dslScope.getObjectFactory();
        ndkConfig = objectFactory.newInstance(NdkOptions.class);
        externalNativeBuildOptions =
                objectFactory.newInstance(ExternalNativeBuildOptions.class, objectFactory);
        javaCompileOptions =
                objectFactory.newInstance(
                        JavaCompileOptions.class, objectFactory, dslScope.getDeprecationReporter());
        shaderOptions = objectFactory.newInstance(ShaderOptions.class);
    }

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters. */
    @Nullable
    public NdkOptions getNdk() {
        return ndkConfig;
    }

    @Override
    @Nullable
    public CoreNdkOptions getNdkConfig() {
        return ndkConfig;
    }

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * <p>To learn more, see <a
     * href="http://developer.android.com/studio/projects/add-native-code.html#">Add C and C++ Code
     * to Your Project</a>.
     */
    @NonNull
    public ExternalNativeBuildOptions getExternalNativeBuild() {
        return externalNativeBuildOptions;
    }

    @NonNull
    @Override
    public CoreExternalNativeBuildOptions getExternalNativeBuildOptions() {
        return externalNativeBuildOptions;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        setMinSdkVersion(new DefaultApiVersion(minSdkVersion));
    }

    /**
     * Sets minimum SDK version.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html">
     * uses-sdk element documentation</a>.
     */
    public void minSdkVersion(int minSdkVersion) {
        setMinSdkVersion(minSdkVersion);
    }

    public void setMinSdkVersion(@Nullable String minSdkVersion) {
        setMinSdkVersion(getApiVersion(minSdkVersion));
    }

    /**
     * Sets minimum SDK version.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html">
     * uses-sdk element documentation</a>.
     */
    public void minSdkVersion(@Nullable String minSdkVersion) {
        setMinSdkVersion(minSdkVersion);
    }

    @NonNull
    public com.android.builder.model.ProductFlavor setTargetSdkVersion(int targetSdkVersion) {
        setTargetSdkVersion(new DefaultApiVersion(targetSdkVersion));
        return this;
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html">
     * uses-sdk element documentation</a>.
     */
    public void targetSdkVersion(int targetSdkVersion) {
        setTargetSdkVersion(targetSdkVersion);
    }

    public void setTargetSdkVersion(@Nullable String targetSdkVersion) {
        setTargetSdkVersion(getApiVersion(targetSdkVersion));
    }

    /**
     * Sets the target SDK version to the given value.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html">
     * uses-sdk element documentation</a>.
     */
    public void targetSdkVersion(@Nullable String targetSdkVersion) {
        setTargetSdkVersion(targetSdkVersion);
    }

    /**
     * Sets the maximum SDK version to the given value.
     *
     * <p>See <a href="http://developer.android.com/guide/topics/manifest/uses-sdk-element.html">
     * uses-sdk element documentation</a>.
     */
    public void maxSdkVersion(int maxSdkVersion) {
        setMaxSdkVersion(maxSdkVersion);
    }

    @Nullable
    private static ApiVersion getApiVersion(@Nullable String value) {
        if (!Strings.isNullOrEmpty(value)) {
            if (Character.isDigit(value.charAt(0))) {
                try {
                    int apiLevel = Integer.valueOf(value);
                    return new DefaultApiVersion(apiLevel);
                } catch (NumberFormatException e) {
                    throw new RuntimeException("'" + value + "' is not a valid API level. ", e);
                }
            }

            return new DefaultApiVersion(value);
        }

        return null;
    }

    /**
     * Adds a custom argument to the test instrumentation runner, e.g:
     *
     * <p>
     *
     * <pre>testInstrumentationRunnerArgument "size", "medium"</pre>
     *
     * <p>Test runner arguments can also be specified from the command line:
     *
     * <p>
     *
     * <pre>
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * </pre>
     */
    public void testInstrumentationRunnerArgument(@NonNull String key, @NonNull String value) {
        getTestInstrumentationRunnerArguments().put(key, value);
    }

    /**
     * Adds custom arguments to the test instrumentation runner, e.g:
     *
     * <p>
     *
     * <pre>testInstrumentationRunnerArguments(size: "medium", foo: "bar")</pre>
     *
     * <p>Test runner arguments can also be specified from the command line:
     *
     * <p>
     *
     * <pre>
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.size=medium
     * ./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.foo=bar
     * </pre>
     */
    public void testInstrumentationRunnerArguments(@NonNull Map<String, String> args) {
        getTestInstrumentationRunnerArguments().putAll(args);
    }

    /** Signing config used by this product flavor. */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return (SigningConfig) super.getSigningConfig();
    }

    // -- DSL Methods. TODO remove once the instantiator does what I expect it to do.

    /**
     * Adds a new field to the generated BuildConfig class.
     *
     * <p>The field is generated as: {@code <type> <name> = <value>;}
     *
     * <p>This means each of these must have valid Java content. If the type is a String, then the
     * value should include quotes.
     *
     * @param type the type of the field
     * @param name the name of the field
     * @param value the value of the field
     */
    public void buildConfigField(
            @NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField alreadyPresent = getBuildConfigFields().get(name);
        if (alreadyPresent != null) {
            String flavorName = getName();
            if (BuilderConstants.MAIN.equals(flavorName)) {
                dslScope.getLogger()
                        .info(
                                "DefaultConfig: buildConfigField '{}' value is being replaced: {} -> {}",
                                name,
                                alreadyPresent.getValue(),
                                value);
            } else {
                dslScope.getLogger()
                        .info(
                                "ProductFlavor({}): buildConfigField '{}' "
                                        + "value is being replaced: {} -> {}",
                                flavorName,
                                name,
                                alreadyPresent.getValue(),
                                value);
            }
        }
        addBuildConfigField(new ClassFieldImpl(type, name, value));
    }

    /**
     * Adds a new generated resource.
     *
     * <p>This is equivalent to specifying a resource in res/values.
     *
     * <p>See <a
     * href="http://developer.android.com/guide/topics/resources/available-resources.html">Resource
     * Types</a>.
     *
     * @param type the type of the resource
     * @param name the name of the resource
     * @param value the value of the resource
     */
    public void resValue(@NonNull String type, @NonNull String name, @NonNull String value) {
        ClassField alreadyPresent = getResValues().get(name);
        if (alreadyPresent != null) {
            String flavorName = getName();
            if (BuilderConstants.MAIN.equals(flavorName)) {
                dslScope.getLogger()
                        .info(
                                "DefaultConfig: resValue '{}' value is being replaced: {} -> {}",
                                name,
                                alreadyPresent.getValue(),
                                value);
            } else {
                dslScope.getLogger()
                        .info(
                                "ProductFlavor({}): resValue '{}' value is being replaced: {} -> {}",
                                flavorName,
                                name,
                                alreadyPresent.getValue(),
                                value);
            }
        }
        addResValue(new ClassFieldImpl(type, name, value));
    }

    /**
     * Specifies a ProGuard configuration file that the plugin should use.
     *
     * <p>There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     * <ul>
     *   <li>proguard-android.txt
     *   <li>proguard-android-optimize.txt
     * </ul>
     *
     * <p><code>proguard-android-optimize.txt</code> is identical to <code>proguard-android.txt
     * </code>, exccept with optimizations enabled. You can use <code>
     * getDefaultProguardFile(String filename)</code> to return the full path of each file.
     */
    public void proguardFile(@NonNull Object proguardFile) {
        getProguardFiles().add(dslScope.file(proguardFile));
    }

    /**
     * Specifies ProGuard configuration files that the plugin should use.
     *
     * <p>There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     * <ul>
     *   <li>proguard-android.txt
     *   <li>proguard-android-optimize.txt
     * </ul>
     *
     * <p><code>proguard-android-optimize.txt</code> is identical to <code>proguard-android.txt
     * </code>, exccept with optimizations enabled. You can use <code>
     * getDefaultProguardFile(String filename)</code> to return the full path of each file.
     */
    public void proguardFiles(@NonNull Object... files) {
        for (Object file : files) {
            proguardFile(file);
        }
    }

    /**
     * Sets the ProGuard configuration files.
     *
     * <p>There are two ProGuard rules files that ship with the Android plugin and are used by
     * default:
     *
     * <ul>
     *   <li>proguard-android.txt
     *   <li>proguard-android-optimize.txt
     * </ul>
     *
     * <p><code>proguard-android-optimize.txt</code> is identical to <code>proguard-android.txt
     * </code>, exccept with optimizations enabled. You can use <code>
     * getDefaultProguardFile(String filename)</code> to return the full path of the files.
     */
    public void setProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        getProguardFiles().clear();
        proguardFiles(Iterables.toArray(proguardFileIterable, Object.class));
    }

    /**
     * Adds a proguard rule file to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    public void testProguardFile(@NonNull Object proguardFile) {
        getTestProguardFiles().add(dslScope.file(proguardFile));
    }

    /**
     * Adds proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    public void testProguardFiles(@NonNull Object... proguardFiles) {
        for (Object proguardFile : proguardFiles) {
            testProguardFile(proguardFile);
        }
    }

    /**
     * Specifies proguard rule files to be used when processing test code.
     *
     * <p>Test code needs to be processed to apply the same obfuscation as was done to main code.
     */
    public void setTestProguardFiles(@NonNull Iterable<?> files) {
        getTestProguardFiles().clear();
        testProguardFiles(Iterables.toArray(files, Object.class));
    }

    /**
     * Adds a proguard rule file to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    public void consumerProguardFile(@NonNull Object proguardFile) {
        getConsumerProguardFiles().add(dslScope.file(proguardFile));
    }

    /**
     * Adds proguard rule files to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    public void consumerProguardFiles(@NonNull Object... proguardFiles) {
        for (Object proguardFile : proguardFiles) {
            consumerProguardFile(proguardFile);
        }
    }

    /**
     * Specifies a proguard rule file to be included in the published AAR.
     *
     * <p>This proguard rule file will then be used by any application project that consume the AAR
     * (if proguard is enabled).
     *
     * <p>This allows AAR to specify shrinking or obfuscation exclude rules.
     *
     * <p>This is only valid for Library project. This is ignored in Application project.
     */
    public void setConsumerProguardFiles(@NonNull Iterable<?> proguardFileIterable) {
        getConsumerProguardFiles().clear();
        consumerProguardFiles(Iterables.toArray(proguardFileIterable, Object.class));
    }

    /** Encapsulates per-variant configurations for the NDK, such as ABI filters. */
    public void ndk(Action<NdkOptions> action) {
        action.execute(ndkConfig);
    }

    /**
     * Encapsulates per-variant CMake and ndk-build configurations for your external native build.
     *
     * <p>To learn more, see <a
     * href="http://developer.android.com/studio/projects/add-native-code.html#">Add C and C++ Code
     * to Your Project</a>.
     */
    public void externalNativeBuild(@NonNull Action<ExternalNativeBuildOptions> action) {
        action.execute(externalNativeBuildOptions);
    }

    /**
     * Specifies an <a
     * href="https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources">
     * alternative resource</a> to keep.
     *
     * <p>For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the language that your app officially supports,
     * you can specify those languages using the <code>resConfig</code> property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * <pre>
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locale specified below.
     *         resConfig "en"
     *     }
     * }
     * </pre>
     *
     * <p>You can also use this property to filter resources for screen densities. For example,
     * specifying <code>hdpi</code> removes all other screen density resources (such as <code>mdpi
     * </code>, <code>xhdpi</code>, etc) from the final APK.
     *
     * <p><b>Note:</b> <code>auto</code> is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify the locale that your app
     * supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the <code>auto
     * </code> argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * <p>To learn more, see <a
     * href="https://d.android.com/studio/build/shrink-code.html#unused-alt-resources">Remove unused
     * alternative resources</a>.
     */
    public void resConfig(@NonNull String config) {
        addResourceConfiguration(config);
    }

    /**
     * Specifies a list of <a
     * href="https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources">
     * alternative resources</a> to keep.
     *
     * <p>For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the <code>resConfigs</code> property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * <pre>
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * </pre>
     *
     * <p>You can also use this property to filter resources for screen densities. For example,
     * specifying <code>hdpi</code> removes all other screen density resources (such as <code>mdpi
     * </code>, <code>xhdpi</code>, etc) from the final APK.
     *
     * <p><b>Note:</b> <code>auto</code> is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the <code>
     * auto</code> argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * <p>To learn more, see <a
     * href="https://d.android.com/studio/build/shrink-code.html#unused-alt-resources">Remove unused
     * alternative resources</a>.
     */
    public void resConfigs(@NonNull String... config) {
        addResourceConfigurations(config);
    }

    /**
     * Specifies a list of <a
     * href="https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources">
     * alternative resources</a> to keep.
     *
     * <p>For example, if you are using a library that includes language resources (such as
     * AppCompat or Google Play Services), then your APK includes all translated language strings
     * for the messages in those libraries whether the rest of your app is translated to the same
     * languages or not. If you'd like to keep only the languages that your app officially supports,
     * you can specify those languages using the <code>resConfigs</code> property, as shown in the
     * sample below. Any resources for languages not specified are removed.
     *
     * <pre>
     * android {
     *     defaultConfig {
     *         ...
     *         // Keeps language resources for only the locales specified below.
     *         resConfigs "en", "fr"
     *     }
     * }
     * </pre>
     *
     * <p>You can also use this property to filter resources for screen densities. For example,
     * specifying <code>hdpi</code> removes all other screen density resources (such as <code>mdpi
     * </code>, <code>xhdpi</code>, etc) from the final APK.
     *
     * <p><b>Note:</b> <code>auto</code> is no longer supported because it created a number of
     * issues with multi-module projects. Instead, you should specify a list of locales that your
     * app supports, as shown in the sample above. Android plugin 3.1.0 and higher ignore the <code>
     * auto</code> argument, and Gradle packages all string resources your app and its dependencies
     * provide.
     *
     * <p>To learn more, see <a
     * href="https://d.android.com/studio/build/shrink-code.html#unused-alt-resources">Remove unused
     * alternative resources</a>.
     */
    public void resConfigs(@NonNull Collection<String> config) {
        addResourceConfigurations(config);
    }

    /** Options for configuration Java compilation. */
    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return javaCompileOptions;
    }

    public void javaCompileOptions(@NonNull Action<JavaCompileOptions> action) {
        action.execute(javaCompileOptions);
    }

    /** Options for configuring the shader compiler. */
    @NonNull
    @Override
    public CoreShaderOptions getShaders() {
        return shaderOptions;
    }

    /** Configure the shader compiler options for this product flavor. */
    public void shaders(@NonNull Action<ShaderOptions> action) {
        action.execute(shaderOptions);
    }

    /**
     * Deprecated equivalent of {@code vectorDrawablesOptions.generatedDensities}.
     *
     * @deprecated
     */
    @Deprecated
    @Nullable
    public Set<String> getGeneratedDensities() {
        return getVectorDrawables().getGeneratedDensities();
    }

    @Deprecated
    public void setGeneratedDensities(@Nullable Iterable<String> densities) {
        getVectorDrawables().setGeneratedDensities(densities);
    }

    /** Configures {@link VectorDrawablesOptions}. */
    public void vectorDrawables(Action<VectorDrawablesOptions> action) {
        action.execute(getVectorDrawables());
    }

    /** Options to configure the build-time support for {@code vector} drawables. */
    @NonNull
    @Override
    public VectorDrawablesOptions getVectorDrawables() {
        return (VectorDrawablesOptions) super.getVectorDrawables();
    }

    /**
     * Sets whether to enable unbundling mode for embedded wear app.
     *
     * <p>If true, this enables the app to transition from an embedded wear app to one distributed
     * by the play store directly.
     */
    public void wearAppUnbundled(@Nullable Boolean wearAppUnbundled) {
        setWearAppUnbundled(wearAppUnbundled);
    }
}
