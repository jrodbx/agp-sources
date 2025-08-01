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

package com.android.build.gradle.external.gnumake

/**
 * Return true if the executable in [command] ends with [executableName] or [executableName].exe.
 */
internal fun endsWithExecutableName(command : CommandLine, executableName : String) : Boolean {
    if (command.executable.endsWith(executableName)) return true
    if (command.executable.endsWith("${executableName}.exe")) return true
    return false
}
