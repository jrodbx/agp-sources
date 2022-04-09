/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenRound;
import com.android.resources.ScreenSize;
import com.android.resources.TouchScreen;

public class Screen {
    private ScreenSize mScreenSize;
    private double mDiagonalLength;
    private Density mPixelDensity;
    private ScreenRatio mScreenRatio;
    private int mXDimension;
    private int mYDimension;
    private double mXdpi;
    private double mYdpi;
    private Multitouch mMultitouch;
    private TouchScreen mMechanism;
    private ScreenType mScreenType;
    private int mChin;
    @Nullable
    private ScreenRound mScreenRound;
    private int mFoldedXOffset;
    private int mFoldedYOffset;
    private int mFoldedWidth;
    private int mFoldedHeight;
    private int mFoldedXOffset2;
    private int mFoldedYOffset2;
    private int mFoldedWidth2;
    private int mFoldedHeight2;
    private int mFoldedXOffset3;
    private int mFoldedYOffset3;
    private int mFoldedWidth3;
    private int mFoldedHeight3;

    public ScreenSize getSize() {
        return mScreenSize;
    }

    public void setSize(ScreenSize s) {
        mScreenSize = s;
    }

    public double getDiagonalLength() {
        return mDiagonalLength;
    }

    public void setDiagonalLength(double diagonalLength) {
        mDiagonalLength = diagonalLength;
    }

    public Density getPixelDensity() {
        return mPixelDensity;
    }

    public void setPixelDensity(Density pDensity) {
        mPixelDensity = pDensity;
    }

    public ScreenRatio getRatio() {
        return mScreenRatio;
    }

    public void setRatio(ScreenRatio ratio) {
        mScreenRatio = ratio;
    }

    public int getXDimension() {
        return mXDimension;
    }

    public void setXDimension(int xDimension) {
        mXDimension = xDimension;
    }

    public int getYDimension() {
        return mYDimension;
    }

    public void setYDimension(int yDimension) {
        mYDimension = yDimension;
    }

    public double getXdpi() {
        return mXdpi;
    }

    public void setXdpi(double xdpi) {
        mXdpi = xdpi;
    }

    public double getYdpi() {
        return mYdpi;
    }

    public void setYdpi(double ydpi) {
        mYdpi = ydpi;
    }

    public Multitouch getMultitouch() {
        return mMultitouch;
    }

    public void setMultitouch(Multitouch m) {
        mMultitouch = m;
    }

    public TouchScreen getMechanism() {
        return mMechanism;
    }

    public void setMechanism(TouchScreen mechanism) {
        mMechanism = mechanism;
    }

    public ScreenType getScreenType() {
        return mScreenType;
    }

    public void setScreenType(ScreenType screenType) {
        mScreenType = screenType;
    }

    @Nullable
    public ScreenRound getScreenRound() {
        return mScreenRound;
    }

    public void setScreenRound(@Nullable ScreenRound screenRound) {
        mScreenRound = screenRound;
    }

    public boolean isFoldable() {
        return mFoldedHeight > 0 && mFoldedWidth > 0;
    }
    public void setFoldedXOffset(int xOffset) {
        mFoldedXOffset = xOffset;
    }

    public int getFoldedXOffset() {
        return mFoldedXOffset;
    }

    public void setFoldedYOffset(int yOffset) {
        mFoldedYOffset = yOffset;
    }
    public int getFoldedYOffset() {
        return mFoldedYOffset;
    }
    public void setFoldedWidth(int width) {
        mFoldedWidth = width;
    }
    public int getFoldedWidth() {
        return mFoldedWidth;
    }
    public void setFoldedHeight(int height) {
        mFoldedHeight = height;
    }
    public int getFoldedHeight() {
        return mFoldedHeight;
    }

    public void setFoldedXOffset2(int xOffset) {
        mFoldedXOffset2 = xOffset;
    }

    public int getFoldedXOffset2() {
        return mFoldedXOffset2;
    }

    public void setFoldedYOffset2(int yOffset) {
        mFoldedYOffset2 = yOffset;
    }

