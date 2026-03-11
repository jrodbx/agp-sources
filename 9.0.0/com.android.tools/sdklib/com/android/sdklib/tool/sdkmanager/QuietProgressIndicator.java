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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.api.ProgressIndicatorAdapter;

class QuietProgressIndicator extends ProgressIndicatorAdapter {
    private final ProgressIndicator myProgress;

    public QuietProgressIndicator(ProgressIndicator progress) {
        myProgress = progress;
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        myProgress.logWarning(s, e);
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        myProgress.logError(s, e);
    }
}
