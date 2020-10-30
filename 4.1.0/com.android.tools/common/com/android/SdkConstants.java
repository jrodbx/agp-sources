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

package com.android;

import com.android.support.AndroidxName;
import java.io.File;

/**
 * Constant definition class.<br>
 * <br>
 * Most constants have a prefix defining the content.
 * <ul>
 * <li><code>OS_</code> OS path constant. These paths are different depending on the platform.</li>
 * <li><code>FN_</code> File name constant.</li>
 * <li><code>FD_</code> Folder name constant.</li>
 * <li><code>TAG_</code> XML element tag name</li>
 * <li><code>ATTR_</code> XML attribute name</li>
 * <li><code>VALUE_</code> XML attribute value</li>
 * <li><code>CLASS_</code> Class name</li>
 * <li><code>DOT_</code> File name extension, including the dot </li>
 * <li><code>EXT_</code> File name extension, without the dot </li>
 * </ul>
 */
@SuppressWarnings({"javadoc", "unused"}) // Not documenting all the fields here
public final class SdkConstants {
    public static final int PLATFORM_UNKNOWN = 0;
    public static final int PLATFORM_LINUX = 1;
    public static final int PLATFORM_WINDOWS = 2;
    public static final int PLATFORM_DARWIN = 3;

    /**
     * Returns current platform, one of {@link #PLATFORM_WINDOWS}, {@link #PLATFORM_DARWIN}, {@link
     * #PLATFORM_LINUX} or {@link #PLATFORM_UNKNOWN}.
     */
    public static final int CURRENT_PLATFORM = currentPlatform();

    /**
     * ANDROID_HOME environment variable that specifies the installation path of an Android SDK.
     *
     * @deprecated Use {@link #ANDROID_SDK_ROOT_ENV} instead.
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     *     environment variables</a>
     */
    public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

    /**
     * ANDROID_SDK_ROOT environment variable that specifies the installation path of an Android SDK.
     *
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     *     environment variables</a>
     */
    public static final String ANDROID_SDK_ROOT_ENV = "ANDROID_SDK_ROOT";

    /** Property in local.properties file that specifies the path of the Android SDK. */
    public static final String SDK_DIR_PROPERTY = "sdk.dir";

    /** Property in local.properties file that specifies the path of the Android NDK. */
    public static final String NDK_DIR_PROPERTY = "ndk.dir";

    /** Property in local.properties file that specifies the path of CMake. */
    public static final String CMAKE_DIR_PROPERTY = "cmake.dir";

    /** Property in local.properties file that specifies the path to symlink the NDK under. */
    public static final String NDK_SYMLINK_DIR = "ndk.symlinkdir";

    /**
     * Property in gradle-wrapper.properties file that specifies the URL to the correct Gradle
     * distribution.
     */
    public static final String GRADLE_DISTRIBUTION_URL_PROPERTY = "distributionUrl"; //$NON-NLS-1$

    /** Properties in aar-metadata.properties file */
    public static final String AAR_FORMAT_VERSION_PROPERTY = "aarFormatVersion";
    public static final String AAR_METADATA_VERSION_PROPERTY = "aarMetadataVersion";
    public static final String MIN_COMPILE_SDK_PROPERTY = "minCompileSdk";

    /**
     * The encoding we strive to use for all files we write.
     *
     * <p>When possible, use the APIs which take a {@link java.nio.charset.Charset} and pass in
     * {@link com.google.common.base.Charsets#UTF_8} instead of using the String encoding method.
     */
    public static final String UTF_8 = "UTF-8"; //$NON-NLS-1$

    /** Charset for the ini file handled by the SDK. */
    public static final String INI_CHARSET = UTF_8;

    /** Path separator used by Gradle */
    public static final String GRADLE_PATH_SEPARATOR = ":"; //$NON-NLS-1$

    /** An SDK Project's AndroidManifest.xml file */
    public static final String FN_ANDROID_MANIFEST_XML = "AndroidManifest.xml"; //$NON-NLS-1$

    public static final String FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML =
            "SharedLibraryAndroidManifest.xml"; // $NON-NLS-1$
    /** pre-dex jar filename. i.e. "classes.jar" */
    public static final String FN_CLASSES_JAR = "classes.jar"; //$NON-NLS-1$
    /** api.jar filename */
    public static final String FN_API_JAR = "api.jar"; //$NON-NLS-1$
    /** Dex filename inside the APK. i.e. "classes.dex" */
    public static final String FN_APK_CLASSES_DEX = "classes.dex"; //$NON-NLS-1$
    /** Dex filename inside the APK. e.g. "classes2.dex" */
    public static final String FN_APK_CLASSES_N_DEX = "classes%d.dex"; //$NON-NLS-1$
    /** Regex to match dex filenames inside the APK. e.g., classes.dex, classes2.dex */
    public static final String REGEX_APK_CLASSES_DEX = "classes\\d*\\.dex"; //$NON-NLS-1$

    /** intermediate publishing between projects */
    public static final String FN_INTERMEDIATE_RES_JAR = "res.jar";                   //$NON-NLS-1$
    public static final String FN_INTERMEDIATE_FULL_JAR = "full.jar"; //$NON-NLS-1$

    /** list of splits for a variant */
    public static final String FN_APK_LIST = "apk-list.gson"; //$NON-NLS-1$

    /** An SDK Project's build.xml file */
    public static final String FN_BUILD_XML = "build.xml"; //$NON-NLS-1$
    /** An SDK Project's build.gradle file */
    public static final String FN_BUILD_GRADLE = "build.gradle"; //$NON-NLS-1$
    /** An SDK Project's build.gradle Kotlin script file */
    public static final String FN_BUILD_GRADLE_KTS = "build.gradle.kts"; //$NON-NLS-1$
    /** An SDK Project's settings.gradle file */
    public static final String FN_SETTINGS_GRADLE = "settings.gradle"; //$NON-NLS-1$
    /** An SDK Project's settings.gradle Kotlin script file */
    public static final String FN_SETTINGS_GRADLE_KTS = "settings.gradle.kts"; //$NON-NLS-1$
    /** An SDK Project's gradle.properties file */
    public static final String FN_GRADLE_PROPERTIES = "gradle.properties"; //$NON-NLS-1$
    /** An SDK Project's gradle daemon executable */
    public static final String FN_GRADLE_UNIX = "gradle"; //$NON-NLS-1$
    /** An SDK Project's gradle.bat daemon executable (gradle for windows) */
    public static final String FN_GRADLE_WIN = FN_GRADLE_UNIX + ".bat"; //$NON-NLS-1$
    /** An SDK Project's gradlew file */
    public static final String FN_GRADLE_WRAPPER_UNIX = "gradlew"; //$NON-NLS-1$
    /** An SDK Project's gradlew.bat file (gradlew for windows) */
    public static final String FN_GRADLE_WRAPPER_WIN =
            FN_GRADLE_WRAPPER_UNIX + ".bat"; //$NON-NLS-1$
    /** An SDK Project's gradle wrapper library */
    public static final String FN_GRADLE_WRAPPER_JAR = "gradle-wrapper.jar"; //$NON-NLS-1$
    /** Name of the framework library, i.e. "android.jar" */
    public static final String FN_FRAMEWORK_LIBRARY = "android.jar"; //$NON-NLS-1$
    /** Name of the framework library, i.e. "uiautomator.jar" */
    public static final String FN_UI_AUTOMATOR_LIBRARY = "uiautomator.jar"; //$NON-NLS-1$
    /** Name of the layout attributes, i.e. "attrs.xml" */
    public static final String FN_ATTRS_XML = "attrs.xml"; //$NON-NLS-1$
    /** Name of the layout attributes, i.e. "attrs_manifest.xml" */
    public static final String FN_ATTRS_MANIFEST_XML = "attrs_manifest.xml"; //$NON-NLS-1$
    /** framework aidl import file */
    public static final String FN_FRAMEWORK_AIDL = "framework.aidl"; //$NON-NLS-1$
    /** framework renderscript folder */
    public static final String FN_FRAMEWORK_RENDERSCRIPT = "renderscript"; //$NON-NLS-1$
    /** framework include folder */
    public static final String FN_FRAMEWORK_INCLUDE = "include"; //$NON-NLS-1$
    /** framework include (clang) folder */
    public static final String FN_FRAMEWORK_INCLUDE_CLANG = "clang-include"; //$NON-NLS-1$
    /** layoutlib.jar file */
    public static final String FN_LAYOUTLIB_JAR = "layoutlib.jar"; //$NON-NLS-1$
    /** widget list file */
    public static final String FN_WIDGETS = "widgets.txt"; //$NON-NLS-1$
    /** Intent activity actions list file */
    public static final String FN_INTENT_ACTIONS_ACTIVITY = "activity_actions.txt"; //$NON-NLS-1$
    /** Intent broadcast actions list file */
    public static final String FN_INTENT_ACTIONS_BROADCAST = "broadcast_actions.txt"; //$NON-NLS-1$
    /** Intent service actions list file */
    public static final String FN_INTENT_ACTIONS_SERVICE = "service_actions.txt"; //$NON-NLS-1$
    /** Intent category list file */
    public static final String FN_INTENT_CATEGORIES = "categories.txt"; //$NON-NLS-1$
    /** Name of the lint library, i.e. "lint.jar" */
    public static final String FN_LINT_JAR = "lint.jar"; //$NON-NLS-1$

    /** annotations support jar */
    public static final String FN_ANNOTATIONS_JAR = "annotations.jar"; //$NON-NLS-1$

    /** platform build property file */
    public static final String FN_BUILD_PROP = "build.prop"; //$NON-NLS-1$
    /** plugin properties file */
    public static final String FN_PLUGIN_PROP = "plugin.prop"; //$NON-NLS-1$
    /** add-on manifest file */
    public static final String FN_MANIFEST_INI = "manifest.ini"; //$NON-NLS-1$
    /** add-on layout device XML file. */
    public static final String FN_DEVICES_XML = "devices.xml"; //$NON-NLS-1$
    /** hardware properties definition file */
    public static final String FN_HARDWARE_INI = "hardware-properties.ini"; //$NON-NLS-1$

    /** project property file */
    public static final String FN_PROJECT_PROPERTIES = "project.properties"; //$NON-NLS-1$

    /** project local property file */
    public static final String FN_LOCAL_PROPERTIES = "local.properties"; //$NON-NLS-1$

    /** project ant property file */
    public static final String FN_ANT_PROPERTIES = "ant.properties"; //$NON-NLS-1$

    /** project local property file */
    public static final String FN_GRADLE_WRAPPER_PROPERTIES =
            "gradle-wrapper.properties"; //$NON-NLS-1$

    /** Skin layout file */
    public static final String FN_SKIN_LAYOUT = "layout"; //$NON-NLS-1$

    /** dx.jar file */
    public static final String FN_DX_JAR = "dx.jar"; //$NON-NLS-1$

