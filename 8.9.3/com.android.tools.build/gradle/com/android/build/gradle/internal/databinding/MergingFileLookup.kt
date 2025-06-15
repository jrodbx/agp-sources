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

package com.android.build.gradle.internal.databinding

import android.databinding.tool.LayoutXmlProcessor
import com.android.ide.common.blame.MergingLog
import com.android.ide.common.blame.SourceFile
import java.io.File

/**
 * Implementation of [LayoutXmlProcessor.OriginalFileLookup] over a resource merge blame file.
 */
class MergingFileLookup(private val resourceBlameLogDir: File) : LayoutXmlProcessor.OriginalFileLookup {
    override fun getOriginalFileFor(file: File): File? {
        val input = SourceFile(file)
        val original = mergingLog.find(input)
        return if (input === original) {
            null
        } else original.sourceFile
    }

    private val mergingLog: MergingLog by lazy {
        MergingLog(resourceBlameLogDir)
    }
}