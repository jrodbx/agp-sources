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

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.Result.Status;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * An object allowing interaction with an Android layout.
 *
 * This is returned by {@link Bridge#createSession(SessionParams)}.
 * and can then be used for subsequent actions on the layout.
 *
 * @since 5
 *
 */
public class RenderSession {

    /**
     * Returns the last operation result.
     */
    public Result getResult() {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Returns the {@link ViewInfo} objects for the top level views.
     * <p>
     * It contains {@code ViewInfo} for only the views in the layout. For {@code ViewInfo} of the
     * System UI surrounding the layout use {@link #getSystemRootViews()}. In most cases the list
     * will only contain one item. If the top level node is a {@code merge} though then it will
     * contain all the items under the {@code merge} tag.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed (and the method returned a {@link Result} with
     * {@link Status#ERROR_UNKNOWN} or {@link Status#NOT_IMPLEMENTED}.
     * <p>
     * This can be safely modified by the caller, but {@code #getSystemRootViews} and
     * {@code #getRootViews} share some view infos, so modifying one result can affect the other.
     *
     * @return the list of {@link ViewInfo} or null if there aren't any.
     *
     * @see #getSystemRootViews()
     */
    public List<ViewInfo> getRootViews() {
        return null;
    }

    /**
     * Returns the {@link ViewInfo} objects for the system decor views, like the ActionBar.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed, or there was no system decor.
     * <p>
     * This can be safely modified by the caller, but {@code #getSystemRootViews} and
     * {@code #getRootViews} share some view infos, so modifying one result can affect the other.
     *
     * @return the list of {@link ViewInfo} or null if there aren't any.
     */
    public List<ViewInfo> getSystemRootViews() {
        return null;
    }

    /**
     * Returns the rendering of the full layout.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed (and the method returned a {@link Result} with
     * {@link Status#ERROR_UNKNOWN} or {@link Status#NOT_IMPLEMENTED}.
     * <p>
     * This can be safely modified by the caller.
     */
    public BufferedImage getImage() {
        return null;
    }

    /**
     * Returns the map of View Cookie → properties (attribute name, attribute value) for all the
     * views that have a view cookie.
     */
    public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultNamespacedProperties() {
        return null;
    }

    /** Returns the map of View Cookie → default style for all the views that have a view cookie. */
    public Map<Object, ResourceReference> getDefaultNamespacedStyles() {
        return null;
    }

    /**
     * Re-renders the layout as-is.
     * In case of success, this should be followed by calls to {@link #getRootViews()} and
     * {@link #getImage()} to access the result of the rendering.
     *
     * This is equivalent to calling <code>render(SceneParams.DEFAULT_TIMEOUT)</code>
     *
     * @return a {@link Result} indicating the status of the action.
     */
    public Result render() {
        return render(RenderParams.DEFAULT_TIMEOUT);
    }

    /**
     * Re-renders the layout as-is, with a given timeout in case other renderings are being done.
     * In case of success, this should be followed by calls to {@link #getRootViews()} and
     * {@link #getImage()} to access the result of the rendering.
     *
     * The {@link Bridge} is only able to inflate or render one layout at a time. There
     * is an internal lock object whenever such an action occurs. The timeout parameter is used
     * when attempting to acquire the lock. If the timeout expires, the method will return
     * {@link Status#ERROR_TIMEOUT}.
     *
     * @param timeout timeout for the rendering, in milliseconds.
     *
     * @return a {@link Result} indicating the status of the action.
     */
    public Result render(long timeout) {
        return render(timeout, false);
    }

    /**
     * Does a measure pass and returns the result.
     * <p>
     * This is equivalent to calling <code>measure(RenderParams.DEFAULT_TIMEOUT)</code>
     * <p>
     * The {@link Bridge} is only able to inflate or render one layout at a time. There
     * is an internal lock object whenever such an action occurs. The timeout parameter is used
     * when attempting to acquire the lock. If the timeout expires, the method will return
     * {@link Status#ERROR_TIMEOUT}.
     * @param timeout timeout for the measure call, in milliseconds.
     * @return a {@link Result} indicating the status of the action.
     */
    public Result measure() {
        return measure(RenderParams.DEFAULT_TIMEOUT);
    }

    /**
     * Does a measure pass and returns the result.
     * <p>
     * The {@link Bridge} is only able to inflate or render one layout at a time. There
     * is an internal lock object whenever such an action occurs. The timeout parameter is used
     * when attempting to acquire the lock. If the timeout expires, the method will return
     * {@link Status#ERROR_TIMEOUT}.
     * @param timeout timeout for the measure call, in milliseconds.
     * @return a {@link Result} indicating the status of the action.
     */
    public Result measure(long timeout) {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Re-renders the layout as-is, with a given timeout in case other renderings are being done.
     * In case of success, this should be followed by calls to {@link #getRootViews()} and
     * {@link #getImage()} to access the result of the rendering.
     * This call also allows triggering a forced measure.
     *
     * The {@link Bridge} is only able to inflate or render one layout at a time. There
     * is an internal lock object whenever such an action occurs. The timeout parameter is used
     * when attempting to acquire the lock. If the timeout expires, the method will return
     * {@link Status#ERROR_TIMEOUT}.
     *
     * @param timeout timeout for the rendering, in milliseconds.
     * @param forceMeasure force running measure for the layout.
     *
     * @return a {@link Result} indicating the status of the action.
     */
    public Result render(long timeout, boolean forceMeasure) {
        return NOT_IMPLEMENTED.createResult();
    }

    /**
     * Sets the current system time in nanos.
     */
    public void setSystemTimeNanos(long nanos) {
        throw new UnsupportedOperationException(
                "Current layoutlib version doesn't support Features.SYSTEM_TIME");
    }

    /**
     * Sets the system boot time in nanos.
     */
    public void setSystemBootTimeNanos(long nanos) {
        throw new UnsupportedOperationException(
                "Current layoutlib version doesn't support Features.SYSTEM_TIME");
    }

    /**
     * Sets the time for which the next frame will be selected. The time is the elapsed time from
     * the current system nanos time.
     */
    public void setElapsedFrameTimeNanos(long nanos) {
        throw new UnsupportedOperationException(
                "Current layoutlib version doesn't support Features.SYSTEM_TIME");
    }

    /**
     * Requests execution of all the callbacks (used e.g. for animations) that should be executed
     * before the specified time.
     *
     * @param nanos absolute time in nanoseconds to know what callbacks to execute.
     * @return true if there are more callbacks left to execute (at a later time) in queue, false
     *     otherwise.
     */
    public boolean executeCallbacks(long nanos) {
        return false;
    }

    /**
     * Type of touch event, a substitute for MotionEvent type, so that clients of layoutlib are kept
     * android platform agnostic.
     */
    public enum TouchEventType {
        PRESS,
        RELEASE,
        DRAG
    }

    /**
     * Inform RenderSession that a touch event happened.
     *
     * @param type type of touch event that happened.
     * @param x horizontal coordinate of a point where touch event happened.
     * @param y vertical coordinate of a point where touch event happened.
     */
    public void triggerTouchEvent(TouchEventType type, int x, int y) {}

    /**
     * Discards the layout. No more actions can be called on this object.
     */
    public void dispose() {
    }

    /** Returns validation data if it exists. */
    @Nullable
    public Object getValidationData() {
        return null;
    }

    /**
     * Executes the {@link Runnable} within the session context, ensuring that the session is valid
     * and the environment is set up.
     *
     * @param r {@link Runnable} to execute
     */
    public void execute(Runnable r) {}
}