    public int getFoldedYOffset2() {
        return mFoldedYOffset2;
    }

    public void setFoldedWidth2(int width) {
        mFoldedWidth2 = width;
    }

    public int getFoldedWidth2() {
        return mFoldedWidth2;
    }

    public void setFoldedHeight2(int height) {
        mFoldedHeight2 = height;
    }

    public int getFoldedHeight2() {
        return mFoldedHeight2;
    }

    public void setFoldedXOffset3(int xOffset) {
        mFoldedXOffset3 = xOffset;
    }

    public int getFoldedXOffset3() {
        return mFoldedXOffset3;
    }

    public void setFoldedYOffset3(int yOffset) {
        mFoldedYOffset3 = yOffset;
    }

    public int getFoldedYOffset3() {
        return mFoldedYOffset3;
    }

    public void setFoldedWidth3(int width) {
        mFoldedWidth3 = width;
    }

    public int getFoldedWidth3() {
        return mFoldedWidth3;
    }

    public void setFoldedHeight3(int height) {
        mFoldedHeight3 = height;
    }

    public int getFoldedHeight3() {
        return mFoldedHeight3;
    }

    /**
     * Get the "chin" height in pixels. This is for round screens with a flat section at the
     * bottom. The "chin" height is the largest perpendicular distance from the flat section to
     * the original circle.
     * @return The offset in pixels.
     */
    public int getChin() {
        return mChin;
    }

    /**
     * Sets the "chin" height in pixels.
     * @see #getChin()
     */
    public void setChin(int chin) {
        mChin = chin;
    }

