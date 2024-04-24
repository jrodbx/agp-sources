/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.annotations.NonNull;
import java.util.List;
import java.util.function.Function;

/**
 * Rendering parameters for a {@link RenderSession}.
 */
public class SessionParams extends RenderParams {

    public enum RenderingMode {
        NORMAL(SizeAction.KEEP, SizeAction.KEEP),
        V_SCROLL(SizeAction.KEEP, SizeAction.EXPAND),
        H_SCROLL(SizeAction.EXPAND, SizeAction.KEEP),
        FULL_EXPAND(SizeAction.EXPAND, SizeAction.EXPAND),
        // Shrink canvas to the minimum size that is needed to cover the scene
        SHRINK(SizeAction.SHRINK, SizeAction.SHRINK);

        public enum SizeAction {
            EXPAND,
            KEEP,
            SHRINK
        }

        private final SizeAction mHorizAction;
        private final SizeAction mVertAction;

        RenderingMode(@NonNull SizeAction horizAction, @NonNull SizeAction vertAction) {
            mHorizAction = horizAction;
            mVertAction = vertAction;
        }

        public SizeAction getHorizAction() {
            return mHorizAction;
        }

        public SizeAction getVertAction() {
            return mVertAction;
        }
    }

    private final ILayoutPullParser mLayoutDescription;
    private final RenderingMode mRenderingMode;
    private boolean mExtendedViewInfoMode = false;
    private final int mSimulatedPlatformVersion;
    private Function<Object, List<ViewInfo>> mCustomContentHierarchyParser = null;

    /**
     * @param layoutDescription the {@link ILayoutPullParser} letting the LayoutLib Bridge visit the
     *     layout file.
     * @param renderingMode The rendering mode.
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param hardwareConfig the {@link HardwareConfig}.
     * @param renderResources a {@link RenderResources} object providing access to the resources.
     * @param layoutlibCallback The {@link LayoutlibCallback} object to get information from the
     *     project.
     * @param minSdkVersion the minSdkVersion of the project
     * @param targetSdkVersion the targetSdkVersion of the project
     * @param log the object responsible for displaying warning/errors to the user.
     */
    public SessionParams(
            ILayoutPullParser layoutDescription,
            RenderingMode renderingMode,
            Object projectKey,
            HardwareConfig hardwareConfig,
            RenderResources renderResources,
            LayoutlibCallback layoutlibCallback,
            int minSdkVersion,
            int targetSdkVersion,
            ILayoutLog log) {
        this(layoutDescription, renderingMode, projectKey, hardwareConfig,
                renderResources, layoutlibCallback, minSdkVersion, targetSdkVersion, log, 0);
    }

    /**
     * @param layoutDescription the {@link ILayoutPullParser} letting the LayoutLib Bridge visit the
     *     layout file.
     * @param renderingMode The rendering mode.
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param hardwareConfig the {@link HardwareConfig}.
     * @param renderResources a {@link RenderResources} object providing access to the resources.
     * @param projectCallback The {@link LayoutlibCallback} object to get information from the
     *     project.
     * @param minSdkVersion the minSdkVersion of the project
     * @param targetSdkVersion the targetSdkVersion of the project
     * @param log the object responsible for displaying warning/errors to the user.
     * @param simulatedPlatformVersion try to simulate an old android platform. 0 means disabled.
     */
    public SessionParams(
            ILayoutPullParser layoutDescription,
            RenderingMode renderingMode,
            Object projectKey,
            HardwareConfig hardwareConfig,
            RenderResources renderResources,
            LayoutlibCallback projectCallback,
            int minSdkVersion,
            int targetSdkVersion,
            ILayoutLog log,
            int simulatedPlatformVersion) {
        super(projectKey, hardwareConfig, renderResources, projectCallback,
                minSdkVersion, targetSdkVersion, log);

        mLayoutDescription = layoutDescription;
        mRenderingMode = renderingMode;
        mSimulatedPlatformVersion = simulatedPlatformVersion;
    }

    public SessionParams(SessionParams params) {
        super(params);
        mLayoutDescription = params.mLayoutDescription;
        mRenderingMode = params.mRenderingMode;
        mSimulatedPlatformVersion = params.mSimulatedPlatformVersion;
        mExtendedViewInfoMode = params.mExtendedViewInfoMode;
        mCustomContentHierarchyParser = params.mCustomContentHierarchyParser;
    }

    public ILayoutPullParser getLayoutDescription() {
        return mLayoutDescription;
    }

    public RenderingMode getRenderingMode() {
        return mRenderingMode;
    }

    public void setExtendedViewInfoMode(boolean mode) {
        mExtendedViewInfoMode = mode;
    }

    public boolean getExtendedViewInfoMode() {
        return mExtendedViewInfoMode;
    }

    /**
     * Sets a custom parser to create the {@link ViewInfo} hierarchy, that will replace the default
     * parser used by layoutlib.
     *
     * @param parser function that will be applied to the content root of the layout, and should
     *     return a list of the {@link ViewInfo}s for its children
     */
    public void setCustomContentHierarchyParser(Function<Object, List<ViewInfo>> parser) {
        mCustomContentHierarchyParser = parser;
    }

    public Function<Object, List<ViewInfo>> getCustomContentHierarchyParser() {
        return mCustomContentHierarchyParser;
    }

    public int getSimulatedPlatformVersion() {
        return mSimulatedPlatformVersion;
    }

    /**
     * The class should be in RenderParams, but is here because it was
     * originally here and we cannot change the API without breaking backwards
     * compatibility.
     */
    public static class Key<T> {
        public final Class<T> mExpectedClass;
        public final String mName;

        public Key(String name, Class<T> expectedClass) {
            assert name != null;
            assert expectedClass != null;

            mExpectedClass = expectedClass;
            mName = name;
        }

        @Override
        public int hashCode() {
            int result = mExpectedClass.hashCode();
            return 31 * result + mName.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj != null && getClass() == obj.getClass()) {
                Key<?> k = (Key<?>) obj;
                return mExpectedClass.equals(k.mExpectedClass) && mName.equals(k.mName);
            }
            return false;
        }

        @Override
        public String toString() {
            return mName;
        }
    }
}
