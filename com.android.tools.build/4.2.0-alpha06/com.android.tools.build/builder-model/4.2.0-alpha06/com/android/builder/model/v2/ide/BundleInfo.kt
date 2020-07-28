/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.builder.model.v2.ide

import java.io.File

/**
 * Information for artifact that generates bundle
 *
 * @since 4.2
 */
interface BundleInfo {

    /**
     * Returns the name of the task used to generate the bundle file (.aab), or null if the task is
     * not supported.
     *
     * @return name of the task used to generate the bundle file (.aab)
     */
    val bundleTaskName: String?

    /**
     * Returns the path to the listing file generated after each [.getBundleTaskName] task
     * execution. The listing file will contain a reference to the produced bundle file (.aab).
     * Returns null when [.getBundleTaskName] returns null.
     *
     * @return the file path for the bundle model file.
     */
    val bundleTaskOutputListingFile: String?

    /**
     * Returns the name of the task used to generate APKs via the bundle file (.aab), or null if the
     * task is not supported.
     *
     * @return name of the task used to generate the APKs via the bundle
     */
    val apkFromBundleTaskName: String?

    /**
     * Returns the path to the model file generated after each [.getApkFromBundleTaskName]
     * task execution. The model will contain a reference to the folder where APKs from bundle are
     * placed into. Returns null when [.getApkFromBundleTaskName] returns null.
     *
     * @return the file path for the [.getApkFromBundleTaskName] output model.
     */
    val apkFromBundleTaskOutputListingFile: String?

    /**
     * The bundle file (.aab file).
     *
     * TODO is this still needed now that we have bundleTaskOutputListingFile?
     */
    val bundleFile: File

    /**
     * The location of the generated APK(s) from the bundle.
     *
     * The location is always returned but it may not have been generated if the build request
     * only built the bundle.
     *
     * TODO is this still needed now that we have apkFromBundleTaskOutputListingFile?
     */
    val apkFolder: File
}