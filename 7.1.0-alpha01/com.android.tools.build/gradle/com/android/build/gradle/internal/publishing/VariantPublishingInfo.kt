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

package com.android.build.gradle.internal.publishing

import com.android.build.gradle.internal.dsl.AbstractPublishing

/**
 * A data class wraps publishing info for a variant.
 */
data class VariantPublishingInfo(
    val components: Set<ComponentPublishingInfo>
) {
    private val aarComponent = components.find { it.type == AbstractPublishing.Type.AAR }
    private val apkComponent = components.find { it.type == AbstractPublishing.Type.APK }
    private val aabComponent = components.find { it.type == AbstractPublishing.Type.AAB }

    fun isAarPublished(): Boolean {
        return aarComponent != null
    }

    fun isApkPublished(): Boolean {
        return apkComponent != null
    }

    fun isAabPublished(): Boolean {
        return aabComponent != null
    }

    fun getApkComponentName(): String {
        return apkComponent!!.componentName
    }

    fun getAabComponentName(): String {
        return aabComponent!!.componentName
    }
}
