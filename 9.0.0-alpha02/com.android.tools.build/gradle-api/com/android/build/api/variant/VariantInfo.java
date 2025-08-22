/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.api.variant;

import com.android.annotations.NonNull;
import com.google.common.collect.ImmutableList;

/**
 * Information about the variant being built.
 *
 * <p>Only the Android Gradle Plugin should create instances of this interface.
 *
 * <p>Immutable, no access to tasks
 * @deprecated
 */
@Deprecated
public interface VariantInfo {

    /** Returns the name of the variant. This is composed of the build types and flavors */
    @NonNull
    String getFullVariantName();

    /**
     * Returns the name of the build type.
     *
     * <p>By convention, build-type settings should override flavor settings.
     */
    @NonNull
    String getBuildTypeName();

    /**
     * Returns a list of flavor names that make up this variant.
     *
     * <p>By convention settings from earlier flavors should override settings from later flavors.
     *
     * @return the ordered list of flavor names. May be empty.
     */
    @NonNull
    ImmutableList<String> getFlavorNames();

    /** Returns true if this is a test variant */
    boolean isTest();

    /** Returns true if the variant is debuggable */
    boolean isDebuggable();
}
