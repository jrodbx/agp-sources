/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.build.gradle.internal.profile;


import com.android.Version;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.api.transform.Transform;
import com.android.build.api.variant.AndroidVersion;
import com.android.build.gradle.internal.core.Abi;
import com.android.build.gradle.internal.dsl.Splits;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.services.BuildServicesKt;
import com.android.build.gradle.options.BooleanOption;
import com.android.build.gradle.options.IntegerOption;
import com.android.build.gradle.options.OptionalBooleanOption;
import com.android.build.gradle.options.ProjectOptions;
import com.android.build.gradle.options.StringOption;
import com.android.builder.model.TestOptions;
import com.android.resources.Density;
import com.android.tools.analytics.CommonMetricsData;
import com.android.tools.build.gradle.internal.profile.GradleTaskExecutionType;
import com.android.tools.build.gradle.internal.profile.GradleTransformExecutionType;
import com.android.tools.build.gradle.internal.profile.VariantApiArtifactType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import com.google.protobuf.Descriptors;
import com.google.wireless.android.sdk.stats.ApiVersion;
import com.google.wireless.android.sdk.stats.DeviceInfo;
import com.google.wireless.android.sdk.stats.GradleBuildProject;
import com.google.wireless.android.sdk.stats.GradleBuildSplits;
import com.google.wireless.android.sdk.stats.GradleBuildVariant;
import com.google.wireless.android.sdk.stats.GradleIntegerOptionEntry;
import com.google.wireless.android.sdk.stats.GradleProjectOptionsSettings;
import com.google.wireless.android.sdk.stats.ProductDetails;
import com.google.wireless.android.sdk.stats.TestRun;
import java.lang.reflect.Method;
import java.util.Locale;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logging;

/**
 * Utilities to map internal representations of types to analytics.
 */
public class AnalyticsUtil {

    public static ProductDetails getProductDetails() {
        return ProductDetails.newBuilder()
                .setProduct(ProductDetails.ProductKind.GRADLE)
                .setVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setOsArchitecture(CommonMetricsData.getOsArchitecture())
                .build();
    }

    public static GradleTransformExecutionType getTransformType(
            @NonNull Class<? extends Transform> taskClass) {
        Descriptors.EnumValueDescriptor value =
                GradleTransformExecutionType.getDescriptor()
                        .findValueByName(getPotentialTransformTypeName(taskClass));
        if (value == null) {
            return GradleTransformExecutionType.UNKNOWN_TRANSFORM_TYPE;
        }
        return GradleTransformExecutionType.valueOf(value);
    }

