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

package com.android.build.gradle.options

import com.android.build.gradle.internal.errors.DeprecationReporter

/**
 * The stage of an API or feature in its life cycle.
 *
 * The difference between an API and a feature is that:
 *   - An API can be represented by any [Option]. If it is represented by a [BooleanOption] (or
 *     [OptionalBooleanOption]), it is intended that eventually both values of the option will be
 *     supported.
 *   - A feature can be represented only by a [BooleanOption] (or [OptionalBooleanOption]). It is
 *     intended that eventually only one of the two values of the option which represents the
 *     feature being enabled (usually the `true` value) will be supported.
 */
open class Stage(

    /**
     * Status of the [Option] which represents an API or feature. It is related but not the same as
     * the [Stage] of the API of feature.
     */
    val status: Option.Status
)

/**
 * The stage of an API in its life cycle.
 *
 * See [Stage] for the difference between an API and a feature.
 */
sealed class ApiStage(status: Option.Status) : Stage(status) {

    /**
     * Indicates that the API is experimental.
     *
     * It may become stable or may be removed in a future release (see stage [Stable] and
     * [Removed]).
     */
    object Experimental : ApiStage(Option.Status.EXPERIMENTAL)

    /**
     * Indicates that the API is stable.
     */
    object Stable : ApiStage(Option.Status.STABLE)

    /**
     * Indicates that the API will be removed soon because it was not / is no longer useful (see
     * stage [Removed]).
     *
     * @param removalTarget a target when the API and the corresponding [Option] will be removed
     */
    class Deprecated(removalTarget: DeprecationReporter.DeprecationTarget) :
        ApiStage(Option.Status.Deprecated(removalTarget))

    /**
     * Indicates that the API and the corresponding [Option] have been removed.
     *
     * @param removedVersion the version when the API and the corresponding [Option] were removed
     * @param additionalMessage the additional message to be shown if the [Option] is used
     */
    class Removed(removedVersion: Version, additionalMessage: String) :
        ApiStage(Option.Status.Removed(removedVersion, additionalMessage))
}

/**
 * The stage of a feature in its life cycle.
 *
 * See [Stage] for the difference between an API and a feature.
 */
sealed class FeatureStage(status: Option.Status) : Stage(status) {

    /**
     * Indicates that the feature is experimental.
     *
     * It may be enforced or removed in a future release (see stage [Enforced] and [Removed]).
     */
    object Experimental : FeatureStage(Option.Status.EXPERIMENTAL)

    /**
     * Indicates that the feature is fully supported.
     *
     * It may or may not be enabled by default. If it is not yet enabled by default, it will likely
     * be enabled by default in a future release.
     *
     * Eventually, the feature will likely be enforced (see stage [SoftlyEnforced] and [Enforced]).
     * In some rare cases, it may be removed (see stage [Deprecated] and [Removed]).
     */
    object Supported : FeatureStage(Option.Status.STABLE)

    /**
     * Indicates that the feature will be enforced soon (see stage [Enforced]).
     *
     * @param enforcementTarget a target when the feature will be enforced, at which point the
     *     corresponding [Option] will be removed (hence this parameter has type
     *     `DeprecationTarget`)
     */
    class SoftlyEnforced(enforcementTarget: DeprecationReporter.DeprecationTarget) :
        FeatureStage(Option.Status.Deprecated(enforcementTarget))

    /**
     * Indicates that the feature is enforced: The feature is enabled by default and the
     * corresponding [Option] has been removed.
     *
     * @param enforcedVersion the version when the feature is enforced and the corresponding
     *     [Option] was removed
     * @param additionalMessage the additional message to be shown if the [Option] is used
     */
    class Enforced(enforcedVersion: Version, additionalMessage: String? = null) :
        FeatureStage(Option.Status.Removed(enforcedVersion, additionalMessage))

    /**
     * Indicates that the feature will be removed soon because it was not / is no longer useful (see
     * stage [Removed]).
     *
     * @param removalTarget a target when the feature and the corresponding [Option] will be removed
     */
    class Deprecated(removalTarget: DeprecationReporter.DeprecationTarget) :
        FeatureStage(Option.Status.Deprecated(removalTarget))

    /**
     * Indicates that the feature has been removed: The feature is disabled by default and the
     * corresponding [Option] has been removed.
     *
     * @param removedVersion the version when the feature and the corresponding [Option] were
     *     removed
     * @param additionalMessage the additional message to be shown if the [Option] is used
     */
    class Removed(removedVersion: Version, additionalMessage: String? = null) :
        FeatureStage(Option.Status.Removed(removedVersion, additionalMessage))
}

/** An Android Gradle plugin version. */
enum class Version(

    // Mark these properties as private to prevent external usages from constructing
    // inconsistent messages from these values. They should use methods like
    // getDeprecationTargetMessage() or getRemovedVersionMessage() instead.

    /**
     * String value of the version.
     *
     * Note that since this value will be used in error/warning messages, it should be defined such
     * that it fits well in the overall message:
     *
     *     "This feature will be / was removed in $stringValue of the Android Gradle plugin."
     *
     * For example, avoid assigning string values such as "AGP 4.0", as it will not go along well
     * with the pre-formatted message.
     */
    private val stringValue: String
) {

    /**
     * A version before version 4.0, used when the exact version is not known, except that it's
     * guaranteed to be before 4.0.
     */
    VERSION_BEFORE_4_0("a version before 4.0"),

    VERSION_3_5("version 3.5"),
    VERSION_3_6("version 3.6"),
    VERSION_4_0("version 4.0"),
    VERSION_5_0("version 5.0"),

    ;

    fun getDeprecationTargetMessage(): String {
        return "It will be removed in $stringValue of the Android Gradle plugin."
    }

    fun getRemovedVersionMessage(): String {
        return if (this == VERSION_BEFORE_4_0) {
            "It has been removed from the current version of the Android Gradle plugin."
        } else {
            "It was removed in $stringValue of the Android Gradle plugin."
        }
    }
}