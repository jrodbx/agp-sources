/*
 * Copyright (C) 2017 The Android Open Source Project
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

@file:JvmName("PsProjectTypeUtil")

package com.android.projectmodel

/**
 * Represents the type of artifact produced by an Android project.
 *
 * @param isApplication determines whether or not this project type is an application.
 */
enum class ProjectType(val isApplication: Boolean) {
    APP(true),
    LIBRARY(false),
    TEST(false),
    ATOM(false),
    INSTANT_APP(true),
    FEATURE(false),
    DYNAMIC_FEATURE(false),
}
