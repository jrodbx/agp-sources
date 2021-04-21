/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.build.gradle.internal.tasks

import org.gradle.api.tasks.Internal

/**
 * Base Android task with a variant name and support for analytics
 *
 * DO NOT EXTEND THIS METHOD DIRECTLY. Instead extend:
 * - [NewIncrementalTask]
 * - [NonIncrementalTask]
 *
 */
abstract class AndroidVariantTask : BaseTask(), VariantAwareTask {

    @Internal("No influence on output, this is for our build stats reporting mechanism")
    override lateinit var variantName: String
}
