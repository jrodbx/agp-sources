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

package com.android.build.api.variant

import org.gradle.api.Incubating

/**
 * Interface denoting a sub type of [Variant] that produces dex files.
 *
 * @param T the sub type of [Dexing] that contains all dexing related settings for that particular
 * [Variant] type.
 */
@Incubating
interface ProducesDex<T: Dexing> {

    /**
     * Variant settings related to transforming bytecodes into dex files initialized from
     * the corresponding fields in the DSL.
     */
    val dexing: T

    /**
     * Variant settings related to transforming bytecodes into dex files initialized from
     * the corresponding fields in the DSL.
     */
    fun dexing(action: T.() -> Unit)
}
