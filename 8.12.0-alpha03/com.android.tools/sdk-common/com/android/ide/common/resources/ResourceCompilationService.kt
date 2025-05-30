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

package com.android.ide.common.resources

import java.io.Closeable
import java.io.File
import java.io.IOException

/** Abstraction for resource compiler services used by the resource merger. */
interface ResourceCompilationService : Closeable {
    /** Submit a request. */
    @Throws(IOException::class)
    fun submitCompile(request: CompileResourceRequest)
    /** Given a request, returns the output file that would, will or has been written. */
    fun compileOutputFor(request: CompileResourceRequest): File
}