    /** dx executable (with extension for the current OS) */
    public static final String FN_DX =
            "dx" + ext(".bat", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** aapt executable (with extension for the current OS) */
    public static final String FN_AAPT =
            "aapt" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** aapt2 executable (with extension for the current OS) */
    public static final String FN_AAPT2 =
            "aapt2" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** aidl executable (with extension for the current OS) */
    public static final String FN_AIDL =
            "aidl" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript executable (with extension for the current OS) */
    public static final String FN_RENDERSCRIPT =
            "llvm-rs-cc" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support exe (with extension for the current OS) */
    public static final String FN_BCC_COMPAT =
            "bcc_compat" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support linker for ARM (with extension for the current OS) */
    public static final String FN_LD_ARM =
            "arm-linux-androideabi-ld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support linker for ARM64 (with extension for the current OS) */
    public static final String FN_LD_ARM64 =
            "aarch64-linux-android-ld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support linker for X86 (with extension for the current OS) */
    public static final String FN_LD_X86 =
            "i686-linux-android-ld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support linker for X86_64 (with extension for the current OS) */
    public static final String FN_LD_X86_64 =
            "x86_64-linux-android-ld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** renderscript support linker for MIPS (with extension for the current OS) */
    public static final String FN_LD_MIPS =
            "mipsel-linux-android-ld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /**
     * 64 bit (host) renderscript support linker for all ABIs (with extension for the current OS)
     */
    public static final String FN_LLD =
            "lld" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** adb executable (with extension for the current OS) */
    public static final String FN_ADB =
            "adb" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** emulator executable for the current OS */
    public static final String FN_EMULATOR =
            "emulator" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** emulator-check executable for the current OS */
    public static final String FN_EMULATOR_CHECK =
            "emulator-check" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** zipalign executable (with extension for the current OS) */
    public static final String FN_ZIPALIGN =
            "zipalign" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** dexdump executable (with extension for the current OS) */
    public static final String FN_DEXDUMP =
            "dexdump" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** proguard executable (with extension for the current OS) */
    public static final String FN_PROGUARD =
            "proguard" + ext(".bat", ".sh"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** find_lock for Windows (with extension for the current OS) */
    public static final String FN_FIND_LOCK =
            "find_lock" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** hprof-conv executable (with extension for the current OS) */
    public static final String FN_HPROF_CONV =
            "hprof-conv" + ext(".exe", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /** jack.jar */
    public static final String FN_JACK = "jack.jar"; //$NON-NLS-1$
    /** jill.jar */
    public static final String FN_JILL = "jill.jar"; //$NON-NLS-1$
    /** code coverage plugin for jack */
    public static final String FN_JACK_COVERAGE_PLUGIN = "jack-coverage-plugin.jar"; //$NON-NLS-1$
    /** jack-jacoco-report.jar */
    public static final String FN_JACK_JACOCO_REPORTER = "jack-jacoco-reporter.jar"; //$NON-NLS-1$
    /** core-lambda-stubs.jar necessary for lambda compilation. */
    public static final String FN_CORE_LAMBDA_STUBS = "core-lambda-stubs.jar"; // $NON-NLS-1$

    /** split-select */
    public static final String FN_SPLIT_SELECT = "split-select" + ext(".exe", "");

    /** glslc */
    public static final String FD_SHADER_TOOLS = "shader-tools";

    public static final String FN_GLSLC = "glslc" + ext(".exe", "");

    /** properties file for SDK Updater packages */
    public static final String FN_SOURCE_PROP = "source.properties"; //$NON-NLS-1$
    /** properties file for content hash of installed packages */
    public static final String FN_CONTENT_HASH_PROP = "content_hash.properties"; //$NON-NLS-1$
    /** properties file for the SDK */
    public static final String FN_SDK_PROP = "sdk.properties"; //$NON-NLS-1$

    public static final String FN_ANDROIDX_RS_JAR = "androidx-rs.jar"; //$NON-NLS-1$
    public static final String FN_RENDERSCRIPT_V8_JAR = "renderscript-v8.jar"; //$NON-NLS-1$

    public static final String FN_ANDROIDX_RENDERSCRIPT_PACKAGE =
            "androidx.renderscript"; //$NON-NLS-1$
    public static final String FN_RENDERSCRIPT_V8_PACKAGE =
            "android.support.v8.renderscript"; //$NON-NLS-1$

    /** filename for gdbserver. */
    public static final String FN_GDBSERVER = "gdbserver"; //$NON-NLS-1$

    public static final String FN_GDB_SETUP = "gdb.setup"; //$NON-NLS-1$

    /** proguard config file in a bundle. */
    public static final String FN_PROGUARD_TXT = "proguard.txt"; //$NON-NLS-1$
    /** global Android proguard config file */
    public static final String FN_ANDROID_PROGUARD_FILE = "proguard-android.txt"; //$NON-NLS-1$
    /** global Android proguard config file with optimization enabled */
    public static final String FN_ANDROID_OPT_PROGUARD_FILE =
            "proguard-android-optimize.txt"; //$NON-NLS-1$
    /** default proguard config file with new file extension (for project specific stuff) */
    public static final String FN_PROJECT_PROGUARD_FILE = "proguard-project.txt"; //$NON-NLS-1$
    /** proguard rules generated by aapt */
    public static final String FN_AAPT_RULES = "aapt_rules.txt";
    /** merged proguard rules generated by aapt, from base and its features */
    public static final String FN_MERGED_AAPT_RULES = "merged_aapt_rules.txt";
    /** File holding a list of advanced features */
    public static final String FN_ADVANCED_FEATURES = "advancedFeatures.ini";
    /** File holding a list of advanced features when user is on canary channel */
    public static final String FN_ADVANCED_FEATURES_CANARY = "advancedFeaturesCanary.ini";
    /**
     * File contains a serialized AndroidGradlePluginAttributionData object to be deserialized and
     * used in the IDE build attribution.
     */
    public static final String FN_AGP_ATTRIBUTION_DATA = "androidGradlePluginAttributionData";

    /* Folder Names for Android Projects . */

    /** Resources folder name, i.e. "res". */
    public static final String FD_RESOURCES = "res"; //$NON-NLS-1$
    /** Assets folder name, i.e. "assets" */
    public static final String FD_ASSETS = "assets"; //$NON-NLS-1$
    /**
     * Default source folder name in an SDK project, i.e. "src".
     *
     * <p>Note: this is not the same as {@link #FD_PKG_SOURCES} which is an SDK sources folder for
     * packages.
     */
    public static final String FD_SOURCES = "src"; //$NON-NLS-1$
    /** Default main source set folder name, i.e. "main" */
    public static final String FD_MAIN = "main"; //$NON-NLS-1$
    /** Default test source set folder name, i.e. "androidTest" */
    public static final String FD_TEST = "androidTest"; //$NON-NLS-1$
    /** Default unit test source set folder name, i.e. "test" */
    public static final String FD_UNIT_TEST = "test"; //$NON-NLS-1$
    /** Default java code folder name, i.e. "java" */
    public static final String FD_JAVA = "java"; //$NON-NLS-1$
    /** Default native code folder name, i.e. "jni" */
    public static final String FD_JNI = "jni"; //$NON-NLS-1$
    /** Default gradle folder name, i.e. "gradle" */
    public static final String FD_GRADLE = "gradle"; //$NON-NLS-1$
    /** Default gradle wrapper folder name, i.e. "gradle/wrapper" */
    public static final String FD_GRADLE_WRAPPER =
            FD_GRADLE + File.separator + "wrapper"; //$NON-NLS-1$
    /** Default generated source folder name, i.e. "gen" */
    public static final String FD_GEN_SOURCES = "gen"; //$NON-NLS-1$
    /**
     * Default native library folder name inside the project, i.e. "libs" While the folder inside
     * the .apk is "lib", we call that one libs because that's what we use in ant for both .jar and
     * .so and we need to make the 2 development ways compatible.
     */
    public static final String FD_NATIVE_LIBS = "libs"; //$NON-NLS-1$
    /** Native lib folder inside the APK: "lib" */
    public static final String FD_APK_NATIVE_LIBS = "lib"; //$NON-NLS-1$
    /** Default output folder name, i.e. "bin" */
    public static final String FD_OUTPUT = "bin"; //$NON-NLS-1$
    /** Classes output folder name, i.e. "classes" */
    public static final String FD_CLASSES_OUTPUT = "classes"; //$NON-NLS-1$
    /** proguard output folder for mapping, etc.. files */
    public static final String FD_PROGUARD = "proguard"; //$NON-NLS-1$
    /** aidl output folder for copied aidl files */
    public static final String FD_AIDL = "aidl"; //$NON-NLS-1$
    /** aar libs folder */
    public static final String FD_AAR_LIBS = "libs"; //$NON-NLS-1$
    /** symbols output folder */
    public static final String FD_SYMBOLS = "symbols"; //$NON-NLS-1$
    /** resource blame output folder */
    public static final String FD_BLAME = "blame"; //$NON-NLS-1$
    /** Machine learning models folder. */
    public static final String FD_ML_MODELS = "ml"; // $NON-NLS-1$

    /** rs Libs output folder for support mode */
    public static final String FD_RS_LIBS = "rsLibs"; //$NON-NLS-1$
    /** rs Libs output folder for support mode */
    public static final String FD_RS_OBJ = "rsObj"; //$NON-NLS-1$

    /** jars folder */
    public static final String FD_JARS = "jars"; //$NON-NLS-1$

    /* Folder Names for the Android SDK */

    /** Name of the SDK platforms folder. */
    public static final String FD_PLATFORMS = "platforms"; //$NON-NLS-1$
    /** Name of the SDK addons folder. */
    public static final String FD_ADDONS = "add-ons"; //$NON-NLS-1$
    /** Name of the SDK system-images folder. */
    public static final String FD_SYSTEM_IMAGES = "system-images"; //$NON-NLS-1$
    /**
     * Name of the SDK sources folder where source packages are installed.
     *
     * <p>Note this is not the same as {@link #FD_SOURCES} which is the folder name where sources
     * are installed inside a project.
     */
    public static final String FD_PKG_SOURCES = "sources"; //$NON-NLS-1$
    /** Name of the legacy SDK tools folder. */
    public static final String FD_TOOLS = "tools"; //$NON-NLS-1$
    /** Name of the SDK command-line tools folder. */
    public static final String FD_CMDLINE_TOOLS = "cmdline-tools";
    /** Name of the SDK emulator folder. */
    public static final String FD_EMULATOR = "emulator"; //$NON-NLS-1$
    /** Name of the SDK tools/support folder. */
    public static final String FD_SUPPORT = "support"; //$NON-NLS-1$
    /** Name of the SDK platform tools folder. */
    public static final String FD_PLATFORM_TOOLS = "platform-tools"; //$NON-NLS-1$
    /** Name of the SDK build tools folder. */
    public static final String FD_BUILD_TOOLS = "build-tools"; //$NON-NLS-1$
    /** Name of the SDK tools/lib folder. */
    public static final String FD_LIB = "lib"; //$NON-NLS-1$
    /** Name of the SDK docs folder. */
    public static final String FD_DOCS = "docs"; //$NON-NLS-1$
    /** Name of the doc folder containing API reference doc (javadoc) */
    public static final String FD_DOCS_REFERENCE = "reference"; //$NON-NLS-1$
    /** Name of the SDK images folder. */
    public static final String FD_IMAGES = "images"; //$NON-NLS-1$
    /** Name of the ABI to support. */
    public static final String ABI_ARMEABI = "armeabi"; //$NON-NLS-1$

    public static final String ABI_ARMEABI_V7A = "armeabi-v7a"; //$NON-NLS-1$
    public static final String ABI_ARM64_V8A = "arm64-v8a"; //$NON-NLS-1$
    public static final String ABI_INTEL_ATOM = "x86"; //$NON-NLS-1$
    public static final String ABI_INTEL_ATOM64 = "x86_64"; //$NON-NLS-1$
    public static final String ABI_MIPS = "mips"; //$NON-NLS-1$
    public static final String ABI_MIPS64 = "mips64"; //$NON-NLS-1$
    /** Name of the CPU arch to support. */
    public static final String CPU_ARCH_ARM = "arm"; //$NON-NLS-1$

    public static final String CPU_ARCH_ARM64 = "arm64"; //$NON-NLS-1$
    public static final String CPU_ARCH_INTEL_ATOM = "x86"; //$NON-NLS-1$
    public static final String CPU_ARCH_INTEL_ATOM64 = "x86_64"; //$NON-NLS-1$
    public static final String CPU_ARCH_MIPS = "mips"; //$NON-NLS-1$
    /** TODO double-check this is appropriate value for mips64 */
    public static final String CPU_ARCH_MIPS64 = "mips64"; //$NON-NLS-1$
    /** Name of the CPU model to support. */
    public static final String CPU_MODEL_CORTEX_A8 = "cortex-a8"; //$NON-NLS-1$

    /** Name of the SDK skins folder. */
    public static final String FD_SKINS = "skins"; //$NON-NLS-1$
    /** Name of the SDK samples folder. */
    public static final String FD_SAMPLES = "samples"; //$NON-NLS-1$
    /** Name of the SDK extras folder. */
    public static final String FD_EXTRAS = "extras"; //$NON-NLS-1$

    public static final String FD_ANDROID_EXTRAS = "android"; //$NON-NLS-1$
    public static final String FD_M2_REPOSITORY = "m2repository"; //$NON-NLS-1$
    public static final String FD_NDK = "ndk-bundle"; //$NON-NLS-1$
    public static final String FD_LLDB = "lldb"; //$NON-NLS-1$
    public static final String FD_CMAKE = "cmake"; //$NON-NLS-1$
    public static final String FD_NDK_SIDE_BY_SIDE = "ndk"; //$NON-NLS-1$
    public static final String FD_GAPID = "gapid"; //$NON-NLS-1$
    /** Sample data for the project sample data */
    public static final String FD_SAMPLE_DATA = "sampledata";

    /**
     * Name of an extra's sample folder. Ideally extras should have one {@link #FD_SAMPLES} folder
     * containing one or more sub-folders (one per sample). However some older extras might contain
     * a single "sample" folder with directly the samples files in it. When possible we should
     * encourage extras' owners to move to the multi-samples format.
     */
    public static final String FD_SAMPLE = "sample"; //$NON-NLS-1$
    /** Name of the SDK templates folder, i.e. "templates" */
    public static final String FD_TEMPLATES = "templates"; //$NON-NLS-1$
    /** Name of the SDK Ant folder, i.e. "ant" */
    public static final String FD_ANT = "ant"; //$NON-NLS-1$
    /** Name of the SDK data folder, i.e. "data" */
    public static final String FD_DATA = "data"; //$NON-NLS-1$
    /** Name of the SDK renderscript folder, i.e. "rs" */
    public static final String FD_RENDERSCRIPT = "rs"; //$NON-NLS-1$
    /** Name of the Java resources folder, i.e. "resources" */
    public static final String FD_JAVA_RES = "resources"; //$NON-NLS-1$
    /** Name of the SDK resources folder, i.e. "res" */
    public static final String FD_RES = "res"; //$NON-NLS-1$
    /** Name of the SDK font folder, i.e. "fonts" */
    public static final String FD_FONTS = "fonts"; //$NON-NLS-1$
    /** Name of the android sources directory and the root of the SDK sources package folder. */
    public static final String FD_ANDROID_SOURCES = "sources"; //$NON-NLS-1$
    /** Name of the addon libs folder. */
    public static final String FD_ADDON_LIBS = "libs"; //$NON-NLS-1$
    /** Name of the merged resources folder. */
    public static final String FD_MERGED = "merged"; //$NON-NLS-1$
    /** Name of the compiled resources folder. */
    public static final String FD_COMPILED = "compiled"; //$NON-NLS-1$
    /** Name of the folder containing partial R files. */
    public static final String FD_PARTIAL_R = "partial-r"; //$NON-NLS-1$
    /** Name of the output dex folder. */
    public static final String FD_DEX = "dex"; //$NON-NLS-1$
    /** Name of the generated source folder. */
    public static final String FD_SOURCE_GEN = "source";
    /** Name of the generated R.class source folder */
    public static final String FD_RES_CLASS = "r";

    /** Name of the cache folder in the $HOME/.android. */
    public static final String FD_CACHE = "cache"; //$NON-NLS-1$

    /** Name of the build attribution internal output folder */
    public static final String FD_BUILD_ATTRIBUTION = "build-attribution"; //$NON-NLS-1$

    /** API codename of a release (non preview) system image or platform. */
    public static final String CODENAME_RELEASE = "REL"; //$NON-NLS-1$

    /**
     * Namespace pattern for the custom resource XML, i.e. "http://schemas.android.com/apk/res/%s"
     *
     * <p>This string contains a %s. It must be combined with the desired Java package, e.g.:
     *
     * <pre>
     *    String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, "android");
     *    String.format(SdkConstants.NS_CUSTOM_RESOURCES_S, "com.test.mycustomapp");
     * </pre>
     *
     * Note: if you need an URI specifically for the "android" namespace, consider using {@link
     * #ANDROID_URI} instead.
     */
    public static final String NS_CUSTOM_RESOURCES_S =
            "http://schemas.android.com/apk/res/%1$s"; //$NON-NLS-1$

    /** The name of the uses-library that provides "android.test.runner" */
    public static final String ANDROID_TEST_RUNNER_LIB = "android.test.runner"; //$NON-NLS-1$

    /* Folder path relative to the SDK root */
    /**
     * Path of the documentation directory relative to the sdk folder. This is an OS path, ending
     * with a separator.
     */
    public static final String OS_SDK_DOCS_FOLDER = FD_DOCS + File.separator;

    /**
     * Path of the platform tools directory relative to the sdk folder. This is an OS path, ending
     * with a separator.
     */
    public static final String OS_SDK_PLATFORM_TOOLS_FOLDER = FD_PLATFORM_TOOLS + File.separator;

    /* Folder paths relative to a platform or add-on folder */

    /**
     * Path of the images directory relative to a platform or addon folder. This is an OS path,
     * ending with a separator.
     */
    public static final String OS_IMAGES_FOLDER = FD_IMAGES + File.separator;

    /**
     * Path of the skin directory relative to a platform or addon folder. This is an OS path, ending
     * with a separator.
     */
    public static final String OS_SKINS_FOLDER = FD_SKINS + File.separator;

    /* Folder paths relative to a Platform folder */

    /**
     * Path of the data directory relative to a platform folder. This is an OS path, ending with a
     * separator.
     */
    public static final String OS_PLATFORM_DATA_FOLDER = FD_DATA + File.separator;

    /**
     * Path of the renderscript directory relative to a platform folder. This is an OS path, ending
     * with a separator.
     */
    public static final String OS_PLATFORM_RENDERSCRIPT_FOLDER = FD_RENDERSCRIPT + File.separator;

    /**
     * Path of the samples directory relative to a platform folder. This is an OS path, ending with
     * a separator.
     */
    public static final String OS_PLATFORM_SAMPLES_FOLDER = FD_SAMPLES + File.separator;

    /**
     * Path of the resources directory relative to a platform folder. This is an OS path, ending
     * with a separator.
     */
    public static final String OS_PLATFORM_RESOURCES_FOLDER =
            OS_PLATFORM_DATA_FOLDER + FD_RES + File.separator;

    /**
     * Path of the fonts directory relative to a platform folder. This is an OS path, ending with a
     * separator.
     */
    public static final String OS_PLATFORM_FONTS_FOLDER =
            OS_PLATFORM_DATA_FOLDER + FD_FONTS + File.separator;

    /**
     * Path of the android source directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    public static final String OS_PLATFORM_SOURCES_FOLDER = FD_ANDROID_SOURCES + File.separator;

    /**
     * Path of the android templates directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    public static final String OS_PLATFORM_TEMPLATES_FOLDER = FD_TEMPLATES + File.separator;

    /**
     * Path of the Ant build rules directory relative to a platform folder. This is an OS path,
     * ending with a separator.
     */
    public static final String OS_PLATFORM_ANT_FOLDER = FD_ANT + File.separator;

    /** Path of the attrs.xml file relative to a platform folder. */
    public static final String OS_PLATFORM_ATTRS_XML =
            OS_PLATFORM_RESOURCES_FOLDER
                    + SdkConstants.FD_RES_VALUES
                    + File.separator
                    + FN_ATTRS_XML;

    /** Path of the attrs_manifest.xml file relative to a platform folder. */
    public static final String OS_PLATFORM_ATTRS_MANIFEST_XML =
            OS_PLATFORM_RESOURCES_FOLDER
                    + SdkConstants.FD_RES_VALUES
                    + File.separator
                    + FN_ATTRS_MANIFEST_XML;

    /** Path of the layoutlib.jar file relative to a platform folder. */
    public static final String OS_PLATFORM_LAYOUTLIB_JAR =
            OS_PLATFORM_DATA_FOLDER + FN_LAYOUTLIB_JAR;

    /** Path of the renderscript include folder relative to a platform folder. */
    public static final String OS_FRAMEWORK_RS =
            FN_FRAMEWORK_RENDERSCRIPT + File.separator + FN_FRAMEWORK_INCLUDE;
    /** Path of the renderscript (clang) include folder relative to a platform folder. */
    public static final String OS_FRAMEWORK_RS_CLANG =
            FN_FRAMEWORK_RENDERSCRIPT + File.separator + FN_FRAMEWORK_INCLUDE_CLANG;

    /* Folder paths relative to a addon folder */

    /**
     * Path of the images directory relative to a folder folder. This is an OS path, ending with a
     * separator.
     */
    public static final String OS_ADDON_LIBS_FOLDER = FD_ADDON_LIBS + File.separator;

    /** Skin default */
    public static final String SKIN_DEFAULT = "default"; //$NON-NLS-1$

    /** SDK property: ant templates revision */
    public static final String PROP_SDK_ANT_TEMPLATES_REVISION =
            "sdk.ant.templates.revision"; //$NON-NLS-1$

    /** SDK property: default skin */
    public static final String PROP_SDK_DEFAULT_SKIN = "sdk.skin.default"; //$NON-NLS-1$

    /** LLDB SDK package major.minor revision compatible with the current version of Studio */
    public static final String LLDB_PINNED_REVISION = "3.1";

    /* Android Class Constants */
    public static final String CLASS_ACTIVITY = "android.app.Activity"; //$NON-NLS-1$
    public static final String CLASS_APPLICATION = "android.app.Application"; //$NON-NLS-1$
    public static final String CLASS_SERVICE = "android.app.Service"; //$NON-NLS-1$
    public static final String CLASS_BROADCASTRECEIVER =
            "android.content.BroadcastReceiver"; //$NON-NLS-1$
    public static final String CLASS_CONTENTPROVIDER =
            "android.content.ContentProvider"; //$NON-NLS-1$
    public static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet"; //$NON-NLS-1$
    public static final String CLASS_INSTRUMENTATION = "android.app.Instrumentation"; //$NON-NLS-1$
    public static final String CLASS_INSTRUMENTATION_RUNNER =
            "android.test.InstrumentationTestRunner"; //$NON-NLS-1$
    public static final String CLASS_BUNDLE = "android.os.Bundle"; //$NON-NLS-1$
    public static final String CLASS_R = "android.R"; //$NON-NLS-1$
    public static final String CLASS_R_PREFIX = CLASS_R + "."; //$NON-NLS-1$
    public static final String CLASS_MANIFEST = "android.Manifest";
    public static final String CLASS_MANIFEST_PERMISSION =
            "android.Manifest$permission"; //$NON-NLS-1$
    public static final String CLASS_INTENT = "android.content.Intent"; //$NON-NLS-1$
    public static final String CLASS_CONTEXT = "android.content.Context"; //$NON-NLS-1$
    public static final String CLASS_RESOURCES = "android.content.res.Resources"; //$NON-NLS-1$
    public static final String CLS_TYPED_ARRAY = "android.content.res.TypedArray"; //$NON-NLS-1$
    public static final String CLASS_VIEW = "android.view.View"; //$NON-NLS-1$
    public static final String CLASS_VIEWGROUP = "android.view.ViewGroup"; //$NON-NLS-1$
    public static final String CLASS_VIEWSTUB = "android.view.ViewStub"; //$NON-NLS-1$
    public static final String CLASS_NAME_LAYOUTPARAMS = "LayoutParams"; //$NON-NLS-1$
    public static final String CLASS_VIEWGROUP_LAYOUTPARAMS =
            CLASS_VIEWGROUP + "$" + CLASS_NAME_LAYOUTPARAMS; //$NON-NLS-1$
    public static final String CLASS_NAME_FRAMELAYOUT = "FrameLayout"; //$NON-NLS-1$
    public static final String CLASS_FRAMELAYOUT =
            "android.widget." + CLASS_NAME_FRAMELAYOUT; //$NON-NLS-1$
    public static final String CLASS_ADAPTER = "android.widget.Adapter";
    public static final String CLASS_PREFERENCE = "android.preference.Preference"; //$NON-NLS-1$
    public static final AndroidxName CLASS_PREFERENCE_ANDROIDX =
            AndroidxName.of("android.support.v7.preference.", "Preference");
    public static final String CLASS_NAME_PREFERENCE_SCREEN = "PreferenceScreen"; //$NON-NLS-1$
    public static final String CLASS_PREFERENCES =
            "android.preference." + CLASS_NAME_PREFERENCE_SCREEN; //$NON-NLS-1$
    public static final String CLASS_PREFERENCE_GROUP =
            "android.preference.PreferenceGroup"; //$NON-NLS-1$
    public static final AndroidxName CLASS_PREFERENCE_GROUP_ANDROIDX =
            AndroidxName.of("android.support.v7.preference.", "PreferenceGroup");
    public static final String CLASS_PARCELABLE = "android.os.Parcelable"; //$NON-NLS-1$
    public static final String CLASS_PARCEL = "android.os.Parcel"; //$NON-NLS-1$
    public static final String CLASS_FRAGMENT = "android.app.Fragment"; //$NON-NLS-1$
    public static final AndroidxName CLASS_V4_FRAGMENT =
            AndroidxName.of("android.support.v4.app.", "Fragment");

    public static final String CLASS_ACTION_PROVIDER = "android.view.ActionProvider";
    public static final String CLASS_V4_ACTION_PROVIDER = "android.support.v4.view.ActionProvider";
    public static final String CLASS_ANDROIDX_ACTION_PROVIDER = "androidx.core.view.ActionProvider";

    public static final String CLASS_BACKUP_AGENT = "android.app.backup.BackupAgent"; //$NON-NLS-1$
    /**
     * MockView is part of the layoutlib bridge and used to display classes that have no rendering
     * in the graphical layout editor.
     */
    public static final String CLASS_MOCK_VIEW =
            "com.android.layoutlib.bridge.MockView"; //$NON-NLS-1$

    public static final String CLASS_LAYOUT_INFLATER = "android.view.LayoutInflater"; //$NON-NLS-1$

    /* Android Support Class Constants */
    public static final AndroidxName CLASS_APP_COMPAT_ACTIVITY =
            AndroidxName.of("android.support.v7.app.", "AppCompatActivity");
    public static final AndroidxName CLASS_MEDIA_ROUTE_ACTION_PROVIDER =
            AndroidxName.of("android.support.v7.app.", "MediaRouteActionProvider");

    public static final AndroidxName CLASS_NESTED_SCROLL_VIEW =
            AndroidxName.of("android.support.v4.widget.", "NestedScrollView");
    public static final AndroidxName CLASS_VIEW_PAGER =
            AndroidxName.of("android.support.v4.view.", "ViewPager");
    public static final String CLASS_VIEW_PAGER2 = "androidx.viewpager2.widget.ViewPager2";
    public static final AndroidxName CLASS_DRAWER_LAYOUT =
            AndroidxName.of("android.support.v4.widget.", "DrawerLayout");

    public static final AndroidxName CLASS_GRID_LAYOUT_V7 =
            AndroidxName.of("android.support.v7.widget.", "GridLayout");
    public static final AndroidxName CLASS_TOOLBAR_V7 =
            AndroidxName.of("android.support.v7.widget.", "Toolbar");
    public static final AndroidxName CLASS_RECYCLER_VIEW_V7 =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView");
    public static final AndroidxName CLASS_RECYCLER_VIEW_ADAPTER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$Adapter");
    public static final AndroidxName CLASS_RECYCLER_VIEW_LAYOUT_MANAGER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$LayoutManager");
    public static final AndroidxName CLASS_RECYCLER_VIEW_VIEW_HOLDER =
            AndroidxName.of("android.support.v7.widget.", "RecyclerView$ViewHolder");

    public static final AndroidxName CLASS_CARD_VIEW =
            AndroidxName.of("android.support.v7.widget.", "CardView");
    public static final AndroidxName CLASS_ACTION_MENU_VIEW =
            AndroidxName.of("android.support.v7.widget.", "ActionMenuView");

    public static final AndroidxName CLASS_SUPPORT_PREFERENCE_SCREEN =
            AndroidxName.of("android.support.v7.preference.", "PreferenceScreen");

    public static final String CLASS_AD_VIEW = "com.google.android.gms.ads.AdView"; //$NON-NLS-1$
    public static final String CLASS_MAP_FRAGMENT =
            "com.google.android.gms.maps.MapFragment"; //$NON-NLS-1$
    public static final String CLASS_MAP_VIEW = "com.google.android.gms.maps.MapView"; //$NON-NLS-1$

    public static final AndroidxName CLASS_BROWSE_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "BrowseFragment");
    public static final AndroidxName CLASS_DETAILS_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "DetailsFragment");
    public static final AndroidxName CLASS_PLAYBACK_OVERLAY_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "PlaybackOverlayFragment");
    public static final AndroidxName CLASS_SEARCH_FRAGMENT =
            AndroidxName.of("android.support.v17.leanback.app.", "SearchFragment");

    public static final String CLASS_PERCENT_RELATIVE_LAYOUT =
            "android.support.percent.PercentRelativeLayout"; //$NON-NLS-1$
    public static final String CLASS_PERCENT_FRAME_LAYOUT =
            "android.support.percent.PercentFrameLayout"; //$NON-NLS-1$

    public static final AndroidxName MULTI_DEX_APPLICATION =
            AndroidxName.of("android.support.multidex.", "MultiDexApplication");

