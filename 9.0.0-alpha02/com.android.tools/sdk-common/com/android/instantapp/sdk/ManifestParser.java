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
import com.google.common.annotations.VisibleForTesting;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Receives a manifest.xml file representing the metadata of the Instant App SDK and parses it,
 * producing an instance of {@link Metadata}.
 */
class ManifestParser {
    @NonNull private final File myManifestFile;
    @NonNull private final File myApksDirectory;

    /**
     * The same instance of a {@link ManifestParser} can be used to produce multiple instances of
     * {@link Metadata}.
     *
     * @param instantAppSdk the folder containing the SDK.
     */
    ManifestParser(@NonNull File instantAppSdk) throws InstantAppSdkException {
        myManifestFile = new File(instantAppSdk, "manifest.xml");
        if (!myManifestFile.exists() || !myManifestFile.isFile()) {
            throw new InstantAppSdkException(
                    "Manifest file " + myManifestFile.getAbsolutePath() + " does not exist.");
        }

        myApksDirectory = new File(new File(instantAppSdk, "tools"), "apks");
        if (!myApksDirectory.exists() || !myApksDirectory.isDirectory()) {
            throw new InstantAppSdkException(
                    "Apks folder " + myApksDirectory.getAbsolutePath() + " does not exist.");
        }
    }

    @NonNull
    @VisibleForTesting
    static List<Metadata.GServicesOverride> parseGServicesOverrides(
            Element gServicesOverridesNode) {
        List<Metadata.GServicesOverride> gServicesOverrides = new LinkedList<>();
        NodeList gServicesOverrideNodes =
                gServicesOverridesNode.getElementsByTagName("gservicesOverride");
        for (int i = 0; i < gServicesOverrideNodes.getLength(); i++) {
            Metadata.GServicesOverride gServicesOverride =
                    parseGServicesOverride((Element) gServicesOverrideNodes.item(i));
            gServicesOverrides.add(gServicesOverride);
        }
        return gServicesOverrides;
    }

    @NonNull
    private static Set<Metadata.Device> parseEnabledDevices(Element enabledDevicesNode) {
        Set<Metadata.Device> enabledDevices = new HashSet<>();
        NodeList enabledDeviceNodes = enabledDevicesNode.getElementsByTagName("device");
        for (int i = 0; i < enabledDeviceNodes.getLength(); i++) {
            Metadata.Device device = parseDevice((Element) enabledDeviceNodes.item(i));
            enabledDevices.add(device);
        }
        return enabledDevices;
    }

    @NonNull
    @VisibleForTesting
    static Metadata.Device parseDevice(@NonNull Element deviceNode) {
        String manufacturer =
                deviceNode.getElementsByTagName("manufacturer").getLength() > 0
                        ? deviceNode.getElementsByTagName("manufacturer").item(0).getTextContent()
                        : null;
        String androidDevice =
                deviceNode.getElementsByTagName("androidDevice").getLength() > 0
                        ? deviceNode.getElementsByTagName("androidDevice").item(0).getTextContent()
                        : null;
        String product =
                deviceNode.getElementsByTagName("product").getLength() > 0
                        ? deviceNode.getElementsByTagName("product").item(0).getTextContent()
                        : null;
        String hardware =
                deviceNode.getElementsByTagName("hardware").getLength() > 0
                        ? deviceNode.getElementsByTagName("hardware").item(0).getTextContent()
                        : null;
        Set<Integer> apiLevelsSet = new HashSet<>();
        NodeList sdkIntNodes = deviceNode.getElementsByTagName("sdkInt");
        for (int i = 0; i < sdkIntNodes.getLength(); i++) {
            String sdkIntString = sdkIntNodes.item(i).getTextContent();
            int sdkInt = Integer.parseInt(sdkIntString);
            apiLevelsSet.add(sdkInt);
        }
        return new Metadata.Device(manufacturer, androidDevice, apiLevelsSet, product, hardware);
    }

    @NonNull
    @VisibleForTesting
    static Metadata.GServicesOverride parseGServicesOverride(
            @NonNull Element gServicesOverrideNode) {
        String key = gServicesOverrideNode.getElementsByTagName("key").item(0).getTextContent();
        String value = gServicesOverrideNode.getElementsByTagName("value").item(0).getTextContent();
        Set<Metadata.Device> devices = new HashSet<>();
        NodeList deviceNodes = gServicesOverrideNode.getElementsByTagName("device");
        for (int i = 0; i < deviceNodes.getLength(); i++) {
            Metadata.Device device = parseDevice((Element) deviceNodes.item(i));
            devices.add(device);
        }
        return new Metadata.GServicesOverride(devices, key, value);
    }

