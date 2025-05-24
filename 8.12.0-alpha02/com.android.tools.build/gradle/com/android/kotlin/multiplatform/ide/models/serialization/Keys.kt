/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.kotlin.multiplatform.ide.models.serialization

import com.android.kotlin.multiplatform.models.AndroidCompilation
import com.android.kotlin.multiplatform.models.AndroidSourceSet
import com.android.kotlin.multiplatform.models.AndroidTarget
import com.android.kotlin.multiplatform.models.DependencyInfo
import org.jetbrains.kotlin.tooling.core.extrasKeyOf

/**
 * The extras map key that is used to serialize the android target models during build import action
 * and deserialize it during IDE project resolution.
 *
 * This is part of the contract between build and sync, and must not change.
 */
val androidTargetKey = extrasKeyOf<AndroidTarget>("android-target-model")

/**
 * The extras map key that is used to serialize the android compilation models during build import
 * action and deserialize it during IDE project resolution.
 *
 * This is part of the contract between build and sync, and must not change.
 */
val androidCompilationKey = extrasKeyOf<AndroidCompilation>("android-compilation-model")

/**
 * The extras map key that is used to serialize the android source set models during build import
 * action and deserialize it during IDE project resolution.
 *
 * This is part of the contract between build and sync, and must not change.
 */
val androidSourceSetKey = extrasKeyOf<AndroidSourceSet>("android-source-set-model")

/**
 * The extras map key that is used to serialize the models sent with the dependencies outgoing from
 * android source sets during build import action and deserialize it during IDE project resolution.
 *
 * This is part of the contract between build and sync, and must not change.
 */
val androidDependencyKey = extrasKeyOf<DependencyInfo>("android-dependency-model")
