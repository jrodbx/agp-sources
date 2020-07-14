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

package com.android.builder.core;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.internal.BaseConfigImpl;
import com.android.builder.model.BuildType;
import com.android.builder.model.SigningConfig;
import com.google.common.base.MoreObjects;

/**
 * Builder-level implementation of BuildType.
 *
 * @deprecated This is deprecated, use DSL objects directly.
 */
@Deprecated
public abstract class AbstractBuildType extends BaseConfigImpl implements BuildType {
    private static final long serialVersionUID = 1L;

    private final String mName;
    private boolean mDebuggable = false;
    private boolean mPseudoLocalesEnabled = false;
    private boolean mTestCoverageEnabled = false;
    private boolean mJniDebuggable = false;
    private boolean mRenderscriptDebuggable = false;
    private int mRenderscriptOptimLevel = 3;
    private boolean mMinifyEnabled = false;
    private SigningConfig mSigningConfig = null;
    private boolean mEmbedMicroApp = true;

    private boolean mZipAlignEnabled = true;

    public AbstractBuildType(@NonNull String name) {
        mName = name;
    }

    /**
     * Copies all properties from the given build type.
     *
     * <p>It can be used like this:
     *
     * <pre>
     * android.buildTypes {
     *     customBuildType {
     *         initWith debug
     *             // customize...
     *         }
     * }
     * </pre>
     */
    public AbstractBuildType initWith(BuildType that) {
        _initWith(that);

        setDebuggable(that.isDebuggable());
        setTestCoverageEnabled(that.isTestCoverageEnabled());
        setJniDebuggable(that.isJniDebuggable());
        setRenderscriptDebuggable(that.isRenderscriptDebuggable());
        setRenderscriptOptimLevel(that.getRenderscriptOptimLevel());
        setVersionNameSuffix(that.getVersionNameSuffix());
        setMinifyEnabled(that.isMinifyEnabled() );
        setZipAlignEnabled(that.isZipAlignEnabled());
        setSigningConfig(that.getSigningConfig());
        setEmbedMicroApp(that.isEmbedMicroApp());
        setPseudoLocalesEnabled(that.isPseudoLocalesEnabled());

        return this;
    }

    /**
     * Name of this build type.
     */
    @Override
    @NonNull
    public String getName() {
        return mName;
    }

    /** Whether this build type should generate a debuggable apk. */
    @NonNull
    public BuildType setDebuggable(boolean debuggable) {
        mDebuggable = debuggable;
        return this;
    }

    /** Whether this build type should generate a debuggable apk. */
    @Override
    public boolean isDebuggable() {
        // Accessing coverage data requires a debuggable package.
        return mDebuggable || mTestCoverageEnabled;
    }


    public void setTestCoverageEnabled(boolean testCoverageEnabled) {
        mTestCoverageEnabled = testCoverageEnabled;
    }

    /**
     * Whether test coverage is enabled for this build type.
     *
     * <p>If enabled this uses Jacoco to capture coverage and creates a report in the build
     * directory.
     *
     * <p>The version of Jacoco can be configured with:
     * <pre>
     * android {
     *   jacoco {
     *     version = '0.6.2.201302030002'
     *   }
     * }
     * </pre>
     *
     */
    @Override
    public boolean isTestCoverageEnabled() {
        return mTestCoverageEnabled;
    }

    public void setPseudoLocalesEnabled(boolean pseudoLocalesEnabled) {
        mPseudoLocalesEnabled = pseudoLocalesEnabled;
    }

    /**
     * Specifies whether the plugin should generate resources for pseudolocales.
     *
     * <p>A pseudolocale is a locale that simulates characteristics of languages that cause UI,
     * layout, and other translation-related problems when an app is localized. Pseudolocales can
     * aid your development workflow because you can test and make adjustments to your UI before you
     * finalize text for translation.
     *
     * <p>When you set this property to <code>true</code> as shown below, the plugin generates
     * resources for the following pseudo locales and makes them available in your connected
     * device's language preferences: <code>en-XA</code> and <code>ar-XB</code>.
     *
     * <pre>
     * android {
     *     buildTypes {
     *         debug {
     *             pseudoLocalesEnabled true
     *         }
     *     }
     * }
     * </pre>
     *
     * <p>When you build your app, the plugin includes the pseudolocale resources in your APK. If
     * you notice that your APK does not include those locale resources, make sure your build
     * configuration isn't limiting which locale resources are packaged with your APK, such as using
     * the <code>resConfigs</code> property to <a
     * href="https://d.android.com/studio/build/shrink-code.html#unused-alt-resources">remove unused
     * locale resources</a>.
     *
     * <p>To learn more, read <a
     * href="https://d.android.com/guide/topics/resources/pseudolocales.html">Test Your App with
     * Pseudolocales</a>.
     */
    @Override
    public boolean isPseudoLocalesEnabled() {
        return mPseudoLocalesEnabled;
    }

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    @NonNull
    public BuildType setJniDebuggable(boolean jniDebugBuild) {
        mJniDebuggable = jniDebugBuild;
        return this;
    }

