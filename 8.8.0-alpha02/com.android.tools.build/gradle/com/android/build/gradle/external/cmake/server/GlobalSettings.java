/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.build.gradle.external.cmake.server;

/**
 * Cmake's current state/global-settings. More Info:
 * https://cmake.org/cmake/help/v3.8/manual/cmake-server.7.html#type-globalsettings
 */
public class GlobalSettings {
    public final String type;
    public final String cookie;
    public final String inReplyTo;
    public final String buildDirectory;
    public final Boolean checkSystemVars;
    public final Boolean debugOutput;
    public final String extraGenerator;
    public final String generator;
    public final String sourceDirectory;
    public final Boolean trace;
    public final Boolean traceExpand;
    public final Boolean warnUninitialized;
    public final Boolean warnUnused;
    public final Boolean warnUnusedCli;
    public final Capabilities capabilities;

    private GlobalSettings() {
        this.type = null;
        this.cookie = null;
        this.inReplyTo = null;
        this.buildDirectory = null;
        this.checkSystemVars = null;
        this.debugOutput = null;
        this.extraGenerator = null;
        this.sourceDirectory = null;
        this.generator = null;
        this.trace = null;
        this.traceExpand = null;
        this.warnUninitialized = null;
        this.warnUnused = null;
        this.warnUnusedCli = null;
        this.capabilities = null;
    }
}
