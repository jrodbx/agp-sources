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

package com.android.builder.model;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.Collection;
import java.util.List;

/**
 * Options for aapt.
 */
public interface AaptOptions {
    enum Namespacing {
        /**
         * Resources are not namespaced.
         *
         * <p>They are merged at the application level, as was the behavior with AAPT1
         */
        DISABLED,
        /**
         * Resources must be namespaced.
         *
         * <p>Each library is compiled in to an AAPT2 static library with its own namespace.
         *
         * <p>Projects using this <em>cannot</em> consume non-namespaced dependencies.
         */
        REQUIRED,
        // TODO: add more modes as implemented.
    }

    /** Returns the value for the --ignore-assets option, or null */
    @Nullable
    String getIgnoreAssets();

    /** Returns the list of values for the -0 (disabled compression) option, or null */
    @Nullable
    Collection<String> getNoCompress();

    /**
     * passes the --error-on-missing-config-entry parameter to the aapt command, by default false.
     */
    boolean getFailOnMissingConfigEntry();

    /** Returns the list of additional parameters to pass. */
    @NonNull
    List<String> getAdditionalParameters();

    /** Returns the resource namespacing strategy for this sub-project */
    @NonNull
    Namespacing getNamespacing();
}
