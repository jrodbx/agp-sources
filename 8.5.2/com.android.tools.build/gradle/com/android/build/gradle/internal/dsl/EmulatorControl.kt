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

import com.android.build.api.dsl.EmulatorControl
import com.android.build.gradle.internal.dsl.decorator.annotation.WithLazyInitialization
import com.android.build.gradle.internal.services.DslServices
import javax.inject.Inject

abstract class EmulatorControl @Inject @WithLazyInitialization("lazyInit") constructor(private val dslServices: DslServices) :
    EmulatorControl {

    @Suppress("unused") // the call is injected by DslDecorator
    protected fun lazyInit() {
        secondsValid = 3600
        enable = false
    }
}
