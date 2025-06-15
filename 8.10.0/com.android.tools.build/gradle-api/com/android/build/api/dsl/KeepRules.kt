package com.android.build.api.dsl

import org.gradle.api.Incubating

/**
 * DSL object for external library dependencies keep rules configurations.
 */
@Incubating
interface KeepRules {

    @Incubating
    @Deprecated("Renamed to ignoreFrom", replaceWith = ReplaceWith("ignoreFrom"))
    fun ignoreExternalDependencies(vararg ids: String)

    /**
     * Ignore keep rules from listed external dependencies. External dependencies can be specified
     * via GAV coordinates(e.g. "groupId:artifactId:version") or in the format of
     * "groupId:artifactId" in which case dependencies are ignored as long as they match
     * groupId & artifactId.
     */
    @Incubating
    fun ignoreFrom(vararg ids: String)

    @Incubating
    @Deprecated(
        "Renamed to ignoreFromAllExternalDependencies",
        replaceWith = ReplaceWith("ignoreFromAllExternalDependencies")
    )
    fun ignoreAllExternalDependencies(ignore: Boolean)

    /**
     * Ignore keep rules from all the external dependencies.
     */
    @Incubating
    fun ignoreFromAllExternalDependencies(ignore: Boolean)
}
