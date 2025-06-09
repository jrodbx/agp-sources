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

package com.android.repository.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * A trivial implementation of ProgressIndicator that does nothing.
 */
public abstract class ProgressIndicatorAdapter implements ProgressIndicator {

    @Override
    public void setText(@Nullable String s) {}

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public void cancel() {}

    @Override
    public void setCancellable(boolean cancellable) {}

    @Override
    public boolean isCancellable() {
        return false;
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {}

    @Override
    public boolean isIndeterminate() {
        return false;
    }

    @Override
    public void setFraction(double v) {}

    @Override
    public double getFraction() {
        return 0;
    }

    @Override
    public void setSecondaryText(@Nullable String s) {}

    @Override
    public void logWarning(@NonNull String s) {
        logWarning(s, null);
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {}

    @Override
    public void logError(@NonNull String s) {
        logError(s, null);
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {}

    @Override
    public void logInfo(@NonNull String s) {}
}
