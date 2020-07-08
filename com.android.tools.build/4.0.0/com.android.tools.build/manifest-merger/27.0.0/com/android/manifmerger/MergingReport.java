/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.manifmerger;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.Immutable;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.utils.ILogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

/**
 * Contains the result of 2 files merging.
 *
 * TODO: more work necessary, this is pretty raw as it stands.
 */
@Immutable
public class MergingReport {

    public enum MergedManifestKind {
        /**
         * Merged manifest file
         */
        MERGED,

        /**
         * Merged manifest file with Instant Run related decorations.
         */
        INSTANT_RUN,

        /**
         * Merged manifest file with unresolved placeholders encoded to be AAPT friendly.
         */
        AAPT_SAFE,

        /** Blame file for merged manifest file. */
        BLAME,

        /** Merged manifest file for bundletool. */
        BUNDLE,

        /** Feature manifest to be merged back into the base. */
        METADATA_FEATURE,

        /** Merged manifest file for deploying app as an instant app. */
        INSTANT_APP,
    }

    @NonNull
    private final Map<MergedManifestKind, String> mergedDocuments;
    @NonNull
    private final Map<MergedManifestKind, XmlDocument> mergedXmlDocuments;
    @NonNull
    private final Result result;
    // list of logging events, ordered by their recording time.
    @NonNull
    private final ImmutableList<Record> records;
    @NonNull
    private final ImmutableList<String> intermediaryStages;
    @NonNull
    private final Actions actions;
    @NonNull
    private final String packageName;

    private MergingReport(@NonNull Map<MergedManifestKind, String> mergedDocuments,
            @NonNull Map<MergedManifestKind, XmlDocument> mergedXmlDocuments,
            @NonNull Result result,
            @NonNull ImmutableList<Record> records,
            @NonNull ImmutableList<String> intermediaryStages,
            @NonNull Actions actions,
            @NonNull String packageName) {
        this.mergedDocuments = mergedDocuments;
        this.mergedXmlDocuments = mergedXmlDocuments;
        this.result = result;
        this.records = records;
        this.intermediaryStages = intermediaryStages;
        this.actions = actions;
        this.packageName = packageName;
    }

    /**
     * dumps all logging records to a logger.
     */
    public void log(@NonNull ILogger logger) {
        for (Record record : records) {
            switch(record.mSeverity) {
                case WARNING:
                    logger.warning(record.toString());
                    break;
                case ERROR:
                    logger.error(null /* throwable */, record.toString());
                    break;
                case INFO:
                    logger.verbose(record.toString());
                    break;
                default:
                    logger.error(null /* throwable */, "Unhandled record type " + record.mSeverity);
            }
        }
        actions.log(logger);

        if (!result.isSuccess()) {
            logger.warning("\nSee http://g.co/androidstudio/manifest-merger for more information"
                    + " about the manifest merger.\n");
        }
    }

    @Nullable
    public String getMergedDocument(@NonNull MergedManifestKind state) {
        return mergedDocuments.get(state);
    }

    @Nullable
    public XmlDocument getMergedXmlDocument(@NonNull MergedManifestKind state) {
        return mergedXmlDocuments.get(state);
    }

    /**
     * Returns all the merging intermediary stages if
     * {@link com.android.manifmerger.ManifestMerger2.Invoker.Feature#KEEP_INTERMEDIARY_STAGES}
     * is set.
     */
    @NonNull
    public ImmutableList<String> getIntermediaryStages() {
        return intermediaryStages;
    }

    /**
     * Overall result of the merging process.
     */
    public enum Result {
        SUCCESS,

        WARNING,

        ERROR;

        public boolean isSuccess() {
            return this == SUCCESS || this == WARNING;
        }

        public boolean isWarning() {
            return this == WARNING;
        }

        public boolean isError() {
            return this == ERROR;
        }
    }

    @NonNull
    public Result getResult() {
        return result;
    }

    @NonNull
    public ImmutableList<Record> getLoggingRecords() {
        return records;
    }

    @NonNull
    public Actions getActions() {
        return actions;
    }

    @NonNull
    public String getReportString() {
        switch (result) {
            case SUCCESS:
                return "Manifest merger executed successfully";
            case WARNING:
                return records.size() > 1
                        ? "Manifest merger exited with warnings, see logs"
                        : "Manifest merger warning : " + records.get(0).mLog;
            case ERROR:
                return records.size() > 1
                        ? "Manifest merger failed with multiple errors, see logs"
                        : "Manifest merger failed : " + records.get(0).mLog;
            default:
                return "Manifest merger returned an invalid result " + result;
        }
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    /**
     * Log record. This is used to give users some information about what is happening and
     * what might have gone wrong.
     */
    public static class Record {


        public enum Severity {WARNING, ERROR, INFO }

        @NonNull
        private final Severity mSeverity;
        @NonNull
        private final String mLog;
        @NonNull
        private final SourceFilePosition mSourceLocation;

        private Record(
                @NonNull SourceFilePosition sourceLocation,
                @NonNull Severity severity,
                @NonNull String mLog) {
            this.mSourceLocation = sourceLocation;
            this.mSeverity = severity;
            this.mLog = mLog;
        }

        @NonNull
        public Severity getSeverity() {
            return mSeverity;
        }

        @NonNull
        public String getMessage() {
            return mLog;
        }

        @NonNull
        public SourceFilePosition getSourceLocation() {
            return mSourceLocation;
        }

        @NonNull
        @Override
        public String toString() {
            return mSourceLocation.toString() // needs short string.
                    + " "
                    + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mSeverity.toString())
                    + ":\n\t"
                    + mLog;
        }
    }

