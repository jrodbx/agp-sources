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

import java.util.OptionalInt;

final class Hinge {
    private int mCount;
    private int mType;
    private int mSubtype;
    private String mRanges;
    private int mDefaults;
    private String mAreas;
    private int mFoldAtPosture = -1;
    private String mPostureList;
    private String mHingeAnglePostureDefinitions;

    int getCount() {
        return mCount;
    }

    void setCount(int count) {
        this.mCount = count;
    }

    int getType() {
        return mType;
    }

    void setType(int type) {
        this.mType = type;
    }

    int getSubtype() {
        return mSubtype;
    }

    void setSubtype(int subtype) {
        this.mSubtype = subtype;
    }

    String getRanges() {
        return mRanges;
    }

    void setRanges(String ranges) {
        this.mRanges = ranges;
    }

    int getDefaults() {
        return mDefaults;
    }

    void setDefaults(int defaults) {
        this.mDefaults = defaults;
    }

    String getAreas() {
        return mAreas;
    }

    void setAreas(String areas) {
        this.mAreas = areas;
    }

    OptionalInt getFoldAtPosture() {
        return mFoldAtPosture == -1 ? OptionalInt.empty() : OptionalInt.of(mFoldAtPosture);
    }

    void setFoldAtPosture(int foldAtPosture) {
        this.mFoldAtPosture = foldAtPosture;
    }

    String getPostureList() {
        return mPostureList;
    }

    void setPostureList(String postureList) {
        this.mPostureList = postureList;
    }

    String getHingeAnglePostureDefinitions() {
        return mHingeAnglePostureDefinitions;
    }

    void setHingeAnglePostureDefinitions(String hingeAnglePostureDefinitions) {
        this.mHingeAnglePostureDefinitions = hingeAnglePostureDefinitions;
    }

    /**
     * Returns a copy of the object that shares no state with it, but is initialized to equivalent
     * values.
     *
     * @return A copy of the object.
     */
    Hinge deepCopy() {
        Hinge h = new Hinge();
        h.mCount = mCount;
        h.mType = mType;
        h.mSubtype = mSubtype;
        h.mRanges = mRanges;
        h.mDefaults = mDefaults;
        h.mAreas = mAreas;
        h.mFoldAtPosture = mFoldAtPosture;
        h.mPostureList = mPostureList;
        h.mHingeAnglePostureDefinitions = mHingeAnglePostureDefinitions;
        return h;
    }
}
