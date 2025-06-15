/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.api.variant

/**
 * Marker type for [Variant] extension objects.
 *
 * Variant extension must be registered using the
 * [AndroidComponentsExtension.registerExtension] API and can be
 * retrieved from a [Variant] instance using the [Variant.getExtension] API.
 *
 * Since this type will most likely be used as [org.gradle.api.Task]'s input, your subtype
 * should also extend [java.io.Serializable]
 *
 * This variant extension will be passed to all plugins that register a
 * [AndroidComponentsExtension.onVariants] block. In case several plugins modify some of the
 * variant extension fields, it is primordial to define all the fields as a
 * [org.gradle.api.provider.Property].  This will ensure that the tasks execution blocks that call
 * [org.gradle.api.provider.Property.get] will get the final value, regardless of the order in which
 * plugins are configured.
 *
 * Example of a sub-type :
 *
 * <code>
 * abstract class VariantDslExtension @Inject constructor(
 *     // Do not keep a reference on the VariantExtensionConfig as it is not serializable,
 *     // use the constructor to extract the values for the BuildType/ProductFlavor extensions
 *     // that can be obtained from the VariantExtensionConfig.
 *     extensionConfig: VariantExtensionConfig<*>
 * ): VariantExtension, java.io.Serializable {
 *     abstract val variantSettingOne: Property<String>
 *     abstract val variantSettingTwo: Property<Int>
 * </code>
 *
 * and this is the corresponding registration and instantiation code :
 * <code>
 *    androidComponents.registerExtension(
 *      DslExtension.Builder("dslExtension")
 *          .extendProjectWith(ProjectDslExtension::class.java)
 *          .build()
 *    ) { config ->
 *          project.objects.newInstance(
 *              VariantDslExtension::class.java,
 *              config
 *          )
 *    }
 * </code>
 *
 */
interface VariantExtension
