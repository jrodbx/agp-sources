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
package com.android.ide.common.gradle.model

import com.android.builder.model.AndroidProject
import java.io.File
import java.io.Serializable

interface IdeAndroidProject : Serializable {
  /**
   * Returns the model version. This is a string in the format X.Y.Z
   *
   * @return a string containing the model version.
   */
  val modelVersion: String

  /**
   * Returns the model api version.
   *
   *
   * This is different from [.getModelVersion] in a way that new model version might
   * increment model version but keep existing api. That means that code which was built against
   * particular 'api version' might be safely re-used for all new model versions as long as they
   * don't change the api.
   *
   *
   * Every new model version is assumed to return an 'api version' value which is equal or
   * greater than the value used by the previous model version.
   *
   * @return model's api version
   */
  val apiVersion: Int

  /**
   * Returns the name of the module.
   *
   * @return the name of the module.
   */
  val name: String

  /**
   * Returns the type of project: Android application, library, feature, instantApp.
   *
   * @return the type of project.
   * @since 2.3
   */
  val projectType: Int

  /**
   * Returns the [IdeProductFlavorContainer] for the 'main' default config.
   *
   * @return the product flavor.
   */
  val defaultConfig: IdeProductFlavorContainer

  /**
   * Returns a list of all the [IdeBuildType] in their container.
   *
   * @return a list of build type containers.
   */
  val buildTypes: Collection<IdeBuildTypeContainer>

  /**
   * Returns a list of all the [IdeProductFlavor] in their container.
   *
   * @return a list of product flavor containers.
   */
  val productFlavors: Collection<IdeProductFlavorContainer>

  /**
   * Returns a list of all the variant names.
   *
   *
   * This does not include test variant. Test variants are additional artifacts in their
   * respective variant info.
   *
   * @return a list of all the variant names.
   * @since 3.2.
   */
  val variantNames: Collection<String>?

  /**
   * Returns a list of all the flavor dimensions, may be empty.
   *
   * @return a list of the flavor dimensions.
   */
  val flavorDimensions: Collection<String>

  /**
   * Returns the compilation target as a string. This is the full extended target hash string.
   * (see com.android.sdklib.IAndroidTarget#hashString())
   *
   * @return the target hash string
   */
  val compileTarget: String

  /**
   * Returns the boot classpath matching the compile target. This is typically android.jar plus
   * other optional libraries.
   *
   * @return a list of jar files.
   */
  val bootClasspath: Collection<String>

  /** Returns a list of [IdeSigningConfig].  */
  val signingConfigs: Collection<IdeSigningConfig>

  /** Returns the aapt options.  */
  val aaptOptions: IdeAaptOptions

  /** Returns the lint options.  */
  val lintOptions: IdeLintOptions

  /** Returns the compile options for Java code.  */
  val javaCompileOptions: IdeJavaCompileOptions

  /** Returns the build folder of this project.  */
  val buildFolder: File

  /**
   * Returns the resource prefix to use, if any. This is an optional prefix which can be set and
   * which is used by the defaults to automatically choose new resources with a certain prefix,
   * warn if resources are not using the given prefix, etc. This helps work with resources in the
   * app namespace where there could otherwise be unintentional duplicated resource names between
   * unrelated libraries.
   *
   * @return the optional resource prefix, or null if not set
   */
  val resourcePrefix: String?

  /**
   * Returns the build tools version used by this module.
   *
   * @return the build tools version.
   */
  val buildToolsVersion: String?

  /**
   * Returns the NDK version used by this module.
   *
   * @return the NDK version.
   */
  val ndkVersion: String?

  /**
   * Returns true if this is the base feature split.
   *
   * @return true if this is the base feature split
   * @since 2.4
   */
  val isBaseSplit: Boolean

  /**
   * Returns the list of dynamic features.
   *
   *
   * The values are Gradle path. Only valid for base splits.
   *
   * @return
   */
  val dynamicFeatures: Collection<String>
  val viewBindingOptions: IdeViewBindingOptions?
  val dependenciesInfo: IdeDependenciesInfo?

  /**
   * Returns the optional group-id of the artifact represented by this project.
   *
   * @since 3.6
   */
  val groupId: String?

  /** Various flags from AGP  */
  val agpFlags: IdeAndroidGradlePluginProjectFlags

  /**
   * Returns the minimal information of variants for this project, excluding test related
   * variants.
   *
   * @since 4.1
   */
  val variantsBuildInformation: Collection<IdeVariantBuildInformation>

  /**
   * Returns the lint jars that this module uses to run extra lint checks.
   *
   *
   * If null, the model does not contain the information because AGP was an older version, and
   * alternative ways to get the information should be used.
   */
  val lintRuleJars: List<File>?

