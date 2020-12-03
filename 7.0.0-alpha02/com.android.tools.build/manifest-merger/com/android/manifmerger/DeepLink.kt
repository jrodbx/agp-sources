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

import com.google.common.annotations.VisibleForTesting
import com.android.ide.common.blame.SourceFilePosition
import com.google.common.collect.ImmutableList
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.StringBuilder
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

/**
 * Represents a loaded <deepLink> element from a navigation xml file.
 *
 * @property schemes the list of uri schemes.
 * @property host the uri's host or `null` if the uri contains no host.
 * @property port the uri's port or `-1` if uri contains no port.
 * @property path the uri's path
 * @property sourceFilePosition the source file position of the deep link element in the
 *                              navigation xml file.
 * @property isAutoVerify true if the <deepLink> element has an android:autoVerify="true" attribute.
 */
@VisibleForTesting
data class DeepLink(
        val schemes: List<String>,
        val host: String?,
        val port: Int,
        val path: String,
        val query: String?,
        val sourceFilePosition: SourceFilePosition,
        val isAutoVerify: Boolean) {

    companion object {
        /** factory method to generate DeepLink from uri String */
        fun fromUri(
                uri: String,
                sourceFilePosition: SourceFilePosition,
                isAutoVerify: Boolean): DeepLink {
            val deepLinkUri = try {
                DeepLinkUri.fromUri(uri)
            } catch (e: URISyntaxException) {
                throw DeepLinkException(e)
            }
            return DeepLink(
                    deepLinkUri.schemes,
                    deepLinkUri.host,
                    deepLinkUri.port,
                    deepLinkUri.path,
                    deepLinkUri.query,
                    sourceFilePosition,
                    isAutoVerify)
        }
    }

    /**
     * A class representing an RFC 2396 compliant URI, following the same rules as java.net.URI,
     * except also allowing the following deviations in the input uri string:
     *
     * 1. use of ".*" or "{placeholder}" wildcards in the URI path and query.
     *
     * 2. use of "${applicationId}" in the URI host and path.
     *
     * 3. use of ".*" wildcard at the beginning of the URI host.
     *
     * @property schemes the list of uri schemes.
     * @property host the uri's host or `null` if the uri contains no host.
     * @property port the uri's port or `-1` if uri contains no port.
     * @property path the uri's path
     */
    @VisibleForTesting
    data class DeepLinkUri(
            val schemes: List<String>,
            val host: String?,
            val port: Int,
            val path: String,
            val query: String?) {

        companion object {

            private val DEFAULT_SCHEMES = ImmutableList.of("http", "https")
            private val APPLICATION_ID_PLACEHOLDER =
                    "\${" + PlaceholderHandler.APPLICATION_ID + "}"
            // PATH_WILDCARD looks for pairs of braces with anything except other braces in between
            private val PATH_WILDCARD = Regex("\\{[^{}]*}")
            private val WILDCARD = ".*"
            private val HOST_WILDCARD = "*"

            /** factory method to generate DeepLinkUri from uri String */
            fun fromUri(uri: String): DeepLinkUri {

                // chooseEncoder() calls below cannot share any character input(s), or there could
                // be inaccurate decoding. Use completely new char1 and char2 characters if adding a
                // new Encoder in the future.
                val applicationIdEncoder = chooseEncoder(uri, 'a', 'b')
                val pathWildcardEncoder = chooseEncoder(uri, 'c', 'd')
                val wildcardEncoder = chooseEncoder(uri, 'e', 'f')
                // Must call uri.replace() with APPLICATION_ID_PLACEHOLDER before PATH_WILDCARD.
                // If encodedUri doesn't contain regex "^[^/]*:/" (which would indicate it contains
                // a scheme) or start with "/" (which would indicate it's just a path), then we want
                // the first part of the uri to be interpreted as the host, but java.net.URI will
                // interpret it as the scheme, unless we prepend it with "//", so we do.
                val encodedUri =
                        uri.replace(APPLICATION_ID_PLACEHOLDER, applicationIdEncoder)
                                .replace(PATH_WILDCARD, pathWildcardEncoder)
                                .replace(WILDCARD, wildcardEncoder)
                                .let {
                                    if (!Pattern.compile("^[^/]*:/").matcher(it).find()
                                            && !it.startsWith("/")) {
                                        "//" + it
                                    } else {
                                        it
                                    }
                                }

                // Attempt to construct URI after encoding all non-compliant characters.
                // If still not compliant, will throw URISyntaxException.
                val compliantUri = URI(encodedUri)

                // determine schemes
                val compliantScheme = compliantUri.scheme
                val schemes = when {
                    compliantScheme == null -> DEFAULT_SCHEMES
                    applicationIdEncoder in compliantScheme ||
                            pathWildcardEncoder in compliantScheme ||
                            wildcardEncoder in compliantScheme ->
                        throw DeepLinkException(
                            "Improper use of wildcards and/or placeholders in deeplink URI scheme")
                    else -> ImmutableList.of(compliantScheme)
                }

                // determine host
                val host: String? =
                        compliantUri.host?.replace(applicationIdEncoder, APPLICATION_ID_PLACEHOLDER)
                                ?.let {
                                    if (it.startsWith(wildcardEncoder)) {
                                        HOST_WILDCARD + it.substring(wildcardEncoder.length)
                                    } else {
                                        it
                                    }
                                }
                // throw exception if host contains an illegal wildcard encoder
                if (host?.contains(pathWildcardEncoder) == true
                        || host?.contains(wildcardEncoder) == true) {
                    throw DeepLinkException(
                            "Improper use of wildcards and/or placeholders in deeplink URI host")
                }

                // determine path
                val path: String =
                    if (compliantUri.path?.isEmpty() != false) {
                        "/"
                    } else {
                        compliantUri.path.replace(applicationIdEncoder, APPLICATION_ID_PLACEHOLDER)
                                .replace(pathWildcardEncoder, WILDCARD)
                                .replace(wildcardEncoder, WILDCARD)
                                .let {
                                    if (it.startsWith("/")) it else "/" + it
                                }
                    }

                val query = compliantUri.query?.replace(pathWildcardEncoder, WILDCARD)

                return DeepLinkUri(schemes, host, compliantUri.port, path, query)
            }

            /**
             * Returns a string which can be used as an encoder in the input uri string; i.e., the
             * encoder can be inserted or substituted anywhere in the uri string and is guaranteed to be
             * unique in the resulting string, allowing accurate decoding.
             *
             *
             * For example, chooseEncoder("www.example.com", 'w', 'x') returns "wwwwx", which, if
             * inserted or substituted in "www.example.com", will remain the only instance of itself in
             * the resulting string. So, e.g., we could encode the "."'s with "wwwwx"'s to yield
             * "wwwwwwwxexamplewwwwxcom", which can be unambiguously decoded back to the original
             * string.
             *
             * @param uri the String which the encoder will be designed to be inserted or substituted
             * into.
             * @param char1 any char
             * @param char2 any char other than char1
             * @return a string which can be used as an encoder in uri. The returned value consists of
             * repeated char1's followed by a single char2 that's not present in the original
             * uri string.
             */
            @VisibleForTesting
            fun chooseEncoder(uri: String, char1: Char, char2: Char): String {
                if (char1 == char2) {
                    throw IllegalArgumentException("char1 and char2 must be different")
                }
                // calculate length of longest substring of repeating char1's
                var longestLength = 0
                var currentLength = 0
                for (c in uri.toCharArray()) {
                    currentLength = if (c == char1) currentLength + 1 else 0
                    longestLength = if (currentLength > longestLength) currentLength else longestLength
                }
                val sb = StringBuilder()
                for (i in 0 until longestLength + 1) {
                    sb.append(char1)
                }
                sb.append(char2)
                return sb.toString()
            }
        }
    }

    /** An exception during the evaluation of a [DeepLink].  */
    class DeepLinkException : RuntimeException {

        constructor(s: String) : super(s)

        constructor(e: Exception) : super(e)
    }
}
