/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.build.gradle.internal.ide.kmp.serialization

import com.android.kotlin.multiplatform.ide.models.serialization.AndroidCompilationModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidDependencyModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidSourceSetModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.AndroidTargetModelSerializer
import com.android.kotlin.multiplatform.ide.models.serialization.androidCompilationKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidDependencyKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidSourceSetKey
import com.android.kotlin.multiplatform.ide.models.serialization.androidTargetKey
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializationExtension
import org.jetbrains.kotlin.gradle.idea.serialize.IdeaKotlinExtrasSerializer
import org.jetbrains.kotlin.tooling.core.Extras

/**
 * An extension that provides the kotlin plugin with information on how to serialize android extra
 * models.
 */
class AndroidExtrasSerializationExtension : IdeaKotlinExtrasSerializationExtension {

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> serializer(key: Extras.Key<T>): IdeaKotlinExtrasSerializer<T>? {
        return when(key) {
            androidTargetKey -> AndroidTargetModelSerializer as IdeaKotlinExtrasSerializer<T>
            androidCompilationKey -> AndroidCompilationModelSerializer as IdeaKotlinExtrasSerializer<T>
            androidSourceSetKey -> AndroidSourceSetModelSerializer as IdeaKotlinExtrasSerializer<T>
            androidDependencyKey -> AndroidDependencyModelSerializer as IdeaKotlinExtrasSerializer<T>
            else -> null
        }
    }
}
