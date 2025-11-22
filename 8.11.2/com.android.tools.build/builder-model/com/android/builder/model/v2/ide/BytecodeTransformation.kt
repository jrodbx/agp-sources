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

package com.android.builder.model.v2.ide

/**
 * Describes some transformation of either .class file or .dex file. Typically, this means that
 * javac or kotlinc output has been additionally processed in some way, or that DEX output has been
 * modified.
 */
enum class BytecodeTransformation(val description: String) {
    JACOCO_INSTRUMENTATION("Jacoco offline instrumentation"),
    MODIFIES_PROJECT_CLASS_FILES("Modified class files in Gradle project"),
    MODIFIES_ALL_CLASS_FILES("Modified class files in Gradle project and all dependencies"),
    ASM_API_PROJECT("ASM instrumentation in Gradle project"),
    ASM_API_ALL("ASM instrumentation for all dependencies"),
}