    @VisibleForTesting
    @NonNull
    static String getPotentialTransformTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }


    @NonNull
    public static GradleTaskExecutionType getTaskExecutionType(@NonNull Class<?> taskClass) {
        Descriptors.EnumValueDescriptor value =
                GradleTaskExecutionType.getDescriptor()
                        .findValueByName(getPotentialTaskExecutionTypeName(taskClass));
        if (value == null) {
            return GradleTaskExecutionType.UNKNOWN_TASK_TYPE;
        }
        return GradleTaskExecutionType.valueOf(value);
    }

    @NonNull
    public static VariantApiArtifactType getVariantApiArtifactType(@NonNull Class<?> artifactType) {
        Descriptors.EnumValueDescriptor value =
                VariantApiArtifactType.getDescriptor()
                        .findValueByName(artifactType.getSimpleName());
        if (value == null) {
            return VariantApiArtifactType.CUSTOM_ARTIFACT_TYPE;
        }
        return VariantApiArtifactType.valueOf(value);
    }

    @NonNull
    static String getPotentialTaskExecutionTypeName(Class<?> taskClass) {
        String taskImpl = taskClass.getSimpleName();
        if (taskImpl.endsWith("_Decorated")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "_Decorated".length());
        }
        if (taskImpl.endsWith("Task")) {
            taskImpl = taskImpl.substring(0, taskImpl.length() - "Task".length());
        }
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, taskImpl);
    }

    @NonNull
    public static ApiVersion toProto(@NonNull AndroidVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

    @NonNull
    public static ApiVersion toProto(@NonNull com.android.builder.model.ApiVersion apiVersion) {
        ApiVersion.Builder builder = ApiVersion.newBuilder().setApiLevel(apiVersion.getApiLevel());
        if (apiVersion.getCodename() != null) {
            builder.setCodename(apiVersion.getCodename());
        }
        return builder.build();
    }

    @NonNull
    public static GradleBuildSplits toProto(@NonNull Splits splits) {
        GradleBuildSplits.Builder builder = GradleBuildSplits.newBuilder();
        if (splits.getDensity().isEnable()) {
            builder.setDensityEnabled(true);

            for (String compatibleScreen : splits.getDensity().getCompatibleScreens()) {
                builder.addDensityCompatibleScreens(getCompatibleScreen(compatibleScreen));
            }

            for (String filter : splits.getDensity().getApplicableFilters()) {
                Density density = Density.getEnum(filter);
                builder.addDensityValues(density == null ? -1 : density.getDpiValue());
            }
        }

        if (splits.getLanguage().isEnable()) {
            builder.setLanguageEnabled(true);

            for (String split : splits.getLanguage().getApplicationFilters()) {
                builder.addLanguageIncludes(split != null ? split : "null");
            }
        }

        if (splits.getAbi().isEnable()) {
            builder.setAbiEnabled(true);
            builder.setAbiEnableUniversalApk(splits.getAbi().isUniversalApk());
            for (String filter : splits.getAbi().getApplicableFilters()) {
                builder.addAbiFilters(getAbi(filter));
            }
        }
        return builder.build();
    }

    @NonNull
    public static GradleBuildVariant.Java8LangSupport toProto(
            @NonNull VariantScope.Java8LangSupport type) {
        Preconditions.checkArgument(
                type != VariantScope.Java8LangSupport.UNUSED
                        && type != VariantScope.Java8LangSupport.INVALID,
                "Unsupported type");
        switch (type) {
            case RETROLAMBDA:
                return GradleBuildVariant.Java8LangSupport.RETROLAMBDA;
            case R8:
                return GradleBuildVariant.Java8LangSupport.R8_DESUGARING;
            case D8:
                return GradleBuildVariant.Java8LangSupport.D8;
            case INVALID:
                // fall through
            case UNUSED:
                throw new IllegalArgumentException("Unexpected type " + type);
        }
        throw new AssertionError("Unrecognized type " + type);
    }

    @NonNull
    public static TestRun.TestExecution toProto(@NonNull TestOptions.Execution execution) {
        switch (execution) {
            case HOST:
                return TestRun.TestExecution.HOST;
            case ANDROID_TEST_ORCHESTRATOR:
            case ANDROIDX_TEST_ORCHESTRATOR:
                return TestRun.TestExecution.ANDROID_TEST_ORCHESTRATOR;
        }
        throw new AssertionError("Unrecognized type " + execution);
    }

    @NonNull
    public static DeviceInfo.ApplicationBinaryInterface getAbi(@NonNull String name) {
        Abi abi = Abi.getByName(name);
        if (abi == null) {
            return DeviceInfo.ApplicationBinaryInterface.UNKNOWN_ABI;
        }
        switch (abi) {
            case ARMEABI:
                return DeviceInfo.ApplicationBinaryInterface.ARME_ABI;
            case ARMEABI_V7A:
                return DeviceInfo.ApplicationBinaryInterface.ARME_ABI_V7A;
            case ARM64_V8A:
                return DeviceInfo.ApplicationBinaryInterface.ARM64_V8A_ABI;
            case X86:
                return DeviceInfo.ApplicationBinaryInterface.X86_ABI;
            case X86_64:
                return DeviceInfo.ApplicationBinaryInterface.X86_64_ABI;
            case MIPS:
                return DeviceInfo.ApplicationBinaryInterface.MIPS_ABI;
            case MIPS64:
                return DeviceInfo.ApplicationBinaryInterface.MIPS_R2_ABI;
        }
        // Shouldn't happen
        return DeviceInfo.ApplicationBinaryInterface.UNKNOWN_ABI;
    }

    @NonNull
    private static GradleBuildSplits.CompatibleScreenSize getCompatibleScreen(
            @NonNull String compatibleScreen) {
        switch (compatibleScreen.toLowerCase(Locale.US)) {
            case "small":
                return GradleBuildSplits.CompatibleScreenSize.SMALL;
            case "normal":
                return GradleBuildSplits.CompatibleScreenSize.NORMAL;
            case "large":
                return GradleBuildSplits.CompatibleScreenSize.LARGE;
            case "xlarge":
                return GradleBuildSplits.CompatibleScreenSize.XLARGE;
            default:
                return GradleBuildSplits.CompatibleScreenSize.UNKNOWN_SCREEN_SIZE;
        }
    }

    @VisibleForTesting
    @NonNull
    static com.android.tools.build.gradle.internal.profile.BooleanOption toProto(
            @NonNull BooleanOption option) {
        Descriptors.EnumValueDescriptor value =
                com.android.tools.build.gradle.internal.profile.BooleanOption.getDescriptor()
                        .findValueByName(option.name());
        if (value == null) {
            return com.android.tools.build.gradle.internal.profile.BooleanOption
                    .UNKNOWN_BOOLEAN_OPTION;
        }
        return com.android.tools.build.gradle.internal.profile.BooleanOption.valueOf(value);
    }

    @VisibleForTesting
    @NonNull
    static com.android.tools.build.gradle.internal.profile.OptionalBooleanOption toProto(
            @NonNull OptionalBooleanOption option) {
        Descriptors.EnumValueDescriptor value =
                com.android.tools.build.gradle.internal.profile.OptionalBooleanOption
                        .getDescriptor()
                        .findValueByName(option.name());
        if (value == null) {
            return com.android.tools.build.gradle.internal.profile.OptionalBooleanOption
                    .UNKNOWN_OPTIONAL_BOOLEAN_OPTION;
        }
        return com.android.tools.build.gradle.internal.profile.OptionalBooleanOption.valueOf(value);
    }

    @VisibleForTesting
    @NonNull
    static com.android.tools.build.gradle.internal.profile.IntegerOption toProto(
            @NonNull IntegerOption option) {
        Descriptors.EnumValueDescriptor value =
                com.android.tools.build.gradle.internal.profile.IntegerOption.getDescriptor()
                        .findValueByName(option.name());
        if (value == null) {
            return com.android.tools.build.gradle.internal.profile.IntegerOption
                    .UNKNOWN_INTEGER_OPTION;
        }
        return com.android.tools.build.gradle.internal.profile.IntegerOption.valueOf(value);
    }

    @VisibleForTesting
    @NonNull
    static com.android.tools.build.gradle.internal.profile.StringOption toProto(
            @NonNull StringOption option) {
        Descriptors.EnumValueDescriptor value =
                com.android.tools.build.gradle.internal.profile.StringOption.getDescriptor()
                        .findValueByName(option.name());
        if (value == null) {
            return com.android.tools.build.gradle.internal.profile.StringOption
                    .UNKNOWN_STRING_OPTION;
        }
        return com.android.tools.build.gradle.internal.profile.StringOption.valueOf(value);
    }

    @NonNull
    public static GradleProjectOptionsSettings toProto(@NonNull ProjectOptions projectOptions) {
        GradleProjectOptionsSettings.Builder builder = GradleProjectOptionsSettings.newBuilder();
        projectOptions
                .getExplicitlySetBooleanOptions()
                .forEach(
                        (BooleanOption option, Boolean value) -> {
                            if (value) {
                                builder.addTrueBooleanOptions(toProto(option).getNumber());
                            } else {
                                builder.addFalseBooleanOptions(toProto(option).getNumber());
                            }
                        });

        projectOptions
                .getExplicitlySetOptionalBooleanOptions()
                .forEach(
                        (OptionalBooleanOption option, Boolean value) -> {
                            if (value) {
                                builder.addTrueOptionalBooleanOptions(toProto(option).getNumber());
                            } else {
                                builder.addFalseOptionalBooleanOptions(toProto(option).getNumber());
                            }
                        });

        projectOptions
                .getExplicitlySetIntegerOptions()
                .forEach(
                        (IntegerOption option, Integer value) -> {
                            builder.addIntegerOptionValues(
                                    GradleIntegerOptionEntry.newBuilder()
                                            .setIntegerOption(toProto(option).getNumber())
                                            .setIntegerOptionValue(value));
                        });

        for (StringOption stringOption : projectOptions.getExplicitlySetStringOptions().keySet()) {
            builder.addStringOptions(toProto(stringOption).getNumber());
        }

        return builder.build();
    }

    @NonNull
    public static GradleBuildProject.GradlePlugin toProto(@NonNull Plugin<?> plugin) {
        return otherPluginToProto(plugin.getClass().getName());
    }

    @VisibleForTesting
    static GradleBuildProject.GradlePlugin otherPluginToProto(@NonNull String pluginClassName) {
        String enumName = getOtherPluginEnumName(pluginClassName);
        Descriptors.EnumValueDescriptor value =
                GradleBuildProject.GradlePlugin.getDescriptor().findValueByName(enumName);
        if (value == null) {
            Logging.getLogger(AnalyticsUtil.class)
                    .info(
                            "Analytics other plugin to proto: Unknown plugin type {} expected enum {}",
                            pluginClassName,
                            enumName);
            return GradleBuildProject.GradlePlugin.UNKNOWN_GRADLE_PLUGIN;
        }
        return GradleBuildProject.GradlePlugin.valueOf(value);
    }

    @VisibleForTesting
    @NonNull
    static String getOtherPluginEnumName(@NonNull String pluginClassName) {
        return pluginClassName.replace(".", "_").toUpperCase(Locale.US);
    }


    public static void recordFirebasePerformancePluginVersion(Project project) {
        String version = getFirebasePerformancePluginVersion(project);
        if (version == null) {
            return;
        }
        GradleBuildProject.Builder projectBuilder =
                BuildServicesKt.getBuildService(
                        project.getGradle().getSharedServices(),
                        AnalyticsConfiguratorService.class)
                        .get()
                        .getProjectBuilder(project.getPath());

        if (projectBuilder != null) {
            projectBuilder.setFirebasePerformancePluginVersion(version);
        }
    }

    /**
     * Get the version of the firebase performance plugin if applied
     *
     * <p>Returns null if the plugin is not applied and "unknown" if something goes wrong.
     */
    @Nullable
    private static String getFirebasePerformancePluginVersion(Project project) {
        Plugin plugin = project.getPlugins().findPlugin("com.google.firebase.firebase-perf");
        if (plugin == null) {
            return null;
        }
        try {
            Method getPluginVersion = plugin.getClass().getMethod("getPluginVersion");
            return getPluginVersion.invoke(null).toString();
        } catch (Throwable e) {
            return "unknown";
        }
    }
}
