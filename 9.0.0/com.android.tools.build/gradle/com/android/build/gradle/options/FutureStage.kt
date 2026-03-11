/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.build.gradle.options

/**
 * Represents the proposed [stage] of a [BooleanOption] in a future AGP [version].
 *
 * This helps to communicate timelines to users and also prevent features from staying in an
 * intermediate stage for too long, which would increase maintenance cost to AGP and users.
 *
 * Note: Depending on the current stage, the [FutureStage] may or may not need to be exact. For
 * example, for [FeatureStage.Experimental] or [FeatureStage.Supported], the [FutureStage] can be an
 * estimation used for internal testing and not a promise to the users; for
 * [FeatureStage.SoftlyEnforced] or [FeatureStage.Deprecated], the [FutureStage] should be honored.
 */
data class FutureStage(val version: Version, val defaultValue: Boolean, val stage: Stage)