    /**
     * Whether this build type is configured to generate an APK with debuggable native code.
     */
    @Override
    public boolean isJniDebuggable() {
        return mJniDebuggable;
    }

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    @Override
    public boolean isRenderscriptDebuggable() {
        return mRenderscriptDebuggable;
    }

    /**
     * Whether the build type is configured to generate an apk with debuggable RenderScript code.
     */
    public BuildType setRenderscriptDebuggable(boolean renderscriptDebugBuild) {
        mRenderscriptDebuggable = renderscriptDebugBuild;
        return this;
    }

    /**
     * Optimization level to use by the renderscript compiler.
     */
    @Override
    public int getRenderscriptOptimLevel() {
        return mRenderscriptOptimLevel;
    }

    /** Optimization level to use by the renderscript compiler. */
    public void setRenderscriptOptimLevel(int renderscriptOptimLevel) {
        mRenderscriptOptimLevel = renderscriptOptimLevel;
    }

    /** Whether Minify is enabled for this build type. */
    @NonNull
    public BuildType setMinifyEnabled(boolean enabled) {
        mMinifyEnabled = enabled;
        return this;
    }

    /** Whether Minify is enabled for this build type. */
    @Override
    public boolean isMinifyEnabled() {
        return mMinifyEnabled;
    }


    /** Whether zipalign is enabled for this build type. */
    @NonNull
    public BuildType setZipAlignEnabled(boolean zipAlign) {
        mZipAlignEnabled = zipAlign;
        return this;
    }

    /** Whether zipalign is enabled for this build type. */
    @Override
    public boolean isZipAlignEnabled() {
        return mZipAlignEnabled;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @NonNull
    public BuildType setSigningConfig(@Nullable SigningConfig signingConfig) {
        mSigningConfig = signingConfig;
        return this;
    }

    /** Sets the signing configuration. e.g.: {@code signingConfig signingConfigs.myConfig} */
    @Override
    @Nullable
    public SigningConfig getSigningConfig() {
        return mSigningConfig;
    }

    /**
     * Whether a linked Android Wear app should be embedded in variant using this build type.
     *
     * <p>Wear apps can be linked with the following code:
     *
     * <pre>
     * dependencies {
     *   freeWearApp project(:wear:free') // applies to variant using the free flavor
     *   wearApp project(':wear:base') // applies to all other variants
     * }
     * </pre>
     */
    @Override
    public boolean isEmbedMicroApp() {
        return mEmbedMicroApp;
    }

    public void setEmbedMicroApp(boolean embedMicroApp) {
        mEmbedMicroApp = embedMicroApp;
    }

    @Override
    @NonNull
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("name", mName)
                .add("debuggable", mDebuggable)
                .add("testCoverageEnabled", mTestCoverageEnabled)
                .add("jniDebuggable", mJniDebuggable)
                .add("pseudoLocalesEnabled", mPseudoLocalesEnabled)
                .add("renderscriptDebuggable", mRenderscriptDebuggable)
                .add("renderscriptOptimLevel", mRenderscriptOptimLevel)
                .add("minifyEnabled", mMinifyEnabled)
                .add("zipAlignEnabled", mZipAlignEnabled)
                .add("signingConfig", mSigningConfig)
                .add("embedMicroApp", mEmbedMicroApp)
                .add("mBuildConfigFields", getBuildConfigFields())
                .add("mResValues", getResValues())
                .add("mProguardFiles", getProguardFiles())
                .add("mConsumerProguardFiles", getConsumerProguardFiles())
                .add("mManifestPlaceholders", getManifestPlaceholders())
                .toString();
    }
}