    /**
     * This builder is used to accumulate logging, action recording and intermediary results as well
     * as final result of the merging activity.
     *
     * <p>Once the merging is finished, the {@link #build()} is called to return an immutable
     * version of itself with all the logging, action recordings and xml files obtainable.
     */
    @VisibleForTesting
    public static class Builder {

        private Map<MergedManifestKind, String> mergedDocuments =
                new EnumMap<MergedManifestKind, String>(MergedManifestKind.class);
        private Map<MergedManifestKind, XmlDocument> mergedXmlDocuments =
          new EnumMap<MergedManifestKind, XmlDocument>(MergedManifestKind.class);


        @NonNull
        private ImmutableList.Builder<Record> mRecordBuilder = new ImmutableList.Builder<Record>();
        @NonNull
        private ImmutableList.Builder<String> mIntermediaryStages = new ImmutableList.Builder<String>();
        private boolean mHasWarnings = false;
        private boolean mHasErrors = false;
        @NonNull
        private ActionRecorder mActionRecorder = new ActionRecorder();
        @NonNull private final ILogger mLogger;
        private String packageName;

        Builder(@NonNull ILogger logger) {
            mLogger = logger;
        }

        Builder setMergedDocument(@NonNull MergedManifestKind mergedManifestKind, @NonNull String mergedDocument) {
            this.mergedDocuments.put(mergedManifestKind, mergedDocument);
            return this;
        }

        Builder setMergedXmlDocument(@NonNull MergedManifestKind mergedManifestKind, @NonNull XmlDocument mergedDocument) {
            this.mergedXmlDocuments.put(mergedManifestKind, mergedDocument);
            return this;
        }

        @NonNull
        @VisibleForTesting
        Builder addMessage(@NonNull SourceFile sourceFile,
                int line,
                int column,
                @NonNull Record.Severity severity,
                @NonNull String message) {
            // The line and column used are 1-based, but SourcePosition uses zero-based.
            return addMessage(
                    new SourceFilePosition(sourceFile, new SourcePosition(line - 1, column -1, -1)),
                    severity,
                    message);
        }

        @NonNull
        Builder addMessage(@NonNull SourceFile sourceFile,
                @NonNull Record.Severity severity,
                @NonNull String message) {
            return addMessage(
                    new SourceFilePosition(sourceFile, SourcePosition.UNKNOWN),
                    severity,
                    message);
        }

        void addMessage(
                @NonNull XmlElement element,
                @NonNull MergingReport.Record.Severity severity,
                @NonNull String message) {
            addMessage(element.getSourceFilePosition(), severity, message);
        }

        @NonNull
        Builder addMessage(
                @NonNull XmlAttribute attribute,
                @NonNull MergingReport.Record.Severity severity,
                @NonNull String message) {
            return addMessage(attribute, attribute.getPosition(), severity, message);
        }

        @NonNull
        Builder addMessage(
                @NonNull XmlAttribute attribute,
                @NonNull SourcePosition position,
                @NonNull MergingReport.Record.Severity severity,
                @NonNull String message) {
            return addMessage(
                    new SourceFilePosition(
                            attribute.getOwnerElement().getDocument().getSourceFile(), position),
                    severity,
                    message);
        }

        @NonNull
        Builder addMessage(@NonNull SourceFilePosition sourceFilePosition,
                    @NonNull Record.Severity severity,
                    @NonNull String message) {
            switch (severity) {
                case ERROR:
                    mHasErrors = true;
                    break;
                case WARNING:
                    mHasWarnings = true;
                    break;
            }
            mRecordBuilder.add(new Record(sourceFilePosition,  severity, message));
            return this;
        }

        @NonNull
        Builder addMergingStage(@NonNull String xml) {
            mIntermediaryStages.add(xml);
            return this;
        }

        /**
         * Returns true if some fatal errors were reported.
         */
        boolean hasErrors() {
            return mHasErrors;
        }

        @NonNull
        ActionRecorder getActionRecorder() {
            return mActionRecorder;
        }

        @NonNull
        MergingReport build() {
            Result result = mHasErrors
                    ? Result.ERROR
                    : mHasWarnings
                            ? Result.WARNING
                            : Result.SUCCESS;

            return new MergingReport(
                    mergedDocuments,
                    mergedXmlDocuments,
                    result,
                    mRecordBuilder.build(),
                    mIntermediaryStages.build(),
                    mActionRecorder.build(),
                    packageName);
        }

        @NonNull
        public ILogger getLogger() {
            return mLogger;
        }

        public String blame(XmlDocument document)
                throws ParserConfigurationException, SAXException, IOException {
            return mActionRecorder.build().blame(document);
        }

        public void setFinalPackageName(String finalPackageName) {
            this.packageName = finalPackageName;
        }
    }
}
