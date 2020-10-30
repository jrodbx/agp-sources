/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.internal.dsl;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.errors.DeprecationReporter.DeprecationTarget;
import com.android.build.gradle.internal.services.DslServices;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

public class LintOptions
        implements com.android.builder.model.LintOptions,
                com.android.build.api.dsl.LintOptions,
                Serializable {
    private static final long serialVersionUID = 1L;

    @Nullable private final DslServices dslServices;
    private final Set<String> disable = Sets.newHashSet();
    private final Set<String> enable = Sets.newHashSet();
    private final Set<String> checkOnly = Sets.newHashSet();
    private boolean abortOnError = true;
    private boolean absolutePaths = true;
    private boolean noLines;
    private boolean quiet;
    private boolean checkAllWarnings;
    private boolean ignoreWarnings;
    private boolean warningsAsErrors;
    private boolean showAll;
    private boolean checkReleaseBuilds = true;
    private boolean explainIssues = true;
    private boolean checkTestSources;
    private boolean ignoreTestSources;
    private boolean checkGeneratedSources;
    private boolean checkDependencies;
    @Nullable
    private File lintConfig;
    private boolean textReport;
    @Nullable
    private File textOutput;
    private boolean htmlReport = true;
    @Nullable
    private File htmlOutput;
    private boolean xmlReport = true;
    @Nullable
    private File xmlOutput;

    private final Map<String, Integer> severities = Maps.newHashMap();
    private File baselineFile;

    @Inject
    public LintOptions(@NonNull DslServices dslServices) {
        this.dslServices = dslServices;
    }

    public LintOptions(
            @NonNull Set<String> disable,
            @NonNull Set<String> enable,
            @NonNull Set<String> checkOnly,
            @Nullable File lintConfig,
            boolean textReport,
            @Nullable File textOutput,
            boolean htmlReport,
            @Nullable File htmlOutput,
            boolean xmlReport,
            @Nullable File xmlOutput,
            boolean abortOnError,
            boolean absolutePaths,
            boolean noLines,
            boolean quiet,
            boolean checkAllWarnings,
            boolean ignoreWarnings,
            boolean warningsAsErrors,
            boolean showAll,
            boolean explainIssues,
            boolean checkReleaseBuilds,
            boolean checkTestSources,
            boolean ignoreTestSources,
            boolean checkGeneratedSources,
            boolean checkDependencies,
            @Nullable File baselineFile,
            @Nullable Map<String, Integer> severityOverrides) {
        this.dslServices = null;
        this.disable.addAll(disable);
        this.enable.addAll(enable);
        this.checkOnly.addAll(checkOnly);
        this.lintConfig = lintConfig;
        this.textReport = textReport;
        this.textOutput = textOutput;
        this.htmlReport = htmlReport;
        this.htmlOutput = htmlOutput;
        this.xmlReport = xmlReport;
        this.xmlOutput = xmlOutput;
        this.abortOnError = abortOnError;
        this.absolutePaths = absolutePaths;
        this.noLines = noLines;
        this.quiet = quiet;
        this.checkAllWarnings = checkAllWarnings;
        this.ignoreWarnings = ignoreWarnings;
        this.warningsAsErrors = warningsAsErrors;
        this.showAll = showAll;
        this.explainIssues = explainIssues;
        this.checkReleaseBuilds = checkReleaseBuilds;
        this.checkTestSources = checkTestSources;
        this.ignoreTestSources = ignoreTestSources;
        this.checkGeneratedSources = checkGeneratedSources;
        this.checkDependencies = checkDependencies;
        this.baselineFile = baselineFile;

        if (severityOverrides != null) {
            for (Map.Entry<String,Integer> entry : severityOverrides.entrySet()) {
                severities.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @NonNull
    public static com.android.builder.model.LintOptions create(@NonNull LintOptions source) {
        return new LintOptions(
                source.getDisable(),
                source.getEnable(),
                source.getCheckOnly(),
                source.getLintConfig(),
                source.getTextReport(),
                source.getTextOutput(),
                source.getHtmlReport(),
                source.getHtmlOutput(),
                source.getXmlReport(),
                source.getXmlOutput(),
                source.isAbortOnError(),
                source.isAbsolutePaths(),
                source.isNoLines(),
                source.isQuiet(),
                source.isCheckAllWarnings(),
                source.isIgnoreWarnings(),
                source.isWarningsAsErrors(),
                source.isShowAll(),
                source.isExplainIssues(),
                source.isCheckReleaseBuilds(),
                source.isCheckTestSources(),
                source.isIgnoreTestSources(),
                source.isCheckGeneratedSources(),
                source.isCheckDependencies(),
                source.getBaselineFile(),
                source.getSeverityOverrides());
    }

    @Override
    @NonNull
    @Input
    public Set<String> getDisable() {
        return disable;
    }

    public void setDisable(@Nullable Set<String> ids) {
        if (ids != null) {
            disable.addAll(ids);
        }
    }

    @Override
    @NonNull
    @Input
    public Set<String> getEnable() {
        return enable;
    }

    public void setEnable(@Nullable Set<String> ids) {
        if (ids != null) {
            enable.addAll(ids);
        }
    }

    @Input
    @NonNull
    @Override
    public Set<String> getCheckOnly() {
        return checkOnly;
    }

    /** @deprecated Replaced by {@link #getCheckOnly()} */
    @Deprecated
    @Override
    @NonNull
    public Set<String> getCheck() {
        return checkOnly;
    }

    @Deprecated
    public void setCheck(@NonNull Set<String> ids) {
        checkOnly.addAll(ids);
    }

    @Override
    @Input
    public boolean isAbortOnError() {
        return abortOnError;
    }

    @Override
    public void setAbortOnError(boolean abortOnError) {
        this.abortOnError = abortOnError;
    }

    @Override
    @Input
    public boolean isAbsolutePaths() {
        return absolutePaths;
    }

    @Override
    public void setAbsolutePaths(boolean absolutePaths) {
        this.absolutePaths = absolutePaths;
    }

    @Override
    @Input
    public boolean isNoLines() {
        return this.noLines;
    }

    @Override
    public void setNoLines(boolean noLines) {
        this.noLines = noLines;
    }

    @Override
    @Input
    public boolean isQuiet() {
        return quiet;
    }

    @Override
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    @Override
    @Input
    public boolean isCheckAllWarnings() {
        return checkAllWarnings;
    }

    @Override
    public void setCheckAllWarnings(boolean warnAll) {
        this.checkAllWarnings = warnAll;
    }

    @Override
    @Input
    public boolean isIgnoreWarnings() {
        return ignoreWarnings;
    }

    @Override
    public void setIgnoreWarnings(boolean noWarnings) {
        this.ignoreWarnings = noWarnings;
    }

    @Override
    @Input
    public boolean isWarningsAsErrors() {
        return warningsAsErrors;
    }

    @Override
    public void setWarningsAsErrors(boolean allErrors) {
        this.warningsAsErrors = allErrors;
    }

    @Override
    public boolean isCheckTestSources() {
        return checkTestSources;
    }

    @Override
    public boolean isIgnoreTestSources() {
        return ignoreTestSources;
    }

    @Override
    public void setCheckTestSources(boolean checkTestSources) {
        this.checkTestSources = checkTestSources;
        if (checkTestSources) {
            ignoreTestSources = false;
        }
    }

    @Override
    public void setIgnoreTestSources(boolean ignoreTestSources) {
        this.ignoreTestSources = ignoreTestSources;
        if (ignoreTestSources) {
            checkTestSources = false;
        }
    }

    @Override
    public boolean isCheckGeneratedSources() {
        return checkGeneratedSources;
    }

    @Override
    public void setCheckGeneratedSources(boolean checkGeneratedSources) {
        this.checkGeneratedSources = checkGeneratedSources;
    }

    @Override
    @Input
    public boolean isCheckDependencies() {
        return checkDependencies;
    }

    @Override
    public void setCheckDependencies(boolean checkDependencies) {
        this.checkDependencies = checkDependencies;
    }

    @Override
    @Input
    public boolean isExplainIssues() {
        return explainIssues;
    }

    @Override
    public void setExplainIssues(boolean explainIssues) {
        this.explainIssues = explainIssues;
    }

    @Override
    @Input
    public boolean isShowAll() {
        return showAll;
    }

    @Override
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    @Override
    @Input
    public boolean isCheckReleaseBuilds() {
        return checkReleaseBuilds;
    }

    @Override
    public void setCheckReleaseBuilds(boolean checkReleaseBuilds) {
        this.checkReleaseBuilds = checkReleaseBuilds;
    }

    @Nullable
    @Override
    @Optional
    @InputFile
    public File getLintConfig() {
        return lintConfig;
    }

    @Override
    @Input
    public boolean getTextReport() {
        return textReport;
    }

    @Override
    public void setTextReport(boolean textReport) {
        this.textReport = textReport;
    }

    @Override
    public void setHtmlReport(boolean htmlReport) {
        this.htmlReport = htmlReport;
    }

    @Override
    public void setHtmlOutput(@NonNull File htmlOutput) {
        this.htmlOutput = htmlOutput;
    }

    @Override
    public void setXmlReport(boolean xmlReport) {
        this.xmlReport = xmlReport;
    }

    @Override
    public void setXmlOutput(@NonNull File xmlOutput) {
        if (xmlOutput.getName().equals("lint.xml")) {
            throw new GradleException(
                    "Don't set the xmlOutput file to \"lint.xml\"; that's a "
                            + "reserved filename used for for lint configuration files, not reports.");
        }
        this.xmlOutput = xmlOutput;
    }

    @Override
    @Nullable
    @Optional
    @Input
    public File getTextOutput() {
        return textOutput;
    }

    @Override
    public void setTextOutput(@NonNull File textOutput) {
        textOutput(textOutput);
    }

    @Override
    @Input
    public boolean getHtmlReport() {
        return htmlReport;
    }

    @Override
    @Nullable
    @Optional
    @OutputFile
    public File getHtmlOutput() {
        return htmlOutput;
    }

    @Override
    @Input
    public boolean getXmlReport() {
        return xmlReport;
    }

    @Override
    @Nullable
    @Optional
    @OutputFile
    public File getXmlOutput() {
        return xmlOutput;
    }

    @Override
    public void setLintConfig(@NonNull File lintConfig) {
        this.lintConfig = lintConfig;
    }

    @Override
    @Nullable
    public File getBaselineFile() {
        return baselineFile;
    }

    @Override
    public void setBaselineFile(@Nullable File baselineFile) {
        this.baselineFile = baselineFile;
    }

    @Override
    public void baseline(@NonNull String baseline) {
        File file = new File(baseline);
        if (!file.isAbsolute()) {
            // If I had the project context, I could do
            //   project.file(baselineFile.getPath())
            file = file.getAbsoluteFile();
        }
        this.baselineFile = file;
    }

    @Override
    public void baseline(@NonNull File baselineFile) {
        this.baselineFile = baselineFile;
    }

    @Override
    @Nullable
    public Map<String, Integer> getSeverityOverrides() {
        if (severities == null || severities.isEmpty()) {
            return null;
        }

        return severities;
    }

    // -- DSL Methods.

    @Override
    public void check(@NonNull String id) {
        emitCheckWarning();
        checkOnly(id);
    }

    @Override
    public void check(@NonNull String... ids) {
        emitCheckWarning();
        checkOnly(ids);
    }

    private void emitCheckWarning() {
        assert dslServices != null; // Always true when these DSL methods are called
        dslServices
                .getDeprecationReporter()
                .reportDeprecatedUsage(
                        "android.lintOptions.checkOnly",
                        "android.lintOptions.check",
                        DeprecationTarget.LINT_CHECK_ONLY);
    }

    @Override
    public void checkOnly(@NonNull String... ids) {
        for (String id : ids) {
            checkOnly(id);
        }
    }

    @Override
    public void checkOnly(@NonNull String id) {
        checkOnly.add(id);
    }

    @Override
    public void enable(@NonNull String id) {
        enable.add(id);
        severities.put(id, SEVERITY_DEFAULT_ENABLED);
    }

    @Override
    public void enable(String... ids) {
        for (String id : ids) {
            enable(id);
        }
    }

    @Override
    public void disable(@NonNull String id) {
        disable.add(id);
        severities.put(id, SEVERITY_IGNORE);
    }

    @Override
    public void disable(String... ids) {
        for (String id : ids) {
            disable(id);
        }
    }

    // For textOutput 'stdout' or 'stderr' (normally a file)
    @Override
    public void textOutput(String textOutput) {
        this.textOutput = new File(textOutput);
    }

    // For textOutput file()
    @Override
    public void textOutput(File textOutput) {
        this.textOutput = textOutput;
    }

    @Override
    public void fatal(@NonNull String id) {
        severities.put(id, SEVERITY_FATAL);
    }

    @Override
    public void fatal(String... ids) {
        for (String id : ids) {
            fatal(id);
        }
    }

    @Override
    public void error(@NonNull String id) {
        severities.put(id, SEVERITY_ERROR);
    }

    @Override
    public void error(String... ids) {
        for (String id : ids) {
            error(id);
        }
    }

    @Override
    public void warning(@NonNull String id) {
        severities.put(id, SEVERITY_WARNING);
    }

    @Override
    public void warning(String... ids) {
        for (String id : ids) {
            warning(id);
        }
    }

    @Override
    public void ignore(@NonNull String id) {
        severities.put(id, SEVERITY_IGNORE);
    }

    @Override
    public void ignore(String... ids) {
        for (String id : ids) {
            ignore(id);
        }
    }

    @Override
    public void informational(@NonNull String id) {
        severities.put(id, SEVERITY_INFORMATIONAL);
    }

    @Override
    public void informational(String... ids) {
        for (String id : ids) {
            informational(id);
        }
    }
}
