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

package com.android.ide.common.gradle.model

import com.android.builder.model.AndroidGradlePluginProjectFlags
import com.android.builder.model.AndroidGradlePluginProjectFlags.BooleanFlag
import java.io.Serializable
import java.util.EnumMap

/**
 * Represents flags that affect the semantic of the build in the Android Gradle Plugin
 * that also should affect the behavior of Android Studio.
 */
data class IdeAndroidGradlePluginProjectFlags(
    private val booleanFlagMap: EnumMap<BooleanFlag, Boolean>
) : Serializable {

    /**
     * Create based on the android gradle plugin model class.
     */
    constructor(flags: AndroidGradlePluginProjectFlags) : this() {
        booleanFlagMap.putAll(flags.booleanFlagMap)
    }

    /**
     * Create an empty set of flags for older AGPs and for studio serialization.
     */
    constructor() : this(booleanFlagMap = EnumMap(BooleanFlag::class.java))

    /**
     * Whether the R class in applications and dynamic features are constant.
     *
     * If they are constant they can be inlined by the java compiler and used in places that
     * require constants such as annotations and cases of switch statements.
     */
    val applicationRClassConstantIds: Boolean
        get() = getBooleanFlag(BooleanFlag.APPLICATION_R_CLASS_CONSTANT_IDS)

    /**
     * Whether the R class in instrumentation tests are constant.
     *
     * If they are constant they can be inlined by the java compiler and used in places that
     * require constants such as annotations and cases of switch statements.
     */
    val testRClassConstantIds: Boolean
        get() = getBooleanFlag(BooleanFlag.TEST_R_CLASS_CONSTANT_IDS)

    /**
     * Whether the R class generated for this project is Transitive.
     *
     * If it is transitive it will contain all of the resources defined in its transitive
     * dependencies alongside those defined in this project.
     * If non-transitive it will only contain the resources defined in this project.
     */
    val transitiveRClasses: Boolean
        get() = getBooleanFlag(BooleanFlag.TRANSITIVE_R_CLASS)

    /** Whether the Jetpack Compose feature is enabled for this project. */
    val usesCompose: Boolean
        get() = getBooleanFlag(BooleanFlag.JETPACK_COMPOSE)

    /** Whether the ML model binding feature is enabled for this project. */
    val mlModelBindingEnabled: Boolean
        get() = getBooleanFlag(BooleanFlag.ML_MODEL_BINDING)

    private fun getBooleanFlag(flag: BooleanFlag): Boolean {
        return booleanFlagMap[flag] ?: flag.legacyDefault
    }
}
