/*
 * Copyright (C) 2016 The Android Open Source Project
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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ProgressIndicator} that just delegates all its functionality to another.
 */
public class DelegatingProgressIndicator implements ProgressIndicator {

    protected Set<ProgressIndicator> mWrapped = ConcurrentHashMap.newKeySet();

    public DelegatingProgressIndicator(@NonNull ProgressIndicator wrapped) {
        mWrapped.add(wrapped);
    }

    public void addDelegate(@NonNull ProgressIndicator wrapped) {
        mWrapped.add(wrapped);
    }

    @Override
    public void setText(@Nullable String s) {
        mWrapped.forEach(progress -> progress.setText(s));
    }

    @Override
    public boolean isCanceled() {
        return mWrapped.stream().filter(ProgressIndicator::isCanceled).findFirst().isPresent();
    }

    @Override
    public void cancel() {
        mWrapped.forEach(ProgressIndicator::cancel);
    }

    @Override
    public void setCancellable(boolean cancellable) {
        mWrapped.forEach(progress -> progress.setCancellable(cancellable));
    }

    @Override
    public boolean isCancellable() {
        // If any are not cancellable we aren't.
        return !mWrapped.stream().filter(progress -> !progress.isCancellable()).findFirst()
                .isPresent();
    }

    @Override
    public void setIndeterminate(boolean indeterminate) {
        mWrapped.forEach(progress -> progress.setIndeterminate(indeterminate));
    }

    @Override
    public boolean isIndeterminate() {
        return mWrapped.stream().filter(ProgressIndicator::isIndeterminate).findFirst().isPresent();
    }

    @Override
    public void setFraction(double v) {
        mWrapped.forEach(progress -> progress.setFraction(v));
    }

    @Override
    public double getFraction() {
        return mWrapped.iterator().next().getFraction();
    }

    @Override
    public void setSecondaryText(@Nullable String s) {
        mWrapped.forEach(progress -> progress.setSecondaryText(s));
    }

    @Override
    public void logWarning(@NonNull String s) {
        mWrapped.forEach(progress -> progress.logWarning(s));
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        mWrapped.forEach(progress -> progress.logWarning(s, e));
    }

    @Override
    public void logError(@NonNull String s) {
        mWrapped.forEach(progress -> progress.logError(s));
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        mWrapped.forEach(progress -> progress.logError(s, e));
    }

    @Override
    public void logInfo(@NonNull String s) {
        mWrapped.forEach(progress -> progress.logInfo(s));
    }
}
