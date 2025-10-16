/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.build.gradle.internal.services.DslServices
import com.android.builder.core.ComponentType
import com.android.builder.errors.IssueReporter

/**
 * Function checks if resource shrink flag is eligible.
 * It reports error and throw exception in case it's not
 */
fun checkShrinkResourceEligibility(
    componentType: ComponentType,
    dslServices: DslServices,
    shrinkResourceFlag: Boolean
) {
    if (shrinkResourceFlag) {
        if (componentType.isDynamicFeature) {
            dslServices.issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                "Resource shrinking must be configured for base module."
            )
        }
        if (componentType.isAar) {
            dslServices.issueReporter.reportError(
                IssueReporter.Type.GENERIC,
                "Resource shrinker cannot be used for libraries."
            )

        }
    }
}

