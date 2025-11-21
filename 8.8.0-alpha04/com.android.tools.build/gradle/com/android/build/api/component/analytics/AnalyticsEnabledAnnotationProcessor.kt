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

import com.android.build.api.variant.AnnotationProcessor
import com.android.tools.build.gradle.internal.profile.VariantPropertiesMethodType
import com.google.wireless.android.sdk.stats.GradleBuildVariant
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject

open class AnalyticsEnabledAnnotationProcessor @Inject constructor(
    open val delegate: AnnotationProcessor,
    val stats: GradleBuildVariant.Builder,
) : AnnotationProcessor {

    override val classNames: ListProperty<String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANNOTATION_PROCESSOR_CLASS_NAMES_VALUE
            return delegate.classNames
        }
    override val arguments: MapProperty<String, String>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANNOTATION_PROCESSOR_ARGUMENTS_VALUE
            return delegate.arguments
        }
    override val argumentProviders: MutableList<CommandLineArgumentProvider>
        get() {
            stats.variantApiAccessBuilder.addVariantPropertiesAccessBuilder().type =
                VariantPropertiesMethodType.ANNOTATION_PROCESSOR_ARGUMENT_PROVIDERS_VALUE
            return delegate.argumentProviders
        }
}
