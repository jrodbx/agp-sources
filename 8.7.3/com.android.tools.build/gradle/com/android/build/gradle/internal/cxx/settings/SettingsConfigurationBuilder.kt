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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.configure.CmakeProperty

/**
 * Builder class for [SettingsConfiguration].
 */
class SettingsConfigurationBuilder {
    var name : String? = null
    var description : String? = null
    var buildRoot : String? = null
    var generator : String? = null
    var configurationType : String? = null
    var installRoot : String? = null
    var cmakeExecutable : String? = null
    var cmakeToolchain : String? = null
    var cmakeCommandArgs : String? = null
    var buildCommandArgs : String? = null
    var ctestCommandArgs : String? = null
    var inheritedEnvironments = listOf<String>()
    val variables = mutableListOf<SettingsConfigurationVariable>()

    /**
     * Initialize this builder with the values from another [SettingsConfiguration]
     */
    fun initialize(settings : SettingsConfiguration) : SettingsConfigurationBuilder {
        name = settings.name
        description = settings.description
        buildRoot = settings.buildRoot
        generator = settings.generator
        configurationType = settings.configurationType
        installRoot = settings.installRoot
        inheritedEnvironments = settings.inheritEnvironments
        cmakeCommandArgs = settings.cmakeCommandArgs
        buildCommandArgs = settings.buildCommandArgs
        ctestCommandArgs = settings.ctestCommandArgs
        cmakeExecutable = settings.cmakeExecutable
        cmakeToolchain = settings.cmakeToolchain
        variables.addAll(settings.variables)
        return this
    }

    /**
     * Add a variable to the map of variables for this builder.
     */
    fun putVariable(property : CmakeProperty, arg : Any) : SettingsConfigurationBuilder {
        variables += SettingsConfigurationVariable(property.name, arg.toString())
        return this
    }

    /**
     * Build an immutable [SettingsConfiguration] from the contents of this builder.
     */
    fun build() : SettingsConfiguration {
        return SettingsConfiguration(
            name = name,
            description = description,
            generator = generator,
            buildRoot =  buildRoot,
            installRoot =  installRoot,
            configurationType = configurationType,
            cmakeExecutable = cmakeExecutable,
            cmakeToolchain = cmakeToolchain,
            cmakeCommandArgs = cmakeCommandArgs,
            buildCommandArgs = buildCommandArgs,
            ctestCommandArgs = ctestCommandArgs,
            inheritEnvironments = inheritedEnvironments,
            variables = variables.toList()
        )
    }
}
