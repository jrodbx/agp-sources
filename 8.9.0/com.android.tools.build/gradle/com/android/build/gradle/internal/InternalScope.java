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

package com.android.build.gradle.internal;

import com.android.build.api.transform.QualifiedContent;

/**
 * Definition of all internal scopes.
 *
 */
public enum InternalScope implements QualifiedContent.ScopeType {

    /**
     * Scope to package classes.dex files in the main split APK in InstantRun mode. All other
     * classes.dex will be packaged in other split APKs.
     */
    MAIN_SPLIT(0x10000),

    /**
     * Only the project's local dependencies (local jars). This is to be used by the library plugin
     * only (and only when building the AAR).
     */
    LOCAL_DEPS(0x20000),

    /** Only the project's feature or dynamic-feature modules. */
    FEATURES(0x40000),
    ;

    private final int value;

    InternalScope(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }
}
