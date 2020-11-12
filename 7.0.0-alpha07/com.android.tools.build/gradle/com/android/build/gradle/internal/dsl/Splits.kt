/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.build.api.dsl.AbiSplit
import com.android.build.api.dsl.DensitySplit
import com.android.build.gradle.internal.services.DslServices
import org.gradle.api.Action
import javax.inject.Inject

open class Splits @Inject constructor(dslServices: DslServices) :
    com.android.build.api.dsl.Splits {

    override val density: DensitySplitOptions =
        dslServices.newInstance(DensitySplitOptions::class.java)

    override val abi: AbiSplitOptions = dslServices.newInstance(AbiSplitOptions::class.java)

    /**
     * Encapsulates settings for
     * [building per-language (or locale) APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split).
     *
     * **Note:** Building per-language APKs is supported only when
     * [building configuration APKs](https://developer.android.com/topic/instant-apps/guides/config-splits.html)
     * for [Android Instant Apps](https://developer.android.com/topic/instant-apps/index.html).
     */
    val language: LanguageSplitOptions =
        dslServices.newInstance(LanguageSplitOptions::class.java)

    /**
     * Encapsulates settings for
     * [building per-density APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-density-split).
     *
     * For more information about the properties you can configure in this block, see
     * [DensitySplitOptions].
     */
    fun density(action: Action<DensitySplitOptions>) {
        action.execute(density)
    }

    override fun density(action: DensitySplit.() -> Unit) {
        action.invoke(density)
    }

    /**
     * Encapsulates settings for <a
     * [building per-ABI APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split).
     *
     * For more information about the properties you can configure in this block, see
     * [AbiSplitOptions].
     */
    fun abi(action: Action<AbiSplitOptions>) {
        action.execute(abi)
    }

    override fun abi(action: AbiSplit.() -> Unit) {
        action.invoke(abi)
    }

    /**
     * Encapsulates settings for
     * [building per-language (or locale) APKs](https://developer.android.com/studio/build/configure-apk-splits.html#configure-abi-split).
     *
     * **Note:** Building per-language APKs is supported only when
     * [building configuration APKs](https://developer.android.com/topic/instant-apps/guides/config-splits.html)
     * for [Android Instant Apps](https://developer.android.com/topic/instant-apps/index.html).
     *
     * For more information about the properties you can configure in this block, see
     * [LanguageSplitOptions].
     */
    fun language(action: Action<LanguageSplitOptions>) {
        action.execute(language)
    }

    override val densityFilters: Set<String>
        get() = density.applicableFilters

    override val abiFilters: Set<String>
        get() = abi.applicableFilters

    /**
     * Returns the list of languages (or locales) that the plugin will generate separate APKs for.
     *
     * If this property returns `null`, it means the plugin will not generate separate per-language
     * APKs. That is, each APK will include resources for all languages your project supports.
     *
     * @return a set of languages (or locales).
     */
    val languageFilters: Set<String>
        get() = language.applicationFilters
}