    /**
     * Parses the manifest file and produces a new instance of {@link Metadata}.
     *
     * @return a new instance of {@link Metadata} representing the SDK.
     */
    @NonNull
    public Metadata getMetadata() throws InstantAppSdkException {
        try {
            Element manifest =
                    (Element)
                            DocumentBuilderFactory.newInstance()
                                    .newDocumentBuilder()
                                    .parse(myManifestFile)
                                    .getElementsByTagName("manifest")
                                    .item(0);
            return parseManifest(manifest);
        } catch (SAXException | IOException | ParserConfigurationException e) {
            throw new InstantAppSdkException("Manifest file is corrupted.", e);
        } catch (RuntimeException e) {
            throw new InstantAppSdkException("Manifest file is not in the expected format.", e);
        }
    }

    @NonNull
    private Metadata parseManifest(@NonNull Element manifestNode) {
        String versionCodeString =
                manifestNode.getElementsByTagName("versionCode").item(0).getTextContent();
        long versionCode = Long.parseLong(versionCodeString);
        String versionName =
                manifestNode.getElementsByTagName("versionName").item(0).getTextContent();
        Map<Metadata.Arch, List<Metadata.ApkInfo>> apks =
                parseApks((Element) manifestNode.getElementsByTagName("apks").item(0));
        Set<Metadata.Device> enabledDevices =
                parseEnabledDevices(
                        (Element) manifestNode.getElementsByTagName("enabledDevices").item(0));
        List<Metadata.GServicesOverride> gServicesOverrides =
                parseGServicesOverrides(
                        (Element) manifestNode.getElementsByTagName("gservicesOverrides").item(0));
        Metadata.LibraryCompatibility libraryCompatibility =
                parseLibraryCompatibility(
                        (Element)
                                manifestNode.getElementsByTagName("libraryCompatibility").item(0));
        return new Metadata(
                versionCode,
                versionName,
                apks,
                enabledDevices,
                gServicesOverrides,
                libraryCompatibility);
    }

    @NonNull
    private Map<Metadata.Arch, List<Metadata.ApkInfo>> parseApks(Element apksNode) {
        Map<Metadata.Arch, List<Metadata.ApkInfo>> apks = new EnumMap<>(Metadata.Arch.class);
        NodeList apkNodes = apksNode.getElementsByTagName("apkVersionInfo");
        for (int i = 0; i < apkNodes.getLength(); i++) {
            Metadata.ApkInfo apkInfo = parseApkVersionInfo((Element) apkNodes.item(i));
            if (!apks.containsKey(apkInfo.getArch())) {
                apks.put(apkInfo.getArch(), new LinkedList<>());
            }
            apks.get(apkInfo.getArch()).add(apkInfo);
        }
        return apks;
    }

    @NonNull
    @VisibleForTesting
    Metadata.ApkInfo parseApkVersionInfo(@NonNull Element apkVersionInfoNode) {
        String path = apkVersionInfoNode.getElementsByTagName("path").item(0).getTextContent();
        File apkFile = new File(myApksDirectory, path.replace("/", File.separator));
        String pkgName =
                apkVersionInfoNode.getElementsByTagName("packageName").item(0).getTextContent();
        NodeList archNodes = apkVersionInfoNode.getElementsByTagName("arch");
        String archName;
        if (archNodes.getLength() == 0) {
            archName = "default";
        } else {
            archName = archNodes.item(0).getTextContent();
        }
        Metadata.Arch arch = Metadata.Arch.create(archName);
        String versionCodeString =
                apkVersionInfoNode.getElementsByTagName("versionCode").item(0).getTextContent();

        Set<Integer> apiLevelsSet = new HashSet<>();
        NodeList sdkIntsNodes = apkVersionInfoNode.getElementsByTagName("sdkInt");
        if (sdkIntsNodes.getLength() > 0) {
            NodeList sdkIntNodes = ((Element) sdkIntsNodes.item(0)).getElementsByTagName("sdkInt");
            for (int i = 0; i < sdkIntNodes.getLength(); i++) {
                String sdkIntString = sdkIntNodes.item(i).getTextContent();
                int sdkInt = Integer.parseInt(sdkIntString);
                apiLevelsSet.add(sdkInt);
            }
        }

        long versionCode = Long.parseLong(versionCodeString);
        return new Metadata.ApkInfo(pkgName, apkFile, arch, apiLevelsSet, versionCode);
    }

    @NonNull
    @VisibleForTesting
    Metadata.LibraryCompatibility parseLibraryCompatibility(
            @NonNull Element libraryCompatibilityNode) {
        String aiaCompatApiMinVersionString =
                libraryCompatibilityNode
                        .getElementsByTagName("aiaCompatApiMinVersion")
                        .item(0)
                        .getTextContent();
        long aiaCompatApiMinVersion = Long.parseLong(aiaCompatApiMinVersionString);
        return new Metadata.LibraryCompatibility(aiaCompatApiMinVersion);
    }
}
