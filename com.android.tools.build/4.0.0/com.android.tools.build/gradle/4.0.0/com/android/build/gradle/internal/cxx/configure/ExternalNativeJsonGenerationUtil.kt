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

package com.android.build.gradle.internal.cxx.configure

import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.model.writeJsonToFile
import com.android.build.gradle.internal.cxx.services.CxxAbiListenerServiceKey
import com.android.build.gradle.internal.cxx.services.doAfterJsonGeneration

/**
 * This is meant to hold utility code that would normally go into Java
 * ExternalNativeJsonGenerator but where Kotlin is preferred.
 */

/**
 * Service key for writing build model to disk after Json generation.
 */
private val WRITE_BUILD_MODEL_AFTER_JSON_GENERATION_KEY = object : CxxAbiListenerServiceKey { }

/**
 * Helper function to register data model json to disk aft json generation.
 */
fun registerWriteModelAfterJsonGeneration(abi : CxxAbiModel) {
    abi.doAfterJsonGeneration(WRITE_BUILD_MODEL_AFTER_JSON_GENERATION_KEY) {
        abi.writeJsonToFile()
    }
}