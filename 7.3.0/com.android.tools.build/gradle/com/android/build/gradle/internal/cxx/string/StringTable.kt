/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.build.gradle.internal.cxx.string

/**
 * A string and list-of-string to ordinal int table class.
 * Strings and lists share the same incremented ID counter.
 */
data class StringTable(
    private var nextId : Int = 0,
    private val idToString : MutableMap<Int, String> = mutableMapOf(),
    private val idToList : MutableMap<Int, List<Int>> = mutableMapOf(),
    private val stringToId : MutableMap<String, Int> = mutableMapOf(),
    private val listToId : MutableMap<List<Int>, Int> = mutableMapOf()
) : StringEncoder, StringDecoder {

    override fun encode(string: String) = getIdCreateIfAbsent(string)
    override fun encodeList(list: List<String>) = getIdCreateIfAbsent(list.map { encode(it) })
    override fun decode(id: Int) = getString(id)
    override fun decodeList(id: Int) = getList(id).map { getString(it) }

    /**
     * Given a [String] return its integer ID value. A new ID will be created if necessary and, in
     * this case, the [createAction] function will be called.
     */
    fun getIdCreateIfAbsent(string : String, createAction:(Int) -> Unit = { }) : Int {
        return stringToId.computeIfAbsent(string) {
            idToString[nextId] = string
            createAction(nextId)
            nextId++
        }
    }

    /**
     * Given a list of [Int] return its integer ID value. If this is the first time
     * this list was seen, then [createAction] will be invoked with its new ID.
     */
    fun getIdCreateIfAbsent(list : List<Int>, createAction:(Int) -> Unit = { }) : Int {
        return listToId.computeIfAbsent(list) {
            idToList[nextId] = list
            createAction(nextId)
            nextId++
        }
    }

    /**
     * Return the [String] corresponding to the given integer [id]. Will throw an exception if
     * [id] is out of range.
     */
    private fun getString(id : Int) = idToString.getValue(id)

    /**
     * Return the list of [String] corresponding to the given integer [id].
     */
    private fun getList(id : Int) = idToList.getValue(id)

    /**
     * Return the id corresponding to the given [string]. Will throw an exception if
     * [string] is not known
     */
    fun getId(string : String) = stringToId.getValue(string)

    /**
     * Return true if [string] is contained in this string table.
     */
    fun containsString(string : String) = stringToId.containsKey(string)

    /**
     * Return true if [id] is contained in this string table.
     */
    fun containsId(id : Int) = idToString.containsKey(id)

    /**
     * Return the number of elements in this string table
     */
    val size get() = idToString.size

    /**
     * Return the string table indexes
     */
    val indices get() = idToString.keys
}

interface StringEncoder {
    fun encode(string : String) : Int
    fun encodeList(list : List<String>) : Int
}

interface StringDecoder {
    fun decode(id : Int) : String
    fun decodeList(id : Int) : List<String>
}
