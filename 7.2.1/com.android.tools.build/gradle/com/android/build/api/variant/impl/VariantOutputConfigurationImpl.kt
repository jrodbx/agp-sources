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

import com.android.build.api.variant.FilterConfiguration
import com.android.build.api.variant.VariantOutputConfiguration
import com.android.build.api.variant.VariantOutputConfiguration.OutputType
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.utils.appendCamelCase
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import java.io.File
import java.io.Serializable
import java.util.Locale

data class VariantOutputConfigurationImpl(
    @get:Input
    private val isUniversal: Boolean = false,
    @get:Nested
    override val filters: Collection<FilterConfigurationImpl> = listOf()
) : VariantOutputConfiguration, Serializable {

    @get:Input
    override val outputType: OutputType
        get() {
            if (isUniversal) return OutputType.UNIVERSAL
            return if (filters.isEmpty()) OutputType.SINGLE
            else OutputType.ONE_OF_MANY
        }

    /**
     * Returns the [FilterConfiguration] for a particular [FilterConfiguration.FilterType] or null
     * if not such filter is configured on this variant output
     */
    fun getFilter(type: FilterConfiguration.FilterType)
            : FilterConfiguration? = filters.firstOrNull { it.filterType == type }
}

fun VariantOutputConfiguration.baseName(variantDslInfo: VariantDslInfo): String =
        when(this.outputType) {
            OutputType.SINGLE -> variantDslInfo.baseName
            OutputType.UNIVERSAL -> variantDslInfo.computeBaseNameWithSplits(
                OutputType.UNIVERSAL.name.toLowerCase(Locale.US)
            )
            OutputType.ONE_OF_MANY ->
                variantDslInfo.computeBaseNameWithSplits(this.filters.getFilterName())
        }


fun VariantOutputConfiguration.dirName(): String {
    return when (this.outputType) {
        OutputType.UNIVERSAL -> outputType.name.toLowerCase(Locale.US)
        OutputType.SINGLE -> ""
        OutputType.ONE_OF_MANY ->
            filters.map(FilterConfiguration::identifier).joinToString(File.separator)
        else -> throw RuntimeException("Unhandled OutputType $this")
    }
}

fun VariantOutputConfiguration.fullName(variantDslInfo: VariantDslInfo): String {
    return when (this.outputType) {
        OutputType.UNIVERSAL ->
            variantDslInfo.computeFullNameWithSplits(
                OutputType.UNIVERSAL.name.toLowerCase(Locale.US))
        OutputType.SINGLE ->
            variantDslInfo.componentIdentity.name
        OutputType.ONE_OF_MANY -> {
            val filterName = filters.getFilterName()
            return variantDslInfo.computeFullNameWithSplits(filterName)
        }
        else -> throw RuntimeException("Unhandled OutputType $this")
    }
}

fun Collection<FilterConfiguration>.joinToString() =
    this.joinToString { filter -> filter.identifier }

fun Collection<FilterConfiguration>.getFilterName(): String {
    val sb = StringBuilder()
    val densityFilter = firstOrNull { it.filterType == FilterConfiguration.FilterType.DENSITY }?.identifier
    if (densityFilter != null) {
        sb.append(densityFilter)
    }
    val abiFilter = firstOrNull() { it.filterType == FilterConfiguration.FilterType.ABI }?.identifier
    if (abiFilter != null) {
        sb.appendCamelCase(abiFilter)
    }
    return sb.toString()
}
