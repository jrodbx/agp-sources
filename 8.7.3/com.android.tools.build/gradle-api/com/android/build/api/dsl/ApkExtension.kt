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

package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * Common extension properties for the Android Gradle Plugin Application and Dynamic Feature
 * plugins.
 *
 *
 * Only the Android Gradle Plugin should create instances of this interface.
 */
// TODO(b/140406102): Could this have a better name?
@Incubating
interface ApkExtension {
    // TODO(b/140406102)
}
