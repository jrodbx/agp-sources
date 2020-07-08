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

package com.android.build.gradle.internal.scope

import com.android.build.FilterData
import com.android.build.VariantOutput

data class DefaultApkData(
    private val _type: VariantOutput.OutputType,
    private val _filters: MutableCollection<FilterData>,
    private val _versionCode: Int,
    private val _versionName: String?,
    private val _filterName: String?,
    private val _outputFileName: String?,
    private val _fullName: String = "",
    private val _baseName: String,
    private val _dirName: String = ""
) : ApkData() {

    override fun getFilter(filterType: VariantOutput.FilterType): FilterData? {
        for (filter in filters) {
            if (filter.filterType == filterType.name) {
                return filter
            }
        }
        return null
    }

    override fun getVersionCode(): Int = _versionCode
    override fun getVersionName(): String? = _versionName
    override fun getOutputFileName(): String? = _outputFileName
    override fun requiresAapt(): Boolean = true
    override fun getFilterName(): String? = _filterName
    override fun getFullName(): String = _fullName
    override fun getBaseName(): String = _baseName
    override fun getType(): VariantOutput.OutputType = _type
    override fun getFilters(): MutableCollection<FilterData> = _filters
    override fun getDirName(): String = _dirName
}