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
import java.io.PrintStream;

/**
 * A simple {@link ProgressIndicator} that prints log messages to {@code stdout} and {@code stderr}.
 */
public class ConsoleProgressIndicator extends ProgressIndicatorAdapter {

    private static final int PROGRESS_WIDTH = 40;
    private static final int MAX_WIDTH = 80;

    private String mText = "";
    private String mSecondaryText = "";
    private double mProgress = 0;

    private PrintStream mOut;
    private PrintStream mErr;

    private String mLast = null;

    private static final String SPACES =
            "                                                                                ";

    public ConsoleProgressIndicator() {
        this(System.out, System.err);
    }

    public ConsoleProgressIndicator(@NonNull PrintStream out, @NonNull PrintStream err) {
        mOut = out;
        mErr = err;
    }

    public void setOut(@NonNull PrintStream out) {
        mOut = out;
    }

    public void setErr(@NonNull PrintStream err) {
        mErr = err;
    }

    @Override
    public double getFraction() {
        return mProgress;
    }

    @Override
    public void setFraction(double progress) {
        mProgress = progress;
        printStatusLine(true);
    }

    private void printStatusLine(boolean forceShowProgress) {
        StringBuilder line = new StringBuilder();
        if (forceShowProgress || getFraction() > 0) {
            line.append("[");
            int i = 1;
            for (; i < PROGRESS_WIDTH * mProgress; i++) {
                line.append("=");
            }
            for (; i < PROGRESS_WIDTH; i++) {
                line.append(" ");
            }
            line.append("] ");

            line.append(String.format("%.0f%%", 100 * mProgress));
            line.append(" ");
        }
        line.append(mText);
        line.append(" ");
        line.append(mSecondaryText);
        if (line.length() > MAX_WIDTH) {
            line.delete(MAX_WIDTH, line.length());
        } else {
            line.append(SPACES.substring(0, MAX_WIDTH - line.length()));
        }

        line.append("\r");
        String result = line.toString();
        if (!result.equals(mLast)) {
            mOut.print(result);
            mOut.flush();
            mLast = result;
        }
    }

    private void logMessage(@NonNull String s, @Nullable Throwable e, @NonNull PrintStream stream) {
        if (mProgress > 0) {
            mOut.print(SPACES);
            mOut.print("\r");
            mLast = null;
        }
        stream.println(s);
        if (e != null) {
            e.printStackTrace();
        }
        if (mProgress > 0) {
            printStatusLine(false);
        }
    }

    @Override
    public void logWarning(@NonNull String s, @Nullable Throwable e) {
        logMessage("Warning: " + s, e, mErr);
    }

    @Override
    public void logError(@NonNull String s, @Nullable Throwable e) {
        logMessage("Error: " + s, e, mErr);
    }

    @Override
    public void logInfo(@NonNull String s) {
        logMessage("Info: " + s, null, mOut);
    }

    @Override
    public void setText(@Nullable String text) {
        mText = text;
        printStatusLine(false);
    }

    @Override
    public void setSecondaryText(@Nullable String text) {
        mSecondaryText = text;
        printStatusLine(false);
    }
}
