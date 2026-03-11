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

package com.android.build.api.extension.impl

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.component.analytics.AnalyticsEnabledComponent
import com.android.build.api.component.impl.ComponentImpl
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.instrumentation.manageddevice.ManagedDeviceRegistry
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Component
import com.android.build.api.variant.DslExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.api.variant.VariantExtension
import com.android.build.api.variant.VariantExtensionConfig
import com.android.build.api.variant.VariantSelector
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.errors.IssueReporter
import com.android.utils.appendCapitalized
import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.ExtensionAware

abstract class AndroidComponentsExtensionImpl<DslExtensionT : Any, VariantBuilderT : VariantBuilder, VariantT : Variant>(
  private val dslServices: DslServices,
  override val sdkComponents: SdkComponents,
  override val managedDeviceRegistry: ManagedDeviceRegistry,
  private val variantApiOperations: VariantApiOperationsRegistrar<DslExtensionT, VariantBuilderT, VariantT>,
  private val extension: DslExtensionT,
) : AndroidComponentsExtension<DslExtensionT, VariantBuilderT, VariantT> {

  override fun finalizeDsl(callback: (DslExtensionT) -> Unit) {
    variantApiOperations.add { callback.invoke(it) }
  }

  override fun finalizeDsl(callback: Action<DslExtensionT>) {
    variantApiOperations.add(callback)
  }

  @Deprecated("Replaced by finalizeDsl", replaceWith = ReplaceWith("finalizeDsl(callback)"))
  @Suppress("OverridingDeprecatedMember")
  override fun finalizeDSl(callback: Action<DslExtensionT>) {
    variantApiOperations.add(callback)
  }

  override val pluginVersion: AndroidPluginVersion
    get() = CurrentAndroidGradlePluginVersion.CURRENT_AGP_VERSION

  override fun beforeVariants(selector: VariantSelector, callback: (VariantBuilderT) -> Unit) {
    variantApiOperations.variantBuilderOperations.addPublicOperation({ callback.invoke(it) }, "beforeVariants", selector)
  }

  override fun beforeVariants(selector: VariantSelector, callback: Action<VariantBuilderT>) {
    variantApiOperations.variantBuilderOperations.addPublicOperation(callback, "beforeVariants", selector)
  }

  override fun onVariants(selector: VariantSelector, callback: (VariantT) -> Unit) {
    variantApiOperations.variantOperations.addPublicOperation({ callback.invoke(it) }, "onVariants", selector)
  }

  override fun onVariants(selector: VariantSelector, callback: Action<VariantT>) {
    variantApiOperations.variantOperations.addPublicOperation(callback, "onVariants", selector)
  }

  override fun selector(): VariantSelectorImpl = dslServices.newInstance(VariantSelectorImpl::class.java)

  class RegisteredApiExtension<VariantT : Variant>(
    val dslExtensionTypes: DslExtension,
    val configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension,
  )

  override fun registerExtension(
    dslExtension: DslExtension,
    configurator: (variantExtensionConfig: VariantExtensionConfig<VariantT>) -> VariantExtension,
  ) {
    variantApiOperations.dslExtensions.add(RegisteredApiExtension(dslExtensionTypes = dslExtension, configurator = configurator))

    dslExtension.projectExtensionType?.let { (extension as ExtensionAware).extensions.add(dslExtension.dslName, it) }

    if (extension is CommonExtension) {
      dslExtension.buildTypeExtensionType?.let {
        extension.buildTypes.configureEach { buildType -> buildType.extensions.add(dslExtension.dslName, it) }
      }
      dslExtension.productFlavorExtensionType?.let {
        extension.productFlavors.configureEach { productFlavor -> productFlavor.extensions.add(dslExtension.dslName, it) }
      }
    }
  }

  override fun registerSourceType(name: String) {
    variantApiOperations.sourceSetExtensions.add(name)
  }

  override fun addSourceSetConfigurations(suffix: String) {
    addSourceSetConfigurations(
      suffix,
      useLegacyPrefix = false,
      useGlobalConfiguration = false,
      callingFunctionName = "addSourceSetConfigurations",
      customResolvableConfigurationNameMapper = null,
    )
  }

  override fun addKspConfigurations(useGlobalConfiguration: Boolean) {
    addSourceSetConfigurations(
      affix = "ksp",
      useLegacyPrefix = true,
      useGlobalConfiguration,
      callingFunctionName = "addKspConfigurations",
    ) { componentName ->
      "ksp${componentName.replaceFirstChar { it.uppercase() }}KotlinProcessorClasspath"
    }
  }

  /**
   * Adds a custom configuration for each source set, allowing for legacy naming and behavior.
   *
   * The [affix] parameter determines the naming convention for the generated configurations. For example, if [affix] is "custom", the
   * generated configurations would be "custom", "debugCustom", "releaseCustom", "testCustom", etc. (or "custom", "customDebug",
   * "customRelease", "customTest", etc. if [useLegacyPrefix] is true).
   *
   * This function does the following:
   * 1. Creates a configuration for each source set.
   * 2. Creates resolvable configurations for each component.
   * 3. Ensures that each resolvable configuration extends the corresponding source set configurations.
   *
   * @param affix the suffix (or prefix) to append (or prepend) to the generated configuration names.
   * @param useLegacyPrefix whether to use the [affix] as a prefix. This should be false unless supporting legacy behavior.
   * @param useGlobalConfiguration whether dependencies added to the plain "[affix]" configuration should be added to all resolvable
   *   configurations. This should be false unless supporting legacy behavior.
   * @param callingFunctionName the name of the function that called this method (useful in case of an error)
   * @param customResolvableConfigurationNameMapper an optional custom mapping function to define the mapping of the component name to the
   *   corresponding resolvable configuration name. This should be null unless supporting legacy behavior.
   */
  open fun addSourceSetConfigurations(
    affix: String,
    useLegacyPrefix: Boolean,
    useGlobalConfiguration: Boolean,
    callingFunctionName: String,
    customResolvableConfigurationNameMapper: ((String) -> String)?,
  ) {

    val lowercaseAffix = affix.lowercase()

    val resolvableConfigurationNameMapper =
      when {
        customResolvableConfigurationNameMapper != null -> {
          customResolvableConfigurationNameMapper
        }
        useLegacyPrefix -> { componentName -> "${lowercaseAffix.appendCapitalized(componentName)}_resolved" }
        else -> { componentName -> "${componentName.appendCapitalized(lowercaseAffix)}_resolved" }
      }

    if (variantApiOperations.sourceSetConfigurationsMap.put(lowercaseAffix, resolvableConfigurationNameMapper) != null) {
      dslServices.issueReporter.reportError(
        IssueReporter.Type.GENERIC,
        "Multiple identical calls to $callingFunctionName is not supported.",
      )
    }

    registerConfigurations(lowercaseAffix, useLegacyPrefix)

    val globalConfiguration =
      if (useGlobalConfiguration) {
        dslServices.configurations.maybeCreate(lowercaseAffix).apply {
          isCanBeResolved = false
          isCanBeConsumed = false
        }
      } else {
        null
      }

    val callback = getOperationCallback(resolvableConfigurationNameMapper, globalConfiguration, lowercaseAffix, useLegacyPrefix)

    variantApiOperations.variantOperations.addInternalOperation({ callback.invoke(it) }, callingFunctionName)
  }

  open fun registerConfigurations(lowercaseAffix: String, useLegacyPrefix: Boolean) {
    if (extension is CommonExtension) {
      extension.sourceSets.configureEach { sourceSet ->
        val configurationName = getConfigurationName(lowercaseAffix, useLegacyPrefix, sourceSet.name)
        dslServices.configurations.maybeCreate(configurationName).apply {
          isCanBeResolved = false
          isCanBeConsumed = false
        }
      }
    }
  }

  /**
   * Returns the names of the source sets that are used for the given component.
   *
   * @param component the component
   */
  private fun calculateSourceSetNames(component: Component): List<String> {
    val sourceSetNames = mutableSetOf<String>()
    val componentImpl =
      if (component is AnalyticsEnabledComponent) {
        (component.delegate as? ComponentImpl<*>)
      } else {
        component as? ComponentImpl<*>
      } ?: throw RuntimeException("Unexpected type for component \"${component.name}\".")
    val sourceSetPrefix = componentImpl.componentType.prefix
    if (sourceSetPrefix.isEmpty()) {
      sourceSetNames.add("main")
    } else {
      sourceSetNames.add(sourceSetPrefix)
    }
    component.buildType?.also { buildType ->
      sourceSetNames.add(sourceSetPrefix.appendCapitalized(buildType).replaceFirstChar { it.lowercase() })
    }
    component.productFlavors.forEach { productFlavor ->
      sourceSetNames.add(sourceSetPrefix.appendCapitalized(productFlavor.second).replaceFirstChar { it.lowercase() })
    }
    val combinedFlavors = component.productFlavors.joinToString("") { it.second.replaceFirstChar { char -> char.uppercase() } }
    sourceSetNames.add("$sourceSetPrefix$combinedFlavors".replaceFirstChar { it.lowercase() })
    val variantSourceSetSuffix = component.name.removeSuffix(componentImpl.componentType.suffix).replaceFirstChar { it.uppercase() }
    sourceSetNames.add("$sourceSetPrefix$variantSourceSetSuffix".replaceFirstChar { it.lowercase() })
    return sourceSetNames.toList()
  }

  open fun getOperationCallback(
    resolvableConfigurationNameMapper: (String) -> String,
    globalConfiguration: Configuration?,
    lowercaseAffix: String,
    useLegacyPrefix: Boolean,
  ): (VariantT) -> Unit {
    val callback: (VariantT) -> Unit = { variant ->
      val variantResolvableConfiguration =
        dslServices.configurations.maybeCreate(resolvableConfigurationNameMapper(variant.name)).apply {
          isCanBeResolved = true
          isCanBeConsumed = false
        }
      if (globalConfiguration?.allDependencies?.isNotEmpty() == true) {
        variantResolvableConfiguration.extendsFrom(globalConfiguration)
      }
      calculateSourceSetNames(variant)
        .mapNotNull { dslServices.configurations.findByName(getConfigurationName(lowercaseAffix, useLegacyPrefix, it)) }
        .filter { it.allDependencies.isNotEmpty() }
        .forEach { variantResolvableConfiguration.extendsFrom(it) }
      variant.nestedComponents.forEach { component ->
        val componentResolvableConfiguration =
          dslServices.configurations.maybeCreate(resolvableConfigurationNameMapper(component.name)).apply {
            isCanBeResolved = true
            isCanBeConsumed = false
          }
        if (globalConfiguration?.allDependencies?.isNotEmpty() == true) {
          componentResolvableConfiguration.extendsFrom(globalConfiguration)
        }
        calculateSourceSetNames(component)
          .mapNotNull { dslServices.configurations.findByName(getConfigurationName(lowercaseAffix, useLegacyPrefix, it)) }
          .filter { it.allDependencies.isNotEmpty() }
          .forEach { componentResolvableConfiguration.extendsFrom(it) }
      }
    }
    return callback
  }

  /**
   * Returns the name of the configuration that should be used for the given source set.
   *
   * @param affix the affix that was used to call [addSourceSetConfigurations]
   * @param useLegacyPrefix whether the legacy prefix should be used
   * @param sourceSetName the name of the source set
   */
  fun getConfigurationName(affix: String, useLegacyPrefix: Boolean, sourceSetName: String): String {
    val thisLowercaseAffix = affix.lowercase()
    if (sourceSetName == "main") {
      return thisLowercaseAffix
    }
    if (useLegacyPrefix) {
      return thisLowercaseAffix.appendCapitalized(sourceSetName)
    }
    return sourceSetName.appendCapitalized(thisLowercaseAffix)
  }
}
