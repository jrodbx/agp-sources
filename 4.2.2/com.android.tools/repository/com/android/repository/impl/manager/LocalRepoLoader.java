/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.repository.impl.manager;

import com.android.annotations.NonNull;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.RepoPackage;
import java.util.Map;

/**
 * A facility for loading {@link RepoPackage}s that are installed locally.
 */
public interface LocalRepoLoader {


    /**
     * Gets our packages, loading them if necessary.
     *
     * @param progress A {@link ProgressIndicator} used to show progress (unimplemented) and
     *                 logging.
     * @return A map of install path to {@link LocalPackage}, containing all the packages found in
     * the given root.
     */
    @NonNull
    Map<String, LocalPackage> getPackages(@NonNull ProgressIndicator progress);

    /**
     * Checks whether it looks like we need to be reloaded.
     *
     * @param lastLocalRefreshMs The last time a load was done
     * @param deepCheck How vigorously to check. If {@code false}, the check should be quite fast.
     *                  If {@code true} it should still be reasonably fast, but less so.
     * @return {@code true} if a reload is needed, {@code false} otherwise.
     */
    boolean needsUpdate(long lastLocalRefreshMs, boolean deepCheck);
}
