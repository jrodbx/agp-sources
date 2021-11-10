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
package com.android.builder.model.v2.ide

/**
 * Represents various AGP project-wide flags.
 *
 * This class is only for use in the Gradle tooling model. On the IDE side use
 * `IdeAndroidGradlePluginProjectFlags` which provides an interpreted view of the flags.
 *
 * @since 4.2
 */
interface AndroidGradlePluginProjectFlags {
    /**
     * Boolean flags for behavior changes in AGP that Android Studio needs to know about.
     *
     * Studio uses the legacy default for AGPs that do not specify that flag.
     *
     * Flags **must** never be removed from here. This is to avoid issues when the
     * current version of studio fetches models from a project that has a legacy flag set. They can
     * be marked as `@Deprecated` and the getter removed from `IdeAndroidGradlePluginProjectFlags`
     */
    enum class BooleanFlag(val legacyDefault: Boolean) {
        /**
         * Whether the R class in applications and dynamic features has constant IDs.
         *
         * If they are constant they can be inlined by the java compiler and used in places that
         * require constants such as annotations and cases of switch statements.
         */
        APPLICATION_R_CLASS_CONSTANT_IDS(true),

        /**
         * Whether the R class in instrumentation tests has constant IDs.
         *
         * If they are constant they can be inlined by the java compiler and used in places that
         * require constants such as annotations and cases of switch statements.
         */
        TEST_R_CLASS_CONSTANT_IDS(true),

        /**
         * Whether the R class generated for this project is transitive.
         *
         * If it is transitive it will contain all of the resources defined in its transitive
         * dependencies alongside those defined in this project. If non-transitive it will only
         * contain the resources defined in this project.
         */
        TRANSITIVE_R_CLASS(true),

        /** Whether the jetpack Compose feature is enabled for this project.  */
        JETPACK_COMPOSE(false),

        /** Whether the ML model binding feature is enabled for this project.  */
        ML_MODEL_BINDING(false);

        /**
         * The apparent value of this flag from Studio if it is not explicitly set in the AGP model.
         *
         * As Studio can open projects from older Android Gradle Plugins this is used in `IdeAndroidGradlePluginProjectFlags` to supply a value if it was not supplied by the build
         * system.
         *
         * This could be used because:
         *
         *  1. The AGP version used does not support this model at all.
         *  1. The AGP version used supports this model but predates the introduction of this
         * flag.
         *  1. The AGP version used supports this model and this flag but did not explicitly set a
         * value for it.
         *
         */
    }

    val booleanFlagMap: Map<BooleanFlag, Boolean>?
}