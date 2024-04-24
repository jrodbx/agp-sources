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

package com.android.build.api.variant

/**
 * Component object that contains properties that must be set during configuration time as it
 * changes the build flow for the variant.
 */
interface ComponentBuilder: ComponentIdentity {

    /**
     * Set to `true` if the variant is active and should be configured, false otherwise.
     */
    var enable: Boolean

    @Deprecated("Will be removed in 8.0")
    var enabled: Boolean
}
