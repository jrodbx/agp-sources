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

package com.android.build.gradle.internal.res.shrinker.gatherer

import com.android.SdkConstants.FN_RESOURCE_TEXT
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel
import com.android.ide.common.symbols.SymbolIo
import com.android.resources.ResourceType.STYLEABLE
import java.io.File
import java.nio.file.Files

/**
 * Gathers application resources from R text files.
 *
 * @param rDir location for R.txt, can be either file or directory. If rFile specifies directory
 *             resources are gathered from all R.txt files inside the directory and subdirectories.
 * @param packageName of application module for resources gathered from the specified location
 */
class ResourcesGathererFromRTxt(
    private val rDir: File,
    private val packageName: String
) : ResourcesGatherer {

    override fun gatherResourceValues(model: ResourceShrinkerModel) {
        Files.walk(rDir.toPath())
            .filter { it.endsWith(FN_RESOURCE_TEXT) }
            .forEach { gatherSingleRTxt(it.toFile(), model) }
    }

    private fun gatherSingleRTxt(rTxtFile: File, model: ResourceShrinkerModel) {
        val symbolTable = SymbolIo.readFromAapt(rTxtFile, packageName)

        // Only STYLEABLE parents are supported for now but we don't need to filter out STYLEABLE
        // children resources here, because only parent one is returned by
        // SymbolTable.symbols.values().
        symbolTable.symbols.values().forEach {
            val value = if (it.resourceType != STYLEABLE) it.getValue() else null
            model.addResource(it.resourceType, packageName, it.name, value)
        }
    }
}
