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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.ApplicationAndroidResources
import com.android.build.api.dsl.ApplicationBuildFeatures
import com.android.build.api.dsl.ApplicationBuildType
import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.ApplicationInstallation
import com.android.build.api.dsl.ApplicationProductFlavor
import com.android.build.gradle.internal.dsl.decorator.ApplicationInstallationImpl
import com.android.build.gradle.internal.plugins.DslContainerProvider
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

/** Internal implementation of the 'new' DSL interface */
abstract class ApplicationExtensionImpl @Inject constructor(
    dslServices: DslServices,
    dslContainers: DslContainerProvider<
            ApplicationDefaultConfig,
            ApplicationBuildType,
            ApplicationProductFlavor,
            SigningConfig>
) :
    TestedExtensionImpl<
            ApplicationBuildFeatures,
            ApplicationBuildType,
            ApplicationDefaultConfig,
            ApplicationProductFlavor,
            ApplicationAndroidResources,
            ApplicationInstallation>(
        dslServices,
        dslContainers
    ),
    InternalApplicationExtension {

    override val buildFeatures: ApplicationBuildFeatures =
        dslServices.newInstance(ApplicationBuildFeaturesImpl::class.java)

    override val androidResources: ApplicationAndroidResources =
        dslServices.newDecoratedInstance(ApplicationAndroidResourcesImpl::class.java, dslServices)

    override val installation: ApplicationInstallation =
        dslServices.newDecoratedInstance(ApplicationInstallationImpl::class.java, dslServices)
}
