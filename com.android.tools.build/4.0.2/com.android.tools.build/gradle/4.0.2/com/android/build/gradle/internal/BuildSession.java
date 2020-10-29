/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.internal;

import com.android.annotations.NonNull;
import org.gradle.api.invocation.Gradle;

/**
 * Interface to access the actual {@link BuildSessionImpl} singleton object, which might have been
 * created by a different class loader.
 */
public interface BuildSession {

    /**
     * Notifies the {@link BuildSessionImpl} singleton object when a new build starts.
     *
     * <p>This method must be called immediately whenever a new build starts (for each plugin
     * version). It may be called more than once in a build; if so, subsequent calls simply return
     * immediately since the build has already started.
     */
    void initialize(@NonNull Gradle gradle);

    /**
     * Executes the given action immediately, if it has not yet been executed.
     *
     * <p>If an action with the same group and name has already been executed in the current build,
     * this method simply ignores the request.
     */
    void executeOnce(
            @NonNull String actionGroup, @NonNull String actionName, @NonNull Runnable action);

    /**
     * Registers the given action to be executed at the end of the current build, if it has not yet
     * been registered.
     *
     * <p>If an action with the same group and name has already been registered in the current
     * build, this method simply ignores the request.
     *
     * <p>The actions are executed in the order that they are registered. Registration takes effect
     * in the current build only, it will not be carried over into the next build.
     *
     * <p>REQUIREMENT: The registered action should be small and exception-free; it should not
     * modify objects that may still be in use (e.g., by some unfinished worker threads).
     */
    void executeOnceWhenBuildFinished(
            @NonNull String actionGroup, @NonNull String actionName, @NonNull Runnable action);
}
