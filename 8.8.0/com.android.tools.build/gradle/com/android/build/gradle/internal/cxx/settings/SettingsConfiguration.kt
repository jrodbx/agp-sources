/*
 * Copyright (C) 2019 The Android Source Project
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
 * Schema of 'configurations' element from CMakeSettings.json.
 */
data class SettingsConfiguration(
    /**
     * The configuration name.
     */
    val name: String? = null,
    /**
     * Description of this configuration
     */
    val description: String? = null,
    /**
     * The CMake generator name. Example: Ninja
     */
    val generator: String? = null,
    /**
     * Specifies build type configuration for the selected generator.
     * Example, MinSizeRel
     */
    val configurationType: String? = null,
    /**
     * The environments this configuration depends on.
     * Any custom environment can be used.
     */
    val inheritEnvironments: List<String> = listOf(),
    /**
     * The directory in which CMake generates build scripts for the chosen
     * generator. Supported macros include ${workspaceRoot},
     * ${workspaceHash}, ${projectFile}, ${projectDir}, ${thisFile},
     * ${thisFileDir}, ${name}, ${generator}, ${env.VARIABLE}.
     */
    val buildRoot: String? = null,
    /**
     * The directory in which CMake generates install targets for the chosen
     * generator. Supported macros include ${workspaceRoot}, ${workspaceHash},
     * ${projectFile}, ${projectDir}, ${thisFile}, ${thisFileDir}, ${name},
     * ${generator}, ${env.VARIABLE}.
     */
    val installRoot: String? = null,
    /**
     * Additional command line options passed to CMake when invoked to generate
     * the cache.
     */
    val cmakeCommandArgs: String? = null,
    /**
     * specifies the toolchain file. This is passed to CMake using -DCMAKE_TOOLCHAIN_FILE.
     */
    val cmakeToolchain: String? = null,
    /**
     * specifies the full path to the CMake program executable, including the file name
     * and extension.
     */
    val cmakeExecutable: String? = null,
    /**
     * Native build switches passed to CMake after --build --.
     */
    val buildCommandArgs: String? = null,
    /**
     * Additional command line options passed to CTest when running the tests.
     */
    val ctestCommandArgs: String? = null,
    /**
     * A list of CMake variables. The name value pairs are passed to CMake
     * as -Dname1=value1 -Dname2=value2, etc.
     */
    val variables: List<SettingsConfigurationVariable> = listOf()
)

/**
 * Get a variable value having the name in [property] if present.
 */
fun SettingsConfiguration.getVariableValue(property: CmakeProperty) : String? {
    var value : String? = null
    variables.forEach { variable ->
        if (variable.name == property.name) {
            value = variable.value
        }
    }
    return value
}

/**
 * Accumulate configuration values with later values replacing earlier values when not null.
 */
fun SettingsConfiguration.withConfigurationsFrom(configuration : SettingsConfiguration?) : SettingsConfiguration {
    if (configuration == null) return this
    return SettingsConfiguration(
            name = configuration.name ?: name,
            description = configuration.description ?: description,
            generator = configuration.generator ?: generator,
            configurationType = configuration.configurationType ?: configurationType,
            inheritEnvironments = configuration.inheritEnvironments,
            buildRoot = configuration.buildRoot ?: buildRoot,
            installRoot = configuration.installRoot ?: installRoot,
            cmakeCommandArgs = configuration.cmakeCommandArgs ?: cmakeCommandArgs,
            cmakeToolchain = configuration.cmakeToolchain ?: cmakeToolchain,
            cmakeExecutable = configuration.cmakeExecutable ?: cmakeExecutable,
            buildCommandArgs = configuration.buildCommandArgs ?: buildCommandArgs,
            ctestCommandArgs = configuration.ctestCommandArgs ?: ctestCommandArgs,
            variables = variables + configuration.variables
    )
}
