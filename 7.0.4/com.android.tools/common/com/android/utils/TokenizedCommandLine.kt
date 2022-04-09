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

package com.android.utils

import com.android.SdkConstants
import com.android.SdkConstants.PLATFORM_WINDOWS
import com.google.common.annotations.VisibleForTesting
import java.lang.Character.isWhitespace
import kotlin.math.abs
import kotlin.math.min

const val ZERO_ALLOC_TOKENIZER_END_OF_TOKEN = Int.MAX_VALUE
const val ZERO_ALLOC_TOKENIZER_END_OF_COMMAND = Int.MIN_VALUE

/**
 * Parse a Windows or POSIX command-line without allocating per-character
 * memory. It does this by amortizing with a shared a buffer from parses of
 * other command lines.
 *
 * The first element of the buffer is a generation counter. If the buffer
 * is shared between multiple parses each parse will increment the generation.
 * If a buffer is used with an older [TokenizedCommandLine] then that's a
 * bug in the calling code so an exception will be thrown.
 *
 * After the first element, the buffer consists of indexes of characters within
 * the original [commandLine] delimited by [ZERO_ALLOC_TOKENIZER_END_OF_TOKEN]
 * and terminated by [ZERO_ALLOC_TOKENIZER_END_OF_COMMAND]
 *
 * @param commandLine the command-line to tokenize
 * @param raw then special characters are sent to the receiver. On Windows,
 *        that's double-quote("), backslash(\), and caret(^). On POSIX, that's
 *        single-quote('), double-quote("), and backslash(\).
 * @param platform optional platform type. Default is the current platform.
 * @param indexes optional IntArray to hold indexes of characters, token
 *        delimiters (ZERO_ALLOC_TOKENIZER_END_OF_TOKEN), and end of string
 *        delimiter (ZERO_ALLOC_TOKENIZER_END_OF_COMMAND).
 */