  /**
   * Temporary storage of named data associated with this project. Intended for purposes such as
   * caching data associated with a project. A null value deletes the associated entry. Note that
   * the data is transient and will not be kept across sessions.
   */
  fun putClientProperty(key: String, value: Any?): Any?

  /**
   * Retrieves named data that was previously stored via [.putClientProperty].
   */
  fun getClientProperty(key: String): Any?

  companion object {
    const val PROJECT_TYPE_APP = 0
    const val PROJECT_TYPE_LIBRARY = 1
    const val PROJECT_TYPE_TEST = 2
    const val PROJECT_TYPE_ATOM = 3
    const val PROJECT_TYPE_INSTANTAPP = 4 // Instant App Bundle
    const val PROJECT_TYPE_FEATURE = 5 // com.android.feature module
    const val PROJECT_TYPE_DYNAMIC_FEATURE = 6 // com.android.dynamic-feature module
    const val ARTIFACT_MAIN = AndroidProject.ARTIFACT_MAIN
    const val ARTIFACT_ANDROID_TEST = AndroidProject.ARTIFACT_ANDROID_TEST
    const val ARTIFACT_UNIT_TEST = AndroidProject.ARTIFACT_UNIT_TEST
    const val FD_GENERATED = AndroidProject.FD_GENERATED
    const val FD_INTERMEDIATES = AndroidProject.FD_INTERMEDIATES
    const val MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD = AndroidProject.MODEL_LEVEL_3_VARIANT_OUTPUT_POST_BUILD
    const val PROPERTY_ANDROID_SUPPORT_VERSION = AndroidProject.PROPERTY_ANDROID_SUPPORT_VERSION
    const val PROPERTY_APK_LOCATION = AndroidProject.PROPERTY_APK_LOCATION
    const val PROPERTY_APK_SELECT_CONFIG = AndroidProject.PROPERTY_APK_SELECT_CONFIG
    const val PROPERTY_ATTRIBUTION_FILE_LOCATION = AndroidProject.PROPERTY_ATTRIBUTION_FILE_LOCATION
    const val PROPERTY_BUILD_ABI = AndroidProject.PROPERTY_BUILD_ABI
    const val PROPERTY_BUILD_API = AndroidProject.PROPERTY_BUILD_API
    const val PROPERTY_BUILD_API_CODENAME = AndroidProject.PROPERTY_BUILD_API_CODENAME
    const val PROPERTY_BUILD_DENSITY = AndroidProject.PROPERTY_BUILD_DENSITY
    const val PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD = AndroidProject.PROPERTY_BUILD_MODEL_DISABLE_SRC_DOWNLOAD
    const val PROPERTY_BUILD_MODEL_ONLY = AndroidProject.PROPERTY_BUILD_MODEL_ONLY
    const val PROPERTY_BUILD_MODEL_ONLY_ADVANCED = AndroidProject.PROPERTY_BUILD_MODEL_ONLY_ADVANCED
    const val PROPERTY_BUILD_MODEL_ONLY_VERSIONED = AndroidProject.PROPERTY_BUILD_MODEL_ONLY_VERSIONED
    const val PROPERTY_BUILD_WITH_STABLE_IDS = AndroidProject.PROPERTY_BUILD_WITH_STABLE_IDS
    const val PROPERTY_DEPLOY_AS_INSTANT_APP = AndroidProject.PROPERTY_DEPLOY_AS_INSTANT_APP
    const val PROPERTY_EXTRACT_INSTANT_APK = AndroidProject.PROPERTY_EXTRACT_INSTANT_APK
    const val PROPERTY_GENERATE_SOURCES_ONLY = AndroidProject.PROPERTY_GENERATE_SOURCES_ONLY
    const val PROPERTY_INJECTED_DYNAMIC_MODULES_LIST = AndroidProject.PROPERTY_INJECTED_DYNAMIC_MODULES_LIST
    const val PROPERTY_INVOKED_FROM_IDE = AndroidProject.PROPERTY_INVOKED_FROM_IDE
    const val PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL = AndroidProject.PROPERTY_REFRESH_EXTERNAL_NATIVE_MODEL
    const val PROPERTY_SIGNING_KEY_ALIAS = AndroidProject.PROPERTY_SIGNING_KEY_ALIAS
    const val PROPERTY_SIGNING_KEY_PASSWORD = AndroidProject.PROPERTY_SIGNING_KEY_PASSWORD
    const val PROPERTY_SIGNING_STORE_FILE = AndroidProject.PROPERTY_SIGNING_STORE_FILE
    const val PROPERTY_SIGNING_STORE_PASSWORD = AndroidProject.PROPERTY_SIGNING_STORE_PASSWORD
    const val PROPERTY_SIGNING_V1_ENABLED = AndroidProject.PROPERTY_SIGNING_V1_ENABLED
    const val PROPERTY_SIGNING_V2_ENABLED = AndroidProject.PROPERTY_SIGNING_V2_ENABLED
  }
}