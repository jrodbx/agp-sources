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
@file:JvmName("VariantUtil")

package com.android.projectmodel

/**
 * Name reserved the main artifact in a [Variant].
 */
const val ARTIFACT_NAME_MAIN = "_main_"

/**
 * Name reserved the android test artifact in a [Variant].
 */
const val ARTIFACT_NAME_ANDROID_TEST = "_android_test_"

/**
 * Name reserved the unit test artifact in a [Variant].
 */
const val ARTIFACT_NAME_UNIT_TEST = "_unit_test_"
