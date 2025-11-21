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

package com.android.build.gradle.internal.dsl

import com.android.build.api.dsl.SingleVariant
import com.android.build.gradle.internal.services.DslServices
import com.android.builder.errors.IssueReporter

abstract class AbstractPublishing<T : SingleVariant>(val dslService: DslServices) {

    abstract val singleVariants: MutableList<T>

    protected fun addSingleVariant(
        variantName: String,
        implementationClass: Class<T>
    ): T {
        checkSingleVariantUniqueness(variantName, singleVariants)
        val singleVariant = dslService.newDecoratedInstance(implementationClass, dslService, variantName)
        singleVariants.add(singleVariant)
        return singleVariant
    }

    protected fun addSingleVariantAndConfigure(
        variantName: String,
        implementationClass: Class<T>,
        action: T.() -> Unit
    ) {
        action.invoke(addSingleVariant(variantName, implementationClass))
    }

    private fun checkSingleVariantUniqueness(
        variantName: String,
        singleVariants: List<T>
    ) {
        if (singleVariants.any { it.variantName == variantName}) {
            dslService.issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                "Using singleVariant publishing DSL multiple times to publish " +
                        "variant \"$variantName\" to component \"$variantName\" is not allowed."
            )
        }
    }

    /**
     * Publication artifact types.
     */
    enum class Type {
        AAR,
        APK,
        AAB
    }
}
