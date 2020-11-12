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
 * A progress indicator and logger. Progress is from 0-1, or indeterminate.
 */
public interface ProgressIndicator {

    /**
     * Sets the main text shown in the progress indicator.
     */
    void setText(@Nullable String s);

    /**
     * @return True if the user has canceled this operation.
     */
    boolean isCanceled();

    /**
     * Try to cancel this operation.
     */
    void cancel();

    /**
     * Sets whether the user should be able to cancel this operation.
     */
    void setCancellable(boolean cancellable);

    /**
     * @return true if the user should be able to cancel this operation.
     */
    boolean isCancellable();

    /**
     * Sets whether this progress indicator should show indeterminate progress.
     */
    void setIndeterminate(boolean indeterminate);

    /**
     * @return true if this progress indicator is set to show indeterminate progress.
     */
    boolean isIndeterminate();

    /**
     * Sets how much progress should be shown on the progress bar, between 0 and 1.
     */
    void setFraction(double v);

    /**
     * @return The current amount of progress shown on the progress bar, between 0 and 1.
     */
    double getFraction();

    /**
     * Sets the secondary text on the progress indicator.
     */
    void setSecondaryText(@Nullable String s);

    /**
     * Logs a warning.
     */
    void logWarning(@NonNull String s);
    /**
     * Logs a warning, including a stacktrace.
     */
    void logWarning(@NonNull String s, @Nullable Throwable e);

    /**
     * Logs an error.
     */
    void logError(@NonNull String s);

    /**
     * Logs an error, including a stacktrace.
     */
    void logError(@NonNull String s, @Nullable Throwable e);

    /**
     * Logs an info message.
     */
    void logInfo(@NonNull String s);

    default void logVerbose(@NonNull String s) {
        logInfo(s);
    }

    /**
     * Creates a new progress indicator that just delegates to {@code this}, except that its
     * {@code [0, 1]} progress interval maps to {@code this}'s {@code [getFraction()-max]}.
     * So for example if {@code this} is currently at 10% and a sub progress is created with
     * {@code max = 0.5}, when the sub progress is at 50% the parent progress will be at 30%, and
     * when the sub progress is at 100% the parent will be at 50%.
     */
    default ProgressIndicator createSubProgress(double max) {
        double start = getFraction();
        // Unfortunately some dummy indicators always report their fraction as 1. In that case just
        // return the indicator itself.
        if (start == 1) {
            return this;
        }

        double subRange = max - start;
        // Check that we're at least close to being a valid value. If we're equal to or less than 0
        // we'll treat it as 0 (that is, sets do nothing and gets just return the 0).
        if (subRange < -0.0001 || subRange > 1.0001) {
            logError("Progress subrange out of bounds: " + subRange);
        }

        return new DelegatingProgressIndicator(ProgressIndicator.this) {
            @Override
            public void setFraction(double subFraction) {
                if (subRange > 0) {
                    subFraction = Math.min(1, Math.max(0, subFraction));
                    super.setFraction(start + subFraction * subRange);
                }
            }

            @Override
            public double getFraction() {
                return Math.min(
                        1,
                        Math.max(0, subRange > 0 ? (super.getFraction() - start) / subRange : 0));
            }
        };
    }
}
