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

package com.android.ide.common.gradle.model.impl

import com.android.ide.common.gradle.model.IdeAndroidLibrary
import com.android.ide.common.gradle.model.IdeJavaLibrary
import com.android.ide.common.gradle.model.IdeModuleLibrary

open class IdeAndroidLibraryDelegate(private val delegate: IdeAndroidLibrary): IdeAndroidLibrary by delegate
open class IdeJavaLibraryDelegate(private val delegate: IdeJavaLibrary): IdeJavaLibrary by delegate
open class IdeModuleLibraryDelegate(private val delegate: IdeModuleLibrary): IdeModuleLibrary by delegate