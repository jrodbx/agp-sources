/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.builder.png;

import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.SdkConstants.TAG_VECTOR;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.resources.ResourcePreprocessor;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.utils.ILogger;
import com.google.common.io.Files;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Generates PNG images (and XML copies) from VectorDrawable files.
 */
public class VectorDrawableRenderer implements ResourcePreprocessor {
    private static final String TAG_GRADIENT = "gradient";

    private final Supplier<ILogger> mLogger;
    private final int mMinSdk;
    private final boolean mSupportLibraryIsUsed;
    private final File mOutputDir;
    private final Collection<Density> mDensities;

    public VectorDrawableRenderer(
            int minSdk,
            boolean supportLibraryIsUsed,
            @NonNull File outputDir,
            @NonNull Collection<Density> densities,
            @NonNull Supplier<ILogger> loggerSupplier) {
        mMinSdk = minSdk;
        mSupportLibraryIsUsed = supportLibraryIsUsed;
        mOutputDir = outputDir;
        mDensities = densities;
        mLogger = loggerSupplier;
    }

    @Override
    @NonNull
    public Collection<File> getFilesToBeGenerated(@NonNull File inputXmlFile) throws IOException {
        FolderConfiguration originalConfiguration = getFolderConfiguration(inputXmlFile);
        PreprocessingReason reason = getReasonForPreprocessing(inputXmlFile, originalConfiguration);
        if (reason == null) {
            return Collections.emptyList();
        }

        Collection<File> filesToBeGenerated = new ArrayList<>();

        DensityQualifier densityQualifier = originalConfiguration.getDensityQualifier();
        boolean validDensityQualifier = ResourceQualifier.isValid(densityQualifier);
        if (validDensityQualifier && densityQualifier.getValue() == Density.NODPI) {
            // If the files uses nodpi, just leave it alone.
            filesToBeGenerated.add(new File(
                    getDirectory(originalConfiguration),
                    inputXmlFile.getName()));
        } else if (validDensityQualifier && densityQualifier.getValue() != Density.ANYDPI) {
            // If the density is specified, generate one png and one xml.
            filesToBeGenerated.add(new File(
                    getDirectory(originalConfiguration),
                    inputXmlFile.getName().replace(".xml", ".png")));

            originalConfiguration.setVersionQualifier(
                    new VersionQualifier(reason.getSdkThreshold()));
            filesToBeGenerated.add(new File(
                    getDirectory(originalConfiguration),
                    inputXmlFile.getName()));
        } else {
            // Otherwise, generate one xml and N pngs, one per density.
            for (Density density : mDensities) {
                FolderConfiguration newConfiguration =
                        FolderConfiguration.copyOf(originalConfiguration);
                newConfiguration.setDensityQualifier(new DensityQualifier(density));

                filesToBeGenerated.add(new File(
                        getDirectory(newConfiguration),
                        inputXmlFile.getName().replace(".xml", ".png")));
            }

            originalConfiguration.setDensityQualifier(new DensityQualifier(Density.ANYDPI));
            originalConfiguration.setVersionQualifier(
                    new VersionQualifier(reason.getSdkThreshold()));

            filesToBeGenerated.add(
                    new File(getDirectory(originalConfiguration), inputXmlFile.getName()));
        }

        return filesToBeGenerated;
    }

    /**
     * Returns the reason for preprocessing, or null if preprocessing is not required. A vector
     * drawable file may need preprocessing for one of the following reasons:
     *
     * <ul>
     *   <li>Min SDK is below 21 and the support library is not used
     *   <li>The drawable contains a gradient or android:fillType attribute and min SDK is below 24
     *       and the support library is not used
     * </ul>
     */
    @Nullable
    private PreprocessingReason getReasonForPreprocessing(
            @NonNull File resourceFile, @NonNull FolderConfiguration folderConfig)
            throws IOException {
        if (mSupportLibraryIsUsed) return null;
        if (mMinSdk >= PreprocessingReason.GRADIENT_SUPPORT.getSdkThreshold()) return null;
        if (!isXml(resourceFile) || !isInDrawable(resourceFile)) return null;
        VersionQualifier versionQualifier = folderConfig.getVersionQualifier();
        int fileVersion = versionQualifier == null ? 0 : versionQualifier.getVersion();
        if (fileVersion >= PreprocessingReason.GRADIENT_SUPPORT.getSdkThreshold()) return null;

        try (InputStream stream = new BufferedInputStream(new FileInputStream(resourceFile))) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            XMLStreamReader xmlReader = factory.createXMLStreamReader(stream);

            boolean beforeFirstTag = true;
            while (xmlReader.hasNext()) {
                int event = xmlReader.next();
                if (event == XMLStreamReader.START_ELEMENT) {
                    if (beforeFirstTag) {
                        if (!TAG_VECTOR.equals(xmlReader.getLocalName())) {
                            return null;  // Not a vector drawable.
                        }
                        beforeFirstTag = false;
                    } else {
                        if (TAG_GRADIENT.equals(xmlReader.getLocalName())) {
                            return PreprocessingReason.GRADIENT_SUPPORT;
                        }
                        int n = xmlReader.getAttributeCount();
                        for (int i = 0; i < n; i++) {
                            if ("fillType".equals(xmlReader.getAttributeLocalName(i))
                                    && NS_RESOURCES.equals(xmlReader.getAttributeNamespace(i))) {
                                return PreprocessingReason.FILLTYPE_SUPPORT;
                            }
                        }
                    }
                }
            }

            // The drawable contains no gradients. Preprocessing is needed if the API level
            // is below 21.
            if (!beforeFirstTag
                    && mMinSdk < PreprocessingReason.VECTOR_SUPPORT.getSdkThreshold()
                    && fileVersion < PreprocessingReason.VECTOR_SUPPORT.getSdkThreshold()) {
                return PreprocessingReason.VECTOR_SUPPORT;
            }
        } catch (XMLStreamException e) {
            throw new IOException(
                    "Failed to parse resource file " + resourceFile.getAbsolutePath(), e);
        }

