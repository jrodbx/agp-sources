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
package com.android.ide.common.rendering.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;

/** Factory for creation of XML pull parsers. */
public interface XmlParserFactory {
    /**
     * Returns an {@link XmlPullParser} for the PSI version of an XML file.
     *
     * <p>The call to the method should be guarded by a check for
     * {@code RenderParamsFlag.FLAG_KEY_XML_FILE_PARSER_SUPPORT}.
     *
     * @param fileName name of the file to parse
     * @return the XML pull parser for the file, or null if the PSI file with the given name is not
     *     found
     */
    @Nullable
    public XmlPullParser createXmlParserForPsiFile(@NonNull String fileName);

    /**
     * Returns an {@link XmlPullParser} for the non-PSI version of an XML file.
     *
     * <p>The call to the method should be guarded by a check for
     * {@code RenderParamsFlag.FLAG_KEY_XML_FILE_PARSER_SUPPORT}.
     *
     * @param fileName name of the file to parse
     * @return the XML pull parser for the file, or null if the file with the given name does not
     *     exist
     */
    @Nullable
    public XmlPullParser createXmlParserForFile(@NonNull String fileName);

    /**
     * Creates and returns an {@link XmlPullParser}. This method is intended for delegating calls
     * from {@code android.util.Xml_Delegate}. It should only be used when the name of the file is
     * not available at the point of the parser creation.
     *
     * @return the newly created XML parser
     */
    @NonNull
    public XmlPullParser createXmlParser();
}
