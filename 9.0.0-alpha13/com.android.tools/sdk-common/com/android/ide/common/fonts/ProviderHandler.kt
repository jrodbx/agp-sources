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

package com.android.ide.common.fonts

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.util.ArrayList
import java.util.HashMap

private const val TAG_PROVIDER = "provider"
private const val ATTR_PROVIDER_NAME = "name"
private const val ATTR_AUTHORITY = "authority"
private const val ATTR_PACKAGE = "package"
private const val ATTR_PROVIDER_URL = "url"
private const val ATTR_PROVIDER_CERT = "cert"
private const val ATTR_PROVIDER_DEV_CERT = "dev_cert"

class ProviderHandler : DefaultHandler() {
    private val providers = HashMap<String, FontProvider>()

    val fontProviders: List<FontProvider>
        get() = ArrayList(providers.values)

    override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
        when (qName) {
            TAG_PROVIDER -> {
                val name = attributes.getValue(ATTR_PROVIDER_NAME)
                val authority = attributes.getValue(ATTR_AUTHORITY)
                val packageName = attributes.getValue(ATTR_PACKAGE)
                val url = attributes.getValue(ATTR_PROVIDER_URL)
                val cert = attributes.getValue(ATTR_PROVIDER_CERT)
                val devCert = attributes.getValue(ATTR_PROVIDER_DEV_CERT)
                if (name.isNotEmpty() && authority.isNotEmpty() && packageName.isNotEmpty()) {
                    providers[authority] = FontProvider(name, authority, packageName, url, cert, devCert)
                }
            }
        }
    }
}