        return null;
    }

    @NonNull
    private File getDirectory(@NonNull FolderConfiguration newConfiguration) {
        return new File(
                mOutputDir,
                newConfiguration.getFolderName(ResourceFolderType.DRAWABLE));
    }

    @Override
    public void generateFile(@NonNull File toBeGenerated, @NonNull File original)
            throws IOException {
        Files.createParentDirs(toBeGenerated);

        if (isXml(toBeGenerated)) {
            Files.copy(original, toBeGenerated);
        } else {
            mLogger.get()
                    .verbose(
                            "Generating PNG: [%s] from [%s]",
                            toBeGenerated.getAbsolutePath(), original.getAbsolutePath());

            FolderConfiguration folderConfiguration = getFolderConfiguration(toBeGenerated);
            checkState(folderConfiguration.getDensityQualifier() != null);
            Density density = folderConfiguration.getDensityQualifier().getValue();
            assert density != null;
            float scaleFactor = density.getDpiValue() / (float) Density.MEDIUM.getDpiValue();
            if (scaleFactor <= 0) {
                scaleFactor = 1.0f;
            }

            VdPreview.TargetSize imageSize = VdPreview.TargetSize.createFromScale(scaleFactor);
            String xmlContent = Files.asCharSource(original, StandardCharsets.UTF_8).read();
            BufferedImage image = VdPreview.getPreviewFromVectorXml(imageSize, xmlContent, null);
            checkState(image != null, "Generating the image failed.");
            ImageIO.write(image, "png", toBeGenerated);
        }
    }

    @NonNull
    private static FolderConfiguration getFolderConfiguration(@NonNull File inputXmlFile) {
        String parentName = inputXmlFile.getParentFile().getName();
        FolderConfiguration originalConfiguration =
                FolderConfiguration.getConfigForFolder(parentName);
        checkArgument(
                originalConfiguration != null,
                "Invalid resource folder name [%s].",
                parentName);
        return originalConfiguration;
    }

    private static boolean isInDrawable(@NonNull File inputXmlFile) {
        ResourceFolderType folderType =
                ResourceFolderType.getFolderType(inputXmlFile.getParentFile().getName());

        return folderType == ResourceFolderType.DRAWABLE;
    }

    private static boolean isXml(@NonNull File resourceFile) {
        return Files.getFileExtension(resourceFile.getName()).equals("xml");
    }

    private enum PreprocessingReason {
        VECTOR_SUPPORT(
                21,
                "File was preprocessed as vector drawable support was added in Android 5.0 (API level 21)"),
        GRADIENT_SUPPORT(
                24,
                "File was preprocessed as vector drawable gradient support was added in Android 7.0 (API level 24)"),
        FILLTYPE_SUPPORT(
                24,
                "File was preprocessed as vector drawable android:filltype support was added in Android 7.0 (API level 24)");

        private final int mSdkThreshold;
        private final String explanation;

        PreprocessingReason(int sdkThreshold, @NonNull String explanation) {
            mSdkThreshold = sdkThreshold;
            this.explanation = explanation;
        }

        /**
         * Returns the minimum API level for which the preprocessing reason does not apply.
         */
        int getSdkThreshold() {
            return mSdkThreshold;
        }

        @NonNull
        public String getExplanation() {
            return explanation;
        }
    }

    protected String getPreprocessingReasonDescription(@NonNull File inputXmlFile)
            throws IOException {
        FolderConfiguration config = getFolderConfiguration(inputXmlFile);
        PreprocessingReason reason = getReasonForPreprocessing(inputXmlFile, config);
        return Objects.requireNonNull(reason, "Processing file for no reason").getExplanation();
    }
}
