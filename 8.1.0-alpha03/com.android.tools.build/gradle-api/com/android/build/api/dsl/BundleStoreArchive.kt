/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.dsl

interface BundleStoreArchive {

    /**
     * Archive is an app state that allows an official app store to reclaim device storage and
     * disable app functionality temporarily until the user interacts with the app again. Upon
     * interaction the latest available version of the app will be restored while leaving user data
     * unaffected.
     *
     * <p> Enabled by default.
     */
    var enable: Boolean?
}
