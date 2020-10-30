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
import com.android.build.gradle.internal.api.TestedVariant;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Zip;

/** A Build variant and all its public data. */
public interface LibraryVariant extends BaseVariant, TestedVariant {

    /**
     * Returns the Library AAR packaging task.
     *
     * @deprecated Use {@link #getPackageLibraryProvider()}
     */
    @Nullable
    @Deprecated
    Zip getPackageLibrary();

    /**
     * Returns the {@link TaskProvider} for the Library AAR packaging task.
     *
     * <p>Prefer this to {@link #getPackageLibrary()} as it triggers eager configuration of the
     * task.
     */
    @Nullable
    TaskProvider<Zip> getPackageLibraryProvider();
}
