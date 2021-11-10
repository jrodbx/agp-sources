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

@file:JvmName("PublishingUtils")

package com.android.build.gradle.internal.utils

import com.android.build.api.dsl.LibraryPublishing
import com.android.build.api.dsl.SingleVariant
import com.android.build.gradle.internal.dsl.AbstractPublishing
import com.android.build.gradle.internal.dsl.ApplicationPublishingImpl
import com.android.build.gradle.internal.publishing.ComponentPublishingInfo
import com.android.build.gradle.internal.publishing.VariantPublishingInfo
import com.android.build.gradle.options.BooleanOption
import com.android.build.gradle.options.ProjectOptions
import com.android.builder.errors.IssueReporter

fun createPublishingInfoForLibrary(
    publishing: LibraryPublishing,
    projectOptions: ProjectOptions,
    variantName: String
): VariantPublishingInfo {
    val optIn = publishingFeatureOptIn(publishing as AbstractPublishing<SingleVariant>, projectOptions)
    val addedVariant = publishing.singleVariants.filter { it.variantName == variantName }
    val publishVariant = !optIn || optIn && addedVariant.isNotEmpty()
    return if (publishVariant) {
        VariantPublishingInfo(setOf(ComponentPublishingInfo(variantName, AbstractPublishing.Type.AAR)))
    } else {
        VariantPublishingInfo(setOf())
    }
}

fun createPublishingInfoForApp(
    publishing: ApplicationPublishingImpl,
    projectOptions: ProjectOptions,
    variantName: String,
    hasDynamicFeatures: Boolean,
    issueReporter: IssueReporter
): VariantPublishingInfo {
    val optIn = publishingFeatureOptIn(publishing as AbstractPublishing<SingleVariant>, projectOptions)
    val components = mutableSetOf<ComponentPublishingInfo>()
    if (optIn) {
        publishing.singleVariants.find { it.variantName == variantName }?.let {
            if (it.publishVariantAsApk) {
                // do not publish the APK(s) if there are dynamic feature.
                if (hasDynamicFeatures) {
                    issueReporter.reportError(
                        IssueReporter.Type.GENERIC,
                        "When dynamic feature modules exist, publishing APK is not allowed."
                    )
                } else {
                    components.add(
                        ComponentPublishingInfo(variantName, AbstractPublishing.Type.APK))
                }
            } else {
                components.add(
                    ComponentPublishingInfo(variantName, AbstractPublishing.Type.AAB))
            }
        }
    } else {
        // do not publish the APK(s) if there are dynamic feature.
        if (!hasDynamicFeatures) {
            components.add(
                ComponentPublishingInfo(
                    "${variantName}_apk",
                    AbstractPublishing.Type.APK))
        }
        components.add(
            ComponentPublishingInfo(
                "${variantName}_aab",
                AbstractPublishing.Type.AAB))
    }
    return VariantPublishingInfo(components)
}

fun publishingFeatureOptIn(
    publishing: AbstractPublishing<SingleVariant>,
    projectOptions: ProjectOptions
): Boolean {
    val publishingDslUsed = publishing.singleVariants.isNotEmpty()
    val disableComponentCreation =
        projectOptions.get(BooleanOption.DISABLE_AUTOMATIC_COMPONENT_CREATION)
    return publishingDslUsed || disableComponentCreation
}
