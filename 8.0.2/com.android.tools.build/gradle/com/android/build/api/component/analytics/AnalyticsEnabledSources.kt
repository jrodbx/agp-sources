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

package com.android.build.api.component.analytics

import com.android.build.api.variant.SourceDirectories
import com.android.build.api.variant.Sources
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class AnalyticsEnabledSources @Inject constructor(
    open val delegate: Sources,
    val stats: GradleBuildVariant.Builder,
    val objectFactory: ObjectFactory
): Sources by delegate {

    override val java: SourceDirectories.Flat
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_JAVA_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledFlat::class.java,
                delegate.java,
                stats,
                objectFactory)
        }

    override val kotlin: SourceDirectories.Flat
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_KOTLIN_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledFlat::class.java,
                delegate.kotlin,
                stats,
                objectFactory)
        }

    override val baselineProfiles: SourceDirectories.Flat?
        get()  = delegate.baselineProfiles?.let {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_BASELINE_PROFILES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledFlat::class.java,
                it,
                stats,
                objectFactory)
        }

    override val res: SourceDirectories.Layered
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_RES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledLayered::class.java,
                delegate.res,
                stats,
                objectFactory)
        }

    override val assets: SourceDirectories.Layered
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_ASSETS_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledLayered::class.java,
                delegate.assets,
                stats,
                objectFactory)
        }

    override val jniLibs: SourceDirectories.Layered
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_JNI_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledLayered::class.java,
                delegate.jniLibs,
                stats,
                objectFactory)
        }

    override val shaders: SourceDirectories.Layered?
        get() =
            delegate.shaders?.let {
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.SOURCES_SHADERS_ACCESS_VALUE
                objectFactory.newInstance(
                    AnalyticsEnabledLayered::class.java,
                    it,
                    stats,
                    objectFactory)
            }

    override val mlModels: SourceDirectories.Layered
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_ML_MODELS_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledLayered::class.java,
                delegate.mlModels,
                stats,
                objectFactory)
        }

    override val aidl: SourceDirectories.Flat?
        get() = delegate.aidl?.let{
                stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                    VariantPropertiesMethodType.SOURCES_AIDL_ACCESS_VALUE
                objectFactory.newInstance(
                    AnalyticsEnabledFlat::class.java,
                    it,
                    stats,
                    objectFactory
                )
            }

    override val renderscript: SourceDirectories.Flat?
        get() = delegate.renderscript?.let {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_RENDERSCRIPT_ACCESS_VALUE
            objectFactory.newInstance(
                AnalyticsEnabledFlat::class.java,
                it,
                stats,
                objectFactory)
        }

    override val resources: SourceDirectories.Flat
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.SOURCES_RESOURCES_ACCESS_VALUE
            return objectFactory.newInstance(
                AnalyticsEnabledFlat::class.java,
                delegate.resources,
                stats,
                objectFactory)
        }

    override fun getByName(name: String): SourceDirectories.Flat {
        stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
            VariantPropertiesMethodType.SOURCES_EXTRAS_ACCESS_VALUE
        return delegate.getByName(name)
    }
}