class TokenizedCommandLine(
    val commandLine: String,
    val raw: Boolean,
    val platform: Int = SdkConstants.currentPlatform(),
    private var indexes: IntArray = allocateTokenizeCommandLineBuffer(commandLine)) {
    private val generation = ++indexes[0]
    private var toStringValue : String? = null
    init {
        checkGeneration()
        if (platform == PLATFORM_WINDOWS) {
            zeroAllocTokenizeWindows(raw)
        } else {
            zeroAllocTokenizePOSIX(raw)
        }
    }

    /**
     * Remove tokens matching [token] and also remove up to [extra] additional tokens
     * following that. If [matchPrefix] is set to true, [token] is treated as a prefix
     * and any token starts with this prefix would be matched and removed.
     *
     * This function updates [indexes] without resizing it or allocating memory. It
     * works by maintaining a 'read' pointer and a 'write' that are pointers within
     * [indexes].
     *
     * In the beginning read and write are the same. When a matching token is found
     * then the read pointer is moved forward past the end of the matching token
     * along with any [extra] tokens.
     *
     * So for example, if removeTokenGroup("-c", 1) is called then the pointers will
     * look like this after the '-c' and one extra token have been skipped.
     *
     *                            read
     *                            v
     *   clang.exe -c my-file.cpp -o my-file.o
     *             ^
     *             write
     *
     * When the parameter [returnFirstExtra] is true, this function returns the first string value
     * of the first parameter after [token]; this requires an extra string allocation. When
     * [returnFirstExtra] is false, null is returned; no extra string allocation is needed in this
     * case.
     */
    fun removeTokenGroup(
        token: String,
        extra: Int,
        matchPrefix: Boolean = false,
        returnFirstExtra: Boolean = false) : String? {
        checkGeneration()
        invalidate()
        var read = 1
        var write = 1
        var firstExtra : String? = null

        do {
            // Check invariants:
            //   write point can't move past read pointer
            assert(read >= write)
            //   read pointer always points to the start of a token
            assert(isStartOfToken(read))
            //   write pointer always points to the start of a token
            assert(isStartOfToken(write))

            // If token matches the one pointed to be read pointer then skip it and also
            // skip any extra tokens.
            if (tokenMatches(token, read, matchPrefix)) {
                if (returnFirstExtra) {
                    firstExtra = if (matchPrefix) {
                        tokenStartingAt(read + token.length)
                    } else {
                        tokenStartingAt(nextTokenAfter(read))
                    }
                }

                var count = 0
                while(count != extra + 1 && !isEndOfCommand(read)) {
                    read = nextTokenAfter(read)
                    ++count
                }
            } else {
                // Skip both read and write pointers to the start of the next token.
                if (!isEndOfCommand(read) && !isEndOfCommand(write)) {
                    do {
                        indexes[write++] = indexes[read++]
                    } while (!isEndOfToken(read - 1))
                }
            }
        } while (!isEndOfCommand(read))
        indexes[write] = ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
        return firstExtra
    }

    private fun tokenStartingAt(start : Int) : String {
        checkGeneration()
        val token = StringBuilder()
        for(read in start until indexes.size) {
            when (val offset = indexes[read]) {
                ZERO_ALLOC_TOKENIZER_END_OF_COMMAND -> break
                ZERO_ALLOC_TOKENIZER_END_OF_TOKEN -> break
                else ->
                    token.append(commandLine[offset])
            }
        }
        return token.trim().toString()
    }

    /**
     * Remove and return the n-th token and return its value as a string.
     * Will return null if the requested token [n] is out of range.
     *
     * This function operates in a manner similar to [removeTokenGroup] in
     * that it uses a read pointer and write pointer to walk across the
     * [commandLine] and it relies on the read pointer being the same as
     * write pointer or larger.
     */
    fun removeNth(n: Int) : String? {
        checkGeneration()
        invalidate()
        val token = StringBuilder()
        var tokenNumber = 0
        var write = 1
        for(read in 1 until indexes.size) {
            if (tokenNumber != n) indexes[write++] = indexes[read]
            when (val offset = indexes[read]) {
                ZERO_ALLOC_TOKENIZER_END_OF_COMMAND -> {
                    if (token.isEmpty()) return null
                    return token.toString()
                }
                ZERO_ALLOC_TOKENIZER_END_OF_TOKEN -> tokenNumber++
                else -> if (tokenNumber == n) token.append(commandLine[offset])
            }
        }
        return null
    }

    /**
     * Construct and return a list of tokens.
     */
    fun toTokenList() : List<String> {
        checkGeneration()
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var i = 1
        while(!isEndOfCommand(i)) {
            when(val c = charAt(i)) {
                null -> {
                    if (token.isNotEmpty()) {
                        result.add(token.toString())
                        token.setLength(0)
                    }
                }
                else -> token.append(c)
            }
            ++i
        }
        return result
    }

    /**
     * Tokenize a string with Windows rules.
     *
     * This is the zero-alloc (per char) part of the tokenizer.
     *
     * This follows Windows tokenization tokenization rules:
     *
     * https://msdn.microsoft.com/en-us/library/17w5ykft.aspx
     *
     *  - A string surrounded by double quotation marks ("string") is interpreted
     *    as a single argument, regardless of white space contained within. A
     *    quoted string can be embedded in an argument.
     *  - A double quotation mark preceded by a backslash (\") is interpreted as a
     *    literal double quotation mark character (").
     *  - Backslashes are interpreted literally, unless they immediately precede a
     *    double quotation mark.
     *  - If an even number of backslashes is followed by a double quotation mark,
     *    one backslash is placed in the argv array for every pair of backslashes,
     *    and the double quotation mark is interpreted as a string delimiter.
     *  - If an odd number of backslashes is followed by a double quotation mark,
     *    one backslash is placed in the argv array for every pair of backslashes,
     *    and the double quotation mark is "escaped" by the remaining backslash.
     *
     * @param raw if true, then special characters double-quote("), backslash(\),
     *   and caret(^) are returned, otherwise they are not returned. Regardless
     *   of this flag, the Windows command line escaping logic is applied and
     *   unquoted whitespace is removed.
     * @return Same as [indexes] for the case that the caller didn't specify it.
     */
    private fun zeroAllocTokenizeWindows(raw: Boolean) : TokenizedCommandLine {
        checkGeneration()
        invalidate()
        var quoting = false
        var i = 0
        val length = commandLine.length // Calculate length once
        var c: Char?
        var offset = 1 // One because first element is generation

        while(i < length && isWhitespace(commandLine[i])) i++

        while (i < length) {
            c = commandLine[i]
            // Quick path for normal case
            when {
                c == '"' -> {
                    // This is an opening or closing double-quote("). Whichever it is, move to the
                    // opposite quoting state for subsequent iterations. If this is raw mode then
                    // the double-quote(") will also be sent.
                    if (raw) {
                        indexes[offset++] = i
                    } // The double-quote(").
                    quoting = !quoting
                    ++i
                }
                c == '\\' -> {
                    // Count the number of backslash(\) and then check for double-quote(") following
                    // them.
                    var forward = i + 1
                    var slashCount = 1
                    // Move position to end of backslashes(\)
                    c = commandLine.getOrNull(forward)
                    while (c == '\\') {
                        ++slashCount
                        c = commandLine.getOrNull(++forward)
                    }
                    val odd = slashCount % 2 == 1
                    val quote = c == '\"' // Was there a double-quote(")?
                    // If double-quote("), halve the backslashes(\). If raw, then don't halve.
                    if (!raw && quote) {
                        slashCount /= 2
                    }
                    // Emit the right number of backslashes(\).
                    repeat(slashCount) { j ->
                        indexes[offset++] = i + j
                    }
                    // If odd backslashes(\) then treat a double-quote(") as literal
                    if (odd && quote) {
                        indexes[offset++] = forward++
                    }
                    i = forward
                }
                !quoting && c == '^' -> {
                    // If caret(^) is seen outside of quoting and the next character is a block of
                    // carriage-return(\r) and line-feed(\n) then remove the caret(^) and the
                    // line-feed(\n). If this is raw mode then the caret(^) is sent but no CR/LF
                    // characters.
                    c = commandLine.getOrNull(++i)
                    // If raw or next character was end of command then write caret(^)
                    if (raw || c == null) {
                        indexes[offset++] = i - 1
                    }
                    // caret(^) is escaped by caret(^)
                    if (c == '^') {
                        indexes[offset++] = i++
                    }
                    // Move past EOL characters.
                    while (c == '\r' || c == '\n') c = commandLine.getOrNull(++i)
                }
                !quoting && isWhitespace(c) -> {
                    // Whitespace outside of quotes terminates the token. Send a special
                    // Int.MAX_VALUE to indicate the token is ended.
                    indexes[offset++] = ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
                    c = commandLine.getOrNull(++i)
                    // Skip any additional whitespace that follows the initial whitespace
                    while (c != null && isWhitespace(c)) c = commandLine.getOrNull(++i)
                }
                else -> indexes[offset++] = i++
            }
        }
        if (!isEndOfToken(offset - 1)) {
            indexes[offset++] = ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
        }
        indexes[offset++] = ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
        return this
    }

    /**
     * Tokenize a string with POSIX rules. This function should operate in the
     * same manner as the bash command-line.
     *
     * This is the zero-alloc (per char) part of the tokenizer.
     *
     * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html
     *
     *  - A backslash that is not quoted shall preserve the literal value of the
     *    following character
     *  - Enclosing characters in single-quotes ( '' ) shall preserve the literal
     *    value of each character within the single-quotes.
     *  - Enclosing characters in double-quotes ( "" ) shall preserve the literal
     *    value of all characters within the double-quotes, with the exception of
     *    the characters dollar sign, backquote, and backslash
     *
     * For escaped tokens, this can be validated with a script like this:
     *
     * echo 1=[/$1]
     *
     * echo 2=[/$2]
     *
     * echo 3=[/$3]
     *
     * @param raw if true, then special characters single-quote('), double-quote("), and
     *   backslash(\) are returned, otherwise they are not returned. Regardless of this flag,
     *   the POSIX command line escaping logic is applied.
     */
    private fun zeroAllocTokenizePOSIX(raw: Boolean) : TokenizedCommandLine {
        checkGeneration()
        invalidate()
        var quoting = false
        var quote = '\u0000' // POSIX quote can be either " or '
        var escaping = false
        var skipping = true
        var i = 0
        var c: Char
        val length = commandLine.length
        var offset = 1 // One because first element is generation
        while(i < length) {
            c = commandLine[i++]
            if (skipping) {
                skipping = if (isWhitespace(c)) {
                    continue
                } else {
                    false
                }
            }
            if (quoting || !isWhitespace(c)) {
                if (raw) {
                    indexes[offset++] = i - 1
                }
            }
            if (escaping) {
                escaping = false
                if (c != '\n') {
                    if (!raw) {
                        indexes[offset++] = i - 1
                    }
                }
                continue
            } else if (c == '\\' && (!quoting || quote == '\"')) {
                escaping = true
                continue
            } else if (!quoting && (c == '"' || c == '\'')) {
                quoting = true
                quote = c
                continue
            } else if (quoting && c == quote) {
                quoting = false
                quote = '\u0000'
                continue
            }
            if (!quoting && isWhitespace(c)) {
                skipping = true
                indexes[offset++] = ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
                continue
            }
            if (!raw) {
                indexes[offset++] = i - 1
            }
        }
        if (!isEndOfToken(offset - 1)) {
            indexes[offset++] = ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
        }
        indexes[offset] = ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
        return this
    }

    /**
     * Return true if the token at [offset] matches [token].
     * [offset] is a pointer into [indexes] so it is one-relative to
     * account for the generation counter.
     */
    @VisibleForTesting
    fun tokenMatches(token: String, offset: Int, matchPrefix: Boolean) : Boolean {
        checkGeneration()
        var i = 0
        var index = indexes[offset]
        while(index != ZERO_ALLOC_TOKENIZER_END_OF_COMMAND) {
            val endOfToken = index == ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
            if (i == token.length) {
                return endOfToken || matchPrefix
            }
            if (endOfToken) {
                return false
            }
            if (token[i] != commandLine[index]) {
                return false
            }
            ++i
            index = indexes[offset + i]
        }
        return false
    }

    /**
     * Skip to the start of the next token [offset] is the one-relative starting
     * pointer. The return value is the offset to the start of the next token.
     */
    @VisibleForTesting
    fun nextTokenAfter(offset: Int) : Int {
        checkGeneration()
        var result = offset
        while(!isEndOfToken(result) && !isEndOfCommand(result)) ++result
        return result + 1
    }

    private fun isEndOfToken(i : Int) =
        i < indexes.size && indexes[i] == ZERO_ALLOC_TOKENIZER_END_OF_TOKEN

    private fun isEndOfCommand(i : Int) =
        i >= indexes.size || indexes[i] == ZERO_ALLOC_TOKENIZER_END_OF_COMMAND

    private fun isStartOfToken(i : Int) = i == 1 || isEndOfToken(i - 1)

    private fun charAt(i : Int) =
        if (isEndOfToken(i)) null else commandLine[indexes[i]]

    /**
     * This is what the length of the normalized command-line string would be if it was created.
     */
    fun normalizedCommandLineLength() : Int {
        return if (isEndOfCommand(1)) {
            0
        } else {
            var i = 1
            while (!isEndOfCommand(i + 1)) ++i
            i - 1
        }
    }

    /**
     * Not hashCode() to avoid giving the impression [TokenizedCommandLine] can be directly
     * stored in a hashtable.
     * It is *not* the same same as toString().hashCode(), but it is guaranteed to be a good
     * hash function.
     */
    fun computeNormalizedCommandLineHashCode(): Int {
        checkGeneration()
        var hash = 1469598103934665603
        var i = 1
        while (!isEndOfCommand(i) && !isEndOfCommand(i + 1)) {
            hash = hash xor (charAt(i) ?: ' ').toLong()
            hash *= 1099511628211
            ++i
        }
        return hash.toInt()
    }

    /**
     * Check whether the normalized command-line would equal the given string if created.
     */
    fun normalizedCommandLineEquals(other: String): Boolean {
        val length1 = normalizedCommandLineLength()
        val length2 = other.length
        if (length1 == 0 && length2 == 0) return true
        if (length1 != length2) return false

        // It's okay for indexes size to be different. What matters
        // is that they both have the same values up to ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
        for (i in 1 until length1) {
            if (charAt(i) ?: ' ' != other[i - 1]) return false
        }
        return true
    }

    fun toString(separator: String): String {
        checkGeneration()
        var i = 1
        if (isEndOfCommand(i)) return ""
        val sb = StringBuilder()
        while(!isEndOfCommand(i + 1)) {
            sb.append(charAt(i) ?: separator)
            ++i
        }
        return sb.toString()
    }

    /**
     * Return the normalize command-line string.
     */
    override fun toString() : String {
        if (toStringValue != null) return toStringValue!!
        toStringValue = toString(" ")
        return toStringValue!!
    }

    private fun invalidate() {
        toStringValue = null
    }

    private fun checkGeneration() {
        if (generation != abs(indexes[0])) {
            throw Exception("Buffer indexes was shared with another " +
                    "TokenizedCommandLine after this one")
        }
    }
}

/**
 * Allocate a buffer large enough to hold [commandLine] indexes.
 *
 * The +3 is derived as:
 *   one element to hold the generation counter
 *   + one element for each character in the commandLine
 *   + one element to hold the last ZERO_ALLOC_TOKENIZER_END_OF_TOKEN
 *   + one element to hold final ZERO_ALLOC_TOKENIZER_END_OF_COMMAND
 *
 * This relies on the fact that Windows and POSIX parsing can only reduce the
 * number of characters and not increase it. Reduction comes, for example,
 * from combining contiguous blocks of whitespace with a single
 * [ZERO_ALLOC_TOKENIZER_END_OF_TOKEN].
 */
fun allocateTokenizeCommandLineBuffer(commandLine: String) =
    IntArray(minimumSizeOfTokenizeCommandLineBuffer(commandLine))

fun minimumSizeOfTokenizeCommandLineBuffer(commandLine: String) =
    commandLine.length + 3

