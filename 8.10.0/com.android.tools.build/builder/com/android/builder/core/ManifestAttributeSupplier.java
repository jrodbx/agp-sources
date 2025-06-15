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

package com.android.builder.core;

import com.android.annotations.Nullable;

/**
 * An interface that provides methods for reading some of the attribute values from the manifest.
 */
@Deprecated
public interface ManifestAttributeSupplier {

    /** Whether the manifest file is required to exist. */
    boolean isManifestFileRequired();

    /**
     * Returns the package name from the manifest file.
     *
     * @return the package name or null if not found.
     */
    @Nullable
    String getPackage();

    /**
     * Returns the split name from the manifest file.
     *
     * @return the split name or null if not found.
     */
    @Nullable
    String getSplit();

    /**
     * Returns the minSdkVersion from the manifest file. The returned value can be an Integer or a
     * String
     *
     * @return the minSdkVersion or null if value is not set.
     */
    Object getMinSdkVersion();

    /**
     * Returns the targetSdkVersion from the manifest file.
     * The returned value can be an Integer or a String
     *
     * @return the targetSdkVersion or null if not found
     */
    Object getTargetSdkVersion();

    /**
     * Returns the instrumentation runner from the instrumentation tag in the manifest file.
     *
     * @return the instrumentation runner or {@code null} if there is none specified.
     */
    @Nullable
    String getInstrumentationRunner();

    /**
     * Returns the targetPackage from the instrumentation tag in the manifest file.
     *
     * @return the targetPackage or {@code null} if there is none specified.
     */
    @Nullable
    String getTargetPackage();

    /**
     * Returns the functionalTest from the instrumentation tag in the manifest file.
     *
     * @return the functionalTest or {@code null} if there is none specified.
     */
    @Nullable
    Boolean getFunctionalTest();

    /**
     * Returns the handleProfiling from the instrumentation tag in the manifest file.
     *
     * @return the handleProfiling or {@code null} if there is none specified.
     */
    @Nullable
    Boolean getHandleProfiling();

    /**
     * Returns the testLabel from the instrumentation tag in the manifest file.
     *
     * @return the testLabel or {@code null} if there is none specified.
     */
    @Nullable
    String getTestLabel();

    /**
     * Returns value of the {@code extractNativeLibs} attribute of the {@code application} tag, if
     * present.
     */
    @Nullable
    Boolean getExtractNativeLibs();

    /**
     * Returns value of the {@code useEmbeddedDex} attribute of the {@code application} tag, if
     * present.
     */
    @Nullable
    Boolean getUseEmbeddedDex();
}
