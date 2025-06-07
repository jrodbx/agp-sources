package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.KeepRules
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

abstract class KeepRulesImpl@Inject constructor(dslService: DslServices) : KeepRules {

    internal abstract val ignoreFrom: MutableSet<String>
    internal abstract var ignoreFromAllExternalDependencies: Boolean

    override fun ignoreExternalDependencies(vararg ids: String) {
        ignoreFrom.addAll(ids)
    }

    override fun ignoreAllExternalDependencies(ignore: Boolean) {
        ignoreFromAllExternalDependencies = ignore
    }

    override fun ignoreFrom(vararg ids: String) {
        ignoreFrom.addAll(ids)
    }

    override fun ignoreFromAllExternalDependencies(ignore: Boolean) {
        ignoreFromAllExternalDependencies = ignore
    }
}
