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

package com.android.build.gradle.internal.cxx.model

import com.android.build.gradle.internal.cxx.services.CxxServiceRegistry
import java.util.UUID

/**
 * Data model for a single build. There is one instance of this per-build and services
 * provided have a lifetime of the overall build.
 */
interface CxxBuildModel {

    /** Unique build ID */
    val buildId : UUID

    /**
     * Service provider entry for build-model-level services. These are services naturally
     * scoped at the per-build level.
     */
    val services: CxxServiceRegistry
}
