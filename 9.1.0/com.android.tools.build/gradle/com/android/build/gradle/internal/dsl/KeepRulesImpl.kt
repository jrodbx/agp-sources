package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.KeepRules
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.services.DslServices
import java.io.File
import javax.inject.Inject
import org.gradle.api.provider.SetProperty

abstract class KeepRulesImpl @Inject constructor(dslService: DslServices) : KeepRules {

  internal abstract val ignoreFrom: MutableSet<String>
  internal abstract var ignoreFromAllExternalDependencies: Boolean
  abstract override var includeDefault: Boolean
  abstract override val files: SetProperty<File>

  @WithLazyInitialization
  @Suppress("unused")
  protected fun lazyInit() {
    includeDefault = true
  }

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
