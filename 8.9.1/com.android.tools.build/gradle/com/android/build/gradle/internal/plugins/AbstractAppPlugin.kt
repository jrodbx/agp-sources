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
package com.android.build.gradle.internal.plugins

import com.android.AndroidProjectTypes
import com.android.build.api.dsl.AndroidResources
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.BuildType
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.DefaultConfig
import com.android.build.api.dsl.Installation
import com.android.build.api.dsl.ProductFlavor
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantBuilder
import com.android.build.gradle.internal.component.VariantCreationConfig
import com.android.build.gradle.internal.core.dsl.VariantDslInfo
import com.google.wireless.android.sdk.stats.GradleBuildProject
import org.gradle.api.component.SoftwareComponentFactory
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

/** Gradle plugin class for 'application' projects.  */
abstract class AbstractAppPlugin<
        BuildFeaturesT: BuildFeatures,
        BuildTypeT: BuildType,
        DefaultConfigT: DefaultConfig,
        ProductFlavorT: ProductFlavor,
        AndroidResourcesT: AndroidResources,
        InstallationT: Installation,
        AndroidT: CommonExtension<
                BuildFeaturesT,
                BuildTypeT,
                DefaultConfigT,
                ProductFlavorT,
                AndroidResourcesT,
                InstallationT>,
        AndroidComponentsT : AndroidComponentsExtension<
                in AndroidT,
                in VariantBuilderT,
                in VariantT>,
        VariantBuilderT : VariantBuilder,
        VariantDslInfoT: VariantDslInfo,
        CreationConfigT: VariantCreationConfig,
        VariantT : Variant>
@Inject constructor(
        registry: ToolingModelBuilderRegistry?,
        componentFactory: SoftwareComponentFactory?,
        listenerRegistry: BuildEventsListenerRegistry?,
        buildFeatures: org.gradle.api.configuration.BuildFeatures
) : BasePlugin<BuildFeaturesT, BuildTypeT, DefaultConfigT, ProductFlavorT, AndroidResourcesT, InstallationT, AndroidT, AndroidComponentsT, VariantBuilderT, VariantDslInfoT, CreationConfigT, VariantT>(
        registry!!,
        componentFactory!!,
        listenerRegistry!!,
        buildFeatures,
) {

    override fun getProjectType(): Int {
        return AndroidProjectTypes.PROJECT_TYPE_APP
    }

    override fun getAnalyticsPluginType(): GradleBuildProject.PluginType {
        return GradleBuildProject.PluginType.APPLICATION
    }
}
