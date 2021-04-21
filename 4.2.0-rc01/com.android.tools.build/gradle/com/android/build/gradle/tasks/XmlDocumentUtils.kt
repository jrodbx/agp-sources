/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.build.gradle.tasks

import com.android.SdkConstants
import com.android.manifmerger.ManifestMerger2
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * This will strip uses-split from the feature manifest used to merge it back into the base
 * module and features that require it. If featureB depends on featureA, we don't want the
 * `<uses split android:name="featureA"/>` from featureB's manifest to appear in
 * featureA's manifest after merging.
 *
 */
internal fun stripUsesSplitFromFeatureManifest(document: Document) {
    // make changes necessary for metadata feature manifest
    val manifest = document.documentElement
    val usesSplitList =
        ManifestMerger2.getChildElementsByName(
            manifest,
            SdkConstants.TAG_USES_SPLIT
        )
    usesSplitList.forEach { node: Element? ->
        manifest.removeChild(node)
    }
}

/**
 * This will strip the min sdk from the feature manifest, used to merge it back into the base
 * module. This is used in dynamic-features, as dynamic-features can have different min sdk than
 * the base module. It doesn't need to be strictly <= the base module like libraries.
 *
 * @param document the resulting document to use for stripping the min sdk from.
 */
internal fun stripMinSdkFromFeatureManifest(document: Document) {
    // make changes necessary for metadata feature manifest
    val manifest = document.documentElement
    val usesSdkList =
        ManifestMerger2.getChildElementsByName(manifest, SdkConstants.TAG_USES_SDK)
    val usesSdk: Element
    if (!usesSdkList.isEmpty()) {
        usesSdk = usesSdkList[0]
        usesSdk.removeAttributeNS(
            SdkConstants.ANDROID_URI,
            SdkConstants.ATTR_MIN_SDK_VERSION
        )
    }
}

/**
 * Set the "android:splitName" attribute to `featureName` for every `activity`,
 * `service` and `provider` element.
 *
 * @param document the document whose attributes are changed
 */
internal fun removeSplitNames(
    document: Document
) {
    val manifest = document.documentElement ?: return
    // then update attributes in the application element's child elements
    val applicationElements =
        ManifestMerger2.getChildElementsByName(manifest, SdkConstants.TAG_APPLICATION)
    if (applicationElements.isEmpty()) {
        return
    }
    // assumes just 1 application element among manifest's immediate children.
    val application = applicationElements[0]
    val elementNamesToUpdate =
        listOf(
            SdkConstants.TAG_ACTIVITY,
            SdkConstants.TAG_SERVICE,
            SdkConstants.TAG_PROVIDER
        )
    for (elementName in elementNamesToUpdate) {
        for (elementToUpdate in ManifestMerger2.getChildElementsByName(
            application,
            elementName
        )) {
            elementToUpdate.removeAttributeNS(
                SdkConstants.ANDROID_URI,
                SdkConstants.ATTR_SPLIT_NAME)
        }
    }
}