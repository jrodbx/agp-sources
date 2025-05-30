/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.ide.common.rendering.api;

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.SessionParams.Key;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for rendering parameters. This include the generic parameters but not what needs
 * to be rendered or additional parameters.
 */
public abstract class RenderParams {
    public static final long DEFAULT_TIMEOUT = 250; //ms

    private final Object mProjectKey;
    private final HardwareConfig mHardwareConfig;
    private final RenderResources mRenderResources;
    private final LayoutlibCallback mLayoutlibCallback;
    private final int mMinSdkVersion;
    private final int mTargetSdkVersion;
    /** The configuration uiMode, see {@link android.content.res.Configuration#uiMode}. */
    private int mUiMode = 0;

    private float mFontScale = 1f;
    private final ILayoutLog mLog;

    private boolean mSetTransparentBackground;
    private long mTimeout;

    private AssetRepository mAssetRepository;
    private IImageFactory mImageFactory;

    private ResourceValue mAppIcon;
    private String mAppLabel;
    private String mLocale;
    private String mActivityName;
    private boolean mForceNoDecor;
    private boolean mSupportsRtl;

    /**
     * A flexible map to pass additional flags to LayoutLib. LayoutLib will ignore flags that it
     * doesn't recognize.
     */
    private Map<Key, Object> mFlags;
    private boolean mEnableQuickStep;

    /**
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param hardwareConfig the {@link HardwareConfig}.
     * @param renderResources a {@link RenderResources} object providing access to the resources.
     * @param layoutlibCallback The {@link LayoutlibCallback} object to get information from the
     *     project.
     * @param minSdkVersion the minSdkVersion of the project
     * @param targetSdkVersion the targetSdkVersion of the project
     * @param log the object responsible for displaying warning/errors to the user.
     */
    public RenderParams(
            Object projectKey,
            HardwareConfig hardwareConfig,
            RenderResources renderResources,
            LayoutlibCallback layoutlibCallback,
            int minSdkVersion,
            int targetSdkVersion,
            ILayoutLog log) {
        mProjectKey = projectKey;
        mHardwareConfig = hardwareConfig;
        mRenderResources = renderResources;
        mLayoutlibCallback = layoutlibCallback;
        mMinSdkVersion = minSdkVersion;
        mTargetSdkVersion = targetSdkVersion;
        mLog = log;
        mSetTransparentBackground = false;
        mTimeout = DEFAULT_TIMEOUT;
    }

    /**
     * Copy constructor.
     */
    public RenderParams(RenderParams params) {
        mProjectKey = params.mProjectKey;
        mHardwareConfig = params.mHardwareConfig;
        mRenderResources = params.mRenderResources;
        mAssetRepository = params.mAssetRepository;
        mLayoutlibCallback = params.mLayoutlibCallback;
        mMinSdkVersion = params.mMinSdkVersion;
        mTargetSdkVersion = params.mTargetSdkVersion;
        mUiMode = params.mUiMode;
        mLog = params.mLog;
        mSetTransparentBackground = params.mSetTransparentBackground;
        mTimeout = params.mTimeout;
        mImageFactory = params.mImageFactory;
        mAppIcon = params.mAppIcon;
        mAppLabel = params.mAppLabel;
        mLocale = params.mLocale;
        mActivityName = params.mActivityName;
        mForceNoDecor = params.mForceNoDecor;
        mSupportsRtl = params.mSupportsRtl;
        if (params.mFlags != null) {
            mFlags = new HashMap<>(params.mFlags);
        }
        mEnableQuickStep = params.mEnableQuickStep;
        mFontScale = params.mFontScale;
    }

    public void setTransparentBackground() {
        mSetTransparentBackground = true;
    }

    public void setTimeout(long timeout) {
        mTimeout = timeout;
    }

    public void setImageFactory(IImageFactory imageFactory) {
        mImageFactory = imageFactory;
    }

    /** Sets the application icon resource, or null if there is no icon. */
    public void setAppIcon(@Nullable ResourceValue appIcon) {
        mAppIcon = appIcon;
    }

    /**
     * Sets the application label, or null if there is no label. The label string has to be resolved
     * and should not require further resource resolution.
     */
    public void setAppLabel(@Nullable String appLabel) {
        mAppLabel = appLabel;
    }

    public void setLocale(String locale) {
        mLocale = locale;
    }

    public void setActivityName(String activityName) {
        mActivityName = activityName;
    }

    public void setForceNoDecor() {
        mForceNoDecor = true;
    }

    public void setRtlSupport(boolean supportsRtl) {
        mSupportsRtl = supportsRtl;
    }

    public void setAssetRepository(AssetRepository assetRepository) {
        mAssetRepository = assetRepository;
    }

    /**
     * Enables/disables the quick step mode in the device. When enabled, this will hide the recents
     * button and show the quick step home button.
     */
    public void setQuickStep(boolean quickStep) {
        mEnableQuickStep = quickStep;
    }

    /**
     * Sets user preference for the scaling factor for fonts, relative to the base density scaling.
     * See {@link android.content.res.Configuration#fontScale}
     */
    public void setFontScale(float fontScale) {
        mFontScale = fontScale;
    }

    /**
     * Returns the user preference for the font scaling factor. See {@link
     * android.content.res.Configuration#fontScale}
     */
    public float getFontScale() {
        return mFontScale;
    }

    /** Sets the uiMode. See {@link android.content.res.Configuration#uiMode} */
    public void setUiMode(int uiMode) {
        mUiMode = uiMode;
    }

    /** Returns the uiMode. See {@link android.content.res.Configuration#uiMode} */
    public int getUiMode() {
        return mUiMode;
    }

    public Object getProjectKey() {
        return mProjectKey;
    }

    public HardwareConfig getHardwareConfig() {
        return mHardwareConfig;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    public RenderResources getResources() {
        return mRenderResources;
    }

    public AssetRepository getAssets() {
        return mAssetRepository;
    }

    public LayoutlibCallback getLayoutlibCallback() {
        return mLayoutlibCallback;
    }

    public ILayoutLog getLog() {
        return mLog;
    }

    /**
     * @return if the background color should be transparent (in contrast to match the window theme
     *     background color).
     */
    public boolean isTransparentBackground() {
        return mSetTransparentBackground;
    }

    public long getTimeout() {
        return mTimeout;
    }

    public IImageFactory getImageFactory() {
        return mImageFactory;
    }

    /** Returns the application icon resource, or null if there is no icon. */
    @Nullable
    public final ResourceValue getAppIcon() {
        return mAppIcon;
    }

    /**
     * Returns the application label, or null if there is no label. The label is already resolved
     * and does not require further resource resolution.
     */
    @Nullable
    public final String getAppLabel() {
        return mAppLabel;
    }

    public final String getLocale() {
        return mLocale;
    }

    public final String getActivityName() {
        return mActivityName;
    }

    public final boolean isForceNoDecor() {
        return mForceNoDecor;
    }

    public final boolean isRtlSupported() {
        return mSupportsRtl;
    }

    public final boolean isQuickStepEnabled() {
        return mEnableQuickStep;
    }

    public <T> void setFlag(Key<T> key, T value) {
        if (mFlags == null) {
            mFlags = new HashMap<>();
        }
        mFlags.put(key, value);
    }

    public <T> T getFlag(Key<T> key) {
        // noinspection since the values in the map can be added only by setFlag which ensures that
        // the types match.
        //noinspection unchecked
        return mFlags == null ? null : (T) mFlags.get(key);
    }
}
