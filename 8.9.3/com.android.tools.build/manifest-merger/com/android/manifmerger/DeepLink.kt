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

import com.android.ide.common.blame.SourceFilePosition
import com.google.common.collect.ImmutableList
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
 * @property action a custom action defined in source file; if not specified, defaults to "VIEW"
 * @property mimeType a mime type specified in source file; this is an optional label
 */
data class DeepLink(
        val schemes: List<String>,
        val host: String?,
        val port: Int,
        val path: String,
        val query: String?,
        val fragment: String?,
        val sourceFilePosition: SourceFilePosition,
        val isAutoVerify: Boolean,
        val action: String = "android.intent.action.VIEW",
        val mimeType: String? = null) {

    companion object {
        /** factory method to generate DeepLink from uri String */
        fun fromUri(
                uri: String,
                sourceFilePosition: SourceFilePosition,
                isAutoVerify: Boolean,
                action: String? = null,
                mimeType: String? = null): DeepLink {
            val deepLinkUri = try {
                DeepLinkUri.fromUri(uri)
            } catch (e: URISyntaxException) {
                throw DeepLinkException(e)
            }
            if (action == null) {
                return DeepLink(
                    deepLinkUri.schemes,
                    deepLinkUri.host,
                    deepLinkUri.port,
                    deepLinkUri.path,
                    deepLinkUri.query,
                    deepLinkUri.fragment,
                    sourceFilePosition,
                    isAutoVerify,
                    mimeType = mimeType)
            }
            return DeepLink(
                    deepLinkUri.schemes,
                    deepLinkUri.host,
                    deepLinkUri.port,
                    deepLinkUri.path,
                    deepLinkUri.query,
                    deepLinkUri.fragment,
                    sourceFilePosition,
                    isAutoVerify,
                    action,
                    mimeType)
        }
    }

    /**
     * A class representing an RFC 2396 compliant URI, following the same rules as java.net.URI,
     * except also allowing the following deviations in the input uri string:
     *
     * 1. use of ".*" or "{placeholder}" wildcards in the URI path and query.
     *
     * 2. use of manifest placeholders, e.g. "${applicationId}", in the URI host, path, and scheme.
     *
     * 3. use of ".*" wildcard at the beginning of the URI host.
     *
     * @property schemes the list of uri schemes.
     * @property host the uri's host or `null` if the uri contains no host.
     * @property port the uri's port or `-1` if uri contains no port.
     * @property path the uri's path
     */
    data class DeepLinkUri(
        val schemes: List<String>,
        val host: String?,
        val port: Int,
        val path: String,
        val query: String?,
        val fragment: String?
    ) {

        companion object {

            private val DEFAULT_SCHEMES = ImmutableList.of("http", "https")
            private const val DOLLAR_SIGN = "$"
            private const val OPEN_BRACKET = "{"
            private const val CLOSE_BRACKET = "}"
            private val MANIFEST_PLACEHOLDER = Regex("\\$\\{[^{}]*}")
            // PATH_WILDCARD looks for pairs of braces, not preceded by a $, with anything except
            // other braces in between
            private val PATH_WILDCARD = Regex("(?<!\\$)\\{[^{}]*}")
            private const val WILDCARD = ".*"
            private const val HOST_WILDCARD = "*"

            /** factory method to generate DeepLinkUri from uri String */
            fun fromUri(uri: String): DeepLinkUri {

                // chooseEncoder() calls below cannot share any character input(s), or there could
                // be inaccurate decoding. Use completely new char1 and char2 characters if adding a
                // new Encoder in the future.
                val dollarSignEncoder = chooseEncoder(uri, 'a', 'b')
                val openBracketEncoder = chooseEncoder(uri, 'c', 'd')
                val closeBracketEncoder = chooseEncoder(uri, 'e', 'f')
                val wildcardEncoder = chooseEncoder(uri, 'g', 'h')
                val hostWildcardEncoder = chooseEncoder(uri, 'i', 'j')
                // If encodedUri doesn't contain regex "^[^/]*:/" (which would indicate it contains
                // a scheme) or start with "/" (which would indicate it's just a path), then we want
                // the first part of the uri to be interpreted as the host, but java.net.URI will
                // interpret it as the scheme, unless we prepend it with "//", so we do.
                val encodedUri =
                    uri.replace(DOLLAR_SIGN, dollarSignEncoder)
                        .replace(OPEN_BRACKET, openBracketEncoder)
                        .replace(CLOSE_BRACKET, closeBracketEncoder)
                        .replace(WILDCARD, wildcardEncoder)
                        .replace(HOST_WILDCARD, hostWildcardEncoder)
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

                // Also make sure we can construct a URI using the stricter MANIFEST_PLACEHOLDER and
                // PATH_WILDCARD substitutions because when we encode $, {, and } separately, as we
                // do for encodedUri above, we don't get the desired URISyntaxException for cases of
                // hanging or nested brackets.
                // We can use an arbitrary encoder string (dollarSignEncoder) because we don't need
                // to decode it later.
                URI(
                    uri.replace(MANIFEST_PLACEHOLDER, dollarSignEncoder)
                        .replace(PATH_WILDCARD, dollarSignEncoder)
                        .replace(WILDCARD, dollarSignEncoder)
                        .let {
                            if (!Pattern.compile("^[^/]*:/").matcher(it).find()
                                && !it.startsWith("/")) {
                                "//" + it
                            } else {
                                it
                            }
                        }
                )

                // determine schemes
                val decodedScheme =
                    compliantUri.scheme
                        ?.replace(dollarSignEncoder, DOLLAR_SIGN)
                        ?.replace(openBracketEncoder, OPEN_BRACKET)
                        ?.replace(closeBracketEncoder, CLOSE_BRACKET)
                val schemes = when {
                    decodedScheme == null -> DEFAULT_SCHEMES
                    PATH_WILDCARD.containsMatchIn(decodedScheme) ||
                            wildcardEncoder in decodedScheme ||
                            hostWildcardEncoder in decodedScheme ->
                        throw DeepLinkException(
                            "Improper use of wildcards and/or placeholders in deeplink URI scheme")
                    else -> ImmutableList.of(decodedScheme)
                }

                // determine host
                val decodedHost =
                    compliantUri.host
                        ?.replace(dollarSignEncoder, DOLLAR_SIGN)
                        ?.replace(openBracketEncoder, OPEN_BRACKET)
                        ?.replace(closeBracketEncoder, CLOSE_BRACKET)
                val host =
                    decodedHost?.let {
                        if (it.startsWith(wildcardEncoder)) {
                            HOST_WILDCARD + it.substring(wildcardEncoder.length)
                        } else if (it.startsWith(hostWildcardEncoder)) {
                            HOST_WILDCARD + it.substring(hostWildcardEncoder.length)
                        } else {
                            it
                        }
                    }
                // throw exception if host contains an illegal wildcard encoder
                if (host != null
                    && (PATH_WILDCARD.containsMatchIn(host) || host.contains(wildcardEncoder))) {
                    throw DeepLinkException(
                            "Improper use of wildcards and/or placeholders in deeplink URI host")
                }

                // determine path
                val path: String =
                    if (compliantUri.path?.isEmpty() != false) {
                        "/"
                    } else {
                        compliantUri.path
                            .replace(dollarSignEncoder, DOLLAR_SIGN)
                            .replace(openBracketEncoder, OPEN_BRACKET)
                            .replace(closeBracketEncoder, CLOSE_BRACKET)
                            .replace(PATH_WILDCARD, WILDCARD)
                            .replace(wildcardEncoder, WILDCARD)
                            .replace(hostWildcardEncoder, HOST_WILDCARD)
                            .let {
                                if (it.startsWith("/")) it else "/" + it
                            }
                    }

                val query =
                    compliantUri.query
                        ?.replace(dollarSignEncoder, DOLLAR_SIGN)
                        ?.replace(openBracketEncoder, OPEN_BRACKET)
                        ?.replace(closeBracketEncoder, CLOSE_BRACKET)
                        ?.replace(PATH_WILDCARD, WILDCARD)
                        ?.replace(wildcardEncoder, WILDCARD)
                        ?.replace(hostWildcardEncoder, HOST_WILDCARD)

                val fragment =
                    compliantUri.fragment
                        ?.replace(dollarSignEncoder, DOLLAR_SIGN)
                        ?.replace(openBracketEncoder, OPEN_BRACKET)
                        ?.replace(closeBracketEncoder, CLOSE_BRACKET)
                        ?.replace(PATH_WILDCARD, WILDCARD)
                        ?.replace(wildcardEncoder, WILDCARD)
                        ?.replace(hostWildcardEncoder, HOST_WILDCARD)

                return DeepLinkUri(schemes, host, compliantUri.port, path, query, fragment)
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
