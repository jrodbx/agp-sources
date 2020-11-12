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

package com.android.build.gradle.internal.cxx.build

import com.android.build.gradle.internal.cxx.gradle.generator.CxxConfigurationModel
import org.gradle.process.ExecOperations
import java.io.File

/**
 * A CxxBuilder that does nothing except for exposing [objFolder] and [soFolder]
 */
class CxxNopBuilder(val referencedModel: CxxConfigurationModel) : CxxBuilder {
    // objFolder must be here for legacy reasons but its value was never correct for CMake.
    // There is no folder that has .o files for the entire variant.
    override val objFolder: File get() = referencedModel.variant.soFolder
    override val soFolder: File get() = referencedModel.variant.soFolder
    override fun build(ops: ExecOperations) { }
}
