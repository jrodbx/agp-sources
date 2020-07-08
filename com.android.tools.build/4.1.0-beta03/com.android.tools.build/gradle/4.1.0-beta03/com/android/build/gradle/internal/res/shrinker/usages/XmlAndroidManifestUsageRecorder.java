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

package com.android.build.gradle.internal.res.shrinker.usages;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.res.shrinker.ResourceShrinkerModel;
import com.android.utils.XmlUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/** Records resource usages from AndroidManifest.xml in raw XML format. */
public final class XmlAndroidManifestUsageRecorder implements ResourceUsageRecorder {

    private final Path manifest;

    public XmlAndroidManifestUsageRecorder(@NonNull Path manifest) {
        this.manifest = manifest;
    }

    @Override
    public void recordUsages(@NonNull ResourceShrinkerModel model) throws IOException {
        String xml = new String(Files.readAllBytes(manifest), StandardCharsets.UTF_8);
        try {
            Document document = XmlUtils.parseDocument(xml, true);
            model.getUsageModel().visitXmlDocument(manifest.toFile(), null, document);
        } catch (SAXException e) {
            throw new IOException(e);
        }
    }
}
