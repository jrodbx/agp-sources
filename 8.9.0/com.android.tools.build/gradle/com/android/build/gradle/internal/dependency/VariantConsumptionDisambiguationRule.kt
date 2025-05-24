/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.gradle.internal.dependency

import com.android.build.api.attributes.BuildTypeAttr
import com.android.build.api.attributes.ProductFlavorAttr
import org.gradle.api.Named
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import javax.inject.Inject

/**
 * A matching fallback rule that tries to select a variant dimension based on the given fallbacks.
 *
 * @param consumerFallbacks a map from the value of the consumer to the matching fallbacks.
 * @param globalFallbacks a list of global fallbacks that apply to all consumers.
 */
abstract class VariantConsumptionDisambiguationRule<T: Named> protected constructor(
    private val consumerFallbacks: Map<String, List<String>> = emptyMap(),
    private val globalFallbacks: List<String> = emptyList()
): AttributeDisambiguationRule<T> {

    private fun maybeMatch(
        details: MultipleCandidatesDetails<T>,
        candidates: Map<String, T>,
        fallbacks: List<String>
    ): Boolean {
        fallbacks.forEach { fallback ->
            candidates[fallback]?.let {
                details.closestMatch(it)
                return true
            }
        }
        return false
    }

    override fun execute(details: MultipleCandidatesDetails<T>) {
        val consumerValue = details.consumerValue
        val candidates = details.candidateValues.associateBy { it.name }

        if (consumerValue != null) {
            if (candidates.containsKey(consumerValue.name)) {
                details.closestMatch(consumerValue)
                return
            }

            consumerFallbacks[consumerValue.name]?.let {
                if (maybeMatch(details, candidates, it)) {
                    return
                }
            }
        }

        maybeMatch(details, candidates, globalFallbacks)
    }
}

class MultiVariantBuildTypeRule @Inject constructor(
    val fallbacks: Map<String, List<String>>
): VariantConsumptionDisambiguationRule<BuildTypeAttr>(
    consumerFallbacks = fallbacks
)

class MultiVariantProductFlavorRule @Inject constructor(
    val fallbacks: Map<String, List<String>>
): VariantConsumptionDisambiguationRule<ProductFlavorAttr>(
    consumerFallbacks = fallbacks
)

class SingleVariantBuildTypeRule @Inject constructor(
    val fallbacks: List<String>
): VariantConsumptionDisambiguationRule<BuildTypeAttr>(
    globalFallbacks = fallbacks
)

class SingleVariantProductFlavorRule @Inject constructor(
    val fallbacks: List<String>
): VariantConsumptionDisambiguationRule<ProductFlavorAttr>(
    globalFallbacks = fallbacks
)
