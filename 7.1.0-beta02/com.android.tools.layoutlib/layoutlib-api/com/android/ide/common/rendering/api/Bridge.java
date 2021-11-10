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

import static com.android.ide.common.rendering.api.Result.Status.NOT_IMPLEMENTED;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * Entry point of the Layout Library. Extensions of this class provide a method to compute and
 * render a layout.
 */
@SuppressWarnings({"UnusedDeclaration"})
public abstract class Bridge {

    public static final int API_CURRENT = 17;

    /** Records whether a Layoutlib native crash has been detected */
    private static boolean sNativeCrash = false;

    /**
     * Initializes the Bridge object.
     *
     * @param platformProperties The build properties for the platform.
     * @param fontLocation the location of the fonts.
     * @param nativeLibDirPath the absolute path of the directory containing all the native
     *     libraries for layoutlib.
     * @param icuDataPath the location of the ICU data used natively.
     * @param enumValueMap map attrName ⇒ { map enumFlagName ⇒ Integer value }. This is typically
     *     read from attrs.xml in the SDK target.
     * @param log a {@link ILayoutLog} object. Can be null.
     * @return true if success.
     */
    public boolean init(
            Map<String, String> platformProperties,
            File fontLocation,
            String nativeLibDirPath,
            String icuDataPath,
            Map<String, Map<String, Integer>> enumValueMap,
            ILayoutLog log) {
        return false;
    }

    /**
     * Prepares the layoutlib to unloaded.
     */
    public boolean dispose() {
        return false;
    }

    /**
     * Starts a layout session by inflating and rendering it. The method returns a
     * {@link RenderSession} on which further actions can be taken.
     *
     * @return a new {@link RenderSession} object that contains the result of the scene creation and
     * first rendering.
     */
    public RenderSession createSession(SessionParams params) {
        return null;
    }

    /**
     * Renders a Drawable. If the rendering is successful, the result image is accessible through
     * {@link Result#getData()}. It is of type {@link BufferedImage}
     * @param params the rendering parameters.
     * @return the result of the action.
     */
    public Result renderDrawable(DrawableParams params) {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Clears the resource cache for a specific project.
     *
     * <p>This cache contains bitmaps and nine patches that are loaded from the disk and reused
     * until this method is called.
     *
     * <p>The cache is not configuration dependent and should only be cleared when a resource
     * changes (at this time only bitmaps and 9 patches go into the cache).
     *
     * <p>The project key provided must be similar to the one passed in {@link RenderParams}.
     *
     * @param projectKey the key for the project.
     */
    public void clearResourceCaches(Object projectKey) {}

    /**
     * Removes a font file from the Typeface cache.
     *
     * @param path path of the font file to remove from the cache
     */
    public void clearFontCache(String path) {}

    /**
     * Clears all caches for a specific project.
     *
     * @param projectKey the key for the project.
     */
    public void clearAllCaches(Object projectKey) {}

    /**
     * Utility method returning the parent of a given view object.
     *
     * @param viewObject the object for which to return the parent.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the parent
     *      object in {@link Result#getData()}
     */
    public Result getViewParent(Object viewObject) {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Utility method returning the index of a given view in its parent.
     * @param viewObject the object for which to return the index.
     *
     * @return a {@link Result} indicating the status of the action, and if success, the index in
     *      the parent in {@link Result#getData()}
     */
    public Result getViewIndex(Object viewObject) {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Returns true if the character orientation of the locale is right to left.
     * @param locale The locale formatted as language-region
     * @return true if the locale is right to left.
     */
    public boolean isRtl(String locale) {
        return false;
    }

    /**
     * Returns a mock view displaying the given label. This mock view should be created by passing
     * the provided arguments to its constructor.
     */
    public Object createMockView(String label, Class<?>[] signature, Object[] args)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException,
                    InvocationTargetException {
        return null;
    }

    public static boolean hasNativeCrash() {
        return sNativeCrash;
    }

    public static void setNativeCrash(boolean nativeCrash) {
        sNativeCrash = nativeCrash;
    }
}
