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

package com.android;

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
@SuppressWarnings("unused")
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
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     *     environment variables</a>
     */
    public static final String ANDROID_HOME_ENV = "ANDROID_HOME";

    /**
     * ANDROID_SDK_ROOT environment variable that specifies the installation path of an Android SDK.
     *
     * @deprecated Use {@link #ANDROID_HOME_ENV} instead.
     * @see <a href="https://developer.android.com/studio/command-line/variables">Android SDK
     *     environment variables</a>
     */
    @Deprecated public static final String ANDROID_SDK_ROOT_ENV = "ANDROID_SDK_ROOT";

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
    public static final String GRADLE_DISTRIBUTION_URL_PROPERTY = "distributionUrl";

    /** Properties in aar-metadata.properties file */
    public static final String AAR_FORMAT_VERSION_PROPERTY = "aarFormatVersion";
    public static final String AAR_METADATA_VERSION_PROPERTY = "aarMetadataVersion";
    public static final String MIN_COMPILE_SDK_PROPERTY = "minCompileSdk";
    public static final String MIN_ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY =
            "minAndroidGradlePluginVersion";
    public static final String FORCE_COMPILE_SDK_PREVIEW_PROPERTY = "forceCompileSdkPreview";
    public static final String MIN_COMPILE_SDK_EXTENSION_PROPERTY = "minCompileSdkExtension";

    /** Properties in app-metadata.properties file */
    public static final String APP_METADATA_VERSION_PROPERTY = "appMetadataVersion";
    public static final String ANDROID_GRADLE_PLUGIN_VERSION_PROPERTY =
            "androidGradlePluginVersion";
    public static final String ANDROID_GAME_DEVELOPMENT_EXTENSION_VERSION_PROPERTY = "agdeVersion";

    /** Properties in lint-model-metadata.properties file */
    public static final String MAVEN_GROUP_ID_PROPERTY = "mavenGroupId";

    public static final String MAVEN_ARTIFACT_ID_PROPERTY = "mavenArtifactId";

    /**
     * The encoding we strive to use for all files we write.
     *
     * <p>When possible, use the APIs which take a {@link java.nio.charset.Charset} and pass in
     * {@link java.nio.charset.StandardCharsets#UTF_8} instead of using the String encoding method.
     */
    public static final String UTF_8 = "UTF-8";

    /** Charset for the ini file handled by the SDK. */
    public static final String INI_CHARSET = UTF_8;

    /** Path separator used by Gradle */
    public static final String GRADLE_PATH_SEPARATOR = ":";

    /** An SDK Project's AndroidManifest.xml file */
    public static final String FN_ANDROID_MANIFEST_XML = "AndroidManifest.xml";

    public static final String FN_SHARED_LIBRARY_ANDROID_MANIFEST_XML =
            "SharedLibraryAndroidManifest.xml";
    /** pre-dex jar filename. i.e. "classes.jar" */
    public static final String FN_CLASSES_JAR = "classes.jar";
    /** api.jar filename */
    public static final String FN_API_JAR = "api.jar";
    /** Dex filename inside the APK. i.e. "classes.dex" */
    public static final String FN_APK_CLASSES_DEX = "classes.dex";
    /** Dex filename inside the APK. e.g. "classes2.dex" */
    public static final String FN_APK_CLASSES_N_DEX = "classes%d.dex";
    /** Regex to match dex filenames inside the APK. e.g., classes.dex, classes2.dex */
    public static final String REGEX_APK_CLASSES_DEX = "classes\\d*\\.dex";

    /** intermediate publishing between projects */
    public static final String FN_INTERMEDIATE_RES_JAR = "res.jar";
    public static final String FN_INTERMEDIATE_FULL_JAR = "full.jar";

    /** list of splits for a variant */
    public static final String FN_APK_LIST = "apk-list.gson";

    /** An SDK Project's build.xml file */
    public static final String FN_BUILD_XML = "build.xml";
    /** An SDK Project's build.gradle file */
    public static final String FN_BUILD_GRADLE = "build.gradle";
    /** An SDK Project's build.gradle Kotlin script file */
    public static final String FN_BUILD_GRADLE_KTS = "build.gradle.kts";
    /** An SDK Project's settings.gradle file */
    public static final String FN_SETTINGS_GRADLE = "settings.gradle";
    /** An SDK Project's settings.gradle Kotlin script file */
    public static final String FN_SETTINGS_GRADLE_KTS = "settings.gradle.kts";
    /** An SDK Project's gradle.properties file */
    public static final String FN_GRADLE_PROPERTIES = "gradle.properties";
    /** An SDK Project's libs.versions.toml file */
    public static final String FN_VERSION_CATALOG = "libs.versions.toml";
    /** An SDK Project's gradle daemon executable */
    public static final String FN_GRADLE_UNIX = "gradle";
    /** An SDK Project's gradle.bat daemon executable (gradle for windows) */
    public static final String FN_GRADLE_WIN = FN_GRADLE_UNIX + ".bat";
    /** An SDK Project's gradlew file */
    public static final String FN_GRADLE_WRAPPER_UNIX = "gradlew";
    /** An SDK Project's gradlew.bat file (gradlew for windows) */
    public static final String FN_GRADLE_WRAPPER_WIN =
            FN_GRADLE_WRAPPER_UNIX + ".bat";
    /** An SDK Project's gradle wrapper library */
    public static final String FN_GRADLE_WRAPPER_JAR = "gradle-wrapper.jar";
    /** Name of the framework library, i.e. "android.jar" */
    public static final String FN_FRAMEWORK_LIBRARY = "android.jar";
    /** Name of the library containing the packages that should be included in the system modules */
    public static final String FN_CORE_FOR_SYSTEM_MODULES =
            "core-for-system-modules.jar";
    /** Name of the framework library, i.e. "uiautomator.jar" */
    public static final String FN_UI_AUTOMATOR_LIBRARY = "uiautomator.jar";
    /** Name of the layout attributes, i.e. "attrs.xml" */
    public static final String FN_ATTRS_XML = "attrs.xml";
    /** Name of the layout attributes, i.e. "attrs_manifest.xml" */
    public static final String FN_ATTRS_MANIFEST_XML = "attrs_manifest.xml";
    /** framework aidl import file */
    public static final String FN_FRAMEWORK_AIDL = "framework.aidl";
    /** framework renderscript folder */
    public static final String FN_FRAMEWORK_RENDERSCRIPT = "renderscript";
    /** framework include folder */
    public static final String FN_FRAMEWORK_INCLUDE = "include";
    /** framework include (clang) folder */
    public static final String FN_FRAMEWORK_INCLUDE_CLANG = "clang-include";
    /** layoutlib.jar file */
    public static final String FN_LAYOUTLIB_JAR = "layoutlib.jar";
    /** widget list file */
    public static final String FN_WIDGETS = "widgets.txt";
    /** Intent activity actions list file */
    public static final String FN_INTENT_ACTIONS_ACTIVITY = "activity_actions.txt";
    /** Intent broadcast actions list file */
    public static final String FN_INTENT_ACTIONS_BROADCAST = "broadcast_actions.txt";
    /** Intent service actions list file */
    public static final String FN_INTENT_ACTIONS_SERVICE = "service_actions.txt";
    /** Intent category list file */
    public static final String FN_INTENT_CATEGORIES = "categories.txt";
    /** Name of the lint library, i.e. "lint.jar" */
    public static final String FN_LINT_JAR = "lint.jar";

    /** annotations support jar */
    public static final String FN_ANNOTATIONS_JAR = "annotations.jar";

    /** platform build property file */
    public static final String FN_BUILD_PROP = "build.prop";
    /** plugin properties file */
    public static final String FN_PLUGIN_PROP = "plugin.prop";
    /** add-on manifest file */
    public static final String FN_MANIFEST_INI = "manifest.ini";
    /** add-on layout device XML file. */
    public static final String FN_DEVICES_XML = "devices.xml";
    /** hardware properties definition file */
    public static final String FN_HARDWARE_INI = "hardware-properties.ini";

    /** project property file */
    public static final String FN_PROJECT_PROPERTIES = "project.properties";

    /** project local property file */
    public static final String FN_LOCAL_PROPERTIES = "local.properties";

    /** project ant property file */
    public static final String FN_ANT_PROPERTIES = "ant.properties";

    /** project local property file */
    public static final String FN_GRADLE_WRAPPER_PROPERTIES =
            "gradle-wrapper.properties";

    /** Skin layout file */
    public static final String FN_SKIN_LAYOUT = "layout";

    /** name of the art runtime profile in aar files (located in the android private assets) */
    public static final String FN_ART_PROFILE = "baseline-prof.txt";

    public static final String FN_BINART_ART_PROFILE_FOLDER_IN_APK = "assets/dexopt";
    public static final String FN_BINART_ART_PROFILE_FOLDER_IN_AAB =
            "com.android.tools.build.profiles";
    public static final String FN_BINARY_ART_PROFILE = "baseline.prof";
    public static final String FN_BINARY_ART_PROFILE_METADATA = "baseline.profm";

    /** aapt executable (with extension for the current OS) */
    public static final String FN_AAPT =
            "aapt" + ext(".exe", "");

    /** aapt2 executable (with extension for the current OS) */
    public static final String FN_AAPT2 =
            "aapt2" + ext(".exe", "");

    /** aidl executable (with extension for the current OS) */
    public static final String FN_AIDL =
            "aidl" + ext(".exe", "");

    /** renderscript executable (with extension for the current OS) */
    public static final String FN_RENDERSCRIPT =
            "llvm-rs-cc" + ext(".exe", "");

    /** renderscript support exe (with extension for the current OS) */
    public static final String FN_BCC_COMPAT =
            "bcc_compat" + ext(".exe", "");

    /** renderscript support linker for ARM (with extension for the current OS) */
    public static final String FN_LD_ARM =
            "arm-linux-androideabi-ld" + ext(".exe", "");

    /** renderscript support linker for ARM64 (with extension for the current OS) */
    public static final String FN_LD_ARM64 =
            "aarch64-linux-android-ld" + ext(".exe", "");

    /** renderscript support linker for X86 (with extension for the current OS) */
    public static final String FN_LD_X86 =
            "i686-linux-android-ld" + ext(".exe", "");

    /** renderscript support linker for X86_64 (with extension for the current OS) */
    public static final String FN_LD_X86_64 =
            "x86_64-linux-android-ld" + ext(".exe", "");

    /** renderscript support linker for MIPS (with extension for the current OS) */
    public static final String FN_LD_MIPS =
            "mipsel-linux-android-ld" + ext(".exe", "");

    /**
     * 64 bit (host) renderscript support linker for all ABIs (with extension for the current OS)
     */
    public static final String FN_LLD =
            "lld" + ext(".exe", "");

    /** adb executable (with extension for the current OS) */
    public static final String FN_ADB =
            "adb" + ext(".exe", "");

    /** sqlite3 executable (with extension for the current OS) */
    public static final String FN_SQLITE3 =
            "sqlite3" + ext(".exe", "");

    /** emulator executable for the current OS */
    public static final String FN_EMULATOR =
            "emulator" + ext(".exe", "");

    /** emulator-check executable for the current OS */
    public static final String FN_EMULATOR_CHECK =
            "emulator-check" + ext(".exe", "");

    /** zipalign executable (with extension for the current OS) */
    public static final String FN_ZIPALIGN =
            "zipalign" + ext(".exe", "");

    /** dexdump executable (with extension for the current OS) */
    public static final String FN_DEXDUMP =
            "dexdump" + ext(".exe", "");

    /** proguard executable (with extension for the current OS) */
    public static final String FN_PROGUARD =
            "proguard" + ext(".bat", ".sh");

    /** find_lock for Windows (with extension for the current OS) */
    public static final String FN_FIND_LOCK =
            "find_lock" + ext(".exe", "");

    /** hprof-conv executable (with extension for the current OS) */
    public static final String FN_HPROF_CONV =
            "hprof-conv" + ext(".exe", "");

    /** jack.jar */
    public static final String FN_JACK = "jack.jar";
    /** jill.jar */
    public static final String FN_JILL = "jill.jar";
    /** code coverage plugin for jack */
    public static final String FN_JACK_COVERAGE_PLUGIN = "jack-coverage-plugin.jar";
    /** jack-jacoco-report.jar */
    public static final String FN_JACK_JACOCO_REPORTER = "jack-jacoco-reporter.jar";
    /** core-lambda-stubs.jar necessary for lambda compilation. */
    public static final String FN_CORE_LAMBDA_STUBS = "core-lambda-stubs.jar";

    /** split-select */
    public static final String FN_SPLIT_SELECT = "split-select" + ext(".exe", "");

    /** glslc */
    public static final String FD_SHADER_TOOLS = "shader-tools";

    public static final String FN_GLSLC = "glslc" + ext(".exe", "");

    /** properties file for SDK Updater packages */
    public static final String FN_SOURCE_PROP = "source.properties";
    /** properties file for content hash of installed packages */
    public static final String FN_CONTENT_HASH_PROP = "content_hash.properties";
    /** properties file for the SDK */
    public static final String FN_SDK_PROP = "sdk.properties";

    public static final String FN_ANDROIDX_RS_JAR = "androidx-rs.jar";
    public static final String FN_RENDERSCRIPT_V8_JAR = "renderscript-v8.jar";

    public static final String FN_ANDROIDX_RENDERSCRIPT_PACKAGE =
            "androidx.renderscript";
    public static final String FN_RENDERSCRIPT_V8_PACKAGE =
            "android.support.v8.renderscript";

    /** filename for gdbserver. */
    public static final String FN_GDBSERVER = "gdbserver";

    public static final String FN_GDB_SETUP = "gdb.setup";

    /** proguard config file in a bundle. */
    public static final String FN_PROGUARD_TXT = "proguard.txt";
    /** global Android proguard config file */
    public static final String FN_ANDROID_PROGUARD_FILE = "proguard-android.txt";
    /** global Android proguard config file with optimization enabled */
    public static final String FN_ANDROID_OPT_PROGUARD_FILE =
            "proguard-android-optimize.txt";
    /** default proguard config file with new file extension (for project specific stuff) */
    public static final String FN_PROJECT_PROGUARD_FILE = "proguard-project.txt";
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

    /** File holding list of resource symbols */
    public static final String FN_RESOURCE_SYMBOLS = "resources_symbols.txt";
    /** File holding relative class entries */
    public static final String FN_CLASS_LIST = "classes.txt";

    /** File holding data extracted from the navigation xml files */
    public static final String FN_NAVIGATION_JSON = "navigation.json";

    /* Folder Names for Android Projects . */

    /** Resources folder name, i.e. "res". */
    public static final String FD_RESOURCES = "res";
    /** Assets folder name, i.e. "assets" */
    public static final String FD_ASSETS = "assets";
    /**
     * Default source folder name in an SDK project, i.e. "src".
     *
     * <p>Note: this is not the same as {@link #FD_PKG_SOURCES} which is an SDK sources folder for
     * packages.
     */
    public static final String FD_SOURCES = "src";
    /** Default main source set folder name, i.e. "main" */
    public static final String FD_MAIN = "main";
    /** Default test source set folder name, i.e. "androidTest" */
    public static final String FD_TEST = "androidTest";
    /** Default unit test source set folder name, i.e. "test" */
    public static final String FD_UNIT_TEST = "test";
    /** Default test fixtures source set folder name, i.e. "testFixtures" */
    public static final String FD_TEST_FIXTURES = "testFixtures";
    /** Default java code folder name, i.e. "java" */
    public static final String FD_JAVA = "java";
    /** Default native code folder name, i.e. "jni" */
    public static final String FD_JNI = "jni";
    /** Default gradle folder name, i.e. "gradle" */
    public static final String FD_GRADLE = "gradle";
    /** Default gradle wrapper folder name, i.e. "gradle/wrapper" */
    public static final String FD_GRADLE_WRAPPER =
            FD_GRADLE + File.separator + "wrapper";
    /** Default generated source folder name, i.e. "gen" */
    public static final String FD_GEN_SOURCES = "gen";
    /**
     * Default native library folder name inside the project, i.e. "libs" While the folder inside
     * the .apk is "lib", we call that one libs because that's what we use in ant for both .jar and
     * .so and we need to make the 2 development ways compatible.
     */
    public static final String FD_NATIVE_LIBS = "libs";
    /** Native lib folder inside the APK: "lib" */
    public static final String FD_APK_NATIVE_LIBS = "lib";
    /** Default output folder name, i.e. "bin" */
    public static final String FD_OUTPUT = "bin";
    /** Classes output folder name, i.e. "classes" */
    public static final String FD_CLASSES_OUTPUT = "classes";
    /** proguard output folder for mapping, etc.. files */
    public static final String FD_PROGUARD = "proguard";
    /** aidl output folder for copied aidl files */
    public static final String FD_AIDL = "aidl";
    /** aar libs folder */
    public static final String FD_AAR_LIBS = "libs";
    /** symbols output folder */
    public static final String FD_SYMBOLS = "symbols";
    /** resource blame output folder */
    public static final String FD_BLAME = "blame";
    /** bundle output folder */
    public static final String FD_BUNDLE = "bundle";
    /** Machine learning models folder. */
    public static final String FD_ML_MODELS = "ml";
    /** rs Libs output folder for support mode */
    public static final String FD_RS_LIBS = "rsLibs";
    /** rs Libs output folder for support mode */
    public static final String FD_RS_OBJ = "rsObj";

    /** jars folder */
    public static final String FD_JARS = "jars";

    /** Intermediates folder under the build directory */
    public static final String FD_INTERMEDIATES = "intermediates";
    /** logs folder under the build directory */
    public static final String FD_LOGS = "logs";
    /** outputs folder under the build directory */
    public static final String FD_OUTPUTS = "outputs";
    /** generated folder under the build directory */
    public static final String FD_GENERATED = "generated";

    /* Folder Names for the Android SDK */

    /** Name of the SDK platforms folder. */
    public static final String FD_PLATFORMS = "platforms";
    /** Name of the SDK addons folder. */
    public static final String FD_ADDONS = "add-ons";
    /** Name of the SDK system-images folder. */
    public static final String FD_SYSTEM_IMAGES = "system-images";
    /**
     * Name of the SDK sources folder where source packages are installed.
     *
     * <p>Note this is not the same as {@link #FD_SOURCES} which is the folder name where sources
     * are installed inside a project.
     */
    public static final String FD_PKG_SOURCES = "sources";
    /** Name of the legacy SDK tools folder. */
    public static final String FD_TOOLS = "tools";
    /** Name of the SDK command-line tools folder. */
    public static final String FD_CMDLINE_TOOLS = "cmdline-tools";
    /** Name of the SDK emulator folder. */
    public static final String FD_EMULATOR = "emulator";
    /** Name of the SDK tools/support folder. */
    public static final String FD_SUPPORT = "support";
    /** Name of the SDK platform tools folder. */
    public static final String FD_PLATFORM_TOOLS = "platform-tools";
    /** Name of the SDK build tools folder. */
    public static final String FD_BUILD_TOOLS = "build-tools";
    /** Name of the SDK tools/lib folder. */
    public static final String FD_LIB = "lib";
    /** Name of the SDK docs folder. */
    public static final String FD_DOCS = "docs";
    /** Name of the doc folder containing API reference doc (javadoc) */
    public static final String FD_DOCS_REFERENCE = "reference";
    /** Name of the SDK images folder. */
    public static final String FD_IMAGES = "images";
    /** Name of the ABI to support. */
    public static final String ABI_ARMEABI = "armeabi";

    public static final String ABI_ARMEABI_V7A = "armeabi-v7a";
    public static final String ABI_ARM64_V8A = "arm64-v8a";
    public static final String ABI_INTEL_ATOM = "x86";
    public static final String ABI_INTEL_ATOM64 = "x86_64";
    public static final String ABI_MIPS = "mips";
    public static final String ABI_MIPS64 = "mips64";
    /** Name of the CPU arch to support. */
    public static final String CPU_ARCH_ARM = "arm";

    public static final String CPU_ARCH_ARM64 = "arm64";
    public static final String CPU_ARCH_INTEL_ATOM = "x86";
    public static final String CPU_ARCH_INTEL_ATOM64 = "x86_64";
    public static final String CPU_ARCH_MIPS = "mips";
    /** TODO double-check this is appropriate value for mips64 */
    public static final String CPU_ARCH_MIPS64 = "mips64";
    /** Name of the CPU model to support. */
    public static final String CPU_MODEL_CORTEX_A8 = "cortex-a8";

    /** Name of the SDK skins folder. */
    public static final String FD_SKINS = "skins";
    /** Name of the SDK samples folder. */
    public static final String FD_SAMPLES = "samples";
    /** Name of the SDK extras folder. */
    public static final String FD_EXTRAS = "extras";

    public static final String FD_ANDROID_EXTRAS = "android";
    public static final String FD_M2_REPOSITORY = "m2repository";
    public static final String FD_NDK = "ndk-bundle";
    public static final String FD_LLDB = "lldb";
    public static final String FD_CMAKE = "cmake";
    public static final String FD_NDK_SIDE_BY_SIDE = "ndk";
    public static final String FD_GAPID = "gapid";
    /** Sample data for the project sample data */
    public static final String FD_SAMPLE_DATA = "sampledata";

    /**
     * Name of an extra's sample folder. Ideally extras should have one {@link #FD_SAMPLES} folder
     * containing one or more sub-folders (one per sample). However some older extras might contain
     * a single "sample" folder with directly the samples files in it. When possible we should
     * encourage extras' owners to move to the multi-samples format.
     */
    public static final String FD_SAMPLE = "sample";
    /** Name of the SDK templates folder, i.e. "templates" */
    public static final String FD_TEMPLATES = "templates";
    /** Name of the SDK Ant folder, i.e. "ant" */
    public static final String FD_ANT = "ant";
    /** Name of the SDK data folder, i.e. "data" */
    public static final String FD_DATA = "data";
    /** Name of the SDK renderscript folder, i.e. "rs" */
    public static final String FD_RENDERSCRIPT = "rs";
    /** Name of the Java resources folder, i.e. "resources" */
    public static final String FD_JAVA_RES = "resources";
    /** Name of the SDK resources folder, i.e. "res" */
    public static final String FD_RES = "res";
    /** Name of the SDK font folder, i.e. "fonts" */
    public static final String FD_FONTS = "fonts";
    /** Name of the android sources directory and the root of the SDK sources package folder. */
    public static final String FD_ANDROID_SOURCES = "sources";
    /** Name of the addon libs folder. */
    public static final String FD_ADDON_LIBS = "libs";
    /** Name of the merged resources folder. */
    public static final String FD_MERGED = "merged";
    /** Name of the compiled resources folder. */
    public static final String FD_COMPILED = "compiled";
    /** Name of the folder containing partial R files. */
    public static final String FD_PARTIAL_R = "partial-r";
    /** Name of the output dex folder. */
    public static final String FD_DEX = "dex";
    /** Name of the generated source folder. */
    public static final String FD_SOURCE_GEN = "source";
    /** Name of the generated R.class source folder */
    public static final String FD_RES_CLASS = "r";
    /** Name of folder where merged XML files are placed before processing by resource compiler. */
    public static final String FD_MERGED_DOT_DIR = "merged.dir";
    /**
     * Name of folder where data-binding stripped merged layout XML files are placed before
     * processing by resource compiler.
     */
    public static final String FD_STRIPPED_DOT_DIR = "stripped.dir";

    /** Name of the cache folder in the $HOME/.android. */
    public static final String FD_CACHE = "cache";

    /** Name of the build attribution internal output folder */
    public static final String FD_BUILD_ATTRIBUTION = "build-attribution";

    /** API codename of a release (non preview) system image or platform. */
    public static final String CODENAME_RELEASE = "REL";

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
            "http://schemas.android.com/apk/res/%1$s";

    /** The name of the uses-library that provides "android.test.runner" */
    public static final String ANDROID_TEST_RUNNER_LIB = "android.test.runner";

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
    public static final String SKIN_DEFAULT = "default";

    /** SDK property: ant templates revision */
    public static final String PROP_SDK_ANT_TEMPLATES_REVISION =
            "sdk.ant.templates.revision";

    /** SDK property: default skin */
    public static final String PROP_SDK_DEFAULT_SKIN = "sdk.skin.default";

    /** LLDB SDK package major.minor revision compatible with the current version of Studio */
    public static final String LLDB_PINNED_REVISION = "3.1";

    /* Android Class Constants */
    public static final String CLASS_ACTIVITY = "android.app.Activity";

    public static final String CLASS_WATCHFACE_WSL
            = "android.support.wearable.watchface.WatchFaceService";

    public static final String CLASS_WATCHFACE_ANDROIDX =
            "androidx.wear.watchface.WatchFaceService";

    public static final String CLASS_TILE_SERVICE = "androidx.wear.tiles.TileService";

    public static final String CLASS_COMPLICATION_SERVICE_ANDROIDX =
            "androidx.wear.watchface.complications.datasource.ComplicationDataSourceService";

    public static final String CLASS_COMPLICATION_SERVICE_WSL =
            "android.support.wearable.complications.ComplicationProviderService";

    public static final String CLASS_APPLICATION = "android.app.Application";

    public static final String CLASS_SERVICE = "android.app.Service";

    public static final String CLASS_BROADCASTRECEIVER =
            "android.content.BroadcastReceiver";

    public static final String CLASS_CONTENTPROVIDER =
            "android.content.ContentProvider";

    public static final String CLASS_ATTRIBUTE_SET = "android.util.AttributeSet";

    public static final String CLASS_INSTRUMENTATION = "android.app.Instrumentation";

    public static final String CLASS_INSTRUMENTATION_RUNNER =
            "android.test.InstrumentationTestRunner";
    public static final String CLASS_BUNDLE = "android.os.Bundle";
    public static final String CLASS_R = "android.R";
    public static final String CLASS_R_PREFIX = CLASS_R + ".";
    public static final String CLASS_MANIFEST = "android.Manifest";
    public static final String CLASS_MANIFEST_PERMISSION =
            "android.Manifest$permission";
    public static final String CLASS_INTENT = "android.content.Intent";
    public static final String CLASS_CONTEXT = "android.content.Context";
    public static final String CLASS_CONFIGURATION = "android.content.res.Configuration";
    public static final String CLASS_RESOURCES = "android.content.res.Resources";
    public static final String CLS_TYPED_ARRAY = "android.content.res.TypedArray";
    public static final String CLASS_VIEW = "android.view.View";
    public static final String CLASS_VIEWGROUP = "android.view.ViewGroup";
    public static final String CLASS_VIEWSTUB = "android.view.ViewStub";
    public static final String CLASS_NAME_LAYOUTPARAMS = "LayoutParams";
    public static final String CLASS_VIEWGROUP_LAYOUTPARAMS =
            CLASS_VIEWGROUP + "$" + CLASS_NAME_LAYOUTPARAMS;
    public static final String CLASS_NAME_FRAMELAYOUT = "FrameLayout";
    public static final String CLASS_FRAMELAYOUT =
            "android.widget." + CLASS_NAME_FRAMELAYOUT;
    public static final String CLASS_ADAPTER = "android.widget.Adapter";
    public static final String CLASS_PARCELABLE = "android.os.Parcelable";
    public static final String CLASS_PARCEL = "android.os.Parcel";
    public static final String CLASS_FRAGMENT = "android.app.Fragment";

    public static final String CLASS_ACTION_PROVIDER = "android.view.ActionProvider";
    public static final String CLASS_V4_ACTION_PROVIDER = "android.support.v4.view.ActionProvider";
    public static final String CLASS_ANDROIDX_ACTION_PROVIDER = "androidx.core.view.ActionProvider";

    public static final String CLASS_BACKUP_AGENT = "android.app.backup.BackupAgent";

    public static final String CLASS_DRAWABLE = "android.graphics.drawable.Drawable";

    /**
     * MockView is part of the layoutlib bridge and used to display classes that have no rendering
     * in the graphical layout editor.
     */
    public static final String CLASS_MOCK_VIEW =
            "com.android.layoutlib.bridge.MockView";

    public static final String CLASS_LAYOUT_INFLATER = "android.view.LayoutInflater";

    public static final String CLASS_VIEW_PAGER2 = "androidx.viewpager2.widget.ViewPager2";

    public static final String CLASS_FRAGMENT_CONTAINER_VIEW =
            "androidx.fragment.app.FragmentContainerView";

    public static final String CLASS_AD_VIEW = "com.google.android.gms.ads.AdView";
    public static final String CLASS_MAP_FRAGMENT =
            "com.google.android.gms.maps.MapFragment";
    public static final String CLASS_MAP_VIEW = "com.google.android.gms.maps.MapView";

    public static final String CLASS_PERCENT_RELATIVE_LAYOUT =
            "android.support.percent.PercentRelativeLayout";
    public static final String CLASS_PERCENT_FRAME_LAYOUT =
            "android.support.percent.PercentFrameLayout";

    public static final String CLASS_BOTTOM_APP_BAR =
            "com.google.android.material.bottomappbar.BottomAppBar";
    public static final String CLASS_CHIP = "com.google.android.material.chip.Chip";
    public static final String CLASS_CHIP_GROUP = "com.google.android.material.chip.ChipGroup";
    public static final String CLASS_MATERIAL_BUTTON =
            "com.google.android.material.button.MaterialButton";
    public static final String CLASS_MATERIAL_TOOLBAR =
            "com.google.android.material.appbar.MaterialToolbar";

    // Flow Alignment values.
    public static class FlowAlignment {
        public static final String NONE = "none";
        public static final String START = "start";
        public static final String END = "end";
        public static final String TOP = "top";
        public static final String BOTTOM = "bottom";
        public static final String CENTER = "center";
        public static final String BASELINE = "baseline";
    }

    // Flow Style values.
    public static class FlowStyle {
        public static final String SPREAD = "spread";
        public static final String SPREAD_INSIDE = "spread_inside";
        public static final String PACKED = "packed";
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
    /** Name of the Compose interoperability view that can be injected in regular XML layouts. */
    public static final String CLASS_COMPOSE_VIEW = "androidx.compose.ui.platform.ComposeView";

    public static final String CLASS_COMPOSE_VIEW_ADAPTER =
            "androidx.compose.ui.tooling.preview.ComposeViewAdapter";

    public static final String ATTR_COMPOSABLE_NAME = "composableName";

    public static final String PACKAGE_COMPOSE_ANIMATION = "androidx.compose.animation.core";

    /**
     * Returns the appropriate name for the 'mksdcard' command, which is 'mksdcard.exe' for Windows
     * and 'mksdcard' for all other platforms.
     */
    public static String mkSdCardCmdName() {
        String os = System.getProperty("os.name");
        String cmd = "mksdcard";
        if (os.startsWith("Windows")) {
            cmd += ".exe";
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
        String os = System.getProperty("os.name");
        if (os.startsWith("Mac OS")) {
            return PLATFORM_DARWIN;
        } else if (os.startsWith("Windows")) {
            return PLATFORM_WINDOWS;
        } else if (os.startsWith("Linux")) {
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
        String os = System.getProperty("os.name");
        if (os.startsWith("Mac OS")) {
            return "Mac OS X";
        } else if (os.startsWith("Windows")) {
            return "Windows";
        } else if (os.startsWith("Linux")) {
            return "Linux";
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
    public static final String FD_RES_ANIM = "anim";
    /** Default animator resource folder name, i.e. "animator" */
    public static final String FD_RES_ANIMATOR = "animator";
    /** Default color resource folder name, i.e. "color" */
    public static final String FD_RES_COLOR = "color";
    /** Default drawable resource folder name, i.e. "drawable" */
    public static final String FD_RES_DRAWABLE = "drawable";
    /** Default interpolator resource folder name, i.e. "interpolator" */
    public static final String FD_RES_INTERPOLATOR = "interpolator";
    /** Default layout resource folder name, i.e. "layout" */
    public static final String FD_RES_LAYOUT = "layout";
    /** Default menu resource folder name, i.e. "menu" */
    public static final String FD_RES_MENU = "menu";
    /** Default mipmap resource folder name, i.e. "mipmap" */
    public static final String FD_RES_MIPMAP = "mipmap";
    /** Default navigation resource folder name, i.e. "navigation" */
    public static final String FD_RES_NAVIGATION = "navigation";
    /** Default values resource folder name, i.e. "values" */
    public static final String FD_RES_VALUES = "values";
    /** Default values resource folder name for the dark theme, i.e. "values-night" */
    public static final String FD_RES_VALUES_NIGHT = "values-night";
    /** Default xml resource folder name, i.e. "xml" */
    public static final String FD_RES_XML = "xml";
    /** Default raw resource folder name, i.e. "raw" */
    public static final String FD_RES_RAW = "raw";
    /** Base name for the resource package files */
    public static final String FN_RES_BASE = "resources";
    /** Separator between the resource folder qualifier. */
    public static final String RES_QUALIFIER_SEP = "-";

    // ---- XML ----

    /** URI of the reserved "xml" prefix. */
    public static final String XML_NAMESPACE_URI = "http://www.w3.org/XML/1998/namespace";
    /** URI of the reserved "xmlns" prefix */
    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
    /** The "xmlns" attribute name */
    public static final String XMLNS = "xmlns";
    /** The default prefix used for the {@link #XMLNS_URI} */
    public static final String XMLNS_PREFIX = "xmlns:";
    /** Qualified name of the xmlns android declaration element */
    public static final String XMLNS_ANDROID = "xmlns:android";
    /** The default prefix used for the {@link #ANDROID_URI} name space */
    public static final String ANDROID_NS_NAME = "android";
    /** The default prefix used for the {@link #ANDROID_URI} name space including the colon */
    public static final String ANDROID_NS_NAME_PREFIX = "android:";

    public static final int ANDROID_NS_NAME_PREFIX_LEN = ANDROID_NS_NAME_PREFIX.length();
    /** The default prefix used for the {@link #TOOLS_URI} name space */
    public static final String TOOLS_NS_NAME = "tools";
    /** The default prefix used for the {@link #TOOLS_URI} name space including the colon */
    public static final String TOOLS_NS_NAME_PREFIX = "tools:";

    /** The default prefix used for the app */
    public static final String APP_PREFIX = "app";
    /** The entity for the ampersand character */
    public static final String AMP_ENTITY = "&amp;";
    /** The entity for the quote character */
    public static final String QUOT_ENTITY = "&quot;";
    /** The entity for the apostrophe character */
    public static final String APOS_ENTITY = "&apos;";
    /** The entity for the less than character */
    public static final String LT_ENTITY = "&lt;";
    /** The entity for the greater than character */
    public static final String GT_ENTITY = "&gt;";
    /** The entity for a newline */
    public static final String NEWLINE_ENTITY = "&#xA;";

    // ---- Elements and Attributes ----

    /** Namespace URI prefix used for all resources. */
    public static final String URI_DOMAIN_PREFIX = "http://schemas.android.com/";
    /** Namespace URI prefix used together with a package names. */
    public static final String URI_PREFIX = "http://schemas.android.com/apk/res/";
    /** Namespace used in XML files for Android attributes */
    public static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    /** @deprecated Use {@link #ANDROID_URI}. */
    @Deprecated public static final String NS_RESOURCES = ANDROID_URI;
    /** Namespace used in XML files for Android Tooling attributes */
    public static final String TOOLS_URI = "http://schemas.android.com/tools";
    /** Namespace used for auto-adjusting namespaces */
    public static final String AUTO_URI = "http://schemas.android.com/apk/res-auto";
    /** Namespace used for specifying module distribution */
    public static final String DIST_URI = "http://schemas.android.com/apk/distribution";

    public static final String AAPT_URI = "http://schemas.android.com/aapt";
    /** Namespace for xliff in string resources. */
    public static final String XLIFF_URI = "urn:oasis:names:tc:xliff:document:1.2";
    /** Default prefix used for tools attributes */
    public static final String TOOLS_PREFIX = "tools";
    /** Default prefix used for xliff tags. */
    public static final String XLIFF_PREFIX = "xliff";
    /** Default prefix used for aapt attributes */
    public static final String AAPT_PREFIX = "aapt";
    /** Default prefix used for distribution attributes */
    public static final String DIST_PREFIX = "dist";

    public static final String R_CLASS = "R";
    public static final String ANDROID_PKG = "android";
    public static final String ANDROID_SUPPORT_PKG = "android.support";
    public static final String ANDROIDX_PKG = "androidx";
    public static final String MATERIAL2_PKG = "com.google.android.material";
    public static final String MATERIAL1_PKG = "android.support.design.widget";

    public static final String SHERPA_PREFIX = "app";
    public static final String SHERPA_URI = "http://schemas.android.com/apk/res-auto";

    /** Namespace for Instant App attributes in manifest files */

    // Tags: Manifest
    public static final String TAG_MANIFEST = "manifest";
    public static final String TAG_SERVICE = "service";
    public static final String TAG_PERMISSION = "permission";
    public static final String TAG_PERMISSION_GROUP = "permission-group";
    public static final String TAG_USES_FEATURE = "uses-feature";
    public static final String TAG_USES_PERMISSION = "uses-permission";
    public static final String TAG_USES_PERMISSION_SDK_23 = "uses-permission-sdk-23";
    public static final String TAG_USES_PERMISSION_SDK_M = "uses-permission-sdk-m";
    public static final String TAG_USES_LIBRARY = "uses-library";
    public static final String TAG_USES_SPLIT = "uses-split";
    public static final String TAG_APPLICATION = "application";
    public static final String TAG_INTENT_FILTER = "intent-filter";
    public static final String TAG_CATEGORY = "category";
    public static final String TAG_USES_SDK = "uses-sdk";
    public static final String TAG_ACTIVITY = "activity";
    public static final String TAG_ACTIVITY_ALIAS = "activity-alias";
    public static final String TAG_RECEIVER = "receiver";
    public static final String TAG_PACKAGE = "package";
    public static final String TAG_PROVIDER = "provider";
    public static final String TAG_GRANT_PERMISSION = "grant-uri-permission";
    public static final String TAG_PATH_PERMISSION = "path-permission";
    public static final String TAG_ACTION = "action";
    public static final String TAG_INSTRUMENTATION = "instrumentation";
    public static final String TAG_META_DATA = "meta-data";
    public static final String TAG_PROPERTY = "property";
    public static final String TAG_RESOURCE = "resource";
    public static final String TAG_MODULE = "module";
    public static final String TAG_NAV_GRAPH = "nav-graph";
    public static final String TAG_QUERIES = "queries";
    public static final String TAG_INTENT = "intent";

    // Tags: Resources
    public static final String TAG_RESOURCES = "resources";
    public static final String TAG_STRING = "string";
    public static final String TAG_ARRAY = "array";
    public static final String TAG_STYLE = "style";
    public static final String TAG_ITEM = "item";
    public static final String TAG_GROUP = "group";
    public static final String TAG_STRING_ARRAY = "string-array";
    public static final String TAG_PLURALS = "plurals";
    public static final String TAG_INTEGER_ARRAY = "integer-array";
    public static final String TAG_COLOR = "color";
    public static final String TAG_DIMEN = "dimen";
    public static final String TAG_DRAWABLE = "drawable";
    public static final String TAG_MENU = "menu";
    public static final String TAG_ENUM = "enum";
    public static final String TAG_FLAG = "flag";
    public static final String TAG_ATTR = "attr";
    public static final String TAG_DECLARE_STYLEABLE = "declare-styleable";
    public static final String TAG_EAT_COMMENT = "eat-comment";
    public static final String TAG_SKIP = "skip";
    public static final String TAG_PUBLIC = "public";
    public static final String TAG_PUBLIC_GROUP = "public-group";
    public static final String TAG_STAGING_PUBLIC_GROUP = "staging-public-group";
    public static final String TAG_STAGING_PUBLIC_GROUP_FINAL = "staging-public-group-final";


    // Tags: Adaptive icon
    public static final String TAG_ADAPTIVE_ICON = "adaptive-icon";
    public static final String TAG_MASKABLE_ICON = "maskable-icon";

    // Font family tag
    public static final String TAG_FONT_FAMILY = "font-family";
    public static final String TAG_FONT = "font";

    // Tags: XML
    public static final String TAG_HEADER = "header";
    public static final String TAG_APPWIDGET_PROVIDER = "appwidget-provider";

    // Tags: Layouts
    public static final String VIEW_TAG = "view";
    public static final String VIEW_INCLUDE = "include";
    public static final String VIEW_MERGE = "merge";
    public static final String VIEW_FRAGMENT = "fragment";
    public static final String REQUEST_FOCUS = "requestFocus";
    public static final String TAG = "tag";

    // Tags: Navigation
    public static final String TAG_INCLUDE = "include";
    public static final String TAG_DEEP_LINK = "deepLink";
    public static final String TAG_NAVIGATION = "navigation";
    public static final String TAG_FRAGMENT = "fragment";
    public static final String TAG_ARGUMENT = "argument";
    public static final String ATTR_MODULE_NAME = "moduleName";

    public static final String VIEW = "View";
    public static final String VIEW_GROUP = "ViewGroup";
    public static final String FRAME_LAYOUT = "FrameLayout";
    public static final String LINEAR_LAYOUT = "LinearLayout";
    public static final String RELATIVE_LAYOUT = "RelativeLayout";
    public static final String GRID_LAYOUT = "GridLayout";
    public static final String SCROLL_VIEW = "ScrollView";
    public static final String BUTTON = "Button";
    public static final String COMPOUND_BUTTON = "CompoundButton";
    public static final String ADAPTER_VIEW = "AdapterView";
    public static final String STACK_VIEW = "StackView";
    public static final String GALLERY = "Gallery";
    public static final String GRID_VIEW = "GridView";
    public static final String TAB_HOST = "TabHost";
    public static final String RADIO_GROUP = "RadioGroup";
    public static final String RADIO_BUTTON = "RadioButton";
    public static final String SWITCH = "Switch";
    public static final String EDIT_TEXT = "EditText";
    public static final String LIST_VIEW = "ListView";
    public static final String TEXT_VIEW = "TextView";
    public static final String CHECKED_TEXT_VIEW = "CheckedTextView";
    public static final String IMAGE_VIEW = "ImageView";
    public static final String SURFACE_VIEW = "SurfaceView";
    public static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";
    public static final String TABLE_LAYOUT = "TableLayout";
    public static final String TABLE_ROW = "TableRow";
    public static final String TAB_WIDGET = "TabWidget";
    public static final String IMAGE_BUTTON = "ImageButton";
    public static final String ZOOM_BUTTON = "ZoomButton";
    public static final String SEEK_BAR = "SeekBar";
    public static final String VIEW_STUB = "ViewStub";
    public static final String SPINNER = "Spinner";
    public static final String WEB_VIEW = "WebView";
    public static final String TOGGLE_BUTTON = "ToggleButton";
    public static final String CHECK_BOX = "CheckBox";
    public static final String ABS_LIST_VIEW = "AbsListView";
    public static final String PROGRESS_BAR = "ProgressBar";
    public static final String RATING_BAR = "RatingBar";
    public static final String ABS_SPINNER = "AbsSpinner";
    public static final String ABS_SEEK_BAR = "AbsSeekBar";
    public static final String VIEW_ANIMATOR = "ViewAnimator";
    public static final String VIEW_FLIPPER = "ViewFlipper";
    public static final String VIEW_SWITCHER = "ViewSwitcher";
    public static final String TEXT_SWITCHER = "TextSwitcher";
    public static final String IMAGE_SWITCHER = "ImageSwitcher";
    public static final String EXPANDABLE_LIST_VIEW = "ExpandableListView";
    public static final String HORIZONTAL_SCROLL_VIEW = "HorizontalScrollView";
    public static final String MULTI_AUTO_COMPLETE_TEXT_VIEW =
            "MultiAutoCompleteTextView";
    public static final String AUTO_COMPLETE_TEXT_VIEW = "AutoCompleteTextView";
    public static final String CHECKABLE = "Checkable";
    public static final String TEXTURE_VIEW = "TextureView";
    public static final String DIALER_FILTER = "DialerFilter";
    public static final String ADAPTER_VIEW_FLIPPER = "AdapterViewFlipper";
    public static final String ADAPTER_VIEW_ANIMATOR = "AdapterViewAnimator";
    public static final String VIDEO_VIEW = "VideoView";
    public static final String SEARCH_VIEW = "SearchView";

    public static final String CHIP = CLASS_CHIP;
    public static final String CHIP_GROUP = CLASS_CHIP_GROUP;

    public static final String BOTTOM_APP_BAR = CLASS_BOTTOM_APP_BAR;
    public static final String MATERIAL_TOOLBAR = CLASS_MATERIAL_TOOLBAR;
    public static final String MATERIAL_BUTTON = CLASS_MATERIAL_BUTTON;

    public static final String VIEW_PAGER2 = CLASS_VIEW_PAGER2;

    public static final String FRAGMENT_CONTAINER_VIEW = CLASS_FRAGMENT_CONTAINER_VIEW;
    public static final String AD_VIEW = CLASS_AD_VIEW;
    public static final String MAP_FRAGMENT = CLASS_MAP_FRAGMENT;
    public static final String MAP_VIEW = CLASS_MAP_VIEW;

    public static final String CONSTRAINT_BARRIER_TOP = "top";
    public static final String CONSTRAINT_BARRIER_BOTTOM = "bottom";
    public static final String CONSTRAINT_BARRIER_LEFT = "left";
    public static final String CONSTRAINT_BARRIER_RIGHT = "right";
    public static final String CONSTRAINT_BARRIER_START = "start";
    public static final String CONSTRAINT_BARRIER_END = "end";
    public static final String CONSTRAINT_REFERENCED_IDS = "constraint_referenced_ids";

    // Tags: Drawables
    public static final String TAG_ANIMATION_LIST = "animation-list";
    public static final String TAG_ANIMATED_SELECTOR = "animated-selector";
    public static final String TAG_ANIMATED_VECTOR = "animated-vector";
    public static final String TAG_BITMAP = "bitmap";
    public static final String TAG_CLIP_PATH = "clip-path";
    public static final String TAG_GRADIENT = "gradient";
    public static final String TAG_INSET = "inset";
    public static final String TAG_LAYER_LIST = "layer-list";
    public static final String TAG_NINE_PATCH = "nine-patch";
    public static final String TAG_PATH = "path";
    public static final String TAG_RIPPLE = "ripple";
    public static final String TAG_ROTATE = "rotate";
    public static final String TAG_SHAPE = "shape";
    public static final String TAG_SELECTOR = "selector";
    public static final String TAG_TRANSITION = "transition";
    public static final String TAG_VECTOR = "vector";
    public static final String TAG_LEVEL_LIST = "level-list";

    // Tags: Data-Binding
    public static final String TAG_LAYOUT = "layout";
    public static final String TAG_DATA = "data";
    public static final String TAG_VARIABLE = "variable";
    public static final String TAG_IMPORT = "import";

    // Attributes: Manifest
    public static final String ATTR_EXPORTED = "exported";
    public static final String ATTR_PERMISSION = "permission";
    public static final String ATTR_PROCESS = "process";
    public static final String ATTR_MIN_SDK_VERSION = "minSdkVersion";
    public static final String ATTR_TARGET_SDK_VERSION = "targetSdkVersion";
    public static final String ATTR_ICON = "icon";
    public static final String ATTR_RESOURCE = "resource";
    public static final String ATTR_ROUND_ICON = "roundIcon";
    public static final String ATTR_PACKAGE = "package";
    public static final String ATTR_CORE_APP = "coreApp";
    public static final String ATTR_THEME = "theme";
    public static final String ATTR_SCHEME = "scheme";
    public static final String ATTR_MIME_TYPE = "mimeType";
    public static final String ATTR_HOST = "host";
    public static final String ATTR_PORT = "port";
    public static final String ATTR_PATH = "path";
    public static final String ATTR_PATH_PREFIX = "pathPrefix";
    public static final String ATTR_PATH_PATTERN = "pathPattern";
    public static final String ATTR_ALLOW_BACKUP = "allowBackup";
    public static final String ATTR_DEBUGGABLE = "debuggable";
    public static final String ATTR_READ_PERMISSION = "readPermission";
    public static final String ATTR_WRITE_PERMISSION = "writePermission";
    public static final String ATTR_VERSION_CODE = "versionCode";
    public static final String ATTR_VERSION_NAME = "versionName";
    public static final String ATTR_FULL_BACKUP_CONTENT = "fullBackupContent";
    public static final String ATTR_TEST_ONLY = "testOnly";
    public static final String ATTR_HAS_CODE = "hasCode";
    public static final String ATTR_AUTHORITIES = "authorities";
    public static final String ATTR_MULTIPROCESS = "multiprocess";
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
    public static final String ATTR_MIMETYPE = "mimeType";

    // Attributes: Resources
    public static final String ATTR_ATTR = "attr";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_FRAGMENT = "fragment";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_PARENT = "parent";
    public static final String ATTR_TRANSLATABLE = "translatable";
    public static final String ATTR_COLOR = "color";
    public static final String ATTR_DRAWABLE = "drawable";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_QUANTITY = "quantity";
    public static final String ATTR_FORMAT = "format";
    public static final String ATTR_PREPROCESSING = "preprocessing";

    // Attributes: Data Binding
    public static final String ATTR_ALIAS = "alias";

    // Attributes: View Binding
    public static final String ATTR_VIEW_BINDING_IGNORE = "viewBindingIgnore";
    public static final String ATTR_VIEW_BINDING_TYPE = "viewBindingType";

    // Attributes: Layout
    public static final String ATTR_LAYOUT_RESOURCE_PREFIX = "layout_";
    public static final String ATTR_CLASS = "class";
    public static final String ATTR_STYLE = "style";
    public static final String ATTR_CONTEXT = "context";
    public static final String ATTR_ID = "id";
    public static final String ATTR_AUTOFILL_HINTS = "autofillHints";
    public static final String ATTR_TEXT = "text";
    public static final String ATTR_TEXT_SIZE = "textSize";
    public static final String ATTR_ALPHA = "alpha";
    public static final String ATTR_LABEL = "label";
    public static final String ATTR_HINT = "hint";
    public static final String ATTR_PROMPT = "prompt";
    public static final String ATTR_ON_CLICK = "onClick";
    public static final String ATTR_INPUT_TYPE = "inputType";
    public static final String ATTR_INPUT_METHOD = "inputMethod";
    public static final String ATTR_LAYOUT_GRAVITY = "layout_gravity";
    public static final String ATTR_LAYOUT_WIDTH = "layout_width";
    public static final String ATTR_LAYOUT_HEIGHT = "layout_height";
    public static final String ATTR_LAYOUT_WEIGHT = "layout_weight";
    public static final String ATTR_PADDING = "padding";
    public static final String ATTR_PADDING_BOTTOM = "paddingBottom";
    public static final String ATTR_PADDING_TOP = "paddingTop";
    public static final String ATTR_PADDING_RIGHT = "paddingRight";
    public static final String ATTR_PADDING_LEFT = "paddingLeft";
    public static final String ATTR_PADDING_START = "paddingStart";
    public static final String ATTR_PADDING_END = "paddingEnd";
    public static final String ATTR_PADDING_HORIZONTAL = "paddingHorizontal";
    public static final String ATTR_PADDING_VERTICAL = "paddingVertical";
    public static final String ATTR_FOREGROUND = "foreground";
    public static final String ATTR_BACKGROUND = "background";
    public static final String ATTR_ORIENTATION = "orientation";
    public static final String ATTR_BARRIER_DIRECTION = "barrierDirection";
    public static final String ATTR_BARRIER_ALLOWS_GONE_WIDGETS = "barrierAllowsGoneWidgets";
    public static final String ATTR_LAYOUT_OPTIMIZATION_LEVEL = "layout_optimizationLevel";
    public static final String ATTR_TRANSITION = "transition";
    public static final String ATTR_TRANSITION_SHOW_PATHS = "showPaths";
    public static final String ATTR_TRANSITION_STATE = "transitionState";
    public static final String ATTR_TRANSITION_POSITION = "transitionPosition";
    public static final String ATTR_LAYOUT = "layout";
    public static final String ATTR_ROW_COUNT = "rowCount";
    public static final String ATTR_COLUMN_COUNT = "columnCount";
    public static final String ATTR_LABEL_FOR = "labelFor";
    public static final String ATTR_BASELINE_ALIGNED = "baselineAligned";
    public static final String ATTR_CONTENT_DESCRIPTION = "contentDescription";
    public static final String ATTR_IME_ACTION_LABEL = "imeActionLabel";
    public static final String ATTR_PRIVATE_IME_OPTIONS = "privateImeOptions";
    public static final String VALUE_NONE = "none";
    public static final String VALUE_NO = "no";
    public static final String VALUE_NO_EXCLUDE_DESCENDANTS = "noExcludeDescendants";
    public static final String VALUE_YES = "yes";
    public static final String VALUE_YES_EXCLUDE_DESCENDANTS = "yesExcludeDescendants";
    public static final String ATTR_NUMERIC = "numeric";
    public static final String ATTR_IME_ACTION_ID = "imeActionId";
    public static final String ATTR_IME_OPTIONS = "imeOptions";
    public static final String ATTR_FREEZES_TEXT = "freezesText";
    public static final String ATTR_EDITOR_EXTRAS = "editorExtras";
    public static final String ATTR_EDITABLE = "editable";
    public static final String ATTR_DIGITS = "digits";
    public static final String ATTR_CURSOR_VISIBLE = "cursorVisible";
    public static final String ATTR_CAPITALIZE = "capitalize";
    public static final String ATTR_PHONE_NUMBER = "phoneNumber";
    public static final String ATTR_PASSWORD = "password";
    public static final String ATTR_BUFFER_TYPE = "bufferType";
    public static final String ATTR_AUTO_TEXT = "autoText";
    public static final String ATTR_ENABLED = "enabled";
    public static final String ATTR_SINGLE_LINE = "singleLine";
    public static final String ATTR_SELECT_ALL_ON_FOCUS = "selectAllOnFocus";
    public static final String ATTR_SCALE_TYPE = "scaleType";
    public static final String ATTR_VISIBILITY = "visibility";
    public static final String ATTR_TEXT_IS_SELECTABLE = "textIsSelectable";
    public static final String ATTR_IMPORTANT_FOR_AUTOFILL =
            "importantForAutofill";
    public static final String ATTR_IMPORTANT_FOR_ACCESSIBILITY =
            "importantForAccessibility";
    public static final String ATTR_ACCESSIBILITY_TRAVERSAL_BEFORE =
            "accessibilityTraversalBefore";
    public static final String ATTR_ACCESSIBILITY_TRAVERSAL_AFTER =
            "accessibilityTraversalAfter";
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_LEFT =
            "listPreferredItemPaddingLeft";
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_RIGHT =
            "listPreferredItemPaddingRight";
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_START =
            "listPreferredItemPaddingStart";
    public static final String ATTR_LIST_PREFERRED_ITEM_PADDING_END =
            "listPreferredItemPaddingEnd";
    public static final String ATTR_INDEX = "index";
    public static final String ATTR_ACTION_BAR_NAV_MODE = "actionBarNavMode";
    public static final String ATTR_MENU = "menu";
    public static final String ATTR_OPEN_DRAWER = "openDrawer";
    public static final String ATTR_SHOW_IN = "showIn";
    public static final String ATTR_PARENT_TAG = "parentTag";
    public static final String ATTR_WIDTH = "width";
    public static final String ATTR_HEIGHT = "height";
    public static final String ATTR_NAV_GRAPH = "navGraph";
    public static final String ATTR_USE_TAG = "useTag";
    public static final String ATTR_IGNORE_A11Y_LINTS = "ignoreA11yLints";

    // ConstraintLayout Flow
    public static final String ATTR_FLOW_WRAP_MODE = "flow_wrapMode";
    public static final String ATTR_FLOW_MAX_ELEMENTS_WRAP = "flow_maxElementsWrap";
    public static final String ATTR_FLOW_FIRST_HORIZONTAL_BIAS = "flow_firstHorizontalBias";
    public static final String ATTR_FLOW_FIRST_HORIZONTAL_STYLE = "flow_firstHorizontalStyle";
    public static final String ATTR_FLOW_HORIZONTAL_BIAS = "flow_horizontalBias";
    public static final String ATTR_FLOW_HORIZONTAL_STYLE = "flow_horizontalStyle";
    public static final String ATTR_FLOW_HORIZONTAL_ALIGN = "flow_horizontalAlign";
    public static final String ATTR_FLOW_HORIZONTAL_GAP = "flow_horizontalGap";
    public static final String ATTR_FLOW_LAST_HORIZONTAL_BIAS = "flow_lastHorizontalBias";
    public static final String ATTR_FLOW_LAST_HORIZONTAL_STYLE = "flow_lastHorizontalStyle";
    public static final String ATTR_FLOW_FIRST_VERTICAL_BIAS = "flow_firstVerticalBias";
    public static final String ATTR_FLOW_FIRST_VERTICAL_STYLE = "flow_firstVerticalStyle";
    public static final String ATTR_FLOW_VERTICAL_BIAS = "flow_verticalBias";
    public static final String ATTR_FLOW_VERTICAL_STYLE = "flow_verticalStyle";
    public static final String ATTR_FLOW_VERTICAL_ALIGN = "flow_verticalAlign";
    public static final String ATTR_FLOW_VERTICAL_GAP = "flow_verticalGap";
    public static final String ATTR_FLOW_LAST_VERTICAL_BIAS = "flow_lastVerticalBias";
    public static final String ATTR_FLOW_LAST_VERTICAL_STYLE = "flow_lastVerticalStyle";

    // Attributes: Drawable
    public static final String ATTR_VIEWPORT_HEIGHT = "viewportHeight";
    public static final String ATTR_VIEWPORT_WIDTH = "viewportWidth";
    public static final String ATTR_PATH_DATA = "pathData";
    public static final String ATTR_FILL_COLOR = "fillColor";

    // Attributes: AnimationDrawable
    public static final String ATTR_ONESHOT = "oneshot";

    // Attributes: AnimatedStateListDrawable
    public static final String ATTR_FROM_ID = "fromId";
    public static final String ATTR_TO_ID = "toId";

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
    public static final String ATTR_LAYOUT_MARGIN_HORIZONTAL = "layout_marginHorizontal";
    public static final String ATTR_LAYOUT_MARGIN_VERTICAL = "layout_marginVertical";
    public static final String ATTR_LAYOUT_PADDING_HORIZONTAL = "layout_paddingHorizontal";
    public static final String ATTR_LAYOUT_PADDING_VERTICAL = "layout_paddingVertical";

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
    public static final String ATTR_HAND_SECOND = "hand_second";

    // CalendarView
    public static final String ATTR_SELECTED_DATE_VERTICAL_BAR = "selectedDateVerticalBar";

    // TextView attributes
    public static final String ATTR_TEXT_APPEARANCE = "textAppearance";
    public static final String ATTR_FONT_FAMILY = "fontFamily";
    public static final String ATTR_TYPEFACE = "typeface";
    public static final String ATTR_LINE_SPACING_EXTRA = "lineSpacingExtra";
    public static final String ATTR_TEXT_STYLE = "textStyle";
    public static final String ATTR_TEXT_ALIGNMENT = "textAlignment";
    public static final String ATTR_TEXT_COLOR = "textColor";
    public static final String ATTR_TEXT_COLOR_HINT = "textColorHint";
    public static final String ATTR_TEXT_COLOR_LINK = "textColorLink";
    public static final String ATTR_TEXT_ALL_CAPS = "textAllCaps";
    public static final String ATTR_SHADOW_COLOR = "shadowColor";
    public static final String ATTR_TEXT_COLOR_HIGHLIGHT = "textColorHighlight";
    public static final String ATTR_AUTO_SIZE_PRESET_SIZES = "autoSizePresetSizes";

    // Tools attributes for AdapterView inheritors
    public static final String ATTR_LISTFOOTER = "listfooter";
    public static final String ATTR_LISTHEADER = "listheader";
    public static final String ATTR_LISTITEM = "listitem";
    public static final String ATTR_ITEM_COUNT = "itemCount";

    // Tools attributes for scrolling
    public static final String ATTR_SCROLLX = "scrollX";
    public static final String ATTR_SCROLLY = "scrollY";

    // Tools attribute for using a different view at design time
    public static final String ATTR_USE_HANDLER = "useHandler";

    // AbsoluteLayout layout params
    public static final String ATTR_LAYOUT_Y = "layout_y";
    public static final String ATTR_LAYOUT_X = "layout_x";

    // GridLayout layout params
    public static final String ATTR_LAYOUT_ROW = "layout_row";
    public static final String ATTR_LAYOUT_ROW_SPAN = "layout_rowSpan";
    public static final String ATTR_LAYOUT_COLUMN = "layout_column";
    public static final String ATTR_LAYOUT_COLUMN_SPAN = "layout_columnSpan";

    // ProgressBar/RatingBar attributes
    public static final String ATTR_MAXIMUM = "max";
    public static final String ATTR_PROGRESS = "progress";
    public static final String ATTR_PROGRESS_DRAWABLE = "progressDrawable";
    public static final String ATTR_PROGRESS_TINT = "progressTint";
    public static final String ATTR_PROGRESS_BACKGROUND_TINT =
            "progressBackgroundTint";
    public static final String ATTR_SECONDARY_PROGRESS_TINT = "secondaryProgressTint";
    public static final String ATTR_INDETERMINATE = "indeterminate";
    public static final String ATTR_INDETERMINATE_DRAWABLE = "indeterminateDrawable";
    public static final String ATTR_INDETERMINATE_TINT = "indeterminateTint";
    public static final String ATTR_RATING = "rating";
    public static final String ATTR_NUM_STARS = "numStars";
    public static final String ATTR_STEP_SIZE = "stepSize";
    public static final String ATTR_IS_INDICATOR = "isIndicator";
    public static final String ATTR_THUMB = "thumb";

    // ImageView attributes
    public static final String ATTR_ADJUST_VIEW_BOUNDS = "adjustViewBounds";
    public static final String ATTR_CROP_TO_PADDING = "cropToPadding";

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
            "layout_editor_absoluteX";
    public static final String ATTR_LAYOUT_EDITOR_ABSOLUTE_Y =
            "layout_editor_absoluteY";
    public static final String ATTR_LAYOUT_LEFT_CREATOR =
            "layout_constraintLeft_creator";
    public static final String ATTR_LAYOUT_RIGHT_CREATOR =
            "layout_constraintRight_creator";
    public static final String ATTR_LAYOUT_TOP_CREATOR =
            "layout_constraintTop_creator";
    public static final String ATTR_LAYOUT_BOTTOM_CREATOR =
            "layout_constraintBottom_creator";
    public static final String ATTR_LAYOUT_BASELINE_CREATOR =
            "layout_constraintBaseline_creator";
    public static final String ATTR_LAYOUT_CENTER_CREATOR =
            "layout_constraintCenter_creator";
    public static final String ATTR_LAYOUT_CENTER_X_CREATOR =
            "layout_constraintCenterX_creator";
    public static final String ATTR_LAYOUT_CENTER_Y_CREATOR =
            "layout_constraintCenterY_creator";
    public static final String ATTR_LAYOUT_LEFT_TO_LEFT_OF =
            "layout_constraintLeft_toLeftOf";
    public static final String ATTR_LAYOUT_LEFT_TO_RIGHT_OF =
            "layout_constraintLeft_toRightOf";
    public static final String ATTR_LAYOUT_RIGHT_TO_LEFT_OF =
            "layout_constraintRight_toLeftOf";
    public static final String ATTR_LAYOUT_RIGHT_TO_RIGHT_OF =
            "layout_constraintRight_toRightOf";
    public static final String ATTR_LAYOUT_TOP_TO_TOP_OF =
            "layout_constraintTop_toTopOf";
    public static final String ATTR_LAYOUT_TOP_TO_BOTTOM_OF =
            "layout_constraintTop_toBottomOf";
    public static final String ATTR_LAYOUT_BOTTOM_TO_TOP_OF =
            "layout_constraintBottom_toTopOf";
    public static final String ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF =
            "layout_constraintBottom_toBottomOf";
    public static final String ATTR_LAYOUT_BASELINE_TO_BASELINE_OF =
            "layout_constraintBaseline_toBaselineOf";

    public static final String ATTR_LAYOUT_START_TO_END_OF =
            "layout_constraintStart_toEndOf";
    public static final String ATTR_LAYOUT_START_TO_START_OF =
            "layout_constraintStart_toStartOf";
    public static final String ATTR_LAYOUT_END_TO_START_OF =
            "layout_constraintEnd_toStartOf";
    public static final String ATTR_LAYOUT_END_TO_END_OF =
            "layout_constraintEnd_toEndOf";
    public static final String ATTR_LAYOUT_GONE_MARGIN_LEFT = "layout_goneMarginLeft";
    public static final String ATTR_LAYOUT_GONE_MARGIN_TOP = "layout_goneMarginTop";
    public static final String ATTR_LAYOUT_GONE_MARGIN_RIGHT =
            "layout_goneMarginRight";
    public static final String ATTR_LAYOUT_GONE_MARGIN_BOTTOM =
            "layout_goneMarginBottom";
    public static final String ATTR_LAYOUT_GONE_MARGIN_START =
            "layout_goneMarginStart";
    public static final String ATTR_LAYOUT_GONE_MARGIN_END = "layout_goneMarginEnd";

    public static final String ATTR_LAYOUT_HORIZONTAL_BIAS =
            "layout_constraintHorizontal_bias";
    public static final String ATTR_LAYOUT_VERTICAL_BIAS =
            "layout_constraintVertical_bias";

    public static final String ATTR_LAYOUT_WIDTH_DEFAULT =
            "layout_constraintWidth_default";
    public static final String ATTR_LAYOUT_HEIGHT_DEFAULT =
            "layout_constraintHeight_default";
    public static final String ATTR_LAYOUT_WIDTH_MIN = "layout_constraintWidth_min";
    public static final String ATTR_LAYOUT_WIDTH_MAX = "layout_constraintWidth_max";
    public static final String ATTR_LAYOUT_WIDTH_PERCENT = "layout_constraintWidth_percent";
    public static final String ATTR_LAYOUT_HEIGHT_MIN = "layout_constraintHeight_min";
    public static final String ATTR_LAYOUT_HEIGHT_MAX = "layout_constraintHeight_max";
    public static final String ATTR_LAYOUT_HEIGHT_PERCENT = "layout_constraintHeight_percent";

    public static final String ATTR_LAYOUT_DIMENSION_RATIO =
            "layout_constraintDimensionRatio";
    public static final String ATTR_LAYOUT_VERTICAL_CHAIN_STYLE =
            "layout_constraintVertical_chainStyle";
    public static final String ATTR_LAYOUT_HORIZONTAL_CHAIN_STYLE =
            "layout_constraintHorizontal_chainStyle";
    public static final String ATTR_LAYOUT_VERTICAL_WEIGHT =
            "layout_constraintVertical_weight";
    public static final String ATTR_LAYOUT_HORIZONTAL_WEIGHT =
            "layout_constraintHorizontal_weight";
    public static final String ATTR_LAYOUT_CHAIN_SPREAD = "spread";
    public static final String ATTR_LAYOUT_CHAIN_SPREAD_INSIDE = "spread_inside";
    public static final String ATTR_LAYOUT_CHAIN_PACKED = "packed";
    public static final String ATTR_LAYOUT_CHAIN_HELPER_USE_RTL = "chainUseRtl";
    public static final String ATTR_LAYOUT_CONSTRAINTSET = "constraintSet";
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle";
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_ANGLE = "layout_constraintCircleAngle";
    public static final String ATTR_LAYOUT_CONSTRAINT_CIRCLE_RADIUS = "layout_constraintCircleRadius";
    public static final String ATTR_LAYOUT_CONSTRAINED_HEIGHT = "layout_constrainedHeight";
    public static final String ATTR_LAYOUT_CONSTRAINED_WIDTH = "layout_constrainedWidth";
    public static final String ATTR_CONSTRAINT_SET_START = "constraintSetStart";
    public static final String ATTR_CONSTRAINT_SET_END = "constraintSetEnd";
    public static final String ATTR_DERIVE_CONSTRAINTS_FROM = "deriveConstraintsFrom";
    public static final String ATTR_LAYOUT_CONSTRAINT_TAG = "layout_constraintTag";

    public static final String ATTR_GUIDELINE_ORIENTATION_HORIZONTAL = "horizontal";
    public static final String ATTR_GUIDELINE_ORIENTATION_VERTICAL = "vertical";
    public static final String LAYOUT_CONSTRAINT_GUIDE_BEGIN =
            "layout_constraintGuide_begin";
    public static final String LAYOUT_CONSTRAINT_GUIDE_END =
            "layout_constraintGuide_end";
    public static final String LAYOUT_CONSTRAINT_GUIDE_PERCENT =
            "layout_constraintGuide_percent";
    public static final String LAYOUT_CONSTRAINT_DEPRECATED_GUIDE_PERCENT =
            "layout_constraintGuide_Percent";
    public static final String ATTR_LOCKED = "locked";
    public static final String ATTR_CONSTRAINT_LAYOUT_DESCRIPTION =
      "layoutDescription";

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
    public static final String ATTR_LAYOUT_SPAN = "layout_span";

    // RelativeLayout layout params:
    public static final String ATTR_LAYOUT_ALIGN_LEFT = "layout_alignLeft";
    public static final String ATTR_LAYOUT_ALIGN_RIGHT = "layout_alignRight";
    public static final String ATTR_LAYOUT_ALIGN_START = "layout_alignStart";
    public static final String ATTR_LAYOUT_ALIGN_END = "layout_alignEnd";
    public static final String ATTR_LAYOUT_ALIGN_TOP = "layout_alignTop";
    public static final String ATTR_LAYOUT_ALIGN_BOTTOM = "layout_alignBottom";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT =
            "layout_alignParentLeft";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT =
            "layout_alignParentRight";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_START =
            "layout_alignParentStart";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_END = "layout_alignParentEnd";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop";
    public static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM =
            "layout_alignParentBottom";
    public static final String ATTR_LAYOUT_ALIGN_WITH_PARENT_MISSING =
            "layout_alignWithParentIfMissing";
    public static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline";
    public static final String ATTR_LAYOUT_CENTER_IN_PARENT = "layout_centerInParent";
    public static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical";
    public static final String ATTR_LAYOUT_CENTER_HORIZONTAL =
            "layout_centerHorizontal";
    public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";
    public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";
    public static final String ATTR_LAYOUT_TO_START_OF = "layout_toStartOf";
    public static final String ATTR_LAYOUT_TO_END_OF = "layout_toEndOf";
    public static final String ATTR_LAYOUT_BELOW = "layout_below";
    public static final String ATTR_LAYOUT_ABOVE = "layout_above";

    // Spinner
    public static final String ATTR_DROPDOWN_SELECTOR = "dropDownSelector";
    public static final String ATTR_POPUP_BACKGROUND = "popupBackground";
    public static final String ATTR_SPINNER_MODE = "spinnerMode";

    // Margins
    public static final String ATTR_LAYOUT_MARGIN = "layout_margin";
    public static final String ATTR_LAYOUT_MARGIN_LEFT = "layout_marginLeft";
    public static final String ATTR_LAYOUT_MARGIN_RIGHT = "layout_marginRight";
    public static final String ATTR_LAYOUT_MARGIN_START = "layout_marginStart";
    public static final String ATTR_LAYOUT_MARGIN_END = "layout_marginEnd";
    public static final String ATTR_LAYOUT_MARGIN_TOP = "layout_marginTop";
    public static final String ATTR_LAYOUT_MARGIN_BOTTOM = "layout_marginBottom";

    // Attributes: Drawables
    public static final String ATTR_TILE_MODE = "tileMode";

    // Attributes: Design and support lib
    public static final String ATTR_LAYOUT_ANCHOR = "layout_anchor";
    public static final String ATTR_LAYOUT_ANCHOR_GRAVITY = "layout_anchorGravity";
    public static final String ATTR_LAYOUT_BEHAVIOR = "layout_behavior";
    public static final String ATTR_LAYOUT_KEYLINE = "layout_keyline";
    public static final String ATTR_BACKGROUND_TINT = "backgroundTint";
    public static final String ATTR_BACKGROUND_TINT_MODE = "backgroundTintMode";
    public static final String ATTR_DRAWABLE_TINT = "drawableTint";
    public static final String ATTR_FOREGROUND_TINT = "foregroundTint";
    public static final String ATTR_FOREGROUND_TINT_MODE = "foregroundTintMode";
    public static final String ATTR_RIPPLE_COLOR = "rippleColor";
    public static final String ATTR_TINT = "tint";
    public static final String ATTR_FAB_SIZE = "fabSize";
    public static final String ATTR_ELEVATION = "elevation";
    public static final String ATTR_FITS_SYSTEM_WINDOWS = "fitsSystemWindows";
    public static final String ATTR_EXPANDED = "expanded";
    public static final String ATTR_LAYOUT_SCROLL_FLAGS = "layout_scrollFlags";
    public static final String ATTR_LAYOUT_COLLAPSE_MODE = "layout_collapseMode";
    public static final String ATTR_COLLAPSE_PARALLAX_MULTIPLIER =
            "layout_collapseParallaxMultiplier";
    public static final String ATTR_SCROLLBAR_STYLE = "scrollbarStyle";
    public static final String ATTR_FILL_VIEWPORT = "fillViewport";
    public static final String ATTR_CLIP_TO_PADDING = "clipToPadding";
    public static final String ATTR_CLIP_CHILDREN = "clipChildren";
    public static final String ATTR_HEADER_LAYOUT = "headerLayout";
    public static final String ATTR_ITEM_BACKGROUND = "itemBackground";
    public static final String ATTR_ITEM_ICON_TINT = "itemIconTint";
    public static final String ATTR_ITEM_TEXT_APPEARANCE = "itemTextAppearance";
    public static final String ATTR_ITEM_TEXT_COLOR = "itemTextColor";
    public static final String ATTR_POPUP_THEME = "popupTheme";
    public static final String ATTR_MIN_HEIGHT = "minHeight";
    public static final String ATTR_MAX_HEIGHT = "maxHeight";
    public static final String ATTR_ACTION_BAR = "actionBar";
    public static final String ATTR_TOOLBAR_ID = "toolbarId";
    public static final String ATTR_CACHE_COLOR_HINT = "cacheColorHint";
    public static final String ATTR_DIVIDER = "divider";
    public static final String ATTR_DIVIDER_PADDING = "dividerPadding";
    public static final String ATTR_DIVIDER_HEIGHT = "dividerHeight";
    public static final String ATTR_FOOTER_DIVIDERS_ENABLED = "footerDividersEnabled";
    public static final String ATTR_HEADER_DIVIDERS_ENABLED = "headerDividersEnabled";
    public static final String ATTR_CARD_BACKGROUND_COLOR = "cardBackgroundColor";
    public static final String ATTR_CARD_CORNER_RADIUS = "cardCornerRadius";
    public static final String ATTR_CONTENT_PADDING = "contentPadding";
    public static final String ATTR_CARD_ELEVATION = "cardElevation";
    public static final String ATTR_CARD_PREVENT_CORNER_OVERLAP =
            "cardPreventCornerOverlap";
    public static final String ATTR_CARD_USE_COMPAT_PADDING = "cardUseCompatPadding";
    public static final String ATTR_ENTRIES = "entries";
    public static final String ATTR_MIN_WIDTH = "minWidth";
    public static final String ATTR_MAX_WIDTH = "maxWidth";
    public static final String ATTR_DROPDOWN_HEIGHT = "dropDownHeight";
    public static final String ATTR_DROPDOWN_WIDTH = "dropDownWidth";
    public static final String ATTR_DRAW_SELECTOR_ON_TOP = "drawSelectorOnTop";
    public static final String ATTR_SCROLLBARS = "scrollbars";
    public static final String ATTR_COMPLETION_HINT = "completionHint";
    public static final String ATTR_COMPLETION_HINT_VIEW = "completionHintView";
    public static final String ATTR_LAYOUT_MANAGER = "layoutManager";
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
            "splitActionBarWhenNarrow";

    // Values: Layouts
    public static final String VALUE_FILL_PARENT = "fill_parent";
    public static final String VALUE_MATCH_PARENT = "match_parent";
    public static final String VALUE_MATCH_CONSTRAINT = "0dp";
    public static final String VALUE_VERTICAL = "vertical";
    public static final String VALUE_TRUE = "true";
    public static final String VALUE_EDITABLE = "editable";
    public static final String VALUE_AUTO_FIT = "auto_fit";
    public static final String VALUE_SELECTABLE_ITEM_BACKGROUND =
            "?android:attr/selectableItemBackground";

    // Values: Resources
    public static final String VALUE_ID = "id";

    // Values: Drawables
    public static final String VALUE_DISABLED = "disabled";
    public static final String VALUE_CLAMP = "clamp";

    // Values: Wear
    public static final String VALUE_COMPLICATION_SUPPORTED_TYPES =
            "android.support.wearable.complications.SUPPORTED_TYPES";

    // Value delimiters: Manifest
    public static final String VALUE_DELIMITER_PIPE = "|";

    // Menus
    public static final String ATTR_CHECKABLE = "checkable";
    public static final String ATTR_CHECKABLE_BEHAVIOR = "checkableBehavior";
    public static final String ATTR_ORDER_IN_CATEGORY = "orderInCategory";
    public static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    public static final String ATTR_TITLE = "title";
    public static final String ATTR_VISIBLE = "visible";
    public static final String VALUE_IF_ROOM = "ifRoom";
    public static final String VALUE_ALWAYS = "always";

    // Units
    public static final String UNIT_DP = "dp";
    public static final String UNIT_DIP = "dip";
    public static final String UNIT_SP = "sp";
    public static final String UNIT_PX = "px";
    public static final String UNIT_IN = "in";
    public static final String UNIT_MM = "mm";
    public static final String UNIT_PT = "pt";

    // Filenames and folder names
    public static final String ANDROID_MANIFEST_XML = "AndroidManifest.xml";
    public static final String OLD_PROGUARD_FILE = "proguard.cfg";
    public static final String CLASS_FOLDER =
            "bin" + File.separator + "classes";
    public static final String GEN_FOLDER = "gen";
    public static final String SRC_FOLDER = "src";
    public static final String LIBS_FOLDER = "libs";
    public static final String BIN_FOLDER = "bin";

    public static final String RES_FOLDER = "res";
    public static final String DOT_XML = ".xml";
    public static final String DOT_XSD = ".xsd";
    public static final String DOT_GIF = ".gif";
    public static final String DOT_JPG = ".jpg";
    public static final String DOT_JPEG = ".jpeg";
    public static final String DOT_WEBP = ".webp";
    public static final String DOT_PNG = ".png";
    public static final String DOT_9PNG = ".9.png";
    public static final String DOT_JAVA = ".java";
    public static final String DOT_KT = ".kt";
    public static final String DOT_KTS = ".kts";
    public static final String DOT_CLASS = ".class";
    public static final String DOT_JAR = ".jar";
    public static final String DOT_KOTLIN_MODULE = ".kotlin_module";
    public static final String DOT_SRCJAR = ".srcjar";
    public static final String DOT_GRADLE = ".gradle";
    public static final String DOT_PROPERTIES = ".properties";
    public static final String DOT_JSON = ".json";
    public static final String DOT_PSD = ".psd";
    public static final String DOT_TTF = ".ttf";
    public static final String DOT_TTC = ".ttc";
    public static final String DOT_OTF = ".otf";
    public static final String DOT_EXEC = ".exec";
    public static final String DOT_AVIF = ".avif";
    public static final String DOT_TOML = ".toml";
    public static final String DOT_VERSIONS_DOT_TOML = ".versions.toml";

    /** Extension of the Application package Files, i.e. "apk". */
    public static final String EXT_ANDROID_PACKAGE = "apk";
    /** Extension of the InstantApp package Files, i.e. "iapk". */
    public static final String EXT_INSTANTAPP_PACKAGE = "iapk";
    /** Extension for Android archive files */
    public static final String EXT_AAR = "aar";
    /** Extension for Android Privacy Sandbox Sdk archives */
    public static final String EXT_ASAR = "asar";
    /** Extension for Android Privacy Sandbox Sdk bundles */
    public static final String EXT_ASB = "asb";
    /** Extension for APKs file containing multiple APKs */
    public static final String EXT_APKS = "apks";
    /** Extension for Android atom files. */
    public static final String EXT_ATOM = "atom";
    /** Extension of java files, i.e. "java" */
    public static final String EXT_JAVA = "java";
    /** Extension of compiled java files, i.e. "class" */
    public static final String EXT_CLASS = "class";
    /** Extension of xml files, i.e. "xml" */
    public static final String EXT_XML = "xml";
    /** Extension of gradle files, i.e. "gradle" */
    public static final String EXT_GRADLE = "gradle";
    /** Extension of Kotlin gradle files, i.e. "gradle.kts" */
    public static final String EXT_GRADLE_KTS = "gradle.kts";
    /** Extension of jar files, i.e. "jar" */
    public static final String EXT_JAR = "jar";
    /** Extension of ZIP files, i.e. "zip" */
    public static final String EXT_ZIP = "zip";
    /** Extension of aidl files, i.e. "aidl" */
    public static final String EXT_AIDL = "aidl";
    /** Extension of Renderscript files, i.e. "rs" */
    public static final String EXT_RS = "rs";
    /** Extension of Renderscript files, i.e. "rsh" */
    public static final String EXT_RSH = "rsh";
    /** Extension of FilterScript files, i.e. "fs" */
    public static final String EXT_FS = "fs";
    /** Extension of Renderscript bitcode files, i.e. "bc" */
    public static final String EXT_BC = "bc";
    /** Extension of dependency files, i.e. "d" */
    public static final String EXT_DEP = "d";
    /** Extension of native libraries, i.e. "so" */
    public static final String EXT_NATIVE_LIB = "so";
    /** Extension of dex files, i.e. "dex" */
    public static final String EXT_DEX = "dex";
    /** Extension for temporary resource files, ie "ap_ */
    public static final String EXT_RES = "ap_";
    /** Extension for pre-processable images. Right now pngs */
    public static final String EXT_PNG = "png";
    /** Extension of app bundle files, i.e. "aab" */
    public static final String EXT_APP_BUNDLE = "aab";

    public static final String EXT_HPROF = "hprof";
    public static final String EXT_GZ = "gz";

    public static final String EXT_JSON = "json";

    public static final String EXT_CSV = "csv";

    /** Extension of native debug metadata files, i.e. "dbg" */
    public static final String EXT_DBG = "dbg";
    /** Extension of native debug symbol table files, i.e. "sym" */
    public static final String EXT_SYM = "sym";

    private static final String DOT = ".";

    /** Dot-Extension of the Application package Files, i.e. ".apk". */
    public static final String DOT_ANDROID_PACKAGE = DOT + EXT_ANDROID_PACKAGE;
    /** Dot-Extension for Android archive files */
    public static final String DOT_AAR = DOT + EXT_AAR;
    /** Extension for Android Privacy Sandbox Sdk archives */
    public static final String DOT_ASAR = DOT + EXT_ASAR;
    /** Extension for Android Privacy Sandbox Sdk bundles */
    public static final String DOT_ASB = DOT + EXT_ASB;
    /** Extension for APKs file containing multiple APKs */
    public static final String DOT_APKS = DOT + EXT_APKS;
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
    public static final String DOT_BMP = ".bmp";
    /** Dot-Extension for SVG files, i.e. ".svg" */
    public static final String DOT_SVG = ".svg";
    /** Dot-Extension for template files */
    public static final String DOT_FTL = ".ftl";
    /** Dot-Extension of text files, i.e. ".txt" */
    public static final String DOT_TXT = ".txt";
    /** Dot-Extension for Java heap dumps. */
    public static final String DOT_HPROF = DOT + EXT_HPROF;
    /** Dot-Extension of native debug metadata files, i.e. ".dbg" */
    public static final String DOT_DBG = ".dbg";
    /** Dot-Extension of native debug symbol table files, i.e. ".sym" */
    public static final String DOT_SYM = ".sym";
    /** Dot-Extension of TensorFlow Lite FlatBuffer files, i.e., ".tflite" */
    public static final String DOT_TFLITE = ".tflite";

    /** Resource base name for java files and classes */
    public static final String FN_RESOURCE_BASE = "R";
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
    public static final String FN_MANIFEST_BASE = "Manifest";
    /** Generated BuildConfig class name */
    public static final String FN_BUILD_CONFIG_BASE = "BuildConfig";
    /** Manifest java class filename, i.e. "Manifest.java" */
    public static final String FN_MANIFEST_CLASS = FN_MANIFEST_BASE + DOT_JAVA;
    /** BuildConfig java class filename, i.e. "BuildConfig.java" */
    public static final String FN_BUILD_CONFIG = FN_BUILD_CONFIG_BASE + DOT_JAVA;
    /**
     * Filename for the automatically generated manifest snippet from a privacy sandbox SDK to be
     * merged into app manifest.
     */
    public static final String PRIVACY_SANDBOX_SDK_DEPENDENCY_MANIFEST_SNIPPET_NAME_SUFFIX =
            "-privacy-sandbox-sdk-dependency-manifest-snippet" + DOT_XML;

    public static final String DRAWABLE_FOLDER = "drawable";
    public static final String MIPMAP_FOLDER = "mipmap";
    public static final String DRAWABLE_XHDPI = "drawable-xhdpi";
    public static final String DRAWABLE_XXHDPI = "drawable-xxhdpi";
    public static final String DRAWABLE_XXXHDPI = "drawable-xxxhdpi";
    public static final String DRAWABLE_HDPI = "drawable-hdpi";
    public static final String DRAWABLE_MDPI = "drawable-mdpi";
    public static final String DRAWABLE_LDPI = "drawable-ldpi";

    // Resources
    public static final String PREFIX_RESOURCE_REF = "@";
    public static final String PREFIX_THEME_REF = "?";
    public static final String PREFIX_BINDING_EXPR = "@{";
    public static final String PREFIX_TWOWAY_BINDING_EXPR = "@={";
    public static final String MANIFEST_PLACEHOLDER_PREFIX = "${";
    public static final String MANIFEST_PLACEHOLDER_SUFFIX = "}";
    public static final String ANDROID_PREFIX = "@android:";
    public static final String ANDROID_THEME_PREFIX = "?android:";
    public static final String LAYOUT_RESOURCE_PREFIX = "@layout/";
    public static final String STYLE_RESOURCE_PREFIX = "@style/";
    public static final String COLOR_RESOURCE_PREFIX = "@color/";
    public static final String NEW_ID_PREFIX = "@+id/";
    public static final String ID_PREFIX = "@id/";
    public static final String DRAWABLE_PREFIX = "@drawable/";
    public static final String STRING_PREFIX = "@string/";
    public static final String DIMEN_PREFIX = "@dimen/";
    public static final String MIPMAP_PREFIX = "@mipmap/";
    public static final String FONT_PREFIX = "@font/";
    public static final String AAPT_ATTR_PREFIX = "@aapt:_aapt/";
    public static final String SAMPLE_PREFIX = "@sample/";
    public static final String NAVIGATION_PREFIX = "@navigation/";

    public static final String TOOLS_SAMPLE_PREFIX = "@tools:sample/";

    public static final String ANDROID_LAYOUT_RESOURCE_PREFIX = "@android:layout/";
    public static final String ANDROID_STYLE_RESOURCE_PREFIX = "@android:style/";
    public static final String ANDROID_COLOR_RESOURCE_PREFIX = "@android:color/";
    public static final String ANDROID_ID_PREFIX = "@android:id/";
    public static final String ANDROID_DRAWABLE_PREFIX = "@android:drawable/";
    public static final String ANDROID_STRING_PREFIX = "@android:string/";

    public static final String RESOURCE_CLZ_ID = "id";
    public static final String RESOURCE_CLZ_COLOR = "color";
    public static final String RESOURCE_CLZ_ARRAY = "array";
    public static final String RESOURCE_CLZ_ATTR = "attr";
    public static final String RESOURCE_CLZ_STYLEABLE = "styleable";
    public static final String NULL_RESOURCE = "@null";
    public static final String TRANSPARENT_COLOR = "@android:color/transparent";
    public static final String REFERENCE_STYLE = "style/";
    public static final String PREFIX_ANDROID = "android:";
    public static final String PREFIX_APP = "app:";

    // Resource Types
    public static final String DRAWABLE_TYPE = "drawable";
    public static final String MENU_TYPE = "menu";

    // Packages
    public static final String ANDROID_PKG_PREFIX = ANDROID_PKG + ".";
    public static final String ANDROIDX_PKG_PREFIX = ANDROIDX_PKG + ".";
    public static final String WIDGET_PKG_PREFIX = "android.widget.";
    public static final String VIEW_PKG_PREFIX = "android.view.";

    // Project properties
    public static final String ANDROID_LIBRARY = "android.library";
    public static final String PROGUARD_CONFIG = "proguard.config";
    public static final String ANDROID_LIBRARY_REFERENCE_FORMAT =
            "android.library.reference.%1$d";
    public static final String PROJECT_PROPERTIES = "project.properties";

    // Java References
    public static final String ATTR_REF_PREFIX = "?attr/";
    public static final String R_PREFIX = "R.";
    public static final String R_ID_PREFIX = "R.id.";
    public static final String R_LAYOUT_RESOURCE_PREFIX = "R.layout.";
    public static final String R_DRAWABLE_PREFIX = "R.drawable.";
    public static final String R_STYLEABLE_PREFIX = "R.styleable.";
    public static final String R_ATTR_PREFIX = "R.attr.";

    // Attributes related to tools
    public static final String ATTR_IGNORE = "ignore";
    public static final String ATTR_LOCALE = "locale";

    // SuppressLint
    public static final String SUPPRESS_ALL = "all";
    public static final String SUPPRESS_LINT = "SuppressLint";
    public static final String TARGET_API = "TargetApi";
    public static final String ATTR_TARGET_API = "targetApi";
    public static final String FQCN_SUPPRESS_LINT =
            "android.annotation." + SUPPRESS_LINT;
    public static final String FQCN_TARGET_API = "android.annotation." + TARGET_API;
    public static final String KOTLIN_SUPPRESS = "kotlin.Suppress";

    // Class Names
    public static final String CONSTRUCTOR_NAME = "<init>";
    public static final String CLASS_CONSTRUCTOR = "<clinit>";

    // Method Names
    public static final String FORMAT_METHOD = "format";
    public static final String GET_STRING_METHOD = "getString";
    public static final String SET_CONTENT_VIEW_METHOD = "setContentView";
    public static final String INFLATE_METHOD = "inflate";

    public static final String ATTR_TAG = "tag";
    public static final String ATTR_NUM_COLUMNS = "numColumns";

    // Some common layout element names
    public static final String CALENDAR_VIEW = "CalendarView";
    public static final String CHRONOMETER = "Chronometer";
    public static final String TEXT_CLOCK = "TextClock";
    public static final String SPACE = "Space";
    public static final String GESTURE_OVERLAY_VIEW = "GestureOverlayView";
    public static final String QUICK_CONTACT_BADGE = "QuickContactBadge";

    public static final String ATTR_HANDLE = "handle";
    public static final String ATTR_BUTTON = "button";
    public static final String ATTR_BUTTON_TINT = "buttonTint";
    public static final String ATTR_CONTENT = "content";
    public static final String ATTR_CHECKED = "checked";
    public static final String ATTR_CHECK_MARK = "checkMark";
    public static final String ATTR_CHECK_MARK_TINT = "checkMarkTint";
    public static final String ATTR_DUPLICATE_PARENT_STATE = "duplicateParentState";
    public static final String ATTR_FOCUSABLE = "focusable";
    public static final String ATTR_CLICKABLE = "clickable";
    public static final String ATTR_TEXT_OFF = "textOff";
    public static final String ATTR_TEXT_ON = "textOn";
    public static final String ATTR_CHECKED_BUTTON = "checkedButton";
    public static final String ATTR_SWITCH_TEXT_APPEARANCE = "switchTextAppearance";
    public static final String ATTR_SWITCH_MIN_WIDTH = "switchMinWidth";
    public static final String ATTR_SWITCH_PADDING = "switchPadding";
    public static final String ATTR_THUMB_TINT = "thumbTint";
    public static final String ATTR_TRACK = "track";
    public static final String ATTR_TRACK_TINT = "trackTint";
    public static final String ATTR_SHOW_TEXT = "showText";
    public static final String ATTR_SPLIT_TRACK = "splitTrack";
    public static final String ATTR_STATE_LIST_ANIMATOR = "stateListAnimator";
    public static final String ATTR_LAYOUT_ANIMATION = "layoutAnimation";

    // TextView
    public static final String ATTR_DRAWABLE_RIGHT = "drawableRight";
    public static final String ATTR_DRAWABLE_LEFT = "drawableLeft";
    public static final String ATTR_DRAWABLE_START = "drawableStart";
    public static final String ATTR_DRAWABLE_END = "drawableEnd";
    public static final String ATTR_DRAWABLE_BOTTOM = "drawableBottom";
    public static final String ATTR_DRAWABLE_TOP = "drawableTop";
    public static final String ATTR_DRAWABLE_PADDING = "drawablePadding";

    // AppCompatTextView
    public static final String ATTR_DRAWABLE_RIGHT_COMPAT = "drawableRightCompat";
    public static final String ATTR_DRAWABLE_LEFT_COMPAT = "drawableLeftCompat";
    public static final String ATTR_DRAWABLE_START_COMPAT = "drawableStartCompat";
    public static final String ATTR_DRAWABLE_END_COMPAT = "drawableEndCompat";
    public static final String ATTR_DRAWABLE_BOTTOM_COMPAT = "drawableBottomCompat";
    public static final String ATTR_DRAWABLE_TOP_COMPAT = "drawableTopCompat";

    public static final String ATTR_USE_DEFAULT_MARGINS = "useDefaultMargins";
    public static final String ATTR_MARGINS_INCLUDED_IN_ALIGNMENT =
            "marginsIncludedInAlignment";

    public static final String VALUE_WRAP_CONTENT = "wrap_content";
    public static final String VALUE_FALSE = "false";
    public static final String VALUE_N_DP = "%ddp";
    public static final String VALUE_ZERO_DP = "0dp";
    public static final String VALUE_ONE_DP = "1dp";
    public static final String VALUE_TOP = "top";
    public static final String VALUE_BOTTOM = "bottom";
    public static final String VALUE_CENTER_VERTICAL = "center_vertical";
    public static final String VALUE_CENTER_HORIZONTAL = "center_horizontal";
    public static final String VALUE_FILL_HORIZONTAL = "fill_horizontal";
    public static final String VALUE_FILL_VERTICAL = "fill_vertical";
    public static final String VALUE_0 = "0";
    public static final String VALUE_1 = "1";

    // Gravity values. These have the GRAVITY_ prefix in front of value because we already
    // have VALUE_CENTER_HORIZONTAL defined for layouts, and its definition conflicts
    // (centerHorizontal versus center_horizontal)
    public static final String GRAVITY_VALUE_ = "center";
    public static final String GRAVITY_VALUE_CENTER = "center";
    public static final String GRAVITY_VALUE_LEFT = "left";
    public static final String GRAVITY_VALUE_RIGHT = "right";
    public static final String GRAVITY_VALUE_START = "start";
    public static final String GRAVITY_VALUE_END = "end";
    public static final String GRAVITY_VALUE_BOTTOM = "bottom";
    public static final String GRAVITY_VALUE_TOP = "top";
    public static final String GRAVITY_VALUE_FILL_HORIZONTAL = "fill_horizontal";
    public static final String GRAVITY_VALUE_FILL_VERTICAL = "fill_vertical";
    public static final String GRAVITY_VALUE_CENTER_HORIZONTAL = "center_horizontal";
    public static final String GRAVITY_VALUE_CENTER_VERTICAL = "center_vertical";
    public static final String GRAVITY_VALUE_CLIP_HORIZONTAL = "clip_horizontal";
    public static final String GRAVITY_VALUE_CLIP_VERTICAL = "clip_vertical";
    public static final String GRAVITY_VALUE_FILL = "fill";

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
        public static final String MULTI_CHECK_PREFERENCE = "MultiCheckPreference";
        public static final String MULTI_SELECT_LIST_PREFERENCE = "MultiSelectListPreference";
        public static final String PREFERENCE_CATEGORY = "PreferenceCategory";
        public static final String PREFERENCE_SCREEN = "PreferenceScreen";
        public static final String RINGTONE_PREFERENCE = "RingtonePreference";
        public static final String SEEK_BAR_PREFERENCE = "SeekBarPreference";
        public static final String SWITCH_PREFERENCE = "SwitchPreference";
        public static final String INTENT = "intent";
    }

    public static final class PreferenceClasses {
        public static final String CLASS_PREFERENCE = ANDROID_PREFERENCE_PKG + "Preference";
        public static final String CLASS_PREFERENCE_GROUP =
                ANDROID_PREFERENCE_PKG + "PreferenceGroup";
        public static final String CLASS_CHECK_BOX_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.CHECK_BOX_PREFERENCE;
        public static final String CLASS_DIALOG_PREFERENCE =
                ANDROID_PREFERENCE_PKG + "DialogPreference";
        public static final String CLASS_EDIT_TEXT_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.EDIT_TEXT_PREFERENCE;
        public static final String CLASS_LIST_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.LIST_PREFERENCE;
        public static final String CLASS_MULTI_CHECK_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.MULTI_CHECK_PREFERENCE;
        public static final String CLASS_MULTI_SELECT_LIST_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.MULTI_SELECT_LIST_PREFERENCE;
        public static final String CLASS_PREFERENCE_CATEGORY =
                ANDROID_PREFERENCE_PKG + PreferenceTags.PREFERENCE_CATEGORY;
        public static final String CLASS_PREFERENCE_SCREEN =
                ANDROID_PREFERENCE_PKG + PreferenceTags.PREFERENCE_SCREEN;
        public static final String CLASS_RINGTONE_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.RINGTONE_PREFERENCE;
        public static final String CLASS_SWITCH_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.SWITCH_PREFERENCE;
        public static final String CLASS_SEEK_BAR_PREFERENCE =
                ANDROID_PREFERENCE_PKG + PreferenceTags.SEEK_BAR_PREFERENCE;
        public static final String CLASS_TWO_STATE_PREFERENCE =
                ANDROID_PREFERENCE_PKG + "TwoStatePreference";
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
        public static final String VIEW_TRANSITION = "ViewTransition";
        public static final String INCLUDE = "Include";
        public static final String STATE_SET = "StateSet";
        public static final String CONSTRAINT_SET = "ConstraintSet";
        public static final String CONSTRAINT = "Constraint";
        public static final String CONSTRAINT_OVERRIDE = "ConstraintOverride";
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
        public static final String NONE = "none";
        public static final String INHERIT = "inherit";
        public static final String GRAVITY = "gravity";
        public static final String TEXT_START = "textStart";
        public static final String TEXT_END = "textEnd";
        public static final String CENTER = "center";
        public static final String VIEW_START = "viewStart";
        public static final String VIEW_END = "viewEnd";
    }

    public static class TextStyle {
        public static final String VALUE_NORMAL = "normal";
        public static final String VALUE_BOLD = "bold";
        public static final String VALUE_ITALIC = "italic";
    }

    public static final class ViewAttributes {
        public static final String MIN_HEIGHT = "minHeight";
    }

    /** The top level android package as a prefix, "android.". */
    public static final String ANDROID_SUPPORT_PKG_PREFIX =
            ANDROID_PKG_PREFIX + "support.";
    /** Architecture component package prefix */
    public static final String ANDROID_ARCH_PKG_PREFIX = ANDROID_PKG_PREFIX + "arch.";

    /** The android.view. package prefix */
    public static final String ANDROID_VIEW_PKG = ANDROID_PKG_PREFIX + "view.";

    /** The android.widget. package prefix */
    public static final String ANDROID_WIDGET_PREFIX = ANDROID_PKG_PREFIX + "widget.";

    /** The android.webkit. package prefix */
    public static final String ANDROID_WEBKIT_PKG = ANDROID_PKG_PREFIX + "webkit.";

    /** The android.preference. package prefix */
    public static final String ANDROID_PREFERENCE_PKG = ANDROID_PKG_PREFIX + "preference.";

    /** The android.app. package prefix */
    public static final String ANDROID_APP_PKG = ANDROID_PKG_PREFIX + "app.";

    /** The android.support.v4. package prefix */
    public static final String ANDROID_SUPPORT_V4_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "v4.";

    /** The android.support.v7. package prefix */
    public static final String ANDROID_SUPPORT_V7_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "v7.";

    /** The android.support.design. package prefix */
    public static final String ANDROID_SUPPORT_DESIGN_PKG =
            ANDROID_SUPPORT_PKG_PREFIX + "design.";

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
            ANDROID_SUPPORT_PKG_PREFIX + "v17.leanback.";

    /** The com.google.android.gms. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_PKG = "com.google.android.gms.";

    /** The com.google.android.gms.ads. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_ADS_PKG =
            GOOGLE_PLAY_SERVICES_PKG + "ads.";

    /** The com.google.android.gms.ads. package prefix */
    public static final String GOOGLE_PLAY_SERVICES_MAPS_PKG =
            GOOGLE_PLAY_SERVICES_PKG + "maps.";

    /** The LayoutParams inner-class name suffix, .LayoutParams */
    public static final String DOT_LAYOUT_PARAMS = ".LayoutParams";

    /** The fully qualified class name of an EditText view */
    public static final String FQCN_EDIT_TEXT = "android.widget.EditText";

    /** The fully qualified class name of a LinearLayout view */
    public static final String FQCN_LINEAR_LAYOUT = "android.widget.LinearLayout";

    /** The fully qualified class name of a RelativeLayout view */
    public static final String FQCN_RELATIVE_LAYOUT = "android.widget.RelativeLayout";

    /** The fully qualified class name of a GridLayout view */
    public static final String FQCN_GRID_LAYOUT = "android.widget.GridLayout";

    /** The fully qualified class name of a FrameLayout view */
    public static final String FQCN_FRAME_LAYOUT = "android.widget.FrameLayout";

    /** The fully qualified class name of a TableRow view */
    public static final String FQCN_TABLE_ROW = "android.widget.TableRow";

    /** The fully qualified class name of a TableLayout view */
    public static final String FQCN_TABLE_LAYOUT = "android.widget.TableLayout";

    /** The fully qualified class name of a GridView view */
    public static final String FQCN_GRID_VIEW = "android.widget.GridView";

    /** The fully qualified class name of a TabWidget view */
    public static final String FQCN_TAB_WIDGET = "android.widget.TabWidget";

    /** The fully qualified class name of a Button view */
    public static final String FQCN_BUTTON = "android.widget.Button";

    /** The fully qualified class name of a CheckBox view */
    public static final String FQCN_CHECK_BOX = "android.widget.CheckBox";

    /** The fully qualified class name of a CheckedTextView view */
    public static final String FQCN_CHECKED_TEXT_VIEW =
            "android.widget.CheckedTextView";

    /** The fully qualified class name of an ImageButton view */
    public static final String FQCN_IMAGE_BUTTON = "android.widget.ImageButton";

    /** The fully qualified class name of a RatingBar view */
    public static final String FQCN_RATING_BAR = "android.widget.RatingBar";

    /** The fully qualified class name of a SeekBar view */
    public static final String FQCN_SEEK_BAR = "android.widget.SeekBar";

    /** The fully qualified class name of a MultiAutoCompleteTextView view */
    public static final String FQCN_AUTO_COMPLETE_TEXT_VIEW =
            "android.widget.AutoCompleteTextView";

    /** The fully qualified class name of a MultiAutoCompleteTextView view */
    public static final String FQCN_MULTI_AUTO_COMPLETE_TEXT_VIEW =
            "android.widget.MultiAutoCompleteTextView";

    /** The fully qualified class name of a RadioButton view */
    public static final String FQCN_RADIO_BUTTON = "android.widget.RadioButton";

    /** The fully qualified class name of a ToggleButton view */
    public static final String FQCN_TOGGLE_BUTTON = "android.widget.ToggleButton";

    /** The fully qualified class name of a Spinner view */
    public static final String FQCN_SPINNER = "android.widget.Spinner";

    /** The fully qualified class name of an AdapterView */
    public static final String FQCN_ADAPTER_VIEW = "android.widget.AdapterView";

    /** The fully qualified class name of a ListView */
    public static final String FQCN_LIST_VIEW = "android.widget.ListView";

    /** The fully qualified class name of an ExpandableListView */
    public static final String FQCN_EXPANDABLE_LIST_VIEW =
            "android.widget.ExpandableListView";

    /** The fully qualified class name of a GestureOverlayView */
    public static final String FQCN_GESTURE_OVERLAY_VIEW =
            "android.gesture.GestureOverlayView";

    /** The fully qualified class name of a DatePicker */
    public static final String FQCN_DATE_PICKER = "android.widget.DatePicker";

    /** The fully qualified class name of a TimePicker */
    public static final String FQCN_TIME_PICKER = "android.widget.TimePicker";

    /** The fully qualified class name of a RadioGroup */
    public static final String FQCN_RADIO_GROUP = "android.widgets.RadioGroup";

    /** The fully qualified class name of a Space */
    public static final String FQCN_SPACE = "android.widget.Space";

    /** The fully qualified class name of a TextView view */
    public static final String FQCN_TEXT_VIEW = "android.widget.TextView";

    /** The fully qualified class name of an ImageView view */
    public static final String FQCN_IMAGE_VIEW = "android.widget.ImageView";

    /** The fully qualified class name of NavHostFragment Fragment subclass */
    public static final String FQCN_NAV_HOST_FRAGMENT =
            "androidx.navigation.fragment.NavHostFragment";

    /** The fully qualified class name of a ScrollView */
    public static final String FQCN_SCROLL_VIEW = "android.widget.ScrollView";

    public static final String ATTR_SRC = "src";
    public static final String ATTR_SRC_COMPAT = "srcCompat";

    public static final String ATTR_GRAVITY = "gravity";

    public static final String ATTR_WEIGHT_SUM = "weightSum";
    public static final String ATTR_EMS = "ems";

    public static final String VALUE_HORIZONTAL = "horizontal";

    public static final String GRADLE_PLUGIN_NAME = "com.android.tools.build:gradle:";

    /** The minimum version of Gradle that this version of Studio will support. */
    public static final String GRADLE_MINIMUM_VERSION = "4.8.1";

    /**
     * The minimum version of Gradle that this version of the Gradle plugin will support. This also
     * happens to be a version of Gradle that is embedded and distributed along with the Android
     * Studio product. It need not actually be the latest version of Gradle, but it will most likely
     * be fairly recent.
     */
    public static final String GRADLE_LATEST_VERSION = "8.0-rc-2";

    /**
     * The minimum released version of the Android Gradle Plugin that this version of Studio will
     * support. (Support of the development series leading up to it is unknown.)
     */
    public static final String GRADLE_PLUGIN_MINIMUM_VERSION = "3.2.0";

    /**
     * The minimum released version of the Android Gradle Plugin that the next version of Studio
     * will support.
     */
    public static final String GRADLE_PLUGIN_NEXT_MINIMUM_VERSION = "4.0.0";

    /**
     * A version of the Android Gradle Plugin that this version of Studio and associated tools (e.g.
     * lint) can safely recommend during its development cycle. If an up-to-date version is
     * required, consider using `LatestKnownPluginVersionProvider` instead.
     */
    public static final String GRADLE_PLUGIN_RECOMMENDED_VERSION = "7.0.3";

    /**
     * The version of NDK to use as default. If no specific version of NDK is specified in
     * build.gradle then this is the version that will be used.
     */
    public static final String NDK_DEFAULT_VERSION = "25.1.8937393";

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
    public static final String CURRENT_BUILD_TOOLS_VERSION = "33.0.1";
    public static final String SUPPORT_LIB_GROUP_ID = "com.android.support";
    public static final String SUPPORT_LIB_ARTIFACT = "com.android.support:support-v4";
    public static final String DESIGN_LIB_ARTIFACT = "com.android.support:design";
    public static final String APPCOMPAT_LIB_ARTIFACT_ID = "appcompat-v7";
    public static final String APPCOMPAT_LIB_ARTIFACT =
            SUPPORT_LIB_GROUP_ID + ":" + APPCOMPAT_LIB_ARTIFACT_ID;
    public static final String CARD_VIEW_LIB_ARTIFACT = "com.android.support:cardview-v7";
    public static final String GRID_LAYOUT_LIB_ARTIFACT = "com.android.support:gridlayout-v7";
    public static final String RECYCLER_VIEW_LIB_ARTIFACT = "com.android.support:recyclerview-v7";
    public static final String FRAGMENT_LIB_ARTIFACT = "androidx.fragment:fragment";
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
    public static final String DATA_BINDING_KTX_LIB_ARTIFACT =
            "androidx.databinding:databinding-ktx";
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

    public static final String CLASS_NAME_DATA_BINDING_COMPONENT = "DataBindingComponent";

    public static final String CLASS_FLOW = "kotlinx.coroutines.flow.Flow";
    public static final String CLASS_STATE_FLOW = "kotlinx.coroutines.flow.StateFlow";

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

    public static final String META_INF = "meta-inf";
    public static final String PROGUARD_RULES_FOLDER_NAME = "proguard";
    /** Folder where proguard rules are located in jar, aar and project generated resources */
    public static final String PROGUARD_RULES_FOLDER = META_INF + "/" + PROGUARD_RULES_FOLDER_NAME;

    /** Folder where configuration files for R8 and other tools are located in jar files */
    public static final String COM_ANDROID_TOOLS_FOLDER = "com.android.tools";

    /** Folder where configuration files for R8 and other tools are located in jar files */
    public static final String TOOLS_CONFIGURATION_FOLDER =
            META_INF + "/" + COM_ANDROID_TOOLS_FOLDER;

    public static final String FD_PREFAB_PACKAGE = "prefab";
}
