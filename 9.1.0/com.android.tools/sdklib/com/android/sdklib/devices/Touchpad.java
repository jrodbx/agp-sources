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
package com.android.sdklib.devices;

import java.util.Objects;

public class Touchpad {
    private int mWidth;
    private int mHeight;

    public int getWidth() {
        return mWidth;
    }

    public void setWidth(int width) {
        this.mWidth = width;
    }

    public int getHeight() {
        return mHeight;
    }

    public void setHeight(int height) {
        this.mHeight = height;
    }

    /**
     * Returns a copy of the object that shares no state with it, but is initialized to equivalent
     * values.
     *
     * @return A copy of the object.
     */
    public Touchpad deepCopy() {
        Touchpad t = new Touchpad();
        t.mWidth = mWidth;
        t.mHeight = mHeight;
        return t;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Touchpad)) {
            return false;
        }
        Touchpad t = (Touchpad) o;
        return t.mWidth == mWidth && t.mHeight == mHeight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWidth, mHeight);
    }

    @Override
    public String toString() {
        return "Touchpad [mWidth=" + mWidth + ", mHeight=" + mHeight + "]";
    }
}
