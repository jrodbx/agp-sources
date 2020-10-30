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

package com.android.build.api.variant.impl

import com.android.build.api.variant.VariantOutput
import com.android.build.api.variant.VariantOutputConfiguration

/**
 * Implementation of [List] of [VariantOutput] with added private services for AGP.
 */
class VariantOutputList(
    private val variantOutputs: List<VariantOutputImpl>): List<VariantOutputImpl> by variantOutputs {

    /**
     * Return a [List] of [VariantOutputImpl] for variant output which [VariantOutput.outputType]
     * is the same as the passed parameter.
     *
     * @param outputType desired output type filter.
     * @return a possibly empty [List] of [VariantOutputImpl]
     */
    fun getSplitsByType(outputType: VariantOutputConfiguration.OutputType): List<VariantOutputImpl> =
        variantOutputs.filter { it.outputType == outputType }

    /**
     * Returns the list of enabled [VariantOutput]
     */
    fun getEnabledVariantOutputs(): List<VariantOutputImpl> =
        variantOutputs.filter { it.isEnabled.get() }

    /**
     * Finds the main split in the current variant context or throws a [RuntimeException] if there
     * are none.
     */
    fun getMainSplit(): VariantOutputImpl =
        getMainSplitOrNull()
            ?: throw RuntimeException("Cannot determine main split information, file a bug.")

    /**
     * Finds the main split in the current variant context or null if there are no variant output.
     */
    fun getMainSplitOrNull(): VariantOutputImpl? =
        variantOutputs.find { variantOutput ->
            variantOutput.outputType == VariantOutputConfiguration.OutputType.SINGLE }
            ?: variantOutputs.find {
                it.outputType == VariantOutputConfiguration.OutputType.UNIVERSAL }
            ?: variantOutputs.find {
                it.outputType == VariantOutputConfiguration.OutputType.ONE_OF_MANY
            }
}