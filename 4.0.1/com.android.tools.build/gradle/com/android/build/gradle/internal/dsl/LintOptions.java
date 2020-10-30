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

/** DSL object for configuring lint options. */
@SuppressWarnings("unused")
public class LintOptions implements com.android.builder.model.LintOptions, Serializable {
    private static final long serialVersionUID = 1L;

    private Set<String> disable = Sets.newHashSet();
    private Set<String> enable = Sets.newHashSet();
    private Set<String> check = Sets.newHashSet();
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
    private boolean ignoreTestSources = false;
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

    private Map<String,Integer> severities = Maps.newHashMap();
    private File baselineFile;

    @Inject
    public LintOptions() {}

    public LintOptions(
            @NonNull Set<String> disable,
            @NonNull Set<String> enable,
            @Nullable Set<String> check,
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
        this.disable = disable;
        this.enable = enable;
        this.check = check;
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
    public static com.android.builder.model.LintOptions create(@NonNull com.android.builder.model.LintOptions source) {
        return new LintOptions(
                source.getDisable(),
                source.getEnable(),
                source.getCheck(),
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

    /**
     * Returns the set of issue id's to suppress. Callers are allowed to modify this collection. To
     * suppress a given issue, add the lint issue id to the returned set.
     */
    @Override
    @NonNull
    @Input
    public Set<String> getDisable() {
        return disable;
    }

    /**
     * Sets the set of issue id's to suppress. Callers are allowed to modify this collection. Note
     * that these ids add to rather than replace the given set of ids.
     */
    public void setDisable(@Nullable Set<String> ids) {
        if (ids != null) {
            disable.addAll(ids);
        }
    }

    /**
     * Returns the set of issue id's to enable. Callers are allowed to modify this collection. To
     * enable a given issue, add the issue ID to the returned set.
     */
    @Override
    @NonNull
    @Input
    public Set<String> getEnable() {
        return enable;
    }

    /**
     * Sets the set of issue id's to enable. Callers are allowed to modify this collection. Note
     * that these ids add to rather than replace the given set of ids.
     */
    public void setEnable(@Nullable Set<String> ids) {
        if (ids != null) {
            enable.addAll(ids);
        }
    }

    /**
     * Returns the exact set of issues to check, or null to run the issues that are enabled by
     * default plus any issues enabled via {@link #getEnable} and without issues disabled via {@link
     * #getDisable}. If non-null, callers are allowed to modify this collection.
     */
    @Override
    @Nullable
    @Optional
    @Input
    public Set<String> getCheck() {
        return check;
    }

    /**
     * Sets the <b>exact</b> set of issues to check.
     *
     * @param ids the set of issue id's to check
     */
    public void setCheck(@NonNull Set<String> ids) {
        check.addAll(ids);
    }

    /** Whether lint should set the exit code of the process if errors are found */
    @Override
    @Input
    public boolean isAbortOnError() {
        return abortOnError;
    }

    /** Sets whether lint should set the exit code of the process if errors are found */
    public void setAbortOnError(boolean abortOnError) {
        this.abortOnError = abortOnError;
    }

    /**
     * Whether lint should display full paths in the error output. By default the paths are relative
     * to the path lint was invoked from.
     */
    @Override
    @Input
    public boolean isAbsolutePaths() {
        return absolutePaths;
    }

    /**
     * Sets whether lint should display full paths in the error output. By default the paths are
     * relative to the path lint was invoked from.
     */
    public void setAbsolutePaths(boolean absolutePaths) {
        this.absolutePaths = absolutePaths;
    }

    /**
     * Whether lint should include the source lines in the output where errors occurred (true by
     * default)
     */
    @Override
    @Input
    public boolean isNoLines() {
        return this.noLines;
    }

    /**
     * Sets whether lint should include the source lines in the output where errors occurred (true
     * by default)
     */
    public void setNoLines(boolean noLines) {
        this.noLines = noLines;
    }

    /**
     * Returns whether lint should be quiet (for example, not write informational messages such as
     * paths to report files written)
     */
    @Override
    @Input
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Sets whether lint should be quiet (for example, not write informational messages such as
     * paths to report files written)
     */
    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    /** Returns whether lint should check all warnings, including those off by default */
    @Override
    @Input
    public boolean isCheckAllWarnings() {
        return checkAllWarnings;
    }

    /** Sets whether lint should check all warnings, including those off by default */
    public void setCheckAllWarnings(boolean warnAll) {
        this.checkAllWarnings = warnAll;
    }

    /** Returns whether lint will only check for errors (ignoring warnings) */
    @Override
    @Input
    public boolean isIgnoreWarnings() {
        return ignoreWarnings;
    }

    /** Sets whether lint will only check for errors (ignoring warnings) */
    public void setIgnoreWarnings(boolean noWarnings) {
        this.ignoreWarnings = noWarnings;
    }

    /** Returns whether lint should treat all warnings as errors */
    @Override
    @Input
    public boolean isWarningsAsErrors() {
        return warningsAsErrors;
    }

    /** Sets whether lint should treat all warnings as errors */
    public void setWarningsAsErrors(boolean allErrors) {
        this.warningsAsErrors = allErrors;
    }

    /**
     * Returns whether lint should run all checks on test sources, instead of just the lint checks
     * that have been specifically written to include tests (e.g. checks looking for specific test
     * errors, or checks that need to consider testing code such as the unused resource detector)
     */
    @Override
    public boolean isCheckTestSources() {
        return checkTestSources;
    }

    /**
     * Like {@link #isCheckTestSources()}, but always skips analyzing tests -- meaning that it also
     * ignores checks that have explicitly asked to look at test sources, such as the unused
     * resource check.
     */
    @Override
    public boolean isIgnoreTestSources() {
        return ignoreTestSources;
    }

    /**
     * Sets whether lint should run all checks on test sources, instead of just the lint checks that
     * have been specifically written to include tests (e.g. checks looking for specific test
     * errors, or checks that need to consider testing code such as the unused resource detector)
     */
    public void setCheckTestSources(boolean checkTestSources) {
        this.checkTestSources = checkTestSources;
        if (checkTestSources) {
            ignoreTestSources = false;
        }
    }

    /**
     * Sets whether lint should ignore all test sources. This is like {@link
     * #setCheckTestSources(boolean)}, but always skips analyzing tests -- meaning that it also
     * ignores checks that have explicitly asked to look at test sources, such as the unused
     * resource check.
     */
    public void setIgnoreTestSources(boolean ignoreTestSources) {
        this.ignoreTestSources = ignoreTestSources;
        if (ignoreTestSources) {
            checkTestSources = false;
        }
    }

    /** Returns whether lint should run checks on generated sources. */
    @Override
    public boolean isCheckGeneratedSources() {
        return checkGeneratedSources;
    }

    /** Sets whether lint should check generated sources */
    public void setCheckGeneratedSources(boolean checkGeneratedSources) {
        this.checkGeneratedSources = checkGeneratedSources;
    }

    /**
     * Returns whether lint should check all dependencies too as part of its analysis. Default is
     * false.
     */
    @Override
    @Input
    public boolean isCheckDependencies() {
        return checkDependencies;
    }

    /**
     * Sets whether lint should check all dependencies too as part of its analysis. Default is
     * false.
     */
    public void setCheckDependencies(boolean checkDependencies) {
        this.checkDependencies = checkDependencies;
    }

    /**
     * Returns whether lint should include explanations for issue errors. (Note that HTML and XML
     * reports intentionally do this unconditionally, ignoring this setting.)
     */
    @Override
    @Input
    public boolean isExplainIssues() {
        return explainIssues;
    }

    /**
     * Sets whether lint should include explanations for issue errors. (Note that HTML and XML
     * reports intentionally do this unconditionally, ignoring this setting.)
     */
    public void setExplainIssues(boolean explainIssues) {
        this.explainIssues = explainIssues;
    }

    /**
     * Returns whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    @Override
    @Input
    public boolean isShowAll() {
        return showAll;
    }

    /**
     * Sets whether lint should include all output (e.g. include all alternate locations, not
     * truncating long messages, etc.)
     */
    public void setShowAll(boolean showAll) {
        this.showAll = showAll;
    }

    /**
     * Returns whether lint should check for fatal errors during release builds. Default is true. If
     * issues with severity "fatal" are found, the release build is aborted.
     */
    @Override
    @Input
    public boolean isCheckReleaseBuilds() {
        return checkReleaseBuilds;
    }

    /**
     * Sets whether lint should check for fatal errors during release builds. Default is true. If
     * issues with severity "fatal" are found, the release build is aborted.
     */
    public void setCheckReleaseBuilds(boolean checkReleaseBuilds) {
        this.checkReleaseBuilds = checkReleaseBuilds;
    }

    /** Returns the default configuration file to use as a fallback */
    @Nullable
    @Override
    @Optional
    @InputFile
    public File getLintConfig() {
        return lintConfig;
    }

    /**
     * Whether we should write a text report. Default is false. The location can be controlled by
     * {@link #getTextOutput()}.
     */
    @Override
    @Input
    public boolean getTextReport() {
        return textReport;
    }

    /**
     * Sets whether we should write a text report. Default is false. The location can be controlled
     * by {@link #textOutput(File)}.
     */
    public void setTextReport(boolean textReport) {
        this.textReport = textReport;
    }

    /**
     * Sets whether we should write an HTML report. Default is true. The location can be controlled by
     * {@link #setHtmlOutput(File)}.
     */
    public void setHtmlReport(boolean htmlReport) {
        this.htmlReport = htmlReport;
    }

    /** Sets the optional path to where an HTML report should be written */
    public void setHtmlOutput(@NonNull File htmlOutput) {
        this.htmlOutput = htmlOutput;
    }

    /**
     * Sets whether we should write an XML report. Default is true. The location can be controlled by
     * {@link #setXmlOutput(File)}.
     */
    public void setXmlReport(boolean xmlReport) {
        this.xmlReport = xmlReport;
    }

    /** Sets the optional path to where an XML report should be written */
    public void setXmlOutput(@NonNull File xmlOutput) {
        if (xmlOutput.getName().equals("lint.xml")) {
            throw new GradleException(
                    "Don't set the xmlOutput file to \"lint.xml\"; that's a "
                            + "reserved filename used for for lint configuration files, not reports.");
        }
        this.xmlOutput = xmlOutput;
    }

    /**
     * The optional path to where a text report should be written. The special value "stdout" can be
     * used to point to standard output.
     */
    @Override
    @Nullable
    @Optional
    @Input
    public File getTextOutput() {
        return textOutput;
    }

    /**
     * Whether we should write an HTML report. Default is true. The location can be controlled by
     * {@link #getHtmlOutput()}.
     */
    @Override
    @Input
    public boolean getHtmlReport() {
        return htmlReport;
    }

    /** The optional path to where an HTML report should be written */
    @Override
    @Nullable
    @Optional
    @OutputFile
    public File getHtmlOutput() {
        return htmlOutput;
    }

    /**
     * Whether we should write an XML report. Default is true. The location can be controlled by {@link
     * #getXmlOutput()}.
     */
    @Override
    @Input
    public boolean getXmlReport() {
        return xmlReport;
    }

    /** The optional path to where an XML report should be written */
    @Override
    @Nullable
    @Optional
    @OutputFile
    public File getXmlOutput() {
        return xmlOutput;
    }

    /**
     * Sets the default config file to use as a fallback. This corresponds to a {@code lint.xml}
     * file with severities etc to use when a project does not have more specific information.
     */
    public void setLintConfig(@NonNull File lintConfig) {
        this.lintConfig = lintConfig;
    }

    /**
     * Returns the baseline file to use, if any. The baseline file is an XML report previously
     * created by lint, and any warnings and errors listed in that report will be ignored from
     * analysis.
     *
     * <p>If you have a project with a large number of existing warnings, this lets you set a
     * baseline and only see newly introduced warnings until you get a chance to go back and address
     * the "technical debt" of the earlier warnings.
     */
    @Override
    @Nullable
    public File getBaselineFile() {
        return baselineFile;
    }

    /**
     * Sets the baseline file to use, if any. The baseline file is an XML report previously created
     * by lint, and any warnings and errors listed in that report will be ignored from analysis.
     *
     * <p>If you have a project with a large number of existing warnings, this lets you set a
     * baseline and only see newly introduced warnings until you get a chance to go back and address
     * the "technical debt" of the earlier warnings.
     */
    public void setBaselineFile(@Nullable File baselineFile) {
        this.baselineFile = baselineFile;
    }

    /**
     * Sets the baseline file to use, if any. The baseline file is an XML report previously created
     * by lint, and any warnings and errors listed in that report will be ignored from analysis.
     *
     * <p>If you have a project with a large number of existing warnings, this lets you set a
     * baseline and only see newly introduced warnings until you get a chance to go back and address
     * the "technical debt" of the earlier warnings.
     */
    // DSL method
    public void baseline(@NonNull String baseline) {
        File file = new File(baseline);
        if (!file.isAbsolute()) {
            // If I had the project context, I could do
            //   project.file(baselineFile.getPath())
            file = file.getAbsoluteFile();
        }
        this.baselineFile = file;
    }

    /**
     * Sets the baseline file to use, if any. The baseline file is an XML report previously created
     * by lint, and any warnings and errors listed in that report will be ignored from analysis.
     *
     * <p>If you have a project with a large number of existing warnings, this lets you set a
     * baseline and only see newly introduced warnings until you get a chance to go back and address
     * the "technical debt" of the earlier warnings.
     */
    public void baseline(@NonNull File baselineFile) {
        this.baselineFile = baselineFile;
    }

    /**
     * An optional map of severity overrides. The map maps from issue id's to the corresponding
     * severity to use, which must be "fatal", "error", "warning", or "ignore".
     *
     * @return a map of severity overrides, or null. The severities are one of the constants {@link
     *     #SEVERITY_FATAL}, {@link #SEVERITY_ERROR}, {@link #SEVERITY_WARNING}, {@link
     *     #SEVERITY_INFORMATIONAL}, {@link #SEVERITY_IGNORE}
     */
    @Override
    @Nullable
    public Map<String, Integer> getSeverityOverrides() {
        if (severities == null || severities.isEmpty()) {
            return null;
        }

        return severities;
    }

    // -- DSL Methods.

    /** Adds the id to the set of issues to check. */
    public void check(String id) {
        check.add(id);
    }

    /** Adds the ids to the set of issues to check. */
    public void check(String... ids) {
        for (String id : ids) {
            check(id);
        }
    }

    /** Adds the id to the set of issues to enable. */
    public void enable(String id) {
        enable.add(id);
        severities.put(id, SEVERITY_DEFAULT_ENABLED);
    }

    /** Adds the ids to the set of issues to enable. */
    public void enable(String... ids) {
        for (String id : ids) {
            enable(id);
        }
    }

    /** Adds the id to the set of issues to suppress. */
    public void disable(String id) {
        disable.add(id);
        severities.put(id, SEVERITY_IGNORE);
    }

    /** Adds the ids to the set of issues to suppess. */
    public void disable(String... ids) {
        for (String id : ids) {
            disable(id);
        }
    }

    /** Sets the optional path to where a text report should be written */
    // For textOutput 'stdout' or 'stderr' (normally a file)
    @SuppressWarnings("unused") // DSL method
    public void textOutput(String textOutput) {
        this.textOutput = new File(textOutput);
    }

    /** Sets the optional path to where a text report should be written */
    // For textOutput file()
    @SuppressWarnings("unused") // DSL method
    public void textOutput(File textOutput) {
        this.textOutput = textOutput;
    }

    /** Adds a severity override for the given issues. */
    public void fatal(String id) {
        severities.put(id, SEVERITY_FATAL);
    }

    /** Adds a severity override for the given issues. */
    public void fatal(String... ids) {
        for (String id : ids) {
            fatal(id);
        }
    }

    /** Adds a severity override for the given issues. */
    public void error(String id) {
        severities.put(id, SEVERITY_ERROR);
    }

    /** Adds a severity override for the given issues. */
    public void error(String... ids) {
        for (String id : ids) {
            error(id);
        }
    }

    /** Adds a severity override for the given issues. */
    public void warning(String id) {
        severities.put(id, SEVERITY_WARNING);
    }

    /** Adds a severity override for the given issues. */
    public void warning(String... ids) {
        for (String id : ids) {
            warning(id);
        }
    }

    /** Adds a severity override for the given issues. */
    public void ignore(String id) {
        severities.put(id, SEVERITY_IGNORE);
    }

    /** Adds a severity override for the given issues. */
    public void ignore(String... ids) {
        for (String id : ids) {
            ignore(id);
        }
    }

    /** Adds a severity override for the given issues. */
    public void informational(String id) {
        severities.put(id, SEVERITY_INFORMATIONAL);
    }

    /** Adds a severity override for the given issues. */
    @SuppressWarnings("unused") // DSL method
    public void informational(String... ids) {
        for (String id : ids) {
            informational(id);
        }
    }
}