    /* Material Components */
    public static final AndroidxName CLASS_APP_BAR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "AppBarLayout");
    public static final AndroidxName CLASS_BOTTOM_NAVIGATION_VIEW =
            AndroidxName.of("android.support.design.widget.", "BottomNavigationView");
    public static final AndroidxName CLASS_COORDINATOR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "CoordinatorLayout");
    public static final AndroidxName CLASS_COLLAPSING_TOOLBAR_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "CollapsingToolbarLayout");
    public static final AndroidxName CLASS_FLOATING_ACTION_BUTTON =
            AndroidxName.of("android.support.design.widget.", "FloatingActionButton");
    public static final AndroidxName CLASS_NAVIGATION_VIEW =
            AndroidxName.of("android.support.design.widget.", "NavigationView");
    public static final AndroidxName CLASS_SNACKBAR =
            AndroidxName.of("android.support.design.widget.", "Snackbar");
    public static final AndroidxName CLASS_TAB_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "TabLayout");
    public static final AndroidxName CLASS_TAB_ITEM =
            AndroidxName.of("android.support.design.widget.", "TabItem");
    public static final AndroidxName CLASS_TEXT_INPUT_LAYOUT =
            AndroidxName.of("android.support.design.widget.", "TextInputLayout");
    public static final AndroidxName CLASS_TEXT_INPUT_EDIT_TEXT =
            AndroidxName.of("android.support.design.widget.", "TextInputEditText");
    public static final String CLASS_BOTTOM_APP_BAR =
            "com.google.android.material.bottomappbar.BottomAppBar";
    public static final String CLASS_CHIP = "com.google.android.material.chip.Chip";
    public static final String CLASS_CHIP_GROUP = "com.google.android.material.chip.ChipGroup";
    public static final String CLASS_MATERIAL_BUTTON =
            "com.google.android.material.button.MaterialButton";

    /* Android ConstraintLayout Constants */
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT =
            AndroidxName.of("android.support.constraint.", "ConstraintLayout");
    public static final AndroidxName CLASS_MOTION_LAYOUT =
            AndroidxName.of("android.support.constraint.motion.", "MotionLayout");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_HELPER =
            AndroidxName.of("android.support.constraint.", "ConstraintHelper");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_BARRIER =
            AndroidxName.of("android.support.constraint.", "Barrier");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GROUP =
            AndroidxName.of("android.support.constraint.", "Group");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CHAIN =
            AndroidxName.of("android.support.constraint.", "Chain");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_LAYER =
            AndroidxName.of("android.support.constraint.helper.", "Layer");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_FLOW =
      AndroidxName.of("android.support.constraint.helper.", "Flow");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_CONSTRAINTS =
            AndroidxName.of("android.support.constraint.", "Constraints");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_REFERENCE =
            AndroidxName.of("android.support.constraint.", "Reference");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_PARAMS =
            AndroidxName.of("android.support.constraint.", "ConstraintLayout$LayoutParams");
    public static final AndroidxName CLASS_TABLE_CONSTRAINT_LAYOUT =
            AndroidxName.of("android.support.constraint.", "TableConstraintLayout");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_GUIDELINE =
            AndroidxName.of("android.support.constraint.", "Guideline");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_MOCK_VIEW =
            AndroidxName.of("android.support.constraint.utils.", "MockView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_VIEW =
            AndroidxName.of("android.support.constraint.utils.", "ImageFilterView");
    public static final AndroidxName CLASS_CONSTRAINT_LAYOUT_IMAGE_FILTER_BUTTON =
            AndroidxName.of("android.support.constraint.utils.", "ImageFilterButton");

    // Flow Alignment values.
    public static class FlowAlignment {
        public static final String NONE = "none"; //$NON-NLS-1$
        public static final String START = "start"; //$NON-NLS-1$
        public static final String END = "end"; //$NON-NLS-1$
        public static final String TOP = "top"; //$NON-NLS-1$
        public static final String BOTTOM = "bottom"; //$NON-NLS-1$
        public static final String CENTER = "center"; //$NON-NLS-1$
        public static final String BASELINE = "baseline"; //$NON-NLS-1$
    }

    // Flow Style values.
    public static class FlowStyle {
        public static final String SPREAD = "spread"; //$NON-NLS-1$
        public static final String SPREAD_INSIDE = "spread_inside"; //$NON-NLS-1$
        public static final String PACKED = "packed"; //$NON-NLS-1$
    }

    public static final String CONSTRAINT_LAYOUT_LIB_GROUP_ID = "com.android.support.constraint";
    public static final String CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID = "constraint-layout";
    public static final String CONSTRAINT_LAYOUT_LIB_ARTIFACT =
            CONSTRAINT_LAYOUT_LIB_GROUP_ID + ":" + CONSTRAINT_LAYOUT_LIB_ARTIFACT_ID;
    /** Latest known version of the ConstraintLayout library (as a string) */
    public static final String LATEST_CONSTRAINT_LAYOUT_VERSION = "1.0.2";

    /* FlexboxLayout constants */
    public static final String CLASS_FLEXBOX_LAYOUT = "com.google.android.flexbox.FlexboxLayout";
    public static final String FLEXBOX_LAYOUT = CLASS_FLEXBOX_LAYOUT;
    public static final String FLEXBOX_LAYOUT_LIB_GROUP_ID = "com.google.android";
    public static final String FLEXBOX_LAYOUT_LIB_ARTIFACT_ID = "flexbox";
    public static final String FLEXBOX_LAYOUT_LIB_ARTIFACT =
            FLEXBOX_LAYOUT_LIB_GROUP_ID + ":" + FLEXBOX_LAYOUT_LIB_ARTIFACT_ID;
    public static final String LATEST_FLEXBOX_LAYOUT_VERSION = "0.2.3";

    public static final String CLASS_SIMPLE_EXO_PLAYER_VIEW =
            "com.google.android.exoplayer2.ui.SimpleExoPlayerView";
    public static final String CLASS_EXO_PLAYBACK_CONTROL_VIEW =
            "com.google.android.exoplayer2.ui.PlaybackControlView";
    public static final String SIMPLE_EXO_PLAYER_VIEW = CLASS_SIMPLE_EXO_PLAYER_VIEW;
    public static final String EXO_PLAYBACK_CONTROL_VIEW = CLASS_EXO_PLAYBACK_CONTROL_VIEW;
    public static final String EXO_PLAYER_GROUP_ID = "com.google.android.exoplayer";
    public static final String EXO_PLAYER_ARTIFACT_ID = "exoplayer";
    public static final String EXO_PLAYER_ARTIFACT =
            EXO_PLAYER_GROUP_ID + ":" + EXO_PLAYER_ARTIFACT_ID;

    /* Compose constants */
    public static final String CLASS_COMPOSE = "androidx.compose.Compose";
    public static final String CLASS_COMPOSE_VIEW_ADAPTER =
            "androidx.ui.tooling.preview.ComposeViewAdapter";
    public static final String CLASS_COMPOSE_INSPECTABLE = "androidx.ui.tooling.InspectableKt";

    /**
     * Returns the appropriate name for the 'mksdcard' command, which is 'mksdcard.exe' for Windows
     * and 'mksdcard' for all other platforms.
     */
    public static String mkSdCardCmdName() {
        String os = System.getProperty("os.name"); //$NON-NLS-1$
        String cmd = "mksdcard"; //$NON-NLS-1$
        if (os.startsWith("Windows")) { //$NON-NLS-1$
            cmd += ".exe"; //$NON-NLS-1$
        }
        return cmd;
    }

    /**
     * Returns current platform
     *
     * @return one of {@link #PLATFORM_WINDOWS}, {@link #PLATFORM_DARWIN}, {@link #PLATFORM_LINUX}
     *     or {@link #PLATFORM_UNKNOWN}.
     */
    public static int currentPlatform() {
        String os = System.getProperty("os.name"); //$NON-NLS-1$
        if (os.startsWith("Mac OS")) { //$NON-NLS-1$
            return PLATFORM_DARWIN;
        } else if (os.startsWith("Windows")) { //$NON-NLS-1$
            return PLATFORM_WINDOWS;
        } else if (os.startsWith("Linux")) { //$NON-NLS-1$
            return PLATFORM_LINUX;
        }

        return PLATFORM_UNKNOWN;
    }

    /**
     * Returns current platform's UI name
     *
     * @return one of "Windows", "Mac OS X", "Linux" or "other".
     */
    public static String currentPlatformName() {
        String os = System.getProperty("os.name"); //$NON-NLS-1$
        if (os.startsWith("Mac OS")) { //$NON-NLS-1$
            return "Mac OS X"; //$NON-NLS-1$
        } else if (os.startsWith("Windows")) { //$NON-NLS-1$
            return "Windows"; //$NON-NLS-1$
        } else if (os.startsWith("Linux")) { //$NON-NLS-1$527
            return "Linux"; //$NON-NLS-1$
        }

        return "Other";
    }

    private static String ext(String windowsExtension, String nonWindowsExtension) {
        if (CURRENT_PLATFORM == PLATFORM_WINDOWS) {
            return windowsExtension;
        } else {
            return nonWindowsExtension;
        }
    }

    /** Default anim resource folder name, i.e. "anim" */
    public static final String FD_RES_ANIM = "anim"; //$NON-NLS-1$
    /** Default animator resource folder name, i.e. "animator" */
    public static final String FD_RES_ANIMATOR = "animator"; //$NON-NLS-1$
    /** Default color resource folder name, i.e. "color" */
    public static final String FD_RES_COLOR = "color"; //$NON-NLS-1$
    /** Default drawable resource folder name, i.e. "drawable" */
    public static final String FD_RES_DRAWABLE = "drawable"; //$NON-NLS-1$
    /** Default interpolator resource folder name, i.e. "interpolator" */
    public static final String FD_RES_INTERPOLATOR = "interpolator"; //$NON-NLS-1$
    /** Default layout resource folder name, i.e. "layout" */
    public static final String FD_RES_LAYOUT = "layout"; //$NON-NLS-1$
    /** Default menu resource folder name, i.e. "menu" */
    public static final String FD_RES_MENU = "menu"; //$NON-NLS-1$
    /** Default mipmap resource folder name, i.e. "mipmap" */
    public static final String FD_RES_MIPMAP = "mipmap"; //$NON-NLS-1$
    /** Default navigation resource folder name, i.e. "navigation" */
    public static final String FD_RES_NAVIGATION = "navigation"; //$NON-NLS-1$
    /** Default values resource folder name, i.e. "values" */
    public static final String FD_RES_VALUES = "values"; //$NON-NLS-1$
    /** Default values resource folder name for the dark theme, i.e. "values-night" */
    public static final String FD_RES_VALUES_NIGHT = "values-night"; // $NON-NLS-1$
    /** Default xml resource folder name, i.e. "xml" */
    public static final String FD_RES_XML = "xml"; //$NON-NLS-1$
    /** Default raw resource folder name, i.e. "raw" */
    public static final String FD_RES_RAW = "raw"; //$NON-NLS-1$
    /** Base name for the resource package files */
    public static final String FN_RES_BASE = "resources"; //$NON-NLS-1$
    /** Separator between the resource folder qualifier. */
    public static final String RES_QUALIFIER_SEP = "-"; //$NON-NLS-1$

    // ---- XML ----

    /** URI of the reserved "xml" prefix. */
    public static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
    /** URI of the reserved "xmlns" prefix */
    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/"; //$NON-NLS-1$
    /** The "xmlns" attribute name */
    public static final String XMLNS = "xmlns"; //$NON-NLS-1$
    /** The default prefix used for the {@link #XMLNS_URI} */
    public static final String XMLNS_PREFIX = "xmlns:"; //$NON-NLS-1$
    /** Qualified name of the xmlns android declaration element */
    public static final String XMLNS_ANDROID = "xmlns:android"; //$NON-NLS-1$
    /** The default prefix used for the {@link #ANDROID_URI} name space */
    public static final String ANDROID_NS_NAME = "android"; //$NON-NLS-1$
    /** The default prefix used for the {@link #ANDROID_URI} name space including the colon */
    public static final String ANDROID_NS_NAME_PREFIX = "android:"; //$NON-NLS-1$

    public static final int ANDROID_NS_NAME_PREFIX_LEN = ANDROID_NS_NAME_PREFIX.length();
    /** The default prefix used for the {@link #TOOLS_URI} name space */
    public static final String TOOLS_NS_NAME = "tools"; //$NON-NLS-1$
    /** The default prefix used for the {@link #TOOLS_URI} name space including the colon */
    public static final String TOOLS_NS_NAME_PREFIX = "tools:"; //$NON-NLS-1$

    /** The default prefix used for the app */
    public static final String APP_PREFIX = "app"; //$NON-NLS-1$
    /** The entity for the ampersand character */
    public static final String AMP_ENTITY = "&amp;"; //$NON-NLS-1$
    /** The entity for the quote character */
    public static final String QUOT_ENTITY = "&quot;"; //$NON-NLS-1$
    /** The entity for the apostrophe character */
    public static final String APOS_ENTITY = "&apos;"; //$NON-NLS-1$
    /** The entity for the less than character */
    public static final String LT_ENTITY = "&lt;"; //$NON-NLS-1$
    /** The entity for the greater than character */
    public static final String GT_ENTITY = "&gt;"; //$NON-NLS-1$
    /** The entity for a newline */
    public static final String NEWLINE_ENTITY = "&#xA;"; //$NON-NLS-1$

    // ---- Elements and Attributes ----

    /** Namespace URI prefix used for all resources. */
    public static final String URI_DOMAIN_PREFIX = "http://schemas.android.com/";
    /** Namespace URI prefix used together with a package names. */
    public static final String URI_PREFIX = "http://schemas.android.com/apk/res/"; //$NON-NLS-1$
    /** Namespace used in XML files for Android attributes */
    public static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android"; //$NON-NLS-1$
    /** @deprecated Use {@link #ANDROID_URI}. */
    @Deprecated public static final String NS_RESOURCES = ANDROID_URI;
    /** Namespace used in XML files for Android Tooling attributes */
    public static final String TOOLS_URI = "http://schemas.android.com/tools"; //$NON-NLS-1$
    /** Namespace used for auto-adjusting namespaces */
    public static final String AUTO_URI = "http://schemas.android.com/apk/res-auto"; //$NON-NLS-1$
    /** Namespace used for specifying module distribution */
    public static final String DIST_URI = "http://schemas.android.com/apk/distribution";

    public static final String AAPT_URI = "http://schemas.android.com/aapt"; //$NON-NLS-1$
    /** Namespace for xliff in string resources. */
    public static final String XLIFF_URI = "urn:oasis:names:tc:xliff:document:1.2";
    /** Default prefix used for tools attributes */
    public static final String TOOLS_PREFIX = "tools"; //$NON-NLS-1$
    /** Default prefix used for xliff tags. */
    public static final String XLIFF_PREFIX = "xliff"; //$NON-NLS-1$
    /** Default prefix used for aapt attributes */
    public static final String AAPT_PREFIX = "aapt"; //$NON-NLS-1$
    /** Default prefix used for distribution attributes */
    public static final String DIST_PREFIX = "dist"; //$NON-NLS-1$

    public static final String R_CLASS = "R"; //$NON-NLS-1$
    public static final String ANDROID_PKG = "android"; //$NON-NLS-1$
    public static final String ANDROID_SUPPORT_PKG = "android.support";
    public static final String ANDROIDX_PKG = "androidx";
    public static final String MATERIAL2_PKG = "com.google.android.material";
    public static final String MATERIAL1_PKG = "android.support.design.widget";

    public static final String SHERPA_PREFIX = "app"; //$NON-NLS-1$
    public static final String SHERPA_URI = "http://schemas.android.com/apk/res-auto"; //$NON-NLS-1$

    /** Namespace for Instant App attributes in manifest files */

    // Tags: Manifest
    public static final String TAG_MANIFEST = "manifest";
    public static final String TAG_SERVICE = "service"; //$NON-NLS-1$
    public static final String TAG_PERMISSION = "permission"; //$NON-NLS-1$
    public static final String TAG_PERMISSION_GROUP = "permission-group"; //$NON-NLS-1$
    public static final String TAG_USES_FEATURE = "uses-feature"; //$NON-NLS-1$
    public static final String TAG_USES_PERMISSION = "uses-permission"; //$NON-NLS-1$
    public static final String TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23"; //$NON-NLS-1$
    public static final String TAG_USES_PERMISSION_SDK_M = "uses-permission-sdk-m"; //$NON-NLS-1$
    public static final String TAG_USES_LIBRARY = "uses-library"; //$NON-NLS-1$
    public static final String TAG_USES_SPLIT = "uses-split"; //$NON-NLS-1$
    public static final String TAG_APPLICATION = "application"; //$NON-NLS-1$
    public static final String TAG_INTENT_FILTER = "intent-filter"; //$NON-NLS-1$
    public static final String TAG_CATEGORY = "category"; //$NON-NLS-1$
    public static final String TAG_USES_SDK = "uses-sdk"; //$NON-NLS-1$
    public static final String TAG_ACTIVITY = "activity"; //$NON-NLS-1$
    public static final String TAG_ACTIVITY_ALIAS = "activity-alias"; //$NON-NLS-1$
    public static final String TAG_RECEIVER = "receiver"; //$NON-NLS-1$
    public static final String TAG_PACKAGE = "package"; //$NON-NLS-1$
    public static final String TAG_PROVIDER = "provider"; //$NON-NLS-1$
    public static final String TAG_GRANT_PERMISSION = "grant-uri-permission"; //$NON-NLS-1$
    public static final String TAG_PATH_PERMISSION = "path-permission"; //$NON-NLS-1$
    public static final String TAG_ACTION = "action"; //$NON-NLS-1$
    public static final String TAG_INSTRUMENTATION = "instrumentation";
    public static final String TAG_META_DATA = "meta-data";
    public static final String TAG_RESOURCE = "resource";
    public static final String TAG_MODULE = "module";
    public static final String TAG_NAV_GRAPH = "nav-graph";
    public static final String TAG_QUERIES = "queries";
    public static final String TAG_INTENT = "intent";

    // Tags: Resources
    public static final String TAG_RESOURCES = "resources"; //$NON-NLS-1$
    public static final String TAG_STRING = "string"; //$NON-NLS-1$
    public static final String TAG_ARRAY = "array"; //$NON-NLS-1$
    public static final String TAG_STYLE = "style"; //$NON-NLS-1$
    public static final String TAG_ITEM = "item"; //$NON-NLS-1$
    public static final String TAG_GROUP = "group"; //$NON-NLS-1$
    public static final String TAG_STRING_ARRAY = "string-array"; //$NON-NLS-1$
    public static final String TAG_PLURALS = "plurals"; //$NON-NLS-1$
    public static final String TAG_INTEGER_ARRAY = "integer-array"; //$NON-NLS-1$
    public static final String TAG_COLOR = "color"; //$NON-NLS-1$
    public static final String TAG_DIMEN = "dimen"; //$NON-NLS-1$
    public static final String TAG_DRAWABLE = "drawable"; //$NON-NLS-1$
    public static final String TAG_MENU = "menu"; //$NON-NLS-1$
    public static final String TAG_ENUM = "enum"; //$NON-NLS-1$
    public static final String TAG_FLAG = "flag"; //$NON-NLS-1$
    public static final String TAG_ATTR = "attr"; //$NON-NLS-1$
    public static final String TAG_DECLARE_STYLEABLE = "declare-styleable"; //$NON-NLS-1$
    public static final String TAG_EAT_COMMENT = "eat-comment"; //$NON-NLS-1$
    public static final String TAG_SKIP = "skip"; //$NON-NLS-1$
    public static final String TAG_PUBLIC = "public"; //$NON-NLS-1$
    public static final String TAG_PUBLIC_GROUP = "public-group";

    // Tags: Adaptive icon
    public static final String TAG_ADAPTIVE_ICON = "adaptive-icon";
    public static final String TAG_MASKABLE_ICON = "maskable-icon";

    // Font family tag
    public static final String TAG_FONT_FAMILY = "font-family";
    public static final String TAG_FONT = "font";

    // Tags: XML
    public static final String TAG_HEADER = "header"; //$NON-NLS-1$
    public static final String TAG_APPWIDGET_PROVIDER = "appwidget-provider"; //$NON-NLS-1$
    public static final String TAG_PREFERENCE_SCREEN = "PreferenceScreen"; //$NON-NLS-1$

    // Tags: Layouts
    public static final String VIEW_TAG = "view"; //$NON-NLS-1$
    public static final String VIEW_INCLUDE = "include"; //$NON-NLS-1$
    public static final String VIEW_MERGE = "merge"; //$NON-NLS-1$
    public static final String VIEW_FRAGMENT = "fragment"; //$NON-NLS-1$
    public static final String REQUEST_FOCUS = "requestFocus"; //$NON-NLS-1$
    public static final String TAG = "tag"; //$NON-NLS-1$

    // Tags: Navigation
    public static final String TAG_INCLUDE = "include";
    public static final String TAG_DEEP_LINK = "deepLink";
    public static final String TAG_NAVIGATION = "navigation";
    public static final String TAG_FRAGMENT = "fragment";
    public static final String ATTR_MODULE_NAME = "moduleName";

    public static final String VIEW = "View"; //$NON-NLS-1$
    public static final String VIEW_GROUP = "ViewGroup"; //$NON-NLS-1$
    public static final String FRAME_LAYOUT = "FrameLayout"; //$NON-NLS-1$
    public static final String LINEAR_LAYOUT = "LinearLayout"; //$NON-NLS-1$
    public static final String RELATIVE_LAYOUT = "RelativeLayout"; //$NON-NLS-1$
    public static final String GRID_LAYOUT = "GridLayout"; //$NON-NLS-1$
    public static final String SCROLL_VIEW = "ScrollView"; //$NON-NLS-1$
    public static final String BUTTON = "Button"; //$NON-NLS-1$
    public static final String COMPOUND_BUTTON = "CompoundButton"; //$NON-NLS-1$
    public static final String ADAPTER_VIEW = "AdapterView"; //$NON-NLS-1$
    public static final String STACK_VIEW = "StackView"; //$NON-NLS-1$
    public static final String GALLERY = "Gallery"; //$NON-NLS-1$
    public static final String GRID_VIEW = "GridView"; //$NON-NLS-1$
    public static final String TAB_HOST = "TabHost"; //$NON-NLS-1$
    public static final String RADIO_GROUP = "RadioGroup"; //$NON-NLS-1$
    public static final String RADIO_BUTTON = "RadioButton"; //$NON-NLS-1$
    public static final String SWITCH = "Switch"; //$NON-NLS-1$
    public static final String EDIT_TEXT = "EditText"; //$NON-NLS-1$
    public static final String LIST_VIEW = "ListView"; //$NON-NLS-1$
    public static final String TEXT_VIEW = "TextView"; //$NON-NLS-1$
    public static final String CHECKED_TEXT_VIEW = "CheckedTextView"; //$NON-NLS-1$
    public static final String IMAGE_VIEW = "ImageView"; //$NON-NLS-1$
    public static final String SURFACE_VIEW = "SurfaceView"; //$NON-NLS-1$
    public static final String ABSOLUTE_LAYOUT = "AbsoluteLayout"; //$NON-NLS-1$
    public static final String TABLE_LAYOUT = "TableLayout"; //$NON-NLS-1$
    public static final String TABLE_ROW = "TableRow"; //$NON-NLS-1$
    public static final String TAB_WIDGET = "TabWidget"; //$NON-NLS-1$
    public static final String IMAGE_BUTTON = "ImageButton"; //$NON-NLS-1$
    public static final String ZOOM_BUTTON = "ZoomButton"; //$NON-NLS-1$
    public static final String SEEK_BAR = "SeekBar"; //$NON-NLS-1$
    public static final String VIEW_STUB = "ViewStub"; //$NON-NLS-1$
    public static final String SPINNER = "Spinner"; //$NON-NLS-1$
    public static final String WEB_VIEW = "WebView"; //$NON-NLS-1$
    public static final String TOGGLE_BUTTON = "ToggleButton"; //$NON-NLS-1$
    public static final String CHECK_BOX = "CheckBox"; //$NON-NLS-1$
    public static final String ABS_LIST_VIEW = "AbsListView"; //$NON-NLS-1$
    public static final String PROGRESS_BAR = "ProgressBar"; //$NON-NLS-1$
    public static final String RATING_BAR = "RatingBar"; //$NON-NLS-1$
    public static final String ABS_SPINNER = "AbsSpinner"; //$NON-NLS-1$
    public static final String ABS_SEEK_BAR = "AbsSeekBar"; //$NON-NLS-1$
    public static final String VIEW_ANIMATOR = "ViewAnimator"; //$NON-NLS-1$
    public static final String VIEW_FLIPPER = "ViewFlipper"; //$NON-NLS-1$
    public static final String VIEW_SWITCHER = "ViewSwitcher"; //$NON-NLS-1$
    public static final String TEXT_SWITCHER = "TextSwitcher"; //$NON-NLS-1$
    public static final String IMAGE_SWITCHER = "ImageSwitcher"; //$NON-NLS-1$
    public static final String EXPANDABLE_LIST_VIEW = "ExpandableListView"; //$NON-NLS-1$
    public static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView"; //$NON-NLS-1$
    public static final String MULTI_AUTO_COMPLETE_TEXT_VIEW =
            "MultiAutoCompleteTextView"; //$NON-NLS-1$
    public static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView"; //$NON-NLS-1$
    public static final String CHECKABLE = "Checkable"; //$NON-NLS-1$
    public static final String TEXTURE_VIEW = "TextureView"; //$NON-NLS-1$
    public static final String DIALER_FILTER = "DialerFilter"; //$NON-NLS-1$
    public static final String ADAPTER_VIEW_FLIPPER = "AdapterViewFlipper"; //$NON-NLS-1$
    public static final String ADAPTER_VIEW_ANIMATOR = "AdapterViewAnimator"; //$NON-NLS-1$
    public static final String VIDEO_VIEW = "VideoView"; //$NON-NLS-1$
    public static final String SEARCH_VIEW = "SearchView"; //$NON-NLS-1$

    /* Android Support Tag Constants */
    public static final AndroidxName COORDINATOR_LAYOUT = CLASS_COORDINATOR_LAYOUT;
    public static final AndroidxName APP_BAR_LAYOUT = CLASS_APP_BAR_LAYOUT;
    public static final AndroidxName BOTTOM_NAVIGATION_VIEW = CLASS_BOTTOM_NAVIGATION_VIEW;
    public static final AndroidxName FLOATING_ACTION_BUTTON = CLASS_FLOATING_ACTION_BUTTON;
    public static final String CHIP = CLASS_CHIP;
    public static final String CHIP_GROUP = CLASS_CHIP_GROUP;
    public static final AndroidxName COLLAPSING_TOOLBAR_LAYOUT = CLASS_COLLAPSING_TOOLBAR_LAYOUT;
    public static final AndroidxName NAVIGATION_VIEW = CLASS_NAVIGATION_VIEW;
    public static final AndroidxName SNACKBAR = CLASS_SNACKBAR;
    public static final AndroidxName TAB_LAYOUT = CLASS_TAB_LAYOUT;
    public static final AndroidxName TAB_ITEM = CLASS_TAB_ITEM;
    public static final AndroidxName TEXT_INPUT_LAYOUT = CLASS_TEXT_INPUT_LAYOUT;
    public static final AndroidxName TEXT_INPUT_EDIT_TEXT = CLASS_TEXT_INPUT_EDIT_TEXT;
    public static final String BOTTOM_APP_BAR = CLASS_BOTTOM_APP_BAR;
    public static final String MATERIAL_BUTTON = CLASS_MATERIAL_BUTTON;
    public static final AndroidxName NESTED_SCROLL_VIEW = CLASS_NESTED_SCROLL_VIEW;
    public static final AndroidxName DRAWER_LAYOUT = CLASS_DRAWER_LAYOUT;
    public static final AndroidxName VIEW_PAGER = CLASS_VIEW_PAGER;
    public static final String VIEW_PAGER2 = CLASS_VIEW_PAGER2;
    public static final AndroidxName GRID_LAYOUT_V7 = CLASS_GRID_LAYOUT_V7;
    public static final AndroidxName TOOLBAR_V7 = CLASS_TOOLBAR_V7;
    public static final AndroidxName RECYCLER_VIEW = CLASS_RECYCLER_VIEW_V7;
    public static final AndroidxName CARD_VIEW = CLASS_CARD_VIEW;
    public static final AndroidxName ACTION_MENU_VIEW = CLASS_ACTION_MENU_VIEW;
    public static final String AD_VIEW = CLASS_AD_VIEW;
    public static final String MAP_FRAGMENT = CLASS_MAP_FRAGMENT;
    public static final String MAP_VIEW = CLASS_MAP_VIEW;
    public static final AndroidxName BROWSE_FRAGMENT = CLASS_BROWSE_FRAGMENT;
    public static final AndroidxName DETAILS_FRAGMENT = CLASS_DETAILS_FRAGMENT;
    public static final AndroidxName PLAYBACK_OVERLAY_FRAGMENT = CLASS_PLAYBACK_OVERLAY_FRAGMENT;
    public static final AndroidxName SEARCH_FRAGMENT = CLASS_SEARCH_FRAGMENT;

    /* Android ConstraintLayout Tag Constants */
    public static final AndroidxName CONSTRAINT_LAYOUT = CLASS_CONSTRAINT_LAYOUT;
    public static final AndroidxName MOTION_LAYOUT = CLASS_MOTION_LAYOUT;
    public static final AndroidxName TABLE_CONSTRAINT_LAYOUT = CLASS_TABLE_CONSTRAINT_LAYOUT;
    public static final AndroidxName CONSTRAINT_LAYOUT_GUIDELINE =
            CLASS_CONSTRAINT_LAYOUT_GUIDELINE;
    public static final AndroidxName CONSTRAINT_LAYOUT_BARRIER = CLASS_CONSTRAINT_LAYOUT_BARRIER;
    public static final String CONSTRAINT_BARRIER_TOP = "top";
    public static final String CONSTRAINT_BARRIER_BOTTOM = "bottom";
    public static final String CONSTRAINT_BARRIER_LEFT = "left";
    public static final String CONSTRAINT_BARRIER_RIGHT = "right";
    public static final String CONSTRAINT_BARRIER_START = "start";
    public static final String CONSTRAINT_BARRIER_END = "end";
    public static final String CONSTRAINT_REFERENCED_IDS = "constraint_referenced_ids";

    // Tags: Drawables
    public static final String TAG_ANIMATED_SELECTOR = "animated-selector";
    public static final String TAG_ANIMATED_VECTOR = "animated-vector"; //$NON-NLS-1$
    public static final String TAG_BITMAP = "bitmap"; //$NON-NLS-1$
    public static final String TAG_CLIP_PATH = "clip-path";
    public static final String TAG_GRADIENT = "gradient";
    public static final String TAG_INSET = "inset"; //$NON-NLS-1$
    public static final String TAG_LAYER_LIST = "layer-list"; //$NON-NLS-1$
    public static final String TAG_NINE_PATCH = "nine-patch";
    public static final String TAG_PATH = "path";
    public static final String TAG_RIPPLE = "ripple";
    public static final String TAG_ROTATE = "rotate";
    public static final String TAG_SHAPE = "shape";
    public static final String TAG_SELECTOR = "selector"; //$NON-NLS-1$
    public static final String TAG_TRANSITION = "transition"; //$NON-NLS-1$
    public static final String TAG_VECTOR = "vector"; //$NON-NLS-1$
    public static final String TAG_LEVEL_LIST = "level-list";

    // Tags: Data-Binding
    public static final String TAG_LAYOUT = "layout"; //$NON-NLS-1$
    public static final String TAG_DATA = "data"; //$NON-NLS-1$
    public static final String TAG_VARIABLE = "variable"; //$NON-NLS-1$
    public static final String TAG_IMPORT = "import"; //$NON-NLS-1$

    // Attributes: Manifest
    public static final String ATTR_EXPORTED = "exported"; //$NON-NLS-1$
    public static final String ATTR_PERMISSION = "permission"; //$NON-NLS-1$
    public static final String ATTR_PROCESS = "process"; //$NON-NLS-1$
    public static final String ATTR_MIN_SDK_VERSION = "minSdkVersion"; //$NON-NLS-1$
    public static final String ATTR_TARGET_SDK_VERSION = "targetSdkVersion"; //$NON-NLS-1$
    public static final String ATTR_ICON = "icon"; //$NON-NLS-1$
    public static final String ATTR_ROUND_ICON = "roundIcon"; //$NON-NLS-1$
    public static final String ATTR_PACKAGE = "package"; //$NON-NLS-1$
    public static final String ATTR_CORE_APP = "coreApp"; //$NON-NLS-1$
    public static final String ATTR_THEME = "theme"; //$NON-NLS-1$
    public static final String ATTR_SCHEME = "scheme"; //$NON_NLS-1$
    public static final String ATTR_MIME_TYPE = "mimeType"; //$NON_NLS-1$
    public static final String ATTR_HOST = "host"; //$NON_NLS-1$
    public static final String ATTR_PORT = "port"; //$NON_NLS-1$
    public static final String ATTR_PATH = "path"; //$NON-NLS-1$
    public static final String ATTR_PATH_PREFIX = "pathPrefix"; //$NON-NLS-1$
    public static final String ATTR_PATH_PATTERN = "pathPattern"; //$NON-NLS-1$
    public static final String ATTR_ALLOW_BACKUP = "allowBackup"; //$NON_NLS-1$
    public static final String ATTR_DEBUGGABLE = "debuggable"; //$NON-NLS-1$
    public static final String ATTR_READ_PERMISSION = "readPermission"; //$NON_NLS-1$
    public static final String ATTR_WRITE_PERMISSION = "writePermission"; //$NON_NLS-1$
    public static final String ATTR_VERSION_CODE = "versionCode"; //$NON_NLS-1$
    public static final String ATTR_VERSION_NAME = "versionName"; //$NON_NLS-1$
    public static final String ATTR_FULL_BACKUP_CONTENT = "fullBackupContent"; //$NON_NLS-1$
    public static final String ATTR_TEST_ONLY = "testOnly"; //$NON-NLS-1$
    public static final String ATTR_HAS_CODE = "hasCode"; //$NON-NLS-1$
    public static final String ATTR_AUTHORITIES = "authorities"; //$NON-NLS-1$
    public static final String ATTR_MULTIPROCESS = "multiprocess"; //$NON-NLS-1$
    public static final String ATTR_SPLIT = "split";
    public static final String ATTR_SHARED_USER_ID = "sharedUserId";
    public static final String ATTR_FUNCTIONAL_TEST = "functionalTest";
    public static final String ATTR_HANDLE_PROFILING = "handleProfiling";
    public static final String ATTR_TARGET_PACKAGE = "targetPackage";
    public static final String ATTR_EXTRACT_NATIVE_LIBS = "extractNativeLibs";
    public static final String ATTR_USE_EMBEDDED_DEX = "useEmbeddedDex";
    public static final String ATTR_SPLIT_NAME = "splitName";
    public static final String ATTR_FEATURE_SPLIT = "featureSplit";
    public static final String ATTR_TARGET_SANDBOX_VERSION = "targetSandboxVersion";
    public static final String ATTR_REQUIRED = "required";
    public static final String ATTR_ON_DEMAND = "onDemand";
    public static final String MANIFEST_ATTR_TITLE = "title";
    public static final String ATTR_TARGET_ACTIVITY = "targetActivity";

    // Attributes: Resources
    public static final String ATTR_ATTR = "attr";
    public static final String ATTR_NAME = "name"; //$NON-NLS-1$
    public static final String ATTR_FRAGMENT = "fragment"; //$NON-NLS-1$
    public static final String ATTR_TYPE = "type"; //$NON-NLS-1$
    public static final String ATTR_PARENT = "parent"; //$NON-NLS-1$
    public static final String ATTR_TRANSLATABLE = "translatable"; //$NON-NLS-1$
    public static final String ATTR_COLOR = "color"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE = "drawable"; //$NON-NLS-1$
    public static final String ATTR_VALUE = "value"; //$NON-NLS-1$
    public static final String ATTR_QUANTITY = "quantity"; //$NON-NLS-1$
    public static final String ATTR_FORMAT = "format"; //$NON-NLS-1$
    public static final String ATTR_PREPROCESSING = "preprocessing"; //$NON-NLS-1$

    // Attributes: Data Binding
    public static final String ATTR_ALIAS = "alias"; //$NON-NLS-1$

    // Attributes: View Binding
    public static final String ATTR_VIEW_BINDING_IGNORE = "viewBindingIgnore";

    // Attributes: Layout
    public static final String ATTR_LAYOUT_RESOURCE_PREFIX = "layout_"; //$NON-NLS-1$
    public static final String ATTR_CLASS = "class"; //$NON-NLS-1$
    public static final String ATTR_STYLE = "style"; //$NON-NLS-1$
    public static final String ATTR_CONTEXT = "context"; //$NON-NLS-1$
    public static final String ATTR_ID = "id"; //$NON-NLS-1$
    public static final String ATTR_AUTOFILL_HINTS = "autofillHints"; //$NON-NLS-1$
    public static final String ATTR_TEXT = "text"; //$NON-NLS-1$
    public static final String ATTR_TEXT_SIZE = "textSize"; //$NON-NLS-1$
    public static final String ATTR_ALPHA = "alpha"; //$NON-NLS-1$
    public static final String ATTR_LABEL = "label"; //$NON-NLS-1$
    public static final String ATTR_HINT = "hint"; //$NON-NLS-1$
    public static final String ATTR_PROMPT = "prompt"; //$NON-NLS-1$
    public static final String ATTR_ON_CLICK = "onClick"; //$NON-NLS-1$
    public static final String ATTR_INPUT_TYPE = "inputType"; //$NON-NLS-1$
    public static final String ATTR_INPUT_METHOD = "inputMethod"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GRAVITY = "layout_gravity"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH = "layout_width"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT = "layout_height"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WEIGHT = "layout_weight"; //$NON-NLS-1$
    public static final String ATTR_PADDING = "padding"; //$NON-NLS-1$
    public static final String ATTR_PADDING_BOTTOM = "paddingBottom"; //$NON-NLS-1$
    public static final String ATTR_PADDING_TOP = "paddingTop"; //$NON-NLS-1$
    public static final String ATTR_PADDING_RIGHT = "paddingRight"; //$NON-NLS-1$
    public static final String ATTR_PADDING_LEFT = "paddingLeft"; //$NON-NLS-1$
    public static final String ATTR_PADDING_START = "paddingStart"; //$NON-NLS-1$
    public static final String ATTR_PADDING_END = "paddingEnd"; //$NON-NLS-1$
    public static final String ATTR_FOREGROUND = "foreground"; //$NON-NLS-1$
    public static final String ATTR_BACKGROUND = "background"; //$NON-NLS-1$
    public static final String ATTR_ORIENTATION = "orientation"; //$NON-NLS-1$
    public static final String ATTR_BARRIER_DIRECTION = "barrierDirection"; //$NON-NLS-1$
    public static final String ATTR_BARRIER_ALLOWS_GONE_WIDGETS = "barrierAllowsGoneWidgets";  //$NON-NLS-1$
    public static final String ATTR_LAYOUT_OPTIMIZATION_LEVEL = "layout_optimizationLevel";  //$NON-NLS-1$
    public static final String ATTR_TRANSITION = "transition"; //$NON-NLS-1$
    public static final String ATTR_TRANSITION_SHOW_PATHS = "showPaths"; //$NON-NLS-1$
    public static final String ATTR_TRANSITION_STATE = "transitionState"; //$NON-NLS-1$
    public static final String ATTR_TRANSITION_POSITION = "transitionPosition"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT = "layout"; //$NON-NLS-1$
    public static final String ATTR_ROW_COUNT = "rowCount"; //$NON-NLS-1$
    public static final String ATTR_COLUMN_COUNT = "columnCount"; //$NON-NLS-1$
    public static final String ATTR_LABEL_FOR = "labelFor"; //$NON-NLS-1$
    public static final String ATTR_BASELINE_ALIGNED = "baselineAligned"; //$NON-NLS-1$
    public static final String ATTR_CONTENT_DESCRIPTION = "contentDescription"; //$NON-NLS-1$
    public static final String ATTR_IME_ACTION_LABEL = "imeActionLabel"; //$NON-NLS-1$
    public static final String ATTR_PRIVATE_IME_OPTIONS = "privateImeOptions"; //$NON-NLS-1$
    public static final String VALUE_NONE = "none"; //$NON-NLS-1$
    public static final String VALUE_NO = "no"; //$NON-NLS-1$
    public static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants"; //$NON-NLS-1$
    public static final String VALUE_YES = "yes"; //$NON-NLS-1$
    public static final String VALUE_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants"; //$NON-NLS-1$
    public static final String ATTR_NUMERIC = "numeric"; //$NON-NLS-1$
    public static final String ATTR_IME_ACTION_ID = "imeActionId"; //$NON-NLS-1$
    public static final String ATTR_IME_OPTIONS = "imeOptions"; //$NON-NLS-1$
    public static final String ATTR_FREEZES_TEXT = "freezesText"; //$NON-NLS-1$
    public static final String ATTR_EDITOR_EXTRAS = "editorExtras"; //$NON-NLS-1$
    public static final String ATTR_EDITABLE = "editable"; //$NON-NLS-1$
    public static final String ATTR_DIGITS = "digits"; //$NON-NLS-1$
    public static final String ATTR_CURSOR_VISIBLE = "cursorVisible"; //$NON-NLS-1$
    public static final String ATTR_CAPITALIZE = "capitalize"; //$NON-NLS-1$
    public static final String ATTR_PHONE_NUMBER = "phoneNumber"; //$NON-NLS-1$
    public static final String ATTR_PASSWORD = "password"; //$NON-NLS-1$
    public static final String ATTR_BUFFER_TYPE = "bufferType"; //$NON-NLS-1$
    public static final String ATTR_AUTO_TEXT = "autoText"; //$NON-NLS-1$
    public static final String ATTR_ENABLED = "enabled"; //$NON-NLS-1$
    public static final String ATTR_SINGLE_LINE = "singleLine"; //$NON-NLS-1$
    public static final String ATTR_SELECT_ALL_ON_FOCUS = "selectAllOnFocus"; //$NON-NLS-1$
    public static final String ATTR_SCALE_TYPE = "scaleType"; //$NON-NLS-1$
    public static final String ATTR_VISIBILITY = "visibility"; //$NON-NLS-1$
    public static final String ATTR_TEXT_IS_SELECTABLE = "textIsSelectable"; //$NON-NLS-1$
    public static final String ATTR_IMPORTANT_FOR_AUTOFILL =
            "importantForAutofill"; //$NON-NLS-1$
    public static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY =
            "importantForAccessibility"; //$NON-NLS-1$
    public static final String ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE =
            "accessibilityTraversalBefore"; //$NON-NLS-1$
    public static final String ATTR_ACCESSIBILITY_TRAVERSAL_AFTER =
            "accessibilityTraversalAfter"; //$NON-NLS-1$
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_LEFT =
            "listPreferredItemPaddingLeft"; //$NON-NLS-1$
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_RIGHT =
            "listPreferredItemPaddingRight"; //$NON-NLS-1$
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_START =
            "listPreferredItemPaddingStart"; //$NON-NLS-1$
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_END =
            "listPreferredItemPaddingEnd"; //$NON-NLS-1$
    public static final String ATTR_INDEX = "index"; //$NON-NLS-1$
    public static final String ATTR_ACTION_BAR_NAV_MODE = "actionBarNavMode"; //$NON-NLS-1$
    public static final String ATTR_MENU = "menu"; //$NON-NLS-1$
    public static final String ATTR_OPEN_DRAWER = "openDrawer"; //$NON-NLS-1$
    public static final String ATTR_SHOW_IN = "showIn"; //$NON-NLS-1$
    public static final String ATTR_PARENT_TAG = "parentTag"; //$NON-NLS-1$
    public static final String ATTR_WIDTH = "width"; //$NON-NLS-1$
    public static final String ATTR_HEIGHT = "height"; //$NON-NLS-1$
    public static final String ATTR_NAV_GRAPH = "navGraph";
    public static final String ATTR_USE_TAG = "useTag";

    // ConstraintLayout Flow
    public static final String ATTR_FLOW_WRAP_MODE = "flow_wrapMode";  //$NON-NLS-1$
    public static final String ATTR_FLOW_MAX_ELEMENTS_WRAP = "flow_maxElementsWrap";  //$NON-NLS-1$
    public static final String ATTR_FLOW_FIRST_HORIZONTAL_BIAS = "flow_firstHorizontalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_FIRST_HORIZONTAL_STYLE = "flow_firstHorizontalStyle";  //$NON-NLS-1$
    public static final String ATTR_FLOW_HORIZONTAL_BIAS = "flow_horizontalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_HORIZONTAL_STYLE = "flow_horizontalStyle";  //$NON-NLS-1$
    public static final String ATTR_FLOW_HORIZONTAL_ALIGN = "flow_horizontalAlign";  //$NON-NLS-1$
    public static final String ATTR_FLOW_HORIZONTAL_GAP = "flow_horizontalGap";  //$NON-NLS-1$
    public static final String ATTR_FLOW_LAST_HORIZONTAL_BIAS = "flow_lastHorizontalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_LAST_HORIZONTAL_STYLE = "flow_lastHorizontalStyle";  //$NON-NLS-1$
    public static final String ATTR_FLOW_FIRST_VERTICAL_BIAS = "flow_firstVerticalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_FIRST_VERTICAL_STYLE = "flow_firstVerticalStyle";  //$NON-NLS-1$
    public static final String ATTR_FLOW_VERTICAL_BIAS = "flow_verticalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_VERTICAL_STYLE = "flow_verticalStyle";  //$NON-NLS-1$
    public static final String ATTR_FLOW_VERTICAL_ALIGN = "flow_verticalAlign";  //$NON-NLS-1$
    public static final String ATTR_FLOW_VERTICAL_GAP = "flow_verticalGap";  //$NON-NLS-1$
    public static final String ATTR_FLOW_LAST_VERTICAL_BIAS = "flow_lastVerticalBias";  //$NON-NLS-1$
    public static final String ATTR_FLOW_LAST_VERTICAL_STYLE = "flow_lastVerticalStyle";  //$NON-NLS-1$

    // Attributes: Drawable
    public static final String ATTR_VIEWPORT_HEIGHT = "viewportHeight";
    public static final String ATTR_VIEWPORT_WIDTH = "viewportWidth";
    public static final String ATTR_PATH_DATA = "pathData";
    public static final String ATTR_FILL_COLOR = "fillColor";

    // Attributes: Gradients
    public static final String ATTR_END_X = "endX";
    public static final String ATTR_END_Y = "endY";
    public static final String ATTR_START_X = "startX";
    public static final String ATTR_START_Y = "startY";
    public static final String ATTR_CENTER_X = "centerX";
    public static final String ATTR_CENTER_Y = "centerY";
    public static final String ATTR_GRADIENT_RADIUS = "gradientRadius";
    public static final String ATTR_STOP_COLOR = "color";
    public static final String ATTR_STOP_OFFSET = "offset";

    // Attributes: Navigation
    public static final String ATTR_GRAPH = "graph";
    public static final String ATTR_URI = "uri";
    public static final String ATTR_AUTO_VERIFY = "autoVerify";
    public static final String ATTR_DEFAULT_NAV_HOST = "defaultNavHost";
    public static final String ATTR_START_DESTINATION = "startDestination";
    public static final String ATTR_NULLABLE = "nullable";
    public static final String ATTR_ARG_TYPE = "argType";
    public static final String ATTR_DEEPLINK_ACTION = "action";
    public static final String ATTR_DEEPLINK_MIMETYPE = "mimeType";

    // android.view.View
    public static final String ATTR_NEXT_CLUSTER_FORWARD = "nextClusterForward";
    public static final String ATTR_NEXT_FOCUS_DOWN = "nextFocusDown";
    public static final String ATTR_NEXT_FOCUS_FORWARD = "nextFocusForward";
    public static final String ATTR_NEXT_FOCUS_LEFT = "nextFocusLeft";
    public static final String ATTR_NEXT_FOCUS_RIGHT = "nextFocusRight";
    public static final String ATTR_NEXT_FOCUS_UP = "nextFocusUp";
    public static final String ATTR_SCROLLBAR_THUMB_HORIZONTAL = "scrollbarThumbHorizontal";
    public static final String ATTR_SCROLLBAR_THUMB_VERTICAL = "scrollbarThumbVertical";
    public static final String ATTR_SCROLLBAR_TRACK_HORIZONTAL = "scrollbarTrackHorizontal";
    public static final String ATTR_SCROLLBAR_TRACK_VERTICAL = "scrollbarTrackVertical";

    // android.view.ViewGroup
    public static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical"; //$NON-NLS-1$
    public static final String ATTR_PADDING_HORIZONTAL = "layout_paddingHorizontal"; //$NON-NLS-1$
    public static final String ATTR_PADDING_VERTICAL = "layout_paddingVertical"; //$NON-NLS-1$

    // AutoCompleteTextView
    public static final String ATTR_DROP_DOWN_ANCHOR = "dropDownAnchor";

    // ProgressBar
    public static final String ATTR_INTERPOLATOR = "interpolator";

    // AppCompatSeekBar
    public static final String ATTR_TICK_MARK = "tickMark";

    // AbsSeekBar
    public static final String ATTR_TICK_MARK_TINT = "tickMarkTint";

    // Toolbar
    public static final String ATTR_COLLAPSE_ICON = "collapseIcon";
    public static final String ATTR_LOGO = "logo";
    public static final String ATTR_TITLE_TEXT_COLOR = "titleTextColor";
    public static final String ATTR_SUBTITLE_TEXT_COLOR = "subtitleTextColor";

    // ViewAnimator
    public static final String ATTR_IN_ANIMATION = "inAnimation";
    public static final String ATTR_OUT_ANIMATION = "outAnimation";

    // TabWidget
    public static final String ATTR_TAB_STRIP_LEFT = "tabStripLeft";
    public static final String ATTR_TAB_STRIP_RIGHT = "tabStripRight";

    // DatePicker
    public static final String ATTR_CALENDAR_TEXT_COLOR = "calendarTextColor";
    public static final String ATTR_DAY_OF_WEEK_BACKGROUND = "dayOfWeekBackground";
    public static final String ATTR_YEAR_LIST_SELECTOR_COLOR = "yearListSelectorColor";
    public static final String ATTR_HEADER_BACKGROUND = "headerBackground";

    // TimePicker
    public static final String ATTR_AM_PM_BACKGROUND_COLOR = "amPmBackgroundColor";
    public static final String ATTR_AM_PM_TEXT_COLOR = "amPmTextColor";
    public static final String ATTR_NUMBERS_INNER_TEXT_COLOR = "numbersInnerTextColor";
    public static final String ATTR_NUMBERS_SELECTOR_COLOR = "numbersSelectorColor";
    public static final String ATTR_NUMBERS_TEXT_COLOR = "numbersTextColor";
    public static final String ATTR_NUMBERS_BACKGROUND_COLOR = "numbersBackgroundColor";

    // RelativeLayout
    public static final String ATTR_IGNORE_GRAVITY = "ignoreGravity";

    // AnalogClock
    public static final String ATTR_DIAL = "dial";
    public static final String ATTR_HAND_HOUR = "hand_hour";
    public static final String ATTR_HAND_MINUTE = "hand_minute";

    // CalendarView
    public static final String ATTR_SELECTED_DATE_VERTICAL_BAR = "selectedDateVerticalBar";

    // TextView attributes
    public static final String ATTR_TEXT_APPEARANCE = "textAppearance"; //$NON-NLS-1$
    public static final String ATTR_FONT_FAMILY = "fontFamily"; //$NON-NLS-1$
    public static final String ATTR_TYPEFACE = "typeface"; //$NON-NLS-1$
    public static final String ATTR_LINE_SPACING_EXTRA = "lineSpacingExtra"; //$NON-NLS-1$
    public static final String ATTR_TEXT_STYLE = "textStyle"; //$NON-NLS-1$
    public static final String ATTR_TEXT_ALIGNMENT = "textAlignment"; //$NON-NLS-1$
    public static final String ATTR_TEXT_COLOR = "textColor"; //$NON-NLS-1$
    public static final String ATTR_TEXT_COLOR_HINT = "textColorHint"; //$NON-NLS-1$
    public static final String ATTR_TEXT_COLOR_LINK = "textColorLink"; //$NON-NLS-1$
    public static final String ATTR_TEXT_ALL_CAPS = "textAllCaps"; //$NON-NLS-1$
    public static final String ATTR_SHADOW_COLOR = "shadowColor";
    public static final String ATTR_TEXT_COLOR_HIGHLIGHT = "textColorHighlight";
    public static final String ATTR_AUTO_SIZE_PRESET_SIZES = "autoSizePresetSizes";

    // Tools attributes for AdapterView inheritors
    public static final String ATTR_LISTFOOTER = "listfooter"; //$NON-NLS-1$
    public static final String ATTR_LISTHEADER = "listheader"; //$NON-NLS-1$
    public static final String ATTR_LISTITEM = "listitem"; //$NON-NLS-1$
    public static final String ATTR_ITEM_COUNT = "itemCount";

    // Tools attributes for scrolling
    public static final String ATTR_SCROLLX = "scrollX"; //$NON-NLS-1$
    public static final String ATTR_SCROLLY = "scrollY"; //$NON-NLS-1$

    // Tools attribute for using a different view at design time
    public static final String ATTR_USE_HANDLER = "useHandler";

    // AbsoluteLayout layout params
    public static final String ATTR_LAYOUT_Y = "layout_y"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_X = "layout_x"; //$NON-NLS-1$

    // GridLayout layout params
    public static final String ATTR_LAYOUT_ROW = "layout_row"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN = "layout_column"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan"; //$NON-NLS-1$

    // ProgressBar/RatingBar attributes
    public static final String ATTR_MAXIMUM = "max"; //$NON-NLS-1$
    public static final String ATTR_PROGRESS = "progress"; //$NON-NLS-1$
    public static final String ATTR_PROGRESS_DRAWABLE = "progressDrawable"; //$NON-NLS-1$
    public static final String ATTR_PROGRESS_TINT = "progressTint"; //$NON-NLS-1$
    public static final String ATTR_PROGRESS_BACKGROUND_TINT =
            "progressBackgroundTint"; //$NON-NLS-1$
    public static final String ATTR_SECONDARY_PROGRESS_TINT = "secondaryProgressTint"; //$NON-NLS-1$
    public static final String ATTR_INDETERMINATE = "indeterminate"; //$NON-NLS-1$
    public static final String ATTR_INDETERMINATE_DRAWABLE = "indeterminateDrawable"; //$NON-NLS-1$
    public static final String ATTR_INDETERMINATE_TINT = "indeterminateTint"; //$NON-NLS-1$
    public static final String ATTR_RATING = "rating"; //$NON-NLS-1$
    public static final String ATTR_NUM_STARS = "numStars"; //$NON-NLS-1$
    public static final String ATTR_STEP_SIZE = "stepSize"; //$NON-NLS-1$
    public static final String ATTR_IS_INDICATOR = "isIndicator"; //$NON-NLS-1$
    public static final String ATTR_THUMB = "thumb"; //$NON-NLS-1$

    // ImageView attributes
    public static final String ATTR_ADJUST_VIEW_BOUNDS = "adjustViewBounds"; //$NON-NLS-1$
    public static final String ATTR_CROP_TO_PADDING = "cropToPadding"; //$NON-NLS-1$

    // Font attributes of a TAG_FONT_FAMILY element
    public static final String ATTR_FONT_PROVIDER_AUTHORITY = "fontProviderAuthority";
    public static final String ATTR_FONT_PROVIDER_QUERY = "fontProviderQuery";
    public static final String ATTR_FONT_PROVIDER_PACKAGE = "fontProviderPackage";
    public static final String ATTR_FONT_PROVIDER_CERTS = "fontProviderCerts";

    // Font attributes of a TAG_FONT element
    public static final String ATTR_FONT_STYLE = "fontStyle";
    public static final String ATTR_FONT_WEIGHT = "fontWeight";
    public static final String ATTR_FONT = "font";

    // ConstraintLayout layout params
    public static final String ATTR_LAYOUT_EDITOR_ABSOLUTE_X =
            "layout_editor_absoluteX"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_EDITOR_ABSOLUTE_Y =
            "layout_editor_absoluteY"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_LEFT_CREATOR =
            "layout_constraintLeft_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_RIGHT_CREATOR =
            "layout_constraintRight_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TOP_CREATOR =
            "layout_constraintTop_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BOTTOM_CREATOR =
            "layout_constraintBottom_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BASELINE_CREATOR =
            "layout_constraintBaseline_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_CREATOR =
            "layout_constraintCenter_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_X_CREATOR =
            "layout_constraintCenterX_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_Y_CREATOR =
            "layout_constraintCenterY_creator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_LEFT_TO_LEFT_OF =
            "layout_constraintLeft_toLeftOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_LEFT_TO_RIGHT_OF =
            "layout_constraintLeft_toRightOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_RIGHT_TO_LEFT_OF =
            "layout_constraintRight_toLeftOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_RIGHT_TO_RIGHT_OF =
            "layout_constraintRight_toRightOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TOP_TO_TOP_OF =
            "layout_constraintTop_toTopOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TOP_TO_BOTTOM_OF =
            "layout_constraintTop_toBottomOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BOTTOM_TO_TOP_OF =
            "layout_constraintBottom_toTopOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF =
            "layout_constraintBottom_toBottomOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BASELINE_TO_BASELINE_OF =
            "layout_constraintBaseline_toBaselineOf"; //$NON-NLS-1$

    public static final String ATTR_LAYOUT_START_TO_END_OF =
            "layout_constraintStart_toEndOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_START_TO_START_OF =
            "layout_constraintStart_toStartOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_END_TO_START_OF =
            "layout_constraintEnd_toStartOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_END_TO_END_OF =
            "layout_constraintEnd_toEndOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_LEFT = "layout_goneMarginLeft"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_TOP = "layout_goneMarginTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_RIGHT =
            "layout_goneMarginRight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_BOTTOM =
            "layout_goneMarginBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_START =
            "layout_goneMarginStart"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_GONE_MARGIN_END = "layout_goneMarginEnd"; //$NON-NLS-1$

    public static final String ATTR_LAYOUT_HORIZONTAL_BIAS =
            "layout_constraintHorizontal_bias"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_VERTICAL_BIAS =
            "layout_constraintVertical_bias"; //$NON-NLS-1$

    public static final String ATTR_LAYOUT_WIDTH_DEFAULT =
            "layout_constraintWidth_default"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT_DEFAULT =
            "layout_constraintHeight_default"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH_MIN = "layout_constraintWidth_min"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH_MAX = "layout_constraintWidth_max"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH_PERCENT = "layout_constraintWidth_percent"; //$NON-NLS-1$=
    public static final String ATTR_LAYOUT_HEIGHT_MIN = "layout_constraintHeight_min"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT_MAX = "layout_constraintHeight_max"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HEIGHT_PERCENT = "layout_constraintHeight_percent"; //$NON-NLS-1$=

    public static final String ATTR_LAYOUT_DIMENSION_RATIO =
            "layout_constraintDimensionRatio"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_VERTICAL_CHAIN_STYLE =
            "layout_constraintVertical_chainStyle"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE =
            "layout_constraintHorizontal_chainStyle"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_VERTICAL_WEIGHT =
            "layout_constraintVertical_weight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_HORIZONTAL_WEIGHT =
            "layout_constraintHorizontal_weight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CHAIN_SPREAD = "spread"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CHAIN_SPREAD_INSIDE = "spread_inside"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CHAIN_PACKED = "packed"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CHAIN_HELPER_USE_RTL = "chainUseRtl"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINTSET = "constraintSet"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE = "layout_constraintCircleAngle"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS = "layout_constraintCircleRadius"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINED_HEIGHT = "layout_constrainedHeight"; //$NON_NLS-1$
    public static final String ATTR_LAYOUT_CONSTRAINED_WIDTH = "layout_constrainedWidth"; //$NON_NLS-1$
    public static final String ATTR_CONSTRAINT_SET_START = "constraintSetStart";
    public static final String ATTR_CONSTRAINT_SET_END = "constraintSetEnd";
    public static final String ATTR_DERIVE_CONSTRAINTS_FROM = "deriveConstraintsFrom";

    public static final String ATTR_GUIDELINE_ORIENTATION_HORIZONTAL = "horizontal"; //$NON-NLS-1$
    public static final String ATTR_GUIDELINE_ORIENTATION_VERTICAL = "vertical"; //$NON-NLS-1$
    public static final String LAYOUT_CONSTRAINT_GUIDE_BEGIN =
            "layout_constraintGuide_begin"; //$NON-NLS-1$
    public static final String LAYOUT_CONSTRAINT_GUIDE_END =
            "layout_constraintGuide_end"; //$NON-NLS-1$
    public static final String LAYOUT_CONSTRAINT_GUIDE_PERCENT =
            "layout_constraintGuide_percent"; //$NON-NLS-1$
    public static final String LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT =
            "layout_constraintGuide_Percent"; //$NON-NLS-1$
    public static final String ATTR_LOCKED = "locked"; //$NON-NLS-1$
    public static final String ATTR_CONSTRAINT_LAYOUT_DESCRIPTION =
      "layoutDescription"; //$NON-NLS-1$

    // MotionLayout
    public static final String ATTR_MOTION_TARGET = "motionTarget";
    public static final String ATTR_MOTION_WAVE_OFFSET = "waveOffset";
    public static final String ATTR_MOTION_TARGET_ID = "targetId";
    public static final String ATTR_MOTION_TOUCH_ANCHOR_ID = "touchAnchorId";
    public static final String ATTR_MOTION_TOUCH_REGION_ID = "touchRegionId";

    // AbsListView
    public static final String ATTR_LIST_SELECTOR = "listSelector";

    // ListView
    public static final String ATTR_OVER_SCROLL_FOOTER = "overScrollFooter";
    public static final String ATTR_OVER_SCROLL_HEADER = "overScrollHeader";
    public static final String ATTR_CHILD_DIVIDER = "childDivider";

    // SearchView
    public static final String ATTR_QUERY_BACKGROUND = "queryBackground";
    public static final String ATTR_SUBMIT_BACKGROUND = "submitBackground";

    // SimpleExoPlayerView
    public static final String ATTR_RESIZE_MODE = "resize_mode";
    public static final String ATTR_FAST_FORWARD_INCREMENT = "fastforward_increment";
    public static final String ATTR_REWIND_INCREMENT = "rewind_increment";

    // FlexboxLayout params
    public static final String ATTR_FLEX_DIRECTION = "flexDirection";
    public static final String ATTR_FLEX_WRAP = "flexWrap";
    public static final String ATTR_JUSTIFY_CONTENT = "justifyContent";
    public static final String ATTR_ALIGN_ITEMS = "alignItems";
    public static final String ATTR_ALIGN_CONTENT = "alignContent";

    // FlexboxLayout layout params
    public static final String ATTR_LAYOUT_ORDER = "layout_order";
    public static final String ATTR_LAYOUT_FLEX_GROW = "layout_flexGrow";
    public static final String ATTR_LAYOUT_FLEX_SHRINK = "layout_flexShrink";
    public static final String ATTR_LAYOUT_ALIGN_SELF = "layout_alignSelf";
    public static final String ATTR_LAYOUT_FLEX_BASIS_PERCENT = "layout_flexBasisPercent";
    public static final String ATTR_LAYOUT_MIN_WIDTH = "layout_minWidth";
    public static final String ATTR_LAYOUT_MIN_HEIGHT = "layout_minHeight";
    public static final String ATTR_LAYOUT_MAX_WIDTH = "layout_maxWidth";
    public static final String ATTR_LAYOUT_MAX_HEIGHT = "layout_maxHeight";
    public static final String ATTR_LAYOUT_WRAP_BEFORE = "layout_wrapBefore";

    // TableRow
    public static final String ATTR_LAYOUT_SPAN = "layout_span"; //$NON-NLS-1$

    // RelativeLayout layout params:
    public static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_START = "layout_alignStart"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_END = "layout_alignEnd"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT =
            "layout_alignParentLeft"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT =
            "layout_alignParentRight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_START =
            "layout_alignParentStart"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_END = "layout_alignParentEnd"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM =
            "layout_alignParentBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING =
            "layout_alignWithParentIfMissing"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_HORIZONTAL =
            "layout_centerHorizontal"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_START_OF = "layout_toStartOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_END_OF = "layout_toEndOf"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BELOW = "layout_below"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ABOVE = "layout_above"; //$NON-NLS-1$

    // Spinner
    public static final String ATTR_DROPDOWN_SELECTOR = "dropDownSelector"; //$NON-NLS-1$
    public static final String ATTR_POPUP_BACKGROUND = "popupBackground"; //$NON-NLS-1$
    public static final String ATTR_SPINNER_MODE = "spinnerMode"; //$NON-NLS-1$

    // Margins
    public static final String ATTR_LAYOUT_MARGIN = "layout_margin"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom"; //$NON-NLS-1$

    // Attributes: Drawables
    public static final String ATTR_TILE_MODE = "tileMode"; //$NON-NLS-1$

    // Attributes: Design and support lib
    public static final String ATTR_LAYOUT_ANCHOR = "layout_anchor"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ANCHOR_GRAVITY = "layout_anchorGravity"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_BEHAVIOR = "layout_behavior"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_KEYLINE = "layout_keyline"; //$NON-NLS-1$
    public static final String ATTR_BACKGROUND_TINT = "backgroundTint"; //$NON-NLS-1$
    public static final String ATTR_BACKGROUND_TINT_MODE = "backgroundTintMode"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_TINT = "drawableTint"; //$NON-NLS-1$
    public static final String ATTR_FOREGROUND_TINT = "foregroundTint"; //$NON-NLS-1$
    public static final String ATTR_FOREGROUND_TINT_MODE = "foregroundTintMode"; //$NON-NLS-1$
    public static final String ATTR_RIPPLE_COLOR = "rippleColor"; //$NON-NLS-1$
    public static final String ATTR_TINT = "tint"; //$NON-NLS-1$
    public static final String ATTR_FAB_SIZE = "fabSize"; //$NON-NLS-1$
    public static final String ATTR_ELEVATION = "elevation"; //$NON-NLS-1$
    public static final String ATTR_FITS_SYSTEM_WINDOWS = "fitsSystemWindows"; //$NON-NLS-1$
    public static final String ATTR_EXPANDED = "expanded"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_SCROLL_FLAGS = "layout_scrollFlags"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_COLLAPSE_MODE = "layout_collapseMode"; //$NON-NLS-1$
    public static final String ATTR_COLLAPSE_PARALLAX_MULTIPLIER =
            "layout_collapseParallaxMultiplier"; //$NON-NLS-1$
    public static final String ATTR_SCROLLBAR_STYLE = "scrollbarStyle"; //$NON-NLS-1$
    public static final String ATTR_FILL_VIEWPORT = "fillViewport"; //$NON-NLS-1$
    public static final String ATTR_CLIP_TO_PADDING = "clipToPadding"; //$NON-NLS-1$
    public static final String ATTR_CLIP_CHILDREN = "clipChildren"; //$NON-NLS-1$
    public static final String ATTR_HEADER_LAYOUT = "headerLayout"; //$NON-NLS-1$
    public static final String ATTR_ITEM_BACKGROUND = "itemBackground"; //$NON-NLS-1$
    public static final String ATTR_ITEM_ICON_TINT = "itemIconTint"; //$NON-NLS-1$
    public static final String ATTR_ITEM_TEXT_APPEARANCE = "itemTextAppearance"; //$NON-NLS-1$
    public static final String ATTR_ITEM_TEXT_COLOR = "itemTextColor"; //$NON-NLS-1$
    public static final String ATTR_POPUP_THEME = "popupTheme"; //$NON-NLS-1$
    public static final String ATTR_MIN_HEIGHT = "minHeight"; //$NON-NLS-1$
    public static final String ATTR_MAX_HEIGHT = "maxHeight"; //$NON-NLS-1$
    public static final String ATTR_ACTION_BAR = "actionBar"; //$NON-NLS-1$
    public static final String ATTR_TOOLBAR_ID = "toolbarId"; //$NON-NLS-1$
    public static final String ATTR_CACHE_COLOR_HINT = "cacheColorHint"; //$NON-NLS-1$
    public static final String ATTR_DIVIDER = "divider"; //$NON-NLS-1$
    public static final String ATTR_DIVIDER_PADDING = "dividerPadding"; //$NON-NLS-1$
    public static final String ATTR_DIVIDER_HEIGHT = "dividerHeight"; //$NON-NLS-1$
    public static final String ATTR_FOOTER_DIVIDERS_ENABLED = "footerDividersEnabled"; //$NON-NLS-1$
    public static final String ATTR_HEADER_DIVIDERS_ENABLED = "headerDividersEnabled"; //$NON-NLS-1$
    public static final String ATTR_CARD_BACKGROUND_COLOR = "cardBackgroundColor"; //$NON-NLS-1$
    public static final String ATTR_CARD_CORNER_RADIUS = "cardCornerRadius"; //$NON-NLS-1$
    public static final String ATTR_CONTENT_PADDING = "contentPadding"; //$NON-NLS-1$
    public static final String ATTR_CARD_ELEVATION = "cardElevation"; //$NON-NLS-1$
    public static final String ATTR_CARD_PREVENT_CORNER_OVERLAP =
            "cardPreventCornerOverlap"; //$NON-NLS-1$
    public static final String ATTR_CARD_USE_COMPAT_PADDING = "cardUseCompatPadding"; //$NON-NLS-1$
    public static final String ATTR_ENTRIES = "entries"; //$NON-NLS-1$
    public static final String ATTR_MIN_WIDTH = "minWidth"; //$NON-NLS-1$
    public static final String ATTR_MAX_WIDTH = "maxWidth"; //$NON-NLS-1$
    public static final String ATTR_DROPDOWN_HEIGHT = "dropDownHeight"; //$NON-NLS-1$
    public static final String ATTR_DROPDOWN_WIDTH = "dropDownWidth"; //$NON-NLS-1$
    public static final String ATTR_DRAW_SELECTOR_ON_TOP = "drawSelectorOnTop"; //$NON-NLS-1$
    public static final String ATTR_SCROLLBARS = "scrollbars"; //$NON-NLS-1$
    public static final String ATTR_COMPLETION_HINT = "completionHint"; //$NON-NLS-1$
    public static final String ATTR_COMPLETION_HINT_VIEW = "completionHintView"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_MANAGER = "layoutManager"; //$NON-NLS-1$
    public static final String ATTR_SPAN_COUNT = "spanCount";
    public static final String ATTR_NAVIGATION_ICON = "navigationIcon";
    public static final String ATTR_LIFT_ON_SCROLL_TARGET_VIEW_ID = "liftOnScrollTargetViewId";
    public static final String ATTR_STATUS_BAR_FOREGROUND = "statusBarForeground";

    // Material BottomAppBar Attributes
    public static final String ATTR_FAB_ALIGNMENT_MODE = "fabAlignmentMode";
    public static final String ATTR_FAB_ANIMATION_MODE = "fabAnimationMode";
    public static final String ATTR_FAB_CRADLE_MARGIN = "fabCradleMargin";
    public static final String ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS =
            "fabCradleRoundedCornerRadius";
    public static final String ATTR_FAB_CRADLE_VERTICAL_OFFSET = "fabCradleVerticalOffset";

    // Material Button Attributes
    public static final String ATTR_INSET_LEFT = "insetLeft";
    public static final String ATTR_INSET_RIGHT = "insetRight";
    public static final String ATTR_INSET_TOP = "insetTop";
    public static final String ATTR_INSET_BOTTOM = "insetBottom";
    public static final String ATTR_ICON_PADDING = "iconPadding";
    public static final String ATTR_ICON_TINT = "iconTint";
    public static final String ATTR_ICON_TINT_MODE = "iconTintMode";
    public static final String ATTR_ADDITIONAL_PADDING_START_FOR_ICON =
            "additionalPaddingStartForIcon";
    public static final String ATTR_ADDITIONAL_PADDING_END_FOR_ICON = "additionalPaddingEndForIcon";
    public static final String ATTR_STROKE_COLOR = "strokeColor";
    public static final String ATTR_STROKE_WIDTH = "strokeWidth";
    public static final String ATTR_CORNER_RADIUS = "cornerRadius";

    // Material CollapsingToolbarLayout
    public static final String ATTR_CONTENT_SCRIM = "contentScrim";
    public static final String ATTR_STATUS_BAR_SCRIM = "statusBarScrim";

    // Material FloatingActionButton Attributes
    public static final String ATTR_FAB_CUSTOM_SIZE = "fabCustomSize";
    public static final String ATTR_HOVERED_FOCUSED_TRANSLATION_Z = "hoveredFocusedTranslationZ";
    public static final String ATTR_PRESSED_TRANSLATION_Z = "pressedTranslationZ";
    public static final String ATTR_BORDER_WIDTH = "borderWidth";
    public static final String ATTR_COMPAT_PADDING = "useCompatPadding";
    public static final String ATTR_MAX_IMAGE_SIZE = "maxImageSize";
    public static final String ATTR_SHOW_MOTION_SPEC = "showMotionSpec";
    public static final String ATTR_HIDE_MOTION_SPEC = "hideMotionSpec";

    // Material NavigationView
    public static final String ATTR_INSET_BACKGROUND = "insetBackground";
    public static final String ATTR_INSET_FOREGROUND = "insetForeground";
    public static final String ATTR_ITEM_SHAPE_APPEARANCE = "itemShapeAppearance";
    public static final String ATTR_ITEM_SHAPE_APPEARANCE_OVERLAY = "itemShapeAppearanceOverlay";
    public static final String ATTR_ITEM_SHAPE_FILL_COLOR = "itemShapeFillColor";

    // Material BottomNavigationView Attributes
    public static final String ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED =
            "itemHorizontalTranslationEnabled";
    public static final String ATTR_ITEM_RIPPLE_COLOR = "itemRippleColor";
    public static final String ATTR_LABEL_VISIBILITY_MODE = "labelVisibilityMode";

    // Material ChipGroup Attributes
    public static final String ATTR_CHIP_SPACING = "chipSpacing";
    public static final String ATTR_CHIP_SPACING_HORIZONTAL = "chipSpacingHorizontal";
    public static final String ATTR_CHIP_SPACING_VERTICAL = "chipSpacingVertical";
    public static final String ATTR_SINGLE_SELECTION = "singleSelection";
    public static final String ATTR_CHECKED_CHIP = "checkedChip";

    // Material Chip (ChipDrawable) Attributes
    public static final String ATTR_CHIP_BACKGROUND_COLOR = "chipBackgroundColor";
    public static final String ATTR_CHIP_TEXT = "chipText";
    public static final String ATTR_CHIP_ICON = "chipIcon";
    public static final String ATTR_CHIP_ICON_TINT = "chipIconTint";
    public static final String ATTR_CHIP_ICON_VISIBLE = "chipIconVisible";
    public static final String ATTR_CHIP_STROKE_COLOR = "chipStrokeColor";
    public static final String ATTR_CHIP_SURFACE_COLOR = "chipSurfaceColor";
    public static final String ATTR_CHECKED_ICON = "checkedIcon";
    public static final String ATTR_CHECKED_ICON_VISIBLE = "checkedIconVisible";
    public static final String ATTR_CLOSE_ICON = "closeIcon";
    public static final String ATTR_CLOSE_ICON_TINT = "closeIconTint";
    public static final String ATTR_CLOSE_ICON_VISIBLE = "closeIconVisible";

    // Material TabLayout Attributes
    public static final String ATTR_TAB_INDICATOR_HEIGHT = "tabIndicatorHeight";
    public static final String ATTR_TAB_BACKGROUND = "tabBackground";
    public static final String ATTR_TAB_INDICATOR = "tabIndicator";
    public static final String ATTR_TAB_INDICATOR_GRAVITY = "tabIndicatorGravity";
    public static final String ATTR_TAB_INDICATOR_ANIMATION_DURATION =
            "tabIndicatorAnimationDuration";
    public static final String ATTR_TAB_INDICATOR_FULL_WIDTH = "tabIndicatorFullWidth";
    public static final String ATTR_TAB_MODE = "tabMode";
    public static final String ATTR_TAB_GRAVITY = "tabGravity";
    public static final String ATTR_TAB_CONTENT_START = "tabContentStart";
    public static final String ATTR_TAB_INDICATOR_COLOR = "tabIndicatorColor";
    public static final String ATTR_TAB_SELECTED_TEXT_COLOR = "tabSelectedTextColor";
    public static final String ATTR_TAB_TEXT_APPEARANCE = "tabTextAppearance";
    public static final String ATTR_TAB_INLINE_LABEL = "tabInlineLabel";
    public static final String ATTR_TAB_MIN_WIDTH = "tabMinWidth";
    public static final String ATTR_TAB_MAX_WIDTH = "tabMaxWidth";
    public static final String ATTR_TAB_TEXT_COLOR = "tabTextColor";
    public static final String ATTR_TAB_PADDING = "tabPadding";
    public static final String ATTR_TAB_PADDING_START = "tabPaddingStart";
    public static final String ATTR_TAB_PADDING_END = "tabPaddingEnd";
    public static final String ATTR_TAB_PADDING_TOP = "tabPaddingTop";
    public static final String ATTR_TAB_PADDING_BOTTOM = "tabPaddingBottom";
    public static final String ATTR_TAB_ICON_TINT = "tabIconTint";
    public static final String ATTR_TAB_ICON_TINT_MODE = "tabIconTintMode";
    public static final String ATTR_TAB_RIPPLE_COLOR = "tabRippleColor";
    public static final String ATTR_TAB_UNBOUNDED_RIPPLE = "tabUnboundedRipple";
    public static final String ATTR_LAYOUT_SCROLL_INTERPOLATOR = "layout_scrollInterpolator";

    // Material TextInputLayout Attributes
    public static final String ATTR_END_ICON_TINT = "endIconTint";
    public static final String ATTR_ERROR_ICON_TINT = "errorIconTint";
    public static final String ATTR_ERROR_TEXT_COLOR = "errorTextColor";
    public static final String ATTR_HELPER_TEXT_TEXT_COLOR = "helperTextTextColor";
    public static final String ATTR_HINT_ENABLED = "hintEnabled";
    public static final String ATTR_HINT_ANIMATION_ENABLED = "hintAnimationEnabled";
    public static final String ATTR_HINT_TEXT_APPEARANCE = "hintTextAppearance";
    public static final String ATTR_HINT_TEXT_COLOR = "hintTextColor";
    public static final String ATTR_HELPER_TEXT = "helperText";
    public static final String ATTR_HELPER_TEXT_ENABLED = "helperTextEnabled";
    public static final String ATTR_HELPER_TEXT_TEXT_APPEARANCE = "helperTextTextAppearance";
    public static final String ATTR_SHAPE_APPEARANCE = "shapeAppearance";
    public static final String ATTR_SHAPE_APPEARANCE_OVERLAY = "shapeAppearanceOverlay";
    public static final String ATTR_START_ICON_TINT = "startIconTint";
    public static final String ATTR_ERROR_ENABLED = "errorEnabled";
    public static final String ATTR_ERROR_TEXT_APPEARANCE = "errorTextAppearance";
    public static final String ATTR_COUNTER_ENABLED = "counterEnabled";
    public static final String ATTR_COUNTER_MAX_LENGTH = "counterMaxLength";
    public static final String ATTR_COUNTER_TEXT_APPEARANCE = "counterTextAppearance";
    public static final String ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE =
            "counterOverflowTextAppearance";
    public static final String ATTR_PASSWORD_TOGGLE_ENABLED = "passwordToggleEnabled";
    public static final String ATTR_PASSWORD_TOGGLE_DRAWABLE = "passwordToggleDrawable";
    public static final String ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION =
            "passwordToggleContentDescription";
    public static final String ATTR_PASSWORD_TOGGLE_TINT = "passwordToggleTint";
    public static final String ATTR_PASSWORD_TOGGLE_TINT_MODE = "passwordToggleTintMode";
    public static final String ATTR_BOX_BACKGROUND_MODE = "boxBackgroundMode";
    public static final String ATTR_BOX_COLLAPSED_PADDING_TOP = "boxCollapsedPaddingTop";
    public static final String ATTR_BOX_STROKE_COLOR = "boxStrokeColor";
    public static final String ATTR_BOX_BACKGROUND_COLOR = "boxBackgroundColor";
    public static final String ATTR_BOX_STROKE_WIDTH = "boxStrokeWidth";

    // Values: Manifest
    public static final String VALUE_SPLIT_ACTION_BAR_WHEN_NARROW =
            "splitActionBarWhenNarrow"; // NON-NLS-$1

    // Values: Layouts
    public static final String VALUE_FILL_PARENT = "fill_parent"; //$NON-NLS-1$
    public static final String VALUE_MATCH_PARENT = "match_parent"; //$NON-NLS-1$
    public static final String VALUE_MATCH_CONSTRAINT = "0dp"; //$NON-NLS-1$
    public static final String VALUE_VERTICAL = "vertical"; //$NON-NLS-1$
    public static final String VALUE_TRUE = "true"; //$NON-NLS-1$
    public static final String VALUE_EDITABLE = "editable"; //$NON-NLS-1$
    public static final String VALUE_AUTO_FIT = "auto_fit"; //$NON-NLS-1$
    public static final String VALUE_SELECTABLE_ITEM_BACKGROUND =
            "?android:attr/selectableItemBackground"; //$NON-NLS-1$

    // Values: Resources
    public static final String VALUE_ID = "id"; //$NON-NLS-1$

    // Values: Drawables
    public static final String VALUE_DISABLED = "disabled"; //$NON-NLS-1$
    public static final String VALUE_CLAMP = "clamp"; //$NON-NLS-1$

    // Value delimiters: Manifest
    public static final String VALUE_DELIMITER_PIPE = "|"; //$NON-NLS-1$

    // Menus
    public static final String ATTR_CHECKABLE = "checkable";
    public static final String ATTR_CHECKABLE_BEHAVIOR = "checkableBehavior";
    public static final String ATTR_ORDER_IN_CATEGORY = "orderInCategory";
    public static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    public static final String ATTR_TITLE = "title";
    public static final String ATTR_VISIBLE = "visible";
    public static final String VALUE_IF_ROOM = "ifRoom"; //$NON-NLS-1$
    public static final String VALUE_ALWAYS = "always"; //$NON-NLS-1$

    // Units
    public static final String UNIT_DP = "dp"; //$NON-NLS-1$
    public static final String UNIT_DIP = "dip"; //$NON-NLS-1$
    public static final String UNIT_SP = "sp"; //$NON-NLS-1$
    public static final String UNIT_PX = "px"; //$NON-NLS-1$
    public static final String UNIT_IN = "in"; //$NON-NLS-1$
    public static final String UNIT_MM = "mm"; //$NON-NLS-1$
    public static final String UNIT_PT = "pt"; //$NON-NLS-1$

    // Filenames and folder names
    public static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml"; //$NON-NLS-1$
    public static final String OLD_PROGUARD_FILE = "proguard.cfg"; //$NON-NLS-1$
    public static final String CLASS_FOLDER =
            "bin" + File.separator + "classes"; //$NON-NLS-1$ //$NON-NLS-2$
    public static final String GEN_FOLDER = "gen"; //$NON-NLS-1$
    public static final String SRC_FOLDER = "src"; //$NON-NLS-1$
    public static final String LIBS_FOLDER = "libs"; //$NON-NLS-1$
    public static final String BIN_FOLDER = "bin"; //$NON-NLS-1$

    public static final String RES_FOLDER = "res"; //$NON-NLS-1$
    public static final String DOT_XML = ".xml"; //$NON-NLS-1$
    public static final String DOT_XSD = ".xsd"; //$NON-NLS-1$
    public static final String DOT_GIF = ".gif"; //$NON-NLS-1$
    public static final String DOT_JPG = ".jpg"; //$NON-NLS-1$
    public static final String DOT_JPEG = ".jpeg"; //$NON-NLS-1$
    public static final String DOT_WEBP = ".webp"; //$NON-NLS-1$
    public static final String DOT_PNG = ".png"; //$NON-NLS-1$
    public static final String DOT_9PNG = ".9.png"; //$NON-NLS-1$
    public static final String DOT_JAVA = ".java"; //$NON-NLS-1$
    public static final String DOT_KT = ".kt"; //$NON-NLS-1$
    public static final String DOT_KTS = ".kts"; //$NON-NLS-1$
    public static final String DOT_CLASS = ".class"; //$NON-NLS-1$
    public static final String DOT_JAR = ".jar"; //$NON-NLS-1$
    public static final String DOT_SRCJAR = ".srcjar"; //$NON-NLS-1$
    public static final String DOT_GRADLE = ".gradle"; //$NON-NLS-1$
    public static final String DOT_PROPERTIES = ".properties"; //$NON-NLS-1$
    public static final String DOT_JSON = ".json"; //$NON-NLS-1$
    public static final String DOT_PSD = ".psd"; //$NON-NLS-1$
    public static final String DOT_TTF = ".ttf"; //$NON-NLS-1$
    public static final String DOT_TTC = ".ttc"; //$NON-NLS-1$
    public static final String DOT_OTF = ".otf"; //$NON-NLS-1$

    /** Extension of the Application package Files, i.e. "apk". */
    public static final String EXT_ANDROID_PACKAGE = "apk"; //$NON-NLS-1$
    /** Extension of the InstantApp package Files, i.e. "iapk". */
    public static final String EXT_INSTANTAPP_PACKAGE = "iapk"; //$NON-NLS-1$
    /** Extension for Android archive files */
    public static final String EXT_AAR = "aar"; //$NON-NLS-1$
    /** Extension for Android atom files. */
    public static final String EXT_ATOM = "atom"; //$NON-NLS-1$
    /** Extension of java files, i.e. "java" */
    public static final String EXT_JAVA = "java"; //$NON-NLS-1$
    /** Extension of compiled java files, i.e. "class" */
    public static final String EXT_CLASS = "class"; //$NON-NLS-1$
    /** Extension of xml files, i.e. "xml" */
    public static final String EXT_XML = "xml"; //$NON-NLS-1$
    /** Extension of gradle files, i.e. "gradle" */
    public static final String EXT_GRADLE = "gradle"; //$NON-NLS-1$
    /** Extension of Kotlin gradle files, i.e. "gradle.kts" */
    public static final String EXT_GRADLE_KTS = "gradle.kts"; //$NON-NLS-1$
    /** Extension of jar files, i.e. "jar" */
    public static final String EXT_JAR = "jar"; //$NON-NLS-1$
    /** Extension of ZIP files, i.e. "zip" */
    public static final String EXT_ZIP = "zip"; //$NON-NLS-1$
    /** Extension of aidl files, i.e. "aidl" */
    public static final String EXT_AIDL = "aidl"; //$NON-NLS-1$
    /** Extension of Renderscript files, i.e. "rs" */
    public static final String EXT_RS = "rs"; //$NON-NLS-1$
    /** Extension of Renderscript files, i.e. "rsh" */
    public static final String EXT_RSH = "rsh"; //$NON-NLS-1$
    /** Extension of FilterScript files, i.e. "fs" */
    public static final String EXT_FS = "fs"; //$NON-NLS-1$
    /** Extension of Renderscript bitcode files, i.e. "bc" */
    public static final String EXT_BC = "bc"; //$NON-NLS-1$
    /** Extension of dependency files, i.e. "d" */
    public static final String EXT_DEP = "d"; //$NON-NLS-1$
    /** Extension of native libraries, i.e. "so" */
    public static final String EXT_NATIVE_LIB = "so"; //$NON-NLS-1$
    /** Extension of dex files, i.e. "dex" */
    public static final String EXT_DEX = "dex"; //$NON-NLS-1$
    /** Extension for temporary resource files, ie "ap_ */
    public static final String EXT_RES = "ap_"; //$NON-NLS-1$
    /** Extension for pre-processable images. Right now pngs */
    public static final String EXT_PNG = "png"; //$NON-NLS-1$
    /** Extension of app bundle files, i.e. "aab" */
    public static final String EXT_APP_BUNDLE = "aab"; //$NON-NLS-1$

    public static final String EXT_HPROF = "hprof"; //$NON-NLS-1$
    public static final String EXT_GZ = "gz"; //$NON-NLS-1$

    public static final String EXT_JSON = "json";

    public static final String EXT_CSV = "csv";

    /** Extension of native debug metadata files, i.e. "dbg" */
    public static final String EXT_DBG = "dbg";
    /** Extension of native debug symbol table files, i.e. "sym" */
    public static final String EXT_SYM = "sym";

    private static final String DOT = "."; //$NON-NLS-1$

    /** Dot-Extension of the Application package Files, i.e. ".apk". */
    public static final String DOT_ANDROID_PACKAGE = DOT + EXT_ANDROID_PACKAGE;
    /** Dot-Extension for Android archive files */
    public static final String DOT_AAR = DOT + EXT_AAR; //$NON-NLS-1$
    /** Dot-Extension of zip files, i.e. ".zip" */
    public static final String DOT_ZIP = DOT + EXT_ZIP;
    /** Dot-Extension of aidl files, i.e. ".aidl" */
    public static final String DOT_AIDL = DOT + EXT_AIDL;
    /** Dot-Extension of renderscript files, i.e. ".rs" */
    public static final String DOT_RS = DOT + EXT_RS;
    /** Dot-Extension of renderscript header files, i.e. ".rsh" */
    public static final String DOT_RSH = DOT + EXT_RSH;
    /** Dot-Extension of FilterScript files, i.e. ".fs" */
    public static final String DOT_FS = DOT + EXT_FS;
    /** Dot-Extension of renderscript bitcode files, i.e. ".bc" */
    public static final String DOT_BC = DOT + EXT_BC;
    /** Dot-Extension of dependency files, i.e. ".d" */
    public static final String DOT_DEP = DOT + EXT_DEP;
    /** Dot-Extension of native dynamic libraries, i.e. ".so" */
    public static final String DOT_NATIVE_LIBS = DOT + EXT_NATIVE_LIB;
    /** Dot-Extension of dex files, i.e. ".dex" */
    public static final String DOT_DEX = DOT + EXT_DEX;
    /** Dot-Extension for temporary resource files, ie "ap_ */
    public static final String DOT_RES = DOT + EXT_RES;
    /** Dot-Extension for BMP files, i.e. ".bmp" */
    public static final String DOT_BMP = ".bmp"; //$NON-NLS-1$
    /** Dot-Extension for SVG files, i.e. ".svg" */
    public static final String DOT_SVG = ".svg"; //$NON-NLS-1$
    /** Dot-Extension for template files */
    public static final String DOT_FTL = ".ftl"; //$NON-NLS-1$
    /** Dot-Extension of text files, i.e. ".txt" */
    public static final String DOT_TXT = ".txt"; //$NON-NLS-1$
    /** Dot-Extension for Java heap dumps. */
    public static final String DOT_HPROF = DOT + EXT_HPROF; //$NON-NLS-1$
    /** Dot-Extension of native debug metadata files, i.e. ".dbg" */
    public static final String DOT_DBG = ".dbg";
    /** Dot-Extension of native debug symbol table files, i.e. ".sym" */
    public static final String DOT_SYM = ".sym";
    /** Dot-Extension of TensorFlow Lite FlatBuffer files, i.e., ".tflite" */
    public static final String DOT_TFLITE = ".tflite";

    /** Resource base name for java files and classes */
    public static final String FN_RESOURCE_BASE = "R"; //$NON-NLS-1$
    /** Resource java class filename, i.e. "R.java" */
    public static final String FN_RESOURCE_CLASS = FN_RESOURCE_BASE + DOT_JAVA;
    /** Resource class file filename, i.e. "R.class" */
    public static final String FN_COMPILED_RESOURCE_CLASS = FN_RESOURCE_BASE + DOT_CLASS;
    /** Resource text filename, i.e. "R.txt" */
    public static final String FN_RESOURCE_TEXT = FN_RESOURCE_BASE + DOT_TXT;
    /** Filename for public resources in AAR archives */
    public static final String FN_PUBLIC_TXT = "public.txt";
    /** Resource static library */
    public static final String FN_RESOURCE_STATIC_LIBRARY = "res.apk";
    /** Resource shared library */
    public static final String FN_RESOURCE_SHARED_STATIC_LIBRARY = "shared.apk";
    /** R class jar, used for resource static library */
    public static final String FN_R_CLASS_JAR = "R.jar";
    /** R file containing only the local resources. */
    public static final String FN_R_DEF_TXT = "R-def.txt";
    /** Generated manifest class name */
    public static final String FN_MANIFEST_BASE = "Manifest"; //$NON-NLS-1$
    /** Generated BuildConfig class name */
    public static final String FN_BUILD_CONFIG_BASE = "BuildConfig"; //$NON-NLS-1$
    /** Manifest java class filename, i.e. "Manifest.java" */
    public static final String FN_MANIFEST_CLASS = FN_MANIFEST_BASE + DOT_JAVA;
    /** BuildConfig java class filename, i.e. "BuildConfig.java" */
    public static final String FN_BUILD_CONFIG = FN_BUILD_CONFIG_BASE + DOT_JAVA;

    public static final String DRAWABLE_FOLDER = "drawable"; //$NON-NLS-1$
    public static final String MIPMAP_FOLDER = "mipmap"; //$NON-NLS-1$
    public static final String DRAWABLE_XHDPI = "drawable-xhdpi"; //$NON-NLS-1$
    public static final String DRAWABLE_XXHDPI = "drawable-xxhdpi"; //$NON-NLS-1$
    public static final String DRAWABLE_XXXHDPI = "drawable-xxxhdpi"; //$NON-NLS-1$
    public static final String DRAWABLE_HDPI = "drawable-hdpi"; //$NON-NLS-1$
    public static final String DRAWABLE_MDPI = "drawable-mdpi"; //$NON-NLS-1$
    public static final String DRAWABLE_LDPI = "drawable-ldpi"; //$NON-NLS-1$

    // Resources
    public static final String PREFIX_RESOURCE_REF = "@"; //$NON-NLS-1$
    public static final String PREFIX_THEME_REF = "?"; //$NON-NLS-1$
    public static final String PREFIX_BINDING_EXPR = "@{"; //$NON-NLS-1$
    public static final String PREFIX_TWOWAY_BINDING_EXPR = "@={"; //$NON-NLS-1$
    public static final String MANIFEST_PLACEHOLDER_PREFIX = "${"; //$NON-NLS-1$
    public static final String MANIFEST_PLACEHOLDER_SUFFIX = "}"; //$NON-NLS-1$
    public static final String ANDROID_PREFIX = "@android:"; //$NON-NLS-1$
    public static final String ANDROID_THEME_PREFIX = "?android:"; //$NON-NLS-1$
    public static final String LAYOUT_RESOURCE_PREFIX = "@layout/"; //$NON-NLS-1$
    public static final String STYLE_RESOURCE_PREFIX = "@style/"; //$NON-NLS-1$
    public static final String COLOR_RESOURCE_PREFIX = "@color/"; //$NON-NLS-1$
    public static final String NEW_ID_PREFIX = "@+id/"; //$NON-NLS-1$
    public static final String ID_PREFIX = "@id/"; //$NON-NLS-1$
    public static final String DRAWABLE_PREFIX = "@drawable/"; //$NON-NLS-1$
    public static final String STRING_PREFIX = "@string/"; //$NON-NLS-1$
    public static final String DIMEN_PREFIX = "@dimen/"; //$NON-NLS-1$
    public static final String MIPMAP_PREFIX = "@mipmap/"; //$NON-NLS-1$
    public static final String FONT_PREFIX = "@font/"; //$NON-NLS-1$
    public static final String AAPT_ATTR_PREFIX = "@aapt:_aapt/";
    public static final String SAMPLE_PREFIX = "@sample/";
    public static final String NAVIGATION_PREFIX = "@navigation/"; //$NON-NLS-1$

    public static final String TOOLS_SAMPLE_PREFIX = "@tools:sample/";

    public static final String ANDROID_LAYOUT_RESOURCE_PREFIX = "@android:layout/"; //$NON-NLS-1$
    public static final String ANDROID_STYLE_RESOURCE_PREFIX = "@android:style/"; //$NON-NLS-1$
    public static final String ANDROID_COLOR_RESOURCE_PREFIX = "@android:color/"; //$NON-NLS-1$
    public static final String ANDROID_ID_PREFIX = "@android:id/"; //$NON-NLS-1$
    public static final String ANDROID_DRAWABLE_PREFIX = "@android:drawable/"; //$NON-NLS-1$
    public static final String ANDROID_STRING_PREFIX = "@android:string/"; //$NON-NLS-1$

    public static final String RESOURCE_CLZ_ID = "id"; //$NON-NLS-1$
    public static final String RESOURCE_CLZ_COLOR = "color"; //$NON-NLS-1$
    public static final String RESOURCE_CLZ_ARRAY = "array"; //$NON-NLS-1$
    public static final String RESOURCE_CLZ_ATTR = "attr"; //$NON-NLS-1$
    public static final String RESOURCE_CLZ_STYLEABLE = "styleable"; //$NON-NLS-1$
    public static final String NULL_RESOURCE = "@null"; //$NON-NLS-1$
    public static final String TRANSPARENT_COLOR = "@android:color/transparent"; //$NON-NLS-1$
    public static final String REFERENCE_STYLE = "style/"; //$NON-NLS-1$
    public static final String PREFIX_ANDROID = "android:"; //$NON-NLS-1$
    public static final String PREFIX_APP = "app:"; //$NON-NLS-1$

    // Resource Types
    public static final String DRAWABLE_TYPE = "drawable"; //$NON-NLS-1$
    public static final String MENU_TYPE = "menu"; //$NON-NLS-1$

    // Packages
    public static final String ANDROID_PKG_PREFIX = ANDROID_PKG + "."; //$NON-NLS-1$
    public static final String ANDROIDX_PKG_PREFIX = ANDROIDX_PKG + "."; //$NON-NLS-1$
    public static final String WIDGET_PKG_PREFIX = "android.widget."; //$NON-NLS-1$
    public static final String VIEW_PKG_PREFIX = "android.view."; //$NON-NLS-1$

    // Project properties
    public static final String ANDROID_LIBRARY = "android.library"; //$NON-NLS-1$
    public static final String PROGUARD_CONFIG = "proguard.config"; //$NON-NLS-1$
    public static final String ANDROID_LIBRARY_REFERENCE_FORMAT =
            "android.library.reference.%1$d"; //$NON-NLS-1$
    public static final String PROJECT_PROPERTIES = "project.properties"; //$NON-NLS-1$

    // Java References
    public static final String ATTR_REF_PREFIX = "?attr/"; //$NON-NLS-1$
    public static final String R_PREFIX = "R."; //$NON-NLS-1$
    public static final String R_ID_PREFIX = "R.id."; //$NON-NLS-1$
    public static final String R_LAYOUT_RESOURCE_PREFIX = "R.layout."; //$NON-NLS-1$
    public static final String R_DRAWABLE_PREFIX = "R.drawable."; //$NON-NLS-1$
    public static final String R_STYLEABLE_PREFIX = "R.styleable."; //$NON-NLS-1$
    public static final String R_ATTR_PREFIX = "R.attr."; //$NON-NLS-1$

    // Attributes related to tools
    public static final String ATTR_IGNORE = "ignore"; //$NON-NLS-1$
    public static final String ATTR_LOCALE = "locale"; //$NON-NLS-1$

    // SuppressLint
    public static final String SUPPRESS_ALL = "all"; //$NON-NLS-1$
    public static final String SUPPRESS_LINT = "SuppressLint"; //$NON-NLS-1$
    public static final String TARGET_API = "TargetApi"; //$NON-NLS-1$
    public static final String ATTR_TARGET_API = "targetApi"; //$NON-NLS-1$
    public static final String FQCN_SUPPRESS_LINT =
            "android.annotation." + SUPPRESS_LINT; //$NON-NLS-1$
    public static final String FQCN_TARGET_API = "android.annotation." + TARGET_API; //$NON-NLS-1$
    public static final String KOTLIN_SUPPRESS = "kotlin.Suppress";

    // Class Names
    public static final String CONSTRUCTOR_NAME = "<init>"; //$NON-NLS-1$
    public static final String CLASS_CONSTRUCTOR = "<clinit>"; //$NON-NLS-1$

    // Method Names
    public static final String FORMAT_METHOD = "format"; //$NON-NLS-1$
    public static final String GET_STRING_METHOD = "getString"; //$NON-NLS-1$
    public static final String SET_CONTENT_VIEW_METHOD = "setContentView";
    public static final String INFLATE_METHOD = "inflate";

    public static final String ATTR_TAG = "tag"; //$NON-NLS-1$
    public static final String ATTR_NUM_COLUMNS = "numColumns"; //$NON-NLS-1$

    // Some common layout element names
    public static final String CALENDAR_VIEW = "CalendarView"; //$NON-NLS-1$
    public static final String CHRONOMETER = "Chronometer"; //$NON-NLS-1$
    public static final String TEXT_CLOCK = "TextClock"; //$NON-NLS-1$
    public static final String SPACE = "Space"; //$NON-NLS-1$
    public static final String GESTURE_OVERLAY_VIEW = "GestureOverlayView"; //$NON-NLS-1$
    public static final String QUICK_CONTACT_BADGE = "QuickContactBadge"; //$NON-NLS-1$

    public static final String ATTR_HANDLE = "handle"; //$NON-NLS-1$
    public static final String ATTR_BUTTON = "button"; //$NON-NLS-1$
    public static final String ATTR_BUTTON_TINT = "buttonTint"; //$NON-NLS-1$
    public static final String ATTR_CONTENT = "content"; //$NON-NLS-1$
    public static final String ATTR_CHECKED = "checked"; //$NON-NLS-1$
    public static final String ATTR_CHECK_MARK = "checkMark"; //$NON-NLS-1$
    public static final String ATTR_CHECK_MARK_TINT = "checkMarkTint"; //$NON-NLS-1$
    public static final String ATTR_DUPLICATE_PARENT_STATE = "duplicateParentState"; //$NON-NLS-1$
    public static final String ATTR_FOCUSABLE = "focusable"; //$NON-NLS-1$
    public static final String ATTR_CLICKABLE = "clickable"; //$NON-NLS-1$
    public static final String ATTR_TEXT_OFF = "textOff"; //$NON-NLS-1$
    public static final String ATTR_TEXT_ON = "textOn"; //$NON-NLS-1$
    public static final String ATTR_CHECKED_BUTTON = "checkedButton"; //$NON-NLS-1$
    public static final String ATTR_SWITCH_TEXT_APPEARANCE = "switchTextAppearance"; //$NON-NLS-1$
    public static final String ATTR_SWITCH_MIN_WIDTH = "switchMinWidth"; //$NON-NLS-1$
    public static final String ATTR_SWITCH_PADDING = "switchPadding"; //$NON-NLS-1$
    public static final String ATTR_THUMB_TINT = "thumbTint"; //$NON-NLS-1$
    public static final String ATTR_TRACK = "track"; //$NON-NLS-1$
    public static final String ATTR_TRACK_TINT = "trackTint"; //$NON-NLS-1$
    public static final String ATTR_SHOW_TEXT = "showText"; //$NON-NLS-1$
    public static final String ATTR_SPLIT_TRACK = "splitTrack"; //$NON-NLS-1$
    public static final String ATTR_STATE_LIST_ANIMATOR = "stateListAnimator"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ANIMATION = "layoutAnimation";

    // TextView
    public static final String ATTR_DRAWABLE_RIGHT = "drawableRight"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_LEFT = "drawableLeft"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_START = "drawableStart"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_END = "drawableEnd"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_BOTTOM = "drawableBottom"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_TOP = "drawableTop"; //$NON-NLS-1$
    public static final String ATTR_DRAWABLE_PADDING = "drawablePadding"; //$NON-NLS-1$

    // AppCompatTextView
    public static final String ATTR_DRAWABLE_RIGHT_COMPAT = "drawableRightCompat"; // $NON-NLS-1$
    public static final String ATTR_DRAWABLE_LEFT_COMPAT = "drawableLeftCompat"; // $NON-NLS-1$
    public static final String ATTR_DRAWABLE_START_COMPAT = "drawableStartCompat"; // $NON-NLS-1$
    public static final String ATTR_DRAWABLE_END_COMPAT = "drawableEndCompat"; // $NON-NLS-1$
    public static final String ATTR_DRAWABLE_BOTTOM_COMPAT = "drawableBottomCompat"; // $NON-NLS-1$
    public static final String ATTR_DRAWABLE_TOP_COMPAT = "drawableTopCompat"; // $NON-NLS-1$

    public static final String ATTR_USE_DEFAULT_MARGINS = "useDefaultMargins"; //$NON-NLS-1$
    public static final String ATTR_MARGINS_INCLUDED_IN_ALIGNMENT =
            "marginsIncludedInAlignment"; //$NON-NLS-1$

    public static final String VALUE_WRAP_CONTENT = "wrap_content"; //$NON-NLS-1$
    public static final String VALUE_FALSE = "false"; //$NON-NLS-1$
    public static final String VALUE_N_DP = "%ddp"; //$NON-NLS-1$
    public static final String VALUE_ZERO_DP = "0dp"; //$NON-NLS-1$
    public static final String VALUE_ONE_DP = "1dp"; //$NON-NLS-1$
    public static final String VALUE_TOP = "top"; //$NON-NLS-1$
    public static final String VALUE_BOTTOM = "bottom"; //$NON-NLS-1$
    public static final String VALUE_CENTER_VERTICAL = "center_vertical"; //$NON-NLS-1$
    public static final String VALUE_CENTER_HORIZONTAL = "center_horizontal"; //$NON-NLS-1$
    public static final String VALUE_FILL_HORIZONTAL = "fill_horizontal"; //$NON-NLS-1$
    public static final String VALUE_FILL_VERTICAL = "fill_vertical"; //$NON-NLS-1$
    public static final String VALUE_0 = "0"; //$NON-NLS-1$
    public static final String VALUE_1 = "1"; //$NON-NLS-1$

    // Gravity values. These have the GRAVITY_ prefix in front of value because we already
    // have VALUE_CENTER_HORIZONTAL defined for layouts, and its definition conflicts
    // (centerHorizontal versus center_horizontal)
    public static final String GRAVITY_VALUE_ = "center"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER = "center"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_LEFT = "left"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_RIGHT = "right"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_START = "start"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_END = "end"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_BOTTOM = "bottom"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_TOP = "top"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL_HORIZONTAL = "fill_horizontal"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL_VERTICAL = "fill_vertical"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER_HORIZONTAL = "center_horizontal"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CENTER_VERTICAL = "center_vertical"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CLIP_HORIZONTAL = "clip_horizontal"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_CLIP_VERTICAL = "clip_vertical"; //$NON-NLS-1$
    public static final String GRAVITY_VALUE_FILL = "fill"; //$NON-NLS-1$

    // Mockup
    public static final String ATTR_MOCKUP = "mockup"; //$NON-NLS-1$
    public static final String ATTR_MOCKUP_CROP = "mockup_crop"; //$NON-NLS-1$
    public static final String ATTR_MOCKUP_POSITION = "mockup_crop"; //$NON-NLS-1$
    public static final String ATTR_MOCKUP_OPACITY = "mockup_opacity"; //$NON-NLS-1$

    // Baselines
    /**
     * Root tag in baseline files (which can be the XML output report files from lint, or a
     * subset of these
     */
    @SuppressWarnings("unused") // used from IDE
    public static final String TAG_ISSUES = "issues";
    public static final String TAG_ISSUE = "issue";
    public static final String TAG_LOCATION = "location";
    public static final String ATTR_MESSAGE = "message";
    public static final String ATTR_FILE = "file";
    public static final String ATTR_LINE = "line";
    public static final String ATTR_COLUMN = "column";

    public static final class ImageViewAttributes {
        public static final String TINT = "tint";
    }

    public static final class PreferenceTags {
        public static final String CHECK_BOX_PREFERENCE = "CheckBoxPreference";
        public static final String EDIT_TEXT_PREFERENCE = "EditTextPreference";
        public static final String LIST_PREFERENCE = "ListPreference";
        public static final String MULTI_SELECT_LIST_PREFERENCE = "MultiSelectListPreference";
        public static final String PREFERENCE_CATEGORY = "PreferenceCategory";
        public static final String PREFERENCE_SCREEN = "PreferenceScreen";
        public static final String RINGTONE_PREFERENCE = "RingtonePreference";
        public static final String SWITCH_PREFERENCE = "SwitchPreference";
        public static final String INTENT = "intent";
    }

    public static final class PreferenceAttributes {
        public static final String ATTR_DEFAULT_VALUE = "defaultValue";
        public static final String ATTR_DEPENDENCY = "dependency";
        public static final String ATTR_DIALOG_ICON = "dialogIcon";
        public static final String ATTR_DISABLE_DEPENDENTS_STATE = "disableDependentsState";
        public static final String ATTR_ENTRIES = "entries";
        public static final String ATTR_ENTRY_VALUES = "entryValues";
        public static final String ATTR_ICON = "icon";
        public static final String ATTR_KEY = "key";
        public static final String ATTR_PERSISTENT = "persistent";
        public static final String ATTR_RINGTONE_TYPE = "ringtoneType";
        public static final String ATTR_SHOW_DEFAULT = "showDefault";
        public static final String ATTR_SHOW_SILENT = "showSilent";
        public static final String ATTR_SINGLE_LINE = "singleLine";
        public static final String ATTR_SUMMARY = "summary";
        public static final String ATTR_SUMMARY_ON = "summaryOn";
        public static final String ATTR_SUMMARY_OFF = "summaryOff";
        public static final String ATTR_SWITCH_TEXT_ON = "switchTextOn";
        public static final String ATTR_SWITCH_TEXT_OFF = "switchTextOff";
    }

    public static class MotionSceneTags {
        public static final String MOTION_SCENE = "MotionScene";
        public static final String TRANSITION = "Transition";
        public static final String STATE_SET = "StateSet";
        public static final String CONSTRAINT_SET = "ConstraintSet";
        public static final String CONSTRAINT = "Constraint";
        public static final String KEY_FRAME_SET = "KeyFrameSet";
        public static final String KEY_ATTRIBUTE = "KeyAttribute";
        public static final String KEY_CYCLE = "KeyCycle";
        public static final String KEY_POSITION = "KeyPosition";
        public static final String KEY_TRIGGER = "KeyTrigger";
        public static final String KEY_TIME_CYCLE = "KeyTimeCycle";
        public static final String ON_CLICK = "OnClick";
        public static final String ON_SWIPE = "OnSwipe";
        public static final String LAYOUT = "Layout";
        public static final String MOTION = "Motion";
        public static final String PROPERTY_SET = "PropertySet";
        public static final String TRANSFORM = "Transform";
        public static final String CUSTOM_ATTRIBUTE = "CustomAttribute";
        public static final String STATE = "State";
        public static final String VARIANT = "Variant";
    }

    public static class MotionSceneAttributes {
        public static final String ATTR_CUSTOM_ATTRIBUTE_NAME = "attributeName";
        public static final String ATTR_CUSTOM_COLOR_VALUE = "customColorValue";
        public static final String ATTR_CUSTOM_COLOR_DRAWABLE_VALUE = "customColorDrawableValue";
        public static final String ATTR_CUSTOM_INTEGER_VALUE = "customIntegerValue";
        public static final String ATTR_CUSTOM_FLOAT_VALUE = "customFloatValue";
        public static final String ATTR_CUSTOM_STRING_VALUE = "customStringValue";
        public static final String ATTR_CUSTOM_DIMENSION_VALUE = "customDimension";
        public static final String ATTR_CUSTOM_PIXEL_DIMENSION_VALUE = "customPixelDimension";
        public static final String ATTR_CUSTOM_BOOLEAN_VALUE = "customBoolean";
    }

    // Text Alignment values.
    public static class TextAlignment {
        public static final String NONE = "none"; //$NON-NLS-1$
        public static final String INHERIT = "inherit"; //$NON-NLS-1$
        public static final String GRAVITY = "gravity"; //$NON-NLS-1$
        public static final String TEXT_START = "textStart"; //$NON-NLS-1$
        public static final String TEXT_END = "textEnd"; //$NON-NLS-1$
        public static final String CENTER = "center"; //$NON-NLS-1$
        public static final String VIEW_START = "viewStart"; //$NON-NLS-1$
        public static final String VIEW_END = "viewEnd"; //$NON-NLS-1$
    }

    public static class TextStyle {
        public static final String VALUE_NORMAL = "normal"; //$NON-NLS-1$
        public static final String VALUE_BOLD = "bold"; //$NON-NLS-1$
        public static final String VALUE_ITALIC = "italic"; //$NON-NLS-1$
    }

    public static final class ViewAttributes {
        public static final String MIN_HEIGHT = "minHeight";
    }

    /** The top level android package as a prefix, "android.". */
    public static final String ANDROID_SUPPORT_PKG_PREFIX =
            ANDROID_PKG_PREFIX + "support."; //$NON-NLS-1$
    /** Architecture component package prefix */
    public static final String ANDROID_ARCH_PKG_PREFIX = ANDROID_PKG_PREFIX + "arch.";

    /** The android.view. package prefix */
    public static final String ANDROID_VIEW_PKG = ANDROID_PKG_PREFIX + "view."; //$NON-NLS-1$

    /** The android.widget. package prefix */
    public static final String ANDROID_WIDGET_PREFIX = ANDROID_PKG_PREFIX + "widget."; //$NON-NLS-1$

    /** The android.webkit. package prefix */
    public static final String ANDROID_WEBKIT_PKG = ANDROID_PKG_PREFIX + "webkit."; //$NON-NLS-1$

    /** The android.app. package prefix */
    public static final String ANDROID_APP_PKG = ANDROID_PKG_PREFIX + "app."; //$NON-NLS-1$

    /** The android.support.v4. package prefix */
    public static final String ANDROID_SUPPORT_V4_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "v4."; //$NON-NLS-1$

    /** The android.support.v7. package prefix */
    public static final String ANDROID_SUPPORT_V7_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "v7."; //$NON-NLS-1$

    /** The android.support.design. package prefix */
    public static final String ANDROID_SUPPORT_DESIGN_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "design."; //$NON-NLS-1$

    /** The com.google.android.material. package prefix */
    public static final String ANDROID_MATERIAL_PKG = "com.google.android.material.";

    /** The android.support.constraint. package prefix */
    public static final String CONSTRAINT_LAYOUT_PKG = "android.support.constraint.";

    /** The androidx.constraintlayout. package prefix */
    public static final String ANDROIDX_CONSTRAINT_LAYOUT_PKG = "androidx.constraintlayout.";

    /** The androidx.recyclerview. package prefix */
    public static final String ANDROIDX_RECYCLER_VIEW_PKG = "androidx.recyclerview.";

    /** The androidx.cardview. package prefix */
    public static final String ANDROIDX_CARD_VIEW_PKG = "androidx.cardview.";

    /** The androidx.gridlayout. package prefix */
    public static final String ANDROIDX_GRID_LAYOUT_PKG = "androidx.gridlayout.";

    /** The androidx.leanback. package prefix */
    public static final String ANDROIDX_LEANBACK_PKG = "androidx.leanback.";

    /** The androidx.coordinatorlayout. package prefix */
    public static final String ANDROIDX_COORDINATOR_LAYOUT_PKG = "androidx.coordinatorlayout.";

    /** The androidx.core. package prefix */
    public static final String ANDROIDX_CORE_PKG = "androidx.core.";

    /** The androidx.viewpager. package prefix */
    public static final String ANDROIDX_VIEWPAGER_PKG = "androidx.viewpager.";

    /** The androidx.appcompat. package prefix */
    public static final String ANDROIDX_APPCOMPAT_PKG = "androidx.appcompat.";

    /** The android.support.v17.leanback. package prefix */
    public static final String ANDROID_SUPPORT_LEANBACK_V17_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "v17.leanback."; //$NON-NLS-1$

    /** The com.google.android.gms. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_PKG = "com.google.android.gms."; //$NON-NLS-1$

    /** The com.google.android.gms.ads. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_ADS_PKG =
            GOOGLE_PLAY_SERVICES_PKG + "ads."; //$NON-NLS-1$

    /** The com.google.android.gms.ads. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_MAPS_PKG =
            GOOGLE_PLAY_SERVICES_PKG + "maps."; //$NON-NLS-1$

    /** The LayoutParams inner-class name suffix, .LayoutParams */
    public static final String DOT_LAYOUT_PARAMS = ".LayoutParams"; //$NON-NLS-1$

    /** The fully qualified class name of an EditText view */
    public static final String FQCN_EDIT_TEXT = "android.widget.EditText"; //$NON-NLS-1$

    /** The fully qualified class name of a LinearLayout view */
    public static final String FQCN_LINEAR_LAYOUT = "android.widget.LinearLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a RelativeLayout view */
    public static final String FQCN_RELATIVE_LAYOUT = "android.widget.RelativeLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a GridLayout view */
    public static final String FQCN_GRID_LAYOUT = "android.widget.GridLayout"; //$NON-NLS-1$

    public static final AndroidxName FQCN_GRID_LAYOUT_V7 =
            AndroidxName.of("android.support.v7.widget.", "GridLayout");

    /** The fully qualified class name of a FrameLayout view */
    public static final String FQCN_FRAME_LAYOUT = "android.widget.FrameLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a TableRow view */
    public static final String FQCN_TABLE_ROW = "android.widget.TableRow"; //$NON-NLS-1$

    /** The fully qualified class name of a TableLayout view */
    public static final String FQCN_TABLE_LAYOUT = "android.widget.TableLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a GridView view */
    public static final String FQCN_GRID_VIEW = "android.widget.GridView"; //$NON-NLS-1$

    /** The fully qualified class name of a TabWidget view */
    public static final String FQCN_TAB_WIDGET = "android.widget.TabWidget"; //$NON-NLS-1$

    /** The fully qualified class name of a Button view */
    public static final String FQCN_BUTTON = "android.widget.Button"; //$NON-NLS-1$

    /** The fully qualified class name of a CheckBox view */
    public static final String FQCN_CHECK_BOX = "android.widget.CheckBox"; //$NON-NLS-1$

    /** The fully qualified class name of a CheckedTextView view */
    public static final String FQCN_CHECKED_TEXT_VIEW =
            "android.widget.CheckedTextView"; //$NON-NLS-1$

    /** The fully qualified class name of an ImageButton view */
    public static final String FQCN_IMAGE_BUTTON = "android.widget.ImageButton"; //$NON-NLS-1$

    /** The fully qualified class name of a RatingBar view */
    public static final String FQCN_RATING_BAR = "android.widget.RatingBar"; //$NON-NLS-1$

    /** The fully qualified class name of a SeekBar view */
    public static final String FQCN_SEEK_BAR = "android.widget.SeekBar"; //$NON-NLS-1$

    /** The fully qualified class name of a MultiAutoCompleteTextView view */
    public static final String FQCN_AUTO_COMPLETE_TEXT_VIEW =
            "android.widget.AutoCompleteTextView"; //$NON-NLS-1$

    /** The fully qualified class name of a MultiAutoCompleteTextView view */
    public static final String FQCN_MULTI_AUTO_COMPLETE_TEXT_VIEW =
            "android.widget.MultiAutoCompleteTextView"; //$NON-NLS-1$

    /** The fully qualified class name of a RadioButton view */
    public static final String FQCN_RADIO_BUTTON = "android.widget.RadioButton"; //$NON-NLS-1$

    /** The fully qualified class name of a ToggleButton view */
    public static final String FQCN_TOGGLE_BUTTON = "android.widget.ToggleButton"; //$NON-NLS-1$

    /** The fully qualified class name of a Spinner view */
    public static final String FQCN_SPINNER = "android.widget.Spinner"; //$NON-NLS-1$

    /** The fully qualified class name of an AdapterView */
    public static final String FQCN_ADAPTER_VIEW = "android.widget.AdapterView"; //$NON-NLS-1$

    /** The fully qualified class name of a ListView */
    public static final String FQCN_LIST_VIEW = "android.widget.ListView"; //$NON-NLS-1$

    /** The fully qualified class name of an ExpandableListView */
    public static final String FQCN_EXPANDABLE_LIST_VIEW =
            "android.widget.ExpandableListView"; //$NON-NLS-1$

    /** The fully qualified class name of a GestureOverlayView */
    public static final String FQCN_GESTURE_OVERLAY_VIEW =
            "android.gesture.GestureOverlayView"; //$NON-NLS-1$

    /** The fully qualified class name of a DatePicker */
    public static final String FQCN_DATE_PICKER = "android.widget.DatePicker"; //$NON-NLS-1$

    /** The fully qualified class name of a TimePicker */
    public static final String FQCN_TIME_PICKER = "android.widget.TimePicker"; //$NON-NLS-1$

    /** The fully qualified class name of a RadioGroup */
    public static final String FQCN_RADIO_GROUP = "android.widgets.RadioGroup"; //$NON-NLS-1$

    /** The fully qualified class name of a Space */
    public static final String FQCN_SPACE = "android.widget.Space"; //$NON-NLS-1$

    public static final AndroidxName FQCN_SPACE_V7 =
            AndroidxName.of("android.support.v7.widget.", "Space");

    /** The fully qualified class name of a TextView view */
    public static final String FQCN_TEXT_VIEW = "android.widget.TextView"; //$NON-NLS-1$

    /** The fully qualified class name of an ImageView view */
    public static final String FQCN_IMAGE_VIEW = "android.widget.ImageView"; //$NON-NLS-1$

    /** The fully qualified class name of NavHostFragment Fragment subclass */
    public static final String FQCN_NAV_HOST_FRAGMENT =
            "androidx.navigation.fragment.NavHostFragment";

    /** The fully qualified class name of a ScrollView */
    public static final String FQCN_SCROLL_VIEW = "android.widget.ScrollView";

    public static final String ATTR_SRC = "src"; //$NON-NLS-1$
    public static final String ATTR_SRC_COMPAT = "srcCompat"; //$NON-NLS-1$

    public static final String ATTR_GRAVITY = "gravity"; //$NON-NLS-1$

    public static final String ATTR_WEIGHT_SUM = "weightSum"; //$NON-NLS-1$
    public static final String ATTR_EMS = "ems"; //$NON-NLS-1$

    public static final String VALUE_HORIZONTAL = "horizontal"; //$NON-NLS-1$

    public static final String GRADLE_PLUGIN_NAME = "com.android.tools.build:gradle:";
    public static final String GRADLE_MINIMUM_VERSION = "6.5";
    public static final String GRADLE_LATEST_VERSION = GRADLE_MINIMUM_VERSION;
    public static final String GRADLE_PLUGIN_MINIMUM_VERSION = "1.0.0";
    public static final String GRADLE_PLUGIN_RECOMMENDED_VERSION = "3.3.2";
    // Temporary - can be removed once the recommended version supports AIA (with splits).
    public static final String GRADLE_PLUGIN_LATEST_VERSION = GRADLE_PLUGIN_RECOMMENDED_VERSION;

    /** use api or implementation */
    @Deprecated public static final String GRADLE_COMPILE_CONFIGURATION = "compile";
    /** use api or implementation */
    @Deprecated public static final String GRADLE_TEST_COMPILE_CONFIGURATION = "testCompile";
    /** use api or implementation */
    @Deprecated
    public static final String GRADLE_ANDROID_TEST_COMPILE_CONFIGURATION = "androidTestCompile";
    public static final String GRADLE_IMPLEMENTATION_CONFIGURATION = "implementation";
    public static final String GRADLE_API_CONFIGURATION = "api";
    public static final String GRADLE_ANDROID_TEST_IMPLEMENTATION_CONFIGURATION =
            "androidTestImplementation";
    public static final String GRADLE_ANDROID_TEST_API_CONFIGURATION = "androidTestApi";

    public static final String GRADLE_ANDROID_TEST_UTIL_CONFIGURATION = "androidTestUtil";
    public static final String CURRENT_BUILD_TOOLS_VERSION = "29.0.2";
    public static final String SUPPORT_LIB_GROUP_ID = "com.android.support";
    public static final String SUPPORT_LIB_ARTIFACT = "com.android.support:support-v4";
    public static final String DESIGN_LIB_ARTIFACT = "com.android.support:design";
    public static final String APPCOMPAT_LIB_ARTIFACT_ID = "appcompat-v7";
    public static final String APPCOMPAT_LIB_ARTIFACT =
            SUPPORT_LIB_GROUP_ID + ":" + APPCOMPAT_LIB_ARTIFACT_ID;
    public static final String CARD_VIEW_LIB_ARTIFACT = "com.android.support:cardview-v7";
    public static final String GRID_LAYOUT_LIB_ARTIFACT = "com.android.support:gridlayout-v7";
    public static final String RECYCLER_VIEW_LIB_ARTIFACT = "com.android.support:recyclerview-v7";
    public static final String MAPS_ARTIFACT = "com.google.android.gms:play-services-maps";
    public static final String ADS_ARTIFACT = "com.google.android.gms:play-services-ads";
    public static final String LEANBACK_V17_ARTIFACT = "com.android.support:leanback-v17";
    public static final String ANNOTATIONS_LIB_ARTIFACT_ID = "support-annotations";
    public static final String ANNOTATIONS_LIB_ARTIFACT =
            SUPPORT_LIB_GROUP_ID + ":" + ANNOTATIONS_LIB_ARTIFACT_ID;
    public static final String MEDIA_ROUTER_LIB_ARTIFACT = "com.android.support:mediarouter-v7";

    public static final String ANDROIDX_MATERIAL_ARTIFACT = "com.google.android.material:material";
    public static final String ANDROIDX_CORE_UI_ARTIFACT = "androidx.core:core-ui";
    public static final String ANDROIDX_CARD_VIEW_ARTIFACT = "androidx.cardview:cardview";
    public static final String ANDROIDX_GRID_LAYOUT_ARTIFACT = "androidx.gridlayout:gridlayout";
    public static final String ANDROIDX_RECYCLER_VIEW_ARTIFACT =
            "androidx.recyclerview:recyclerview";
    public static final String ANDROIDX_LEANBACK_ARTIFACT = "androidx.leanback:leanback";
    public static final String ANDROIDX_ANNOTATIONS_ARTIFACT = "androidx.annotation:annotation";
    public static final String ANDROIDX_SUPPORT_LIB_ARTIFACT = "androidx.legacy:legacy-support-v4";
    public static final String ANDROIDX_VIEW_PAGER_LIB_ARTIFACT = "androidx.viewpager:viewpager";
    public static final String ANDROIDX_VIEW_PAGER2_LIB_ARTIFACT = "androidx.viewpager2:viewpager2";
    public static final String ANDROIDX_APPCOMPAT_LIB_ARTIFACT = "androidx.appcompat:appcompat";
    public static final String ANDROIDX_CONSTRAINT_LAYOUT_LIB_ARTIFACT =
            "androidx.constraintlayout:constraintlayout";
    public static final String ANDROIDX_COORDINATOR_LAYOUT_LIB_ARTIFACT =
            "androidx.coordinatorlayout:coordinatorlayout";

    // Annotations
    public static final AndroidxName SUPPORT_ANNOTATIONS_PREFIX =
            AndroidxName.of("android.support.annotation.");

    public static final AndroidxName INT_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IntDef");
    public static final AndroidxName LONG_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "LongDef");
    public static final AndroidxName STRING_DEF_ANNOTATION =
            AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "StringDef");
    public static final String TYPE_DEF_VALUE_ATTRIBUTE = "value";
    public static final String TYPE_DEF_FLAG_ATTRIBUTE = "flag";
    public static final String FN_ANNOTATIONS_ZIP = "annotations.zip";

    public static final String VIEW_BINDING_ARTIFACT = "com.android.databinding:viewbinding";
    public static final String ANDROIDX_VIEW_BINDING_ARTIFACT = "androidx.databinding:viewbinding";

    // Data Binding MISC
    public static final String DATA_BINDING_LIB_ARTIFACT = "com.android.databinding:library";
    // processor is always AndroidX
    public static final String DATA_BINDING_ANNOTATION_PROCESSOR_ARTIFACT =
            "androidx.databinding:databinding-compiler";
    public static final String DATA_BINDING_ADAPTER_LIB_ARTIFACT =
            "com.android.databinding:adapters";
    public static final String ANDROIDX_DATA_BINDING_LIB_ARTIFACT =
            "androidx.databinding:databinding-runtime";
    public static final String DATA_BINDING_BASELIB_ARTIFACT =
            "com.android.databinding:baseLibrary";
    public static final String ANDROIDX_DATA_BINDING_BASELIB_ARTIFACT =
            "androidx.databinding:databinding-common";
    public static final String ANDROIDX_DATA_BINDING_ADAPTER_LIB_ARTIFACT =
            "androidx.databinding:databinding-adapters";
    public static final String[] TAGS_DATA_BINDING =
            new String[] {TAG_VARIABLE, TAG_IMPORT, TAG_LAYOUT, TAG_DATA};
    public static final String[] ATTRS_DATA_BINDING =
            new String[] {ATTR_NAME, ATTR_TYPE, ATTR_CLASS, ATTR_ALIAS};

    public static final AndroidxName DATA_BINDING_PKG = AndroidxName.of("android.databinding.");
    public static final String CLASS_NAME_DATA_BINDING_COMPONENT = "DataBindingComponent";
    public static final AndroidxName CLASS_DATA_BINDING_COMPONENT =
            AndroidxName.of("android.databinding.", CLASS_NAME_DATA_BINDING_COMPONENT);

    public static final AndroidxName CLASS_DATA_BINDING_BASE_BINDING =
            AndroidxName.of("android.databinding.", "ViewDataBinding");
    public static final AndroidxName CLASS_DATA_BINDING_BINDABLE =
            AndroidxName.of("android.databinding.", "Bindable");
    public static final AndroidxName CLASS_DATA_BINDING_VIEW_STUB_PROXY =
            AndroidxName.of("android.databinding.", "ViewStubProxy");
    public static final AndroidxName BINDING_ADAPTER_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingAdapter");
    public static final AndroidxName BINDING_CONVERSION_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingConversion");
    public static final AndroidxName BINDING_METHODS_ANNOTATION =
            AndroidxName.of("android.databinding.", "BindingMethods");
    public static final AndroidxName INVERSE_BINDING_ADAPTER_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingAdapter");
    public static final AndroidxName INVERSE_BINDING_METHOD_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingMethod");
    public static final AndroidxName INVERSE_BINDING_METHODS_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseBindingMethods");
    public static final AndroidxName INVERSE_METHOD_ANNOTATION =
            AndroidxName.of("android.databinding.", "InverseMethod");
    public static final AndroidxName CLASS_LIVE_DATA =
            AndroidxName.of("android.arch.lifecycle.", "LiveData");
    public static final AndroidxName CLASS_OBSERVABLE_BOOLEAN =
            AndroidxName.of("android.databinding.", "ObservableBoolean");
    public static final AndroidxName CLASS_OBSERVABLE_BYTE =
            AndroidxName.of("android.databinding.", "ObservableByte");
    public static final AndroidxName CLASS_OBSERVABLE_CHAR =
            AndroidxName.of("android.databinding.", "ObservableChar");
    public static final AndroidxName CLASS_OBSERVABLE_SHORT =
            AndroidxName.of("android.databinding.", "ObservableShort");
    public static final AndroidxName CLASS_OBSERVABLE_INT =
            AndroidxName.of("android.databinding.", "ObservableInt");
    public static final AndroidxName CLASS_OBSERVABLE_LONG =
            AndroidxName.of("android.databinding.", "ObservableLong");
    public static final AndroidxName CLASS_OBSERVABLE_FLOAT =
            AndroidxName.of("android.databinding.", "ObservableFloat");
    public static final AndroidxName CLASS_OBSERVABLE_DOUBLE =
            AndroidxName.of("android.databinding.", "ObservableDouble");
    public static final AndroidxName CLASS_OBSERVABLE_FIELD =
            AndroidxName.of("android.databinding.", "ObservableField");
    public static final AndroidxName CLASS_OBSERVABLE_PARCELABLE =
            AndroidxName.of("android.databinding.", "ObservableParcelable");


    /** Name of keep attribute in XML */
    public static final String ATTR_KEEP = "keep";
    /** Name of discard attribute in XML (to mark resources as not referenced, despite guesses) */
    public static final String ATTR_DISCARD = "discard";
    /** Name of attribute in XML to control whether we should guess resources to keep */
    public static final String ATTR_SHRINK_MODE = "shrinkMode";
    /** {@linkplain #ATTR_SHRINK_MODE} value to only shrink explicitly encountered resources */
    public static final String VALUE_STRICT = "strict";
    /** {@linkplain #ATTR_SHRINK_MODE} value to keep possibly referenced resources */
    public static final String VALUE_SAFE = "safe";

    /** Prefix of the Android Support Repository path */
    public static final String ANDROID_SUPPORT_ARTIFACT_PREFIX = "com.android.";
    /** Prefix of the Google Repository path */
    public static final String GOOGLE_SUPPORT_ARTIFACT_PREFIX = "com.google.android.";
    /** Prefix of firebase groupIds */
    public static final String FIREBASE_ARTIFACT_PREFIX = "com.google.firebase.";

    @Deprecated
    public static String androidCmdName() {
        throw new UnsupportedOperationException(
                "The \"android\" command is no longer included in the SDK. Any references to it (e.g. "
                        + "by third-party plugins) should be removed.");
    }

    /** Folder where proguard rules are located in jar, aar and project generated resources */
    public static final String PROGUARD_RULES_FOLDER = "meta-inf/proguard";

    /** Folder where configuration files for R8 and other tools are located in jar files */
    public static final String COM_ANDROID_TOOLS_FOLDER = "com.android.tools";

    /** Folder where configuration files for R8 and other tools are located in jar files */
    public static final String TOOLS_CONFIGURATION_FOLDER = "meta-inf/" + COM_ANDROID_TOOLS_FOLDER;

    public static final String FD_PREFAB_PACKAGE = "prefab";
}
