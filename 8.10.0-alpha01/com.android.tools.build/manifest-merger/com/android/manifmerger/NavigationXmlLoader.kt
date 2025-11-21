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

package com.android.manifmerger

import com.android.ide.common.blame.SourceFile
import com.android.utils.PositionXmlParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

/** Responsible for loading navigation XML files.  */
object NavigationXmlLoader {

    /**
     * Loads a navigation xml file without doing xml validation and returns a
     * [NavigationXmlDocument].
     *
     * @param displayName the navigation file display name.
     * @param navigationXmlFile the navigation xml file.
     * @param inputStream the navigation xml file input stream.
     * @return the initialized [NavigationXmlDocument]
     * @throws IOException if IO error.
     * @throws SAXException if the xml is incorrect.
     * @throws ParserConfigurationException if the xml engine cannot be configured.
     */
    @Throws(IOException::class, SAXException::class, ParserConfigurationException::class)
    fun load(
            displayName: String,
            navigationXmlFile: File,
            inputStream: InputStream): NavigationXmlDocument {
        val domDocument = PositionXmlParser.parse(inputStream)
        return NavigationXmlDocument(
                SourceFile(navigationXmlFile, displayName), domDocument.documentElement)
    }

    /**
     * Loads a navigation xml document from its [String] representation without doing xml
     * validation and returns a [NavigationXmlDocument]
     *
     * @param sourceFile the source location to use for logging and record collection.
     * @param xml the [String] representation of a navigation xml file.
     * @return the initialized [NavigationXmlDocument]
     * @throws SAXException if the xml is incorrect
     * @throws ParserConfigurationException if the xml engine cannot be configured.
     */
    @Throws(SAXException::class, ParserConfigurationException::class)
    fun load(sourceFile: SourceFile, xml: String): NavigationXmlDocument {
        val domDocument = PositionXmlParser.parse(xml)
        return NavigationXmlDocument(sourceFile, domDocument.documentElement)
    }
}
