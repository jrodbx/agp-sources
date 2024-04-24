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

package com.android.build.gradle.internal.testing.screenshot

import com.android.build.gradle.internal.testing.screenshot.ResponseTypeAdapter
import com.google.gson.Gson
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Path

class ResponseProcessor(private val path: Path) {

    fun process(): Response {
        val exitValue: Int
        val responseFile: File
        val response: Response
        try {
            responseFile = path.resolve("response.json").toFile()
            response = ResponseTypeAdapter().fromJson(responseFile.readText())
            exitValue = response.status
        } catch (e: Exception) {
            throw GradleException("Unable to render screenshots.", e)
        }
        if (exitValue == 3) {
            throw GradleException("No previews found to render. Use @Preview annotation on your composables in your test files to render images")
        }
        return response
    }
}
