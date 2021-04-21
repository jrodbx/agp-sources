/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.api;

import com.android.annotations.Nullable;
import com.android.build.gradle.internal.core.InternalBaseVariant;
import com.android.build.gradle.tasks.PackageAndroidArtifact;
import org.gradle.api.tasks.TaskProvider;

/** A Build variant and all its public data. */
public interface ApkVariant
        extends BaseVariant, InstallableVariant, AndroidArtifactVariant, InternalBaseVariant {

    /**
     * Returns the Dex task.
     *
     * This method will actually throw an exception with a clear message.
     *
     * @deprecated  With the new transform mechanism, there is no direct access to the task anymore.
     */
    @Deprecated
    @Nullable
    Object getDex();

    /**
     * Returns the packaging tas
     *
     * @deprecated Use {@link #getPackageApplicationProvider()}
     */
    @Nullable
    @Deprecated
    PackageAndroidArtifact getPackageApplication();

    /**
     * Returns the packaging task
     *
     * <p>Prefer this to {@link #getPackageApplication()} as it triggers eager configuration of the
     * task.
     */
    @Nullable
    TaskProvider<PackageAndroidArtifact> getPackageApplicationProvider();

}
