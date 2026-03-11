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

package com.android.instantapp.sdk;

import com.android.annotations.NonNull;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import wireless.android.instantapps.sdk.ManifestOuterClass;

/** Generates an instance of {@link Metadata} based on the proto serialized binary in the sdk. */
class ManifestProtoParser {
    @NonNull private final File myManifestProtoFile;
    @NonNull private final File myApksDirectory;

    /**
     * The same instance of a {@link ManifestProtoParser} can be used to produce multiple instances
     * of {@link Metadata}.
     *
     * @param instantAppSdk the folder containing the SDK.
     */
    ManifestProtoParser(@NonNull File instantAppSdk) throws InstantAppSdkException {
        myManifestProtoFile = new File(instantAppSdk, "manifest.pb");
        if (!myManifestProtoFile.exists() || !myManifestProtoFile.isFile()) {
            throw new InstantAppSdkException(
                    "Manifest file " + myManifestProtoFile.getAbsolutePath() + " does not exist.");
        }

        myApksDirectory = new File(new File(instantAppSdk, "tools"), "apks");
        if (!myApksDirectory.exists() || !myApksDirectory.isDirectory()) {
            throw new InstantAppSdkException(
                    "Apks folder " + myApksDirectory.getAbsolutePath() + " does not exist.");
        }
    }

    /**
     * Parses the manifest file and produces a new instance of {@link Metadata}.
     *
     * @return a new instance of {@link Metadata} representing the SDK.
     */
    @NonNull
    public Metadata getMetadata() throws InstantAppSdkException {
        ManifestOuterClass.Manifest manifest;
        try {
            FileInputStream inputStream = new FileInputStream(myManifestProtoFile);
            manifest = ManifestOuterClass.Manifest.parseFrom(inputStream);
        } catch (IOException e) {
            throw new InstantAppSdkException("Manifest file is corrupted.", e);
        }
        return convertManifest(manifest);
    }

    @NonNull
    private Metadata convertManifest(@NonNull ManifestOuterClass.Manifest manifest) {
        return new Metadata(
                manifest.getVersionCode(),
                manifest.getVersionName(),
                convertApks(manifest.getApksList()),
                convertDevices(manifest.getEnabledDevicesList()),
                convertGServicesOverrides(manifest.getGservicesOverridesList()),
                convertLibraryCompatibility(manifest.getLibraryCompatibility()));
    }

    @NonNull
    private static List<Metadata.GServicesOverride> convertGServicesOverrides(
            @NonNull List<ManifestOuterClass.GservicesOverride> gservicesOverrides) {
        return Lists.transform(gservicesOverrides, ManifestProtoParser::convertGServicesOverride);
    }

    @NonNull
    private static Metadata.GServicesOverride convertGServicesOverride(
            @NonNull ManifestOuterClass.GservicesOverride gservicesOverride) {
        return new Metadata.GServicesOverride(
                convertDevices(gservicesOverride.getDeviceList()),
                gservicesOverride.getKey(),
                gservicesOverride.getValue());
    }

    @NonNull
    private static Set<Metadata.Device> convertDevices(
            @NonNull List<ManifestOuterClass.Device> devices) {
        return devices.stream().map(ManifestProtoParser::convertDevice).collect(Collectors.toSet());
    }

    @NonNull
    private static Metadata.Device convertDevice(@NonNull ManifestOuterClass.Device device) {
        return new Metadata.Device(
                device.getManufacturer(),
                device.getAndroidDevice(),
                Sets.newHashSet(device.getSdkIntList()),
                device.getProduct(),
                device.getHardware());
    }

    @NonNull
    private Map<Metadata.Arch, List<Metadata.ApkInfo>> convertApks(
            @NonNull List<ManifestOuterClass.ApkVersionInfo> apkVersionInfoList) {
        Map<Metadata.Arch, List<Metadata.ApkInfo>> apks = new EnumMap<>(Metadata.Arch.class);
        for (ManifestOuterClass.ApkVersionInfo apkVersionInfo : apkVersionInfoList) {
            Metadata.Arch arch = convertArch(apkVersionInfo.getArch());
            if (!apks.containsKey(arch)) {
                apks.put(arch, new LinkedList<>());
            }
            apks.get(arch).add(convertApkVersionInfo(apkVersionInfo));
        }
        return apks;
    }

    @NonNull
    private Metadata.ApkInfo convertApkVersionInfo(
            @NonNull ManifestOuterClass.ApkVersionInfo apkVersionInfo) {
        return new Metadata.ApkInfo(
                apkVersionInfo.getPackageName(),
                new File(myApksDirectory, apkVersionInfo.getPath()),
                convertArch(apkVersionInfo.getArch()),
                Sets.newLinkedHashSet(apkVersionInfo.getSdkIntList()),
                apkVersionInfo.getVersionCode());
    }

    @NonNull
    private static Metadata.Arch convertArch(@NonNull ManifestOuterClass.Arch arch) {
        switch (arch) {
            case ALL:
                return Metadata.Arch.DEFAULT;
            case UNRECOGNIZED:
                return Metadata.Arch.UNKNOWN;
            default:
                return Metadata.Arch.valueOf(arch.name());
        }
    }

    @NonNull
    private static Metadata.LibraryCompatibility convertLibraryCompatibility(
            @NonNull ManifestOuterClass.LibraryCompatibility libraryCompatibility) {
        return new Metadata.LibraryCompatibility(libraryCompatibility.getAiaCompatApiMinVersion());
    }
}
