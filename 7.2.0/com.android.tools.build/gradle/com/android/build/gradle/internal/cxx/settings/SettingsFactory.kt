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

package com.android.build.gradle.internal.cxx.settings

import com.android.build.gradle.internal.cxx.logging.errorln
import com.android.build.gradle.internal.cxx.model.CxxAbiModel
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_ABI
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_FULL_CONFIGURATION_HASH
import com.android.build.gradle.internal.cxx.settings.Macro.NDK_PLATFORM_SYSTEM_VERSION

/**
 * Expand ${ndk.abi} and ${abi.systemVersion} in environment names.
 */
fun Settings.expandInheritEnvironmentMacros(abi: CxxAbiModel) : Settings {
    val environments = environments.map { configuration ->
        configuration.copy(
                inheritEnvironments = configuration.inheritEnvironments.map { environment ->
                    val result = environment
                            .replace(NDK_ABI.ref, abi.abi.tag)
                            .replace(NDK_PLATFORM_SYSTEM_VERSION.ref, abi.abiPlatformVersion.toString()
                            )
                    result
                }
        )
    }
    return copy(environments = environments)
}

/**
 * Reify [SettingsConfiguration] by replacing macro values using [SettingsEnvironmentNameResolver].
 */
fun reifyRequestedConfiguration(
        resolver: SettingsEnvironmentNameResolver,
        configuration: SettingsConfiguration)
        : SettingsConfiguration? {

    fun String?.reify() = reifyString(this) { tokenMacro ->
        when(tokenMacro) {
            // Exclude properties that shouldn't be evaluated before the configuration hash.
            NDK_ABI.qualifiedName -> NDK_ABI.ref
            NDK_CONFIGURATION_HASH.qualifiedName -> NDK_CONFIGURATION_HASH.ref
            NDK_FULL_CONFIGURATION_HASH.qualifiedName -> NDK_FULL_CONFIGURATION_HASH.ref
            else -> resolver.resolve(tokenMacro, configuration.inheritEnvironments)
        }
    }

    return configuration.copy(
            buildRoot = configuration.buildRoot.reify(),
            configurationType = configuration.configurationType.reify(),
            installRoot = configuration.installRoot.reify(),
            cmakeCommandArgs = configuration.cmakeCommandArgs.reify(),
            buildCommandArgs = configuration.buildCommandArgs.reify(),
            ctestCommandArgs = configuration.ctestCommandArgs.reify(),
            cmakeExecutable = configuration.cmakeExecutable.reify(),
            cmakeToolchain = configuration.cmakeToolchain.reify(),
            variables = configuration.variables.map { (name, value) ->
                SettingsConfigurationVariable(name, value.reify()!!)
            }
    )
}

/**
 * Tokenize [value] and replace macro tokens with the value returned by [reifier].
 * A macro, when expanded, may include other macros so this function loops until there are no
 * macros to expand.
 */
fun reifyString(value : String?, reifier : (String) -> String?) : String {
    var prior = value ?: return ""
    var replaced: Boolean
    val seen = mutableSetOf<String>()
    do {
        var recursionError = false
        replaced = false
        val sb = StringBuilder()
        tokenizeMacroString(prior) { token ->
            when (token) {
                is Token.LiteralToken -> sb.append(token.literal)
                is Token.MacroToken -> {
                    val tokenMacro = token.macro
                    if (seen.contains(tokenMacro)) {
                        errorln("Settings.json value '$value' has recursive macro expansion \${$tokenMacro}")
                        recursionError = true
                    } else {
                        val resolved = reifier(tokenMacro)
                        val value = resolved ?: ""
                        if (value != "\${$tokenMacro}") {
                            seen += tokenMacro
                            replaced = true
                        }
                        sb.append(value)
                    }
                }
            }
        }
        if (recursionError) return value
        prior = sb.toString()
    } while (replaced)

    return prior
}


