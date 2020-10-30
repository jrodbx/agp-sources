/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.sdklib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.sdklib.repository.meta.DetailsTypes;
import java.io.File;

/** An optional library provided by an Android Target */
public interface OptionalLibrary {
    /** The name of the library, as used in the manifest (&lt;uses-library&gt;). */
    @NonNull
    String getName();

    /**
     * Location of the jar file. Should never be {@code null} when retrieved from a target, but may
     * be in some cases when retrieved from an {@link DetailsTypes.AddonDetailsType}.
     */
    @Nullable
    File getJar();

    /** Description of the library. */
    @NonNull
    String getDescription();

    /** Whether the library requires a manifest entry */
    boolean isManifestEntryRequired();

    /**
     * Path to the library jar file relative to the {@code libs} directory in the package. Can be
     * {@code null} when retrieved from a {@link LocalPackage} that was installed from a legacy
     * source.
     */
    @Nullable
    String getLocalJarPath();
}
