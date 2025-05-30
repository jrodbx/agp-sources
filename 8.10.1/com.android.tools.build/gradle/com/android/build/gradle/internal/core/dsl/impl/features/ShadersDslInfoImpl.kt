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

package com.android.build.gradle.internal.core.dsl.impl.features

import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.ProductFlavor
import com.android.build.gradle.internal.core.dsl.features.ShadersDslInfo
import com.android.build.gradle.internal.dsl.DefaultConfig
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.google.common.collect.Sets

class ShadersDslInfoImpl(
    private val defaultConfig: DefaultConfig,
    private val buildTypeObj: BuildType,
    private val productFlavorList: List<ProductFlavor>,
): ShadersDslInfo {
    // add the lower priority one, to override them with the higher priority ones.
    // can't use merge flavor as it's not a prop on the base class.
    // reverse loop for proper order
    override val defaultGlslcArgs: List<String>
        get() {
            val optionMap: MutableMap<String, String> =
                Maps.newHashMap()
            // add the lower priority one, to override them with the higher priority ones.
            for (option in defaultConfig.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            // can't use merge flavor as it's not a prop on the base class.
            // reverse loop for proper order
            for (i in productFlavorList.indices.reversed()) {
                for (option in productFlavorList[i].shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
            }
            // then the build type
            for (option in buildTypeObj.shaders.glslcArgs) {
                optionMap[getKey(option)] = option
            }
            return Lists.newArrayList(optionMap.values)
        }

    // first collect all possible keys.
    override val scopedGlslcArgs: Map<String, List<String>>
        get() {
            val scopedArgs: MutableMap<String, List<String>> =
                Maps.newHashMap()
            // first collect all possible keys.
            val keys = scopedGlslcKeys
            for (key in keys) { // first add to a temp map to resolve overridden values
                val optionMap: MutableMap<String, String> =
                    Maps.newHashMap()
                // we're going to go from lower priority, to higher priority elements, and for each
                // start with the non scoped version, and then add the scoped version.
                // 1. default config, global.
                for (option in defaultConfig.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 1b. default config, scoped.
                for (option in defaultConfig.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // 2. the flavors.
                // can't use merge flavor as it's not a prop on the base class.
                // reverse loop for proper order
                for (i in productFlavorList.indices.reversed()) { // global
                    for (option in productFlavorList[i].shaders.glslcArgs) {
                        optionMap[getKey(option)] = option
                    }
                    // scoped.
                    for (option in productFlavorList[i].shaders.scopedGlslcArgs[key]) {
                        optionMap[getKey(option)] = option
                    }
                }
                // 3. the build type, global
                for (option in buildTypeObj.shaders.glslcArgs) {
                    optionMap[getKey(option)] = option
                }
                // 3b. the build type, scoped.
                for (option in buildTypeObj.shaders.scopedGlslcArgs[key]) {
                    optionMap[getKey(option)] = option
                }
                // now add the full value list.
                scopedArgs[key] = ImmutableList.copyOf(optionMap.values)
            }
            return scopedArgs
        }


    private val scopedGlslcKeys: Set<String>
        get() {
            val keys: MutableSet<String> =
                Sets.newHashSet()
            keys.addAll(defaultConfig.shaders.scopedGlslcArgs.keySet())
            for (flavor in productFlavorList) {
                keys.addAll(flavor.shaders.scopedGlslcArgs.keySet())
            }
            keys.addAll(buildTypeObj.shaders.scopedGlslcArgs.keySet())
            return keys
        }

    private fun getKey(fullOption: String): String {
        val pos = fullOption.lastIndexOf('=')
        return if (pos == -1) {
            fullOption
        } else fullOption.substring(0, pos)
    }
}
