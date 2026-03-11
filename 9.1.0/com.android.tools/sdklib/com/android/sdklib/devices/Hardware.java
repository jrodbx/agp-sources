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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.Keyboard;
import com.android.resources.Navigation;
import com.android.resources.UiMode;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Hardware {
    private Screen mScreen;
    private Touchpad mTouchpad;
    private Hinge mHinge;
    private EnumSet<Network> mNetworking = EnumSet.noneOf(Network.class);
    private EnumSet<Sensor> mSensors = EnumSet.noneOf(Sensor.class);
    private boolean mMic;
    private List<Camera> mCameras = new ArrayList<Camera>(2);
    private Keyboard mKeyboard;
    private Navigation mNav;
    private Storage mRam;
    private ButtonType mButtons;
    private List<Storage> mInternalStorage = new ArrayList<Storage>();
    private List<Storage> mRemovableStorage = new ArrayList<Storage>();
    private String mCpu;
    private String mGpu;
    private List<Abi> mAbis = new ArrayList<>();
    private List<Abi> mTranslatedAbis = new ArrayList<>();
    private EnumSet<UiMode> mUiModes = EnumSet.noneOf(UiMode.class);
    private PowerType mPluggedIn;
    private File mSkinFile;
    // Set default value to be false, DeviceParser will change it to true
    // when devices has <removable-storage>
    private boolean mSdCard = false;

    public void setSkinFile(@Nullable File skinFile) {
      mSkinFile = skinFile;
    }

    @Nullable
    public File getSkinFile() {
        return mSkinFile;
    }

    @NonNull
    public Set<Network> getNetworking() {
        return mNetworking;
    }

    public void addNetwork(@NonNull Network n) {
        mNetworking.add(n);
    }

    public void addAllNetworks(@NonNull Collection<Network> ns) {
        mNetworking.addAll(ns);
    }

    @NonNull
    public Set<Sensor> getSensors() {
        return mSensors;
    }

    public void addSensor(@NonNull Sensor sensor) {
        mSensors.add(sensor);
    }

    public void addAllSensors(@NonNull Collection<Sensor> sensors) {
        mSensors.addAll(sensors);
    }

    public boolean hasMic() {
        return mMic;
    }

    public void setHasMic(boolean hasMic) {
        mMic = hasMic;
    }

    @NonNull
    public List<Camera> getCameras() {
        return mCameras;
    }

    public void addCamera(@NonNull Camera c) {
        mCameras.add(c);
    }

    public void addAllCameras(@NonNull Collection<Camera> cs) {
        mCameras.addAll(cs);
    }

    @NonNull
    public Camera getCamera(int i) {
        return mCameras.get(i);
    }

    @Nullable
    public Camera getCamera(@NonNull CameraLocation location) {
        for (Camera c : mCameras) {
            if (location == c.getLocation()) {
                return c;
            }
        }
        return null;
    }

    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    public void setKeyboard(@NonNull Keyboard keyboard) {
        mKeyboard = keyboard;
    }

    public Navigation getNav() {
        return mNav;
    }

    public void setNav(@NonNull Navigation n) {
        mNav = n;
    }

    public Storage getRam() {
        return mRam;
    }

    public void setRam(@NonNull Storage ram) {
        mRam = ram;
    }

    public ButtonType getButtonType() {
        return mButtons;
    }

    public void setButtonType(@NonNull ButtonType bt) {
        mButtons = bt;
    }

    @NonNull
    public List<Storage> getInternalStorage() {
        return mInternalStorage;
    }

    public void addInternalStorage(@NonNull Storage is) {
        mInternalStorage.add(is);
    }

    public void addAllInternalStorage(@NonNull Collection<Storage> is) {
        mInternalStorage.addAll(is);
    }

    public boolean hasSdCard() {
        return mSdCard;
    }

    public void setSdCard(boolean sdcard) {
        this.mSdCard = sdcard;
    }

    @NonNull
    public List<Storage> getRemovableStorage() {
        return mRemovableStorage;
    }

    public void addRemovableStorage(@NonNull Storage rs) {
        mRemovableStorage.add(rs);
    }

    public void addAllRemovableStorage(@NonNull Collection<Storage> rs) {
        mRemovableStorage.addAll(rs);
    }

    public String getCpu() {
        return mCpu;
    }

    public void setCpu(@NonNull String cpuName) {
        mCpu = cpuName;
    }

    public String getGpu() {
        return mGpu;
    }

    public void setGpu(@NonNull String gpuName) {
        mGpu = gpuName;
    }

    @NonNull
    public List<Abi> getSupportedAbis() {
        return ImmutableList.copyOf(mAbis);
    }

    public void addSupportedAbi(@NonNull Abi abi) {
        if (!mAbis.contains(abi)) {
            mAbis.add(abi);
        }
    }

    public void addAllSupportedAbis(@NonNull Collection<Abi> abis) {
        abis.forEach(this::addSupportedAbi);
    }

    @NonNull
    public List<Abi> getTranslatedAbis() {
        return ImmutableList.copyOf(mTranslatedAbis);
    }

    public void addTranslatedAbi(@NonNull Abi abi) {
        mTranslatedAbis.add(abi);
    }

    public void addAllTranslatedAbis(@NonNull Collection<Abi> abis) {
        mTranslatedAbis.addAll(abis);
    }

    @NonNull
    public Set<UiMode> getSupportedUiModes() {
        return mUiModes;
    }

    public void addSupportedUiMode(@NonNull UiMode uiMode) {
        mUiModes.add(uiMode);
    }

    public void addAllSupportedUiModes(@NonNull Collection<UiMode> uiModes) {
        mUiModes.addAll(uiModes);
    }

    public PowerType getChargeType() {
        return mPluggedIn;
    }

    public void setChargeType(@NonNull PowerType chargeType) {
        mPluggedIn = chargeType;
    }

    public Screen getScreen() {
        return mScreen;
    }

    public void setScreen(@NonNull Screen s) {
        mScreen = s;
    }

    public Touchpad getTouchpad() {
        return mTouchpad;
    }

    public void setTouchpad(Touchpad mTouchpad) {
        this.mTouchpad = mTouchpad;
    }

    public Hinge getHinge() {
        return mHinge;
    }

    public void setHinge(Hinge mHinge) {
        this.mHinge = mHinge;
    }

    /**
     * Returns a copy of the object that shares no state with it, but is initialized to equivalent
     * values.
     *
     * @return A copy of the object.
     */
    @NonNull
    public Hardware deepCopy() {
        Hardware hw = new Hardware();
        hw.mScreen = mScreen != null ? mScreen.deepCopy() : null;
        hw.mTouchpad = mTouchpad != null ? mTouchpad.deepCopy() : null;
        hw.mHinge = mHinge != null ? mHinge.deepCopy() : null;
        hw.mNetworking = mNetworking.clone();
        hw.mSensors = mSensors.clone();
        // Get the constant boolean value
        hw.mMic = mMic;
        hw.mCameras = new ArrayList<Camera>();
        for (Camera c : mCameras) {
            hw.mCameras.add(c.deepCopy());
        }
        hw.mKeyboard = mKeyboard;
        hw.mNav = mNav;
        hw.mRam = mRam;
        hw.mButtons = mButtons;
        hw.mInternalStorage = new ArrayList<>(mInternalStorage);
        hw.mRemovableStorage = new ArrayList<>(mRemovableStorage);
        hw.mCpu = mCpu;
        hw.mGpu = mGpu;
        hw.mAbis = new ArrayList<>(mAbis);
        hw.mTranslatedAbis = new ArrayList<>(mTranslatedAbis);
        hw.mUiModes = mUiModes.clone();
        hw.mPluggedIn = mPluggedIn;
        hw.mSkinFile = mSkinFile;
        hw.mSdCard = mSdCard;
        return hw;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Hardware)) {
            return false;
        }
        Hardware hw = (Hardware) o;
        return Objects.equal(mScreen, hw.getScreen())
                && Objects.equal(mTouchpad, hw.getTouchpad())
                && Objects.equal(mNetworking, hw.getNetworking())
                && Objects.equal(mSensors, hw.getSensors())
                && mMic == hw.hasMic()
                && mSdCard == hw.hasSdCard()
                && Objects.equal(mCameras, hw.getCameras())
                && Objects.equal(mKeyboard, hw.getKeyboard())
                && Objects.equal(mNav, hw.getNav())
                && Objects.equal(mRam, hw.getRam())
                && Objects.equal(mButtons, hw.getButtonType())
                && Objects.equal(mInternalStorage, hw.getInternalStorage())
                && Objects.equal(mRemovableStorage, hw.getRemovableStorage())
                && Objects.equal(mCpu, hw.getCpu())
                && Objects.equal(mGpu, hw.getGpu())
                && Objects.equal(mAbis, hw.getSupportedAbis())
                && Objects.equal(mTranslatedAbis, hw.getTranslatedAbis())
                && Objects.equal(mUiModes, hw.getSupportedUiModes())
                && Objects.equal(mPluggedIn, hw.getChargeType())
                && Objects.equal(mSkinFile, hw.getSkinFile());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(
                mScreen,
                mTouchpad,
                mNetworking,
                mSensors,
                mMic,
                mSdCard,
                mCameras,
                mKeyboard,
                mNav,
                mRam,
                mButtons,
                mInternalStorage,
                mRemovableStorage,
                mCpu,
                mGpu,
                mAbis,
                mTranslatedAbis,
                mUiModes,
                mPluggedIn,
                mSkinFile);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Hardware <mScreen=");
        sb.append(mScreen);
        sb.append("mTouchpad=");
        sb.append(mTouchpad);
        sb.append(", mNetworking=");
        sb.append(mNetworking);
        sb.append(", mSensors=");
        sb.append(mSensors);
        sb.append(", mMic=");
        sb.append(mMic);
        sb.append(", mSdCard=");
        sb.append(mSdCard);
        sb.append(", mCameras=");
        sb.append(mCameras);
        sb.append(", mKeyboard=");
        sb.append(mKeyboard);
        sb.append(", mNav=");
        sb.append(mNav);
        sb.append(", mRam=");
        sb.append(mRam);
        sb.append(", mButtons=");
        sb.append(mButtons);
        sb.append(", mInternalStorage=");
        sb.append(mInternalStorage);
        sb.append(", mRemovableStorage=");
        sb.append(mRemovableStorage);
        sb.append(", mCpu=");
        sb.append(mCpu);
        sb.append(", mGpu=");
        sb.append(mGpu);
        sb.append(", mAbis=");
        sb.append(mAbis);
        sb.append(", mTranslatedAbis=");
        sb.append(mTranslatedAbis);
        sb.append(", mUiModes=");
        sb.append(mUiModes);
        sb.append(", mPluggedIn=");
        sb.append(mPluggedIn);
        sb.append(", mSkinFile=");
        sb.append(mSkinFile);
        sb.append(">");
        return sb.toString();
    }
}
