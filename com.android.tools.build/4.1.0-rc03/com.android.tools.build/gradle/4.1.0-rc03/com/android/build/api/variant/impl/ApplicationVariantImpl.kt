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
package com.android.build.api.variant.impl

import com.android.build.api.component.ComponentIdentity
import com.android.build.api.dsl.DependenciesInfo
import com.android.build.api.variant.ApplicationVariant
import com.android.build.gradle.internal.core.VariantDslInfo
import com.android.build.gradle.internal.services.VariantApiServices
import org.gradle.api.Action
import javax.inject.Inject

open class ApplicationVariantImpl @Inject constructor(
    variantDslInfo: VariantDslInfo,
    dslDependencyInfo: DependenciesInfo,
    variantConfiguration: ComponentIdentity,
    variantApiServices: VariantApiServices
) : VariantImpl<ApplicationVariantPropertiesImpl>(
    variantDslInfo,
    variantConfiguration,
    variantApiServices
), ApplicationVariant<ApplicationVariantPropertiesImpl> {

    override val debuggable: Boolean
        get() = variantDslInfo.isDebuggable

    // only instantiate this if this is needed. This allows non-built variant to not do too much work.
    override val dependenciesInfo: DependenciesInfo by lazy {
        if (variantApiServices.isPostVariantApi) {
            // this is queried after the Variant API, so we can just return the DSL object.
            dslDependencyInfo
        } else {
            variantApiServices.newInstance(
                MutableDependenciesInfoImpl::class.java,
                dslDependencyInfo,
                variantApiServices
            )
        }
    }

    override fun dependenciesInfo(action: DependenciesInfo.() -> Unit) {
        action.invoke(dependenciesInfo)
    }

    fun dependenciesInfo(action: Action<DependenciesInfo>) {
        action.execute(dependenciesInfo)
    }
}