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

package com.android.build.shrinker.obfuscation

import com.android.build.shrinker.ResourceShrinkerModel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Records obfuscation mappings from single file in proguard format.
 *
 * @param mappingsFile path to proguard.map file.
 */
class ProguardMappingsRecorder(private val mappingsFile: Path) : ObfuscationMappingsRecorder {

    override fun recordObfuscationMappings(model: ResourceShrinkerModel) {
        model.obfuscatedClasses = extractObfuscatedResourceClasses()
    }

    internal fun extractObfuscatedResourceClasses(): ObfuscatedClasses {
        // Proguard obfuscation mappings file has the following structure:
        // # comment1
        // com.package.MainClass -> a.a:
        //     int field -> a
        //     boolean getFlag() -> b
        // com.package.R -> a.b:
        // com.package.R$style -> a.b.a:
        //     int Toolbar_android_gravity -> i1

        val builder = ObfuscatedClasses.Builder()
        Files.readAllLines(mappingsFile, StandardCharsets.UTF_8).forEach { line ->
            when {
                isMethodMapping(line) -> builder.addMethodMapping(extractMethodMapping(line))
                isClassMapping(line) -> builder.addClassMapping(extractClassMapping(line))
            }
        }
        return builder.build()
    }

    private fun isClassMapping(line: String): Boolean = line.contains("->")

    private fun isMethodMapping(line: String): Boolean =
        (line.startsWith(" ") || line.startsWith("\t")) && line.contains("->")

    private fun extractClassMapping(line: String): Pair<String, String> {
        val mapping = line.split("->", limit = 2)
        return Pair(mapping[0].trim(), mapping[1].trim(' ', '\t', ':'))
    }

    private fun extractMethodMapping(line: String): Pair<String, String> {
        val mapping = line.split("->", limit = 2)
        val originalMethod = mapping[0].trim()
            .substringBeforeLast('(')
            .substringAfter(' ')
        val obfuscatedMethod = mapping[1].trim()
        return Pair(originalMethod, obfuscatedMethod)
    }
}
