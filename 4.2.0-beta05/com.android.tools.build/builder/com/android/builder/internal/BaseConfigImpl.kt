/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.builder.internal

import com.android.builder.model.BaseConfig
import com.android.builder.model.ClassField
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import java.io.File
import java.io.Serializable

/**
 * An object that contain a BuildConfig configuration
 */
abstract class BaseConfigImpl : Serializable,
    BaseConfig {

    override var applicationIdSuffix: String? = null

    open fun applicationIdSuffix(applicationIdSuffix: String?) {
        this.applicationIdSuffix = applicationIdSuffix
    }

    override var versionNameSuffix: String? = null

    open fun versionNameSuffix(versionNameSuffix: String?) {
        this.versionNameSuffix = versionNameSuffix
    }

    private val mBuildConfigFields: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    private val mResValues: MutableMap<String, ClassField> =
        Maps.newTreeMap()
    private val mProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mConsumerProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mTestProguardFiles: MutableList<File> =
        Lists.newArrayList()
    private val mManifestPlaceholders: MutableMap<String, Any> =
        Maps.newHashMap()

    override var multiDexEnabled: Boolean? = null

    override var multiDexKeepProguard: File? = null

    override var multiDexKeepFile: File? = null

    /**
     * @see .getApplicationIdSuffix
     */
    fun setApplicationIdSuffix(applicationIdSuffix: String?): BaseConfigImpl {
        this.applicationIdSuffix = applicationIdSuffix
        return this
    }

    /**
     * @see .getVersionNameSuffix
     */
    fun setVersionNameSuffix(versionNameSuffix: String?): BaseConfigImpl {
        this.versionNameSuffix = versionNameSuffix
        return this
    }

    /**
     * Adds a BuildConfig field.
     */
    fun addBuildConfigField(field: ClassField) {
        mBuildConfigFields[field.name] = field
    }

    /**
     * Adds a generated resource value.
     */
    fun addResValue(field: ClassField) {
        mResValues[field.name] = field
    }

    /**
     * Adds a generated resource value.
     */
    fun addResValues(values: Map<String, ClassField>) {
        mResValues.putAll(values)
    }

    override var buildConfigFields: Map<String, ClassField>
        get() = mBuildConfigFields
        set(fields) {
            mBuildConfigFields.clear()
            mBuildConfigFields.putAll(fields)
        }

    /**
     * Adds BuildConfig fields.
     */
    fun addBuildConfigFields(fields: Map<String, ClassField>) {
        mBuildConfigFields.putAll(fields)
    }

    /**
     * Returns the generated resource values.
     */
    override var resValues: MutableMap<String, ClassField>
        get() = mResValues
        set(fields) {
            mResValues.clear()
            mResValues.putAll(fields)
        }

    /** {@inheritDoc}  */
    override val proguardFiles: MutableList<File>
        get() = mProguardFiles

    override val consumerProguardFiles: MutableList<File>
        get() = mConsumerProguardFiles

    override val testProguardFiles: MutableList<File>
        get() = mTestProguardFiles

    override val manifestPlaceholders: MutableMap<String, Any>
        get() = mManifestPlaceholders

    fun addManifestPlaceholders(manifestPlaceholders: Map<String, Any>) {
        mManifestPlaceholders.putAll(manifestPlaceholders)
    }

    fun setManifestPlaceholders(manifestPlaceholders: Map<String, Any>): Void? {
        mManifestPlaceholders.clear()
        mManifestPlaceholders.putAll(manifestPlaceholders)
        return null
    }

    // Here to stop the gradle decorator breaking on the duplicate set methods.
    open fun manifestPlaceholders(manifestPlaceholders: Map<String, Any>) {
        setManifestPlaceholders(manifestPlaceholders)
    }

    open fun _initWith(that: BaseConfig) {
        buildConfigFields = that.buildConfigFields
        resValues = that.resValues as MutableMap<String, ClassField>
        applicationIdSuffix = that.applicationIdSuffix
        versionNameSuffix = that.versionNameSuffix
        mProguardFiles.clear()
        mProguardFiles.addAll(that.proguardFiles)
        mConsumerProguardFiles.clear()
        mConsumerProguardFiles.addAll(that.consumerProguardFiles)
        mTestProguardFiles.clear()
        mTestProguardFiles.addAll(that.testProguardFiles)
        mManifestPlaceholders.clear()
        mManifestPlaceholders.putAll(that.manifestPlaceholders)
        multiDexEnabled = that.multiDexEnabled
        multiDexKeepFile = that.multiDexKeepFile
        multiDexKeepProguard = that.multiDexKeepProguard
    }

    override fun toString(): String {
        return "BaseConfigImpl{" +
                "applicationIdSuffix=" + applicationIdSuffix +
                ", versionNameSuffix=" + versionNameSuffix +
                ", mBuildConfigFields=" + mBuildConfigFields +
                ", mResValues=" + mResValues +
                ", mProguardFiles=" + mProguardFiles +
                ", mConsumerProguardFiles=" + mConsumerProguardFiles +
                ", mManifestPlaceholders=" + mManifestPlaceholders +
                ", mMultiDexEnabled=" + multiDexEnabled +
                ", mMultiDexKeepFile=" + multiDexKeepFile +
                ", mMultiDexKeepProguard=" + multiDexKeepProguard +
                '}'
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
