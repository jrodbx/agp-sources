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

import com.android.build.api.dsl.ApplicationPublishing
import com.android.build.api.dsl.ApplicationSingleVariant
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

abstract class ApplicationPublishingImpl@Inject constructor(dslService: DslServices)
    : ApplicationPublishing, AbstractPublishing<ApplicationSingleVariantImpl>(dslService) {

    override fun singleVariant(variantName: String) {
        addSingleVariant(variantName, ApplicationSingleVariantImpl::class.java)
    }

    override fun singleVariant(
        variantName: String,
        action: ApplicationSingleVariant.() -> Unit
    ) {
        addSingleVariantAndConfigure(variantName, ApplicationSingleVariantImpl::class.java, action)
    }

    fun singleVariant(variantName: String, action: Action<ApplicationSingleVariant>) {
        addSingleVariantAndConfigure(variantName, ApplicationSingleVariantImpl::class.java) {
            action.execute(this) }
    }
}
