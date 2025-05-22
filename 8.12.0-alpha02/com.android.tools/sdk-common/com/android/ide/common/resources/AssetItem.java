/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.ide.common.resources;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.google.common.io.Files;
import java.io.File;
import java.util.Locale;

/**
 * An asset.
 *
 * This includes the name and source file as a {@link AssetFile}.
 *
 */
class AssetItem extends DataItem<AssetFile> {

    /**
     * Constructs the object with a name
     *
     * Note that the object is not fully usable as-is. It must be added to an AssetFile first.
     *
     * @param name the name of the asset
     */
    AssetItem(@NonNull String name) {
        super(name);
    }

    static AssetItem create(@NonNull File sourceFolder, @NonNull File file) {
        // compute the relative path
        StringBuilder sb = new StringBuilder();
        computePath(sb, file.getParentFile(), sourceFolder);
        String fileName = file.getName();
        // When AAPT processed resources, it would uncompress gzipped files, as they will be
        // compressed in the APK anyway. This is now done by the MergedAssetWriter.
        // Map a merged asset item e.g. foo.txt.gz to foo.txt
        sb.append(shouldBeUnGzipped(fileName) ? Files.getNameWithoutExtension(fileName) : fileName);

        return new AssetItem(sb.toString());
    }

    private static void computePath(StringBuilder sb, File current, File stop) {
        if (current.equals(stop)) {
            return;
        }

        computePath(sb, current.getParentFile(), stop);
        sb.append(current.getName()).append('/');
    }

    /**
     * Returns true if the item should be un-gzipped during the asset merge.
     *
     * <p>This exists to replicate the behaviour of AAPT when processing assets: Decompress gzipped files, as they will be re-compressed
     * in the APK anyway.
     *
     * <p>They are renamed in {@link AssetItem#create(File, File)}.
     */
    boolean shouldBeUnGzipped() {
        return shouldBeUnGzipped(getFile().getName());
    }

    private static boolean shouldBeUnGzipped(String fileName) {
        return Files.getFileExtension(fileName).toLowerCase(Locale.US).equals(SdkConstants.EXT_GZ);
    }
}
