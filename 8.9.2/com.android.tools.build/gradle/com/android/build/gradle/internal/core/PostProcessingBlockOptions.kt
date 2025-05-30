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

package com.android.build.gradle.internal.core

import com.android.build.gradle.internal.PostprocessingFeatures
import com.android.build.gradle.internal.ProguardFilesProvider
import com.android.build.gradle.internal.dsl.PostProcessingBlock
import java.io.File

/**
 * This is an implementation of PostProcessingOptions interface for the new (block) DSL
 */
class PostProcessingBlockOptions(
    private val postProcessingBlock: PostProcessingBlock,
    private val isTestComponent: Boolean,
) : PostProcessingOptions, ProguardFilesProvider by postProcessingBlock {

    override fun getDefaultProguardFiles(): List<File> = emptyList()

    // If the new DSL block is not used, all these flags need to be in the config files.
    override fun getPostprocessingFeatures(): PostprocessingFeatures = PostprocessingFeatures(
        postProcessingBlock.isRemoveUnusedCode,
        postProcessingBlock.isObfuscate,
        postProcessingBlock.isOptimizeCode
    )

    override fun codeShrinkerEnabled(): Boolean {
        // For testing code, we only run ProGuard/R8 if main code is obfuscated.
        return if (isTestComponent) {
            postProcessingBlock.isObfuscate
        } else {
            postProcessingBlock.isRemoveUnusedCode
                    || postProcessingBlock.isObfuscate
                    || postProcessingBlock.isOptimizeCode
        }
    }

    override fun resourcesShrinkingEnabled(): Boolean = postProcessingBlock.isRemoveUnusedResources

    override fun hasPostProcessingConfiguration() = true
}
