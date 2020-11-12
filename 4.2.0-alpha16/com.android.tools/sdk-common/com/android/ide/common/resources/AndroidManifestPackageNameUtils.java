/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.util.PathString;
import com.android.ide.common.util.PathStrings;
import com.android.utils.XmlUtils;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Methods for extracting package name from Android manifests. */
public class AndroidManifestPackageNameUtils {
    /**
     * Reads package name from the AndroidManifest.xml file in the given directory. The the
     * AndroidManifest.xml file can be in either text or proto format.
     *
     * @param manifestFile the AndroidManifest.xml file
     * @return the package name from the manifest
     */
    @Nullable
    public static String getPackageNameFromManifestFile(@NonNull PathString manifestFile)
            throws IOException {
        try (InputStream stream = new BufferedInputStream(PathStrings.inputStream(manifestFile))) {
            return getPackageName(stream);
        } catch (XmlPullParserException e) {
            throw new IOException("File " + manifestFile + " has invalid format");
        }
    }

    /**
     * Reads package name from the AndroidManifest.xml stored inside the given res.apk file.
     *
     * @param resApk the res.apk file
     * @return the package name from the manifest
     */
    @Nullable
    public static String getPackageNameFromResApk(@NonNull ZipFile resApk) throws IOException {
        ZipEntry zipEntry = resApk.getEntry(ANDROID_MANIFEST_XML);
        if (zipEntry == null) {
            throw new IOException(
                    "\"" + ANDROID_MANIFEST_XML + "\" not found in " + resApk.getName());
        }

        try (InputStream stream = new BufferedInputStream(resApk.getInputStream(zipEntry))) {
            return getPackageName(stream);
        } catch (XmlPullParserException e) {
            throw new IOException("Invalid " + ANDROID_MANIFEST_XML + " in " + resApk.getName());
        }
    }

    private static String getPackageName(@NonNull InputStream stream)
            throws XmlPullParserException, IOException {
        stream.mark(1);
        // Instantiate an XML pull parser based on the contents of the stream.
        XmlPullParser parser;
        if (XmlUtils.isProtoXml(stream)) {
            parser = new ProtoXmlPullParser(); // Parser for proto XML used in AARs.
        } else {
            parser = new KXmlParser(); // Parser for regular text XML.
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        }
        parser.setInput(stream, null);
        if (parser.nextTag() == XmlPullParser.START_TAG) {
            return parser.getAttributeValue(null, "package");
        }
        return null;
    }
}