    /**
     * Returns a copy of the object that shares no state with it,
     * but is initialized to equivalent values.
     *
     * @return A copy of the object.
     */
    public Screen deepCopy() {
        Screen s = new Screen();
        s.mScreenSize = mScreenSize;
        s.mDiagonalLength = mDiagonalLength;
        s.mPixelDensity = mPixelDensity;
        s.mScreenRatio = mScreenRatio;
        s.mXDimension = mXDimension;
        s.mYDimension = mYDimension;
        s.mXdpi = mXdpi;
        s.mYdpi = mYdpi;
        s.mMultitouch = mMultitouch;
        s.mMechanism = mMechanism;
        s.mScreenType = mScreenType;
        s.mScreenRound = mScreenRound;
        s.mChin = mChin;
        s.mFoldedXOffset = mFoldedXOffset;
        s.mFoldedYOffset = mFoldedYOffset;
        s.mFoldedWidth = mFoldedWidth;
        s.mFoldedHeight = mFoldedHeight;
        s.mFoldedXOffset2 = mFoldedXOffset2;
        s.mFoldedYOffset2 = mFoldedYOffset2;
        s.mFoldedWidth2 = mFoldedWidth2;
        s.mFoldedHeight2 = mFoldedHeight2;
        s.mFoldedXOffset3 = mFoldedXOffset3;
        s.mFoldedYOffset3 = mFoldedYOffset3;
        s.mFoldedWidth3 = mFoldedWidth3;
        s.mFoldedHeight3 = mFoldedHeight3;
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Screen)) {
            return false;
        }
        Screen s = (Screen) o;
        return s.mScreenSize == mScreenSize
                && s.mDiagonalLength == mDiagonalLength
                && s.mPixelDensity == mPixelDensity
                && s.mScreenRatio == mScreenRatio
                && s.mXDimension == mXDimension
                && s.mYDimension == mYDimension
                && s.mXdpi == mXdpi
                && s.mYdpi == mYdpi
                && s.mMultitouch == mMultitouch
                && s.mMechanism == mMechanism
                && s.mScreenType == mScreenType
                && s.mScreenRound == mScreenRound
                && s.mChin == mChin
                && s.mFoldedXOffset == mFoldedXOffset
                && s.mFoldedYOffset == mFoldedYOffset
                && s.mFoldedWidth == mFoldedWidth
                && s.mFoldedHeight == mFoldedHeight
                && s.mFoldedXOffset2 == mFoldedXOffset2
                && s.mFoldedYOffset2 == mFoldedYOffset2
                && s.mFoldedWidth2 == mFoldedWidth2
                && s.mFoldedHeight2 == mFoldedHeight2
                && s.mFoldedXOffset3 == mFoldedXOffset3
                && s.mFoldedYOffset3 == mFoldedYOffset3
                && s.mFoldedWidth3 == mFoldedWidth3
                && s.mFoldedHeight3 == mFoldedHeight3;
    }

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 31 * hash + mScreenSize.ordinal();
        long f = Double.doubleToLongBits(mDiagonalLength);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        hash = 31 * hash + mPixelDensity.ordinal();
        hash = 31 * hash + mScreenRatio.ordinal();
        hash = 31 * hash + mXDimension;
        hash = 31 * hash + mYDimension;
        f = Double.doubleToLongBits(mXdpi);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        f = Double.doubleToLongBits(mYdpi);
        hash = 31 * hash + (int) (f ^ (f >>> 32));
        hash = 31 * hash + mMultitouch.ordinal();
        hash = 31 * hash + mMechanism.ordinal();
        hash = 31 * hash + mScreenType.ordinal();
        hash = 31 * hash + mChin;
        if (mScreenRound != null) {
            hash = 31 * hash + mScreenRound.ordinal();
        }
        hash = 31 * hash + mFoldedXOffset;
        hash = 31 * hash + mFoldedYOffset;
        hash = 31 * hash + mFoldedWidth;
        hash = 31 * hash + mFoldedHeight;
        hash = 31 * hash + mFoldedXOffset2;
        hash = 31 * hash + mFoldedYOffset2;
        hash = 31 * hash + mFoldedWidth2;
        hash = 31 * hash + mFoldedHeight2;
        hash = 31 * hash + mFoldedXOffset3;
        hash = 31 * hash + mFoldedYOffset3;
        hash = 31 * hash + mFoldedWidth3;
        hash = 31 * hash + mFoldedHeight3;
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Screen [mScreenSize=");
        sb.append(mScreenSize);
        sb.append(", mDiagonalLength=");
        sb.append(mDiagonalLength);
        sb.append(", mPixelDensity=");
        sb.append(mPixelDensity);
        sb.append(", mScreenRatio=");
        sb.append(mScreenRatio);
        sb.append(", mXDimension=");
        sb.append(mXDimension);
        sb.append(", mYDimension=");
        sb.append(mYDimension);
        sb.append(", mXdpi=");
        sb.append(mXdpi);
        sb.append(", mYdpi=");
        sb.append(mYdpi);
        sb.append(", mMultitouch=");
        sb.append(mMultitouch);
        sb.append(", mMechanism=");
        sb.append(mMechanism);
        sb.append(", mScreenType=");
        sb.append(mScreenType);
        sb.append(", mScreenRound=");
        sb.append(mScreenRound);
        sb.append(", mChin=");
        sb.append(mChin);
        sb.append(", mFoldableXOffset=");
        sb.append(mFoldedXOffset);
        sb.append(", mFoldableYOffset=");
        sb.append(mFoldedYOffset);
        sb.append(", mFoldableWidth=");
        sb.append(mFoldedWidth);
        sb.append(", mFoldableHeight=");
        sb.append(mFoldedHeight);
        sb.append(", mFoldableXOffset2=");
        sb.append(mFoldedXOffset2);
        sb.append(", mFoldableYOffset2=");
        sb.append(mFoldedYOffset2);
        sb.append(", mFoldableWidth2=");
        sb.append(mFoldedWidth2);
        sb.append(", mFoldableHeight=2");
        sb.append(mFoldedHeight2);
        sb.append(", mFoldableXOffset3=");
        sb.append(mFoldedXOffset3);
        sb.append(", mFoldableYOffset3=");
        sb.append(mFoldedYOffset3);
        sb.append(", mFoldableWidth3=");
        sb.append(mFoldedWidth3);
        sb.append(", mFoldableHeight3=");
        sb.append(mFoldedHeight3);
        sb.append("]");
        return sb.toString();
    }
}
