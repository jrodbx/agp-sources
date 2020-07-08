/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.builder.dexing

import com.android.tools.r8.dexsplitter.DexSplitter
import com.android.tools.r8.dexsplitter.DexSplitter.Options
import java.nio.file.Path

/**
 * A wrapper around R8's DexSplitter
 */
class DexSplitterTool private constructor(private val options: Options) {

    class Builder(output: Path, proguardMap: Path?, mainDexListFile: Path?) {
        private val options = Options()

        init {
            options.output = output.toString()
            options.proguardMap = proguardMap?.toString()
            options.mainDexList = mainDexListFile?.toString()
        }

        fun addInputArchive(path: Path) {
            options.addInputArchive(path.toString())
        }

        fun addFeatureJar(path: Path, name:String) {
            options.addFeatureJar(path.toString(), name)
        }

        fun addBaseJar(path: Path) {
            options.addBaseJar(path.toString())
        }

        fun build() = DexSplitterTool(options)
    }

    fun run() {
        DexSplitter.run(options)
    }
}

