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
package com.android.sdklib.tool.sdkmanager;

import com.android.Version;
import com.android.annotations.NonNull;
import com.android.repository.api.ProgressIndicator;
import java.util.Map;
import java.util.function.Function;

class ShowVersionAction extends SdkAction {
    private static final String ACTION_ARG = "--version";

    private ShowVersionAction(@NonNull SdkManagerCliSettings settings) {
        super(settings);
    }

    public static void register(
            @NonNull Map<String, Function<SdkManagerCliSettings, SdkAction>> argToFactory) {
        argToFactory.put(ACTION_ARG, ShowVersionAction::new);
    }

    @Override
    public void execute(@NonNull ProgressIndicator progress) {
        String version = Version.TOOLS_VERSION;
        getOutputStream().println(version == null ? "Unknown version" : version);
    }
}
