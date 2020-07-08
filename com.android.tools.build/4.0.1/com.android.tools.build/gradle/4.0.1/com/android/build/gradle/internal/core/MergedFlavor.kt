/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.build.gradle.internal.core

import com.android.builder.core.AbstractProductFlavor
import com.android.builder.errors.IssueReporter
import com.android.builder.model.ProductFlavor
import com.google.common.collect.Lists


/**
 * The merger of the default config and all of a variant's flavors (if any)
 */
public class MergedFlavor(
        name: String, val issueReporter: IssueReporter) : AbstractProductFlavor(name) {

    companion object {

        /**
         * Clone a given product flavor.
         *
         * @param productFlavor the flavor to clone.
         * @return a new MergedFlavor instance that is a clone of the flavor.
         */
        @JvmStatic
        fun clone(productFlavor: ProductFlavor, issueReporter: IssueReporter): MergedFlavor {
            val mergedFlavor = MergedFlavor(productFlavor.name, issueReporter)
            mergedFlavor._initWith(productFlavor)
            return mergedFlavor
        }

        /**
         * Merges the flavors by analyzing the specified one and the list. Flavors whose position in
         * the list is higher will have their values overwritten by the lower-position flavors (in
         * case they have non-null values for some properties). E.g. if flavor at position 1
         * specifies applicationId &quot;my.application&quot;, and flavor at position 0 specifies
         * &quot;sample.app&quot;, merged flavor will have applicationId &quot;sampleapp&quot; (if
         * there are no other flavors overwriting this value). Flavor `lowestPriority`, as the name
         * says, has the lowest priority of them all, and will always be overwritten.
         *
         * @param lowestPriority flavor with the lowest priority
         * @param flavors flavors to merge
         * @return final merged product flavor
         */
        @JvmStatic
        fun mergeFlavors(
                lowestPriority: ProductFlavor,
                flavors: List<ProductFlavor>,
                issueReporter: IssueReporter): MergedFlavor {
            val mergedFlavor = clone(lowestPriority, issueReporter)
            for (flavor in Lists.reverse(flavors)) {
                mergedFlavor.mergeWithHigherPriorityFlavor(flavor)
            }

            /*
             * For variants with product flavor dimensions d1, d2 and flavors f1 of d1 and f2 of d2, we
             * will have final applicationSuffixId suffix(default).suffix(f2).suffix(f1). However, the
             * previous implementation of product flavor merging would produce
             * suffix(default).suffix(f1).suffix(f2). We match that behavior below as we do not want to
             * change application id of developers' applications. The same applies to versionNameSuffix.
             */
            var applicationIdSuffix = lowestPriority.applicationIdSuffix
            var versionNameSuffix = lowestPriority.versionNameSuffix
            for (mFlavor in flavors) {
                applicationIdSuffix = mergeApplicationIdSuffix(
                        mFlavor.applicationIdSuffix, applicationIdSuffix)
                versionNameSuffix = mergeVersionNameSuffix(
                        mFlavor.versionNameSuffix, versionNameSuffix)
            }
            mergedFlavor.applicationIdSuffix = applicationIdSuffix
            mergedFlavor.versionNameSuffix = versionNameSuffix

            return mergedFlavor
        }
    }

    override fun setVersionCode(versionCode: Int?): ProductFlavor {
        // calling setVersionCode results in a sync Error because the manifest merger doesn't pick
        // up the change.
        reportErrorWithWorkaround("versionCode", "versionCodeOverride", versionCode)
        return this
    }

    override fun setVersionName(versionName: String?): ProductFlavor {
        // calling setVersionName results in a sync Error because the manifest merger doesn't pick
        // up the change.
        reportErrorWithWorkaround("versionName", "versionNameOverride", versionName)
        return this
    }

    private fun reportErrorWithWorkaround(
            fieldName: String, outputFieldName: String, fieldValue: Any?) {
        val formattedFieldValue = if (fieldValue is String) {
            "\"" + fieldValue + "\""
        } else {
            fieldValue.toString()
        }

        val message = """$fieldName cannot be set on a mergedFlavor directly.
                |$outputFieldName can instead be set for variant outputs using the following syntax:
                |android {
                |    applicationVariants.all { variant ->
                |        variant.outputs.each { output ->
                |            output.$outputFieldName = $formattedFieldValue
                |        }
                |    }
                |}""".trimMargin()

        issueReporter.reportError(IssueReporter.Type.GENERIC, message)
    }
}