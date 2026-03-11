/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl.decorator.annotation

import com.android.build.gradle.options.BooleanOption

/**
 * Mark DSL as requiring runtime checks for the user to opt-in.
 *
 * The DSL decorator will automatically generate the code to check the given BooleanOption is enabled.
 *
 * Ro avoid needing to hide this on the definitions, for now anything managed that should be runtime guarded is annotated in overrides in
 * the internal interfaces.
 */
annotation class RuntimeGuardedExperimentalApi(val enableFlag: BooleanOption)
