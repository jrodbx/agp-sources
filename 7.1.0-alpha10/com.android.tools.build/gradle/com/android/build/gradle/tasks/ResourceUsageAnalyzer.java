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

package com.android.build.gradle.tasks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.DOT_9PNG;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_DEX;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.FN_RESOURCE_TEXT;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_9PNG_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_BINARY_XML_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PNG_CRC;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML;
import static com.android.build.gradle.internal.res.shrinker.DummyContent.TINY_PROTO_XML_CRC;
import static com.android.ide.common.symbols.SymbolIo.readFromAapt;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static com.google.common.base.Charsets.UTF_8;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.res.shrinker.LinkedResourcesFormat;
import com.android.build.gradle.internal.res.shrinker.ResourceShrinker;
import com.android.builder.dexing.AnalysisCallback;
import com.android.builder.dexing.MethodVisitingStatus;
import com.android.builder.dexing.R8ResourceShrinker;
import com.android.builder.utils.ZipEntryUtils;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.ide.common.symbols.Symbol;
import com.android.ide.common.symbols.SymbolTable;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.r8.references.MethodReference;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.ParserConfigurationException;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Class responsible for searching through a Gradle built tree (after resource merging, compilation
 * and shrinking has been completed, but before final .apk assembly), which figures out which
 * resources if any are unused, and removes them.
 *
 * <p>It does this by examining
 *
 * <ul>
 *   <li>The merged manifest, to find root resource references (such as drawables used for activity
 *       icons)
 *   <li>The merged R class (to find the actual integer constants assigned to resources)
 *   <li>The ProGuard mapping files (to find the mapping from original symbol names to short names)*
 *   <li>The merged resources (to find which resources reference other resources, e.g. drawable
 *       state lists including other drawables, or layouts including other layouts, or styles
 *       referencing other drawables, or menus items including action layouts, etc.)
 *   <li>The shrinked output classes (to find resource references in code that are actually
 *       reachable)
 * </ul>
 *
 * From all this, it builds up a reference graph, and based on the root references (e.g. from the
 * manifest and from the remaining code) it computes which resources are actually reachable in the
 * app, and anything that is not reachable is then marked for deletion.
 *
 * <p>A resource is referenced in code if either the field R.type.name is referenced (which is the
 * case for non-final resource references, e.g. in libraries), or if the corresponding int value is
 * referenced (for final resource values). We check this by looking at the shrinked output classes
 * with an ASM visitor. One complication is that code can also call {@code
 * Resources#getIdentifier(String,String,String)} where they can pass in the names of resources to
 * look up. To handle this scenario, we use the ClassVisitor to see if there are any calls to the
 * specific {@code Resources#getIdentifier} method. If not, great, the usage analysis is completely
 * accurate. If we <b>do</b> find one, we check <b>all</b> the string constants found anywhere in
 * the app, and look to see if any look relevant. For example, if we find the string "string/foo" or
 * "my.pkg:string/foo", we will then mark the string resource named foo (if any) as potentially
 * used. Similarly, if we find just "foo" or "/foo", we will mark <b>all</b> resources named "foo"
 * as potentially used. However, if the string is "bar/foo" or " foo " these strings are ignored.
 * This means we can potentially miss resources usages where the resource name is completed computed
 * (e.g. by concatenating individual characters or taking substrings of strings that do not look
 * like resource names), but that seems extremely unlikely to be a real-world scenario.
 *
 * <p>Analyzing dex files is also supported. It follows the same rules as analyzing class files.
 *
 * <p>For now, for reasons detailed in the code, this only applies to file-based resources like
 * layouts, menus and drawables, not value-based resources like strings and dimensions.
 */
public class ResourceUsageAnalyzer implements ResourceShrinker {
    private static final String ANDROID_RES = "android_res/";

    /**
     * Whether we should create small/empty dummy files instead of actually
     * removing file resources. This is to work around crashes on some devices
     * where the device is traversing resources. See http://b.android.com/79325 for more.
     */
    public static final boolean REPLACE_DELETED_WITH_EMPTY = true;

    private static final int ASM_VERSION = Opcodes.ASM7;

    /**
     Whether we support running aapt twice, to regenerate the resources.arsc file
     such that we can strip out value resources as well. We don't do this yet, for
     reasons detailed in the ShrinkResources task

     We have two options:
     (1) Copy the resource files over to a new destination directory, filtering out
     removed file resources and rewriting value resource files by stripping out
     the declarations for removed value resources. We then re-run aapt on this
     new destination directory.

     The problem with this approach is that when we re-run aapt it will assign new
     id's to all the resources, so we have to create dummy placeholders for all the
     removed resources. (The alternative would be to then run compilation one more
     time -- regenerating classes.jar, regenerating .dex) -- this would really slow
     down builds.)

     A cleaner solution than this is to get aapt to support using a predefined set
     of id's. It can emit R.txt symbol files now; if we can get it to read R.txt
     and use those numbers in its assignment, we can solve this cleanly. This request
     is tracked in https://code.google.com/p/android/issues/detail?id=70869

     (2) Just rewrite the .ap_ file directly. It's just a .zip file which contains
     (a) binary files for bitmaps and XML file resources such as layouts and menus
     (b) a binary file, resources.arsc, containing all the values.
     The resources.arsc format is opaque to us. However, MOST of the resource bulk
     comes from the bitmap and other resource files.

     So here we don't even need to run aapt a second time; we simply rewrite the
     .ap_ zip file directly, filtering out res/ files we know to be unused.

     Approach #2 gives us most of the space savings without the risk of #1 (running aapt
     a second time introduces the possibility of aapt compilation errors if we haven't
     been careful enough to insert resource aliases for all necessary items (such as
     inline @+id declarations), or if we haven't carefully not created aliases for items
     already defined in other value files as aliases, and perhaps most importantly,
     introduces risk that aapt will pick a different resource order anyway, which we can
     only guard against by doing a full compilation over again.

     Therefore, for now the below code uses #2, but since we can solve #1 with support
     from aapt), we're preserving all the code to rewrite resource files since that will
     give additional space savings, particularly for apps with a lot of strings or a lot
     of translations.
     */
    @SuppressWarnings("SpellCheckingInspection") // arsc
    public static final boolean TWO_PASS_AAPT = false;

    /** Special marker regexp which does not match a resource name */
    static final String NO_MATCH = "-nomatch-";

    /* A source of resource classes to track, can be either a folder or a jar */
    private final File mResourceClasseseSource;
    private final File mProguardMapping;
    /** These can be class or dex files. */
    private final Iterable<File> mClasses;
    private final File mMergedManifest;
    private final Iterable<File> mResourceDirs;

    private final File mReportFile;
    private final StringWriter mDebugOutput;
    private final PrintWriter mDebugPrinter;

    private boolean mVerbose;
    private boolean mDebug;
    private boolean mDryRun;

    /** The computed set of unused resources */
    private List<Resource> mUnused;

    /**
     * Map from resource class owners (VM format class) to corresponding resource entries.
     * This lets us map back from code references (obfuscated class and possibly obfuscated field
     * reference) back to the corresponding resource type and name.
     */
    private Map<String, Pair<ResourceType, Map<String, String>>> mResourceObfuscation =
            Maps.newHashMapWithExpectedSize(30);

    /** Obfuscated name of android/support/v7/widget/SuggestionsAdapter.java */
    private String mSuggestionsAdapter;

    /** Obfuscated name of android/support/v7/internal/widget/ResourcesWrapper.java */
    private String mResourcesWrapper;

    public ResourceUsageAnalyzer(
            @NonNull File rClasses,
            @NonNull Iterable<File> classes,
            @NonNull File manifest,
            @Nullable File mapping,
            @NonNull Iterable<File> resources,
            @Nullable File reportFile) {
        mResourceClasseseSource = rClasses;
        mProguardMapping = mapping;
        mClasses = classes;
        mMergedManifest = manifest;
        mResourceDirs = resources;

        mReportFile = reportFile;
        if (reportFile != null || mDebug) {
            mDebugOutput = new StringWriter(8*1024);
            mDebugPrinter = new PrintWriter(mDebugOutput);
        } else {
            mDebugOutput = null;
            mDebugPrinter = null;
        }
    }

    public ResourceUsageAnalyzer(
            @NonNull File rClasses,
            @NonNull Iterable<File> classes,
            @NonNull File manifest,
            @Nullable File mapping,
            @NonNull File resources,
            @Nullable File reportFile) {
        this(rClasses, classes, manifest, mapping, Arrays.asList(resources), reportFile);
    }

    @Override
    public void close() {
        if (mDebugOutput != null) {
            String output = mDebugOutput.toString();

            if (mDebug) {
                System.out.println(output);
            }

            if (mReportFile != null) {
                File dir = mReportFile.getParentFile();
                if (dir != null) {
                    if ((dir.exists() || dir.mkdir()) && dir.canWrite()) {
                        try {
                            Files.asCharSink(mReportFile, Charsets.UTF_8).write(output);
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        }
    }

    @Override
    public void analyze() throws IOException, ParserConfigurationException, SAXException {
        gatherResourceValues(mResourceClasseseSource);
        recordMapping(mProguardMapping);

        for (File jarOrDir : mClasses) {
            recordClassUsages(jarOrDir);
        }
        recordManifestUsages(mMergedManifest);
        recordResources(mResourceDirs);
        keepPossiblyReferencedResources();
        dumpReferences();
        mModel.processToolsAttributes();
        mUnused = mModel.findUnused();
    }

    public boolean isDryRun() {
        return mDryRun;
    }

    public void setDryRun(boolean dryRun) {
        mDryRun = dryRun;
    }

    public boolean isVerbose() {
        return mVerbose;
    }

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }


    public boolean isDebug() {
        return mDebug;
    }

    public void setDebug(boolean verbose) {
        mDebug = verbose;
    }

    @Override
    public void rewriteResourcesInBundleFormat(
            @NonNull File source,
            @NonNull File dest,
            @NonNull Map<String, String> moduleToPackageName
    ) {
        throw new UnsupportedOperationException(
                "App bundles are not supported by ResourceUsageAnalyzer");
    }

    /**
     * "Removes" resources from an .ap_ file by writing it out while filtering out
     * unused resources. This won't touch the values XML data (resources.arsc) but
     * will remove the individual file-based resources, which is where most of
     * the data is anyway (usually in drawable bitmaps)
     *
     * @param source the .ap_ file created by aapt
     * @param dest a new .ap_ file with unused file-based resources removed
     * @param format a format BINARY/PROTO in which compiled resources are represented in archive
     */
    @Override
    public void rewriteResourcesInApkFormat(
            @NonNull File source,
            @NonNull File dest,
            @NonNull LinkedResourcesFormat format
    ) throws IOException {
        if (dest.exists()) {
            boolean deleted = dest.delete();
            if (!deleted) {
                throw new IOException("Could not delete " + dest);
            }
        }

        try (JarInputStream zis =
                        new JarInputStream(new BufferedInputStream(new FileInputStream(source)));
                JarOutputStream zos =
                        new JarOutputStream(new BufferedOutputStream(new FileOutputStream(dest)))) {

            // Rather than using Deflater.DEFAULT_COMPRESSION we use 9 here,
            // since that seems to match the compressed sizes we observe in source
            // .ap_ files encountered by the resource shrinker:
            zos.setLevel(9);

            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                boolean directory = entry.isDirectory();
                Resource resource = getResourceByJarPath(name);
                if (!ZipEntryUtils.isValidZipEntryName(entry)) {
                    throw new InvalidPathException(
                            entry.getName(), "Entry name contains invalid characters");
                }
                if (resource == null || resource.isReachable()) {
                    copyToOutput(zis, zos, entry, name, directory);
                } else if (REPLACE_DELETED_WITH_EMPTY && !directory) {
                    replaceWithDummyEntry(zos, entry, name, format);
                } else if (isVerbose() || mDebugPrinter != null) {
                    String message =
                            "Skipped unused resource " + name + ": " + entry.getSize() + " bytes";
                    if (isVerbose()) {
                        System.out.println(message);
                    }
                    if (mDebugPrinter != null) {
                        mDebugPrinter.println(message);
                    }
                }
                entry = zis.getNextEntry();
            }
            zos.flush();
        }

        // If net negative, copy original back. This is unusual, but can happen
        // in some circumstances, such as the one described in
        // https://plus.google.com/+SaidTahsinDane/posts/X9sTSwoVUhB
        // "Removed unused resources: Binary resource data reduced from 588KB to 595KB: Removed -1%"
        // Guard against that, and worst case, just use the original.
        long before = source.length();
        long after = dest.length();
        if (after > before) {
            String message = "Resource shrinking did not work (grew from " + before + " to "
                    + after + "); using original instead";
            if (isVerbose()) {
                System.out.println(message);
            }
            if (mDebugPrinter != null) {
                mDebugPrinter.println(message);
            }

            Files.copy(source, dest);
        }
    }

    /**
     * Replaces the given entry with a minimal valid file of that type.
     *
     * @see #REPLACE_DELETED_WITH_EMPTY
     */
    private void replaceWithDummyEntry(
            JarOutputStream zos,
            ZipEntry entry,
            String name,
            LinkedResourcesFormat format
    ) throws IOException {
        // Create a new entry so that the compressed len is recomputed.
        byte[] bytes;
        long crc;
        if (name.endsWith(DOT_9PNG)) {
            bytes = TINY_9PNG;
            crc = TINY_9PNG_CRC;
        } else if (name.endsWith(DOT_PNG)) {
            bytes = TINY_PNG;
            crc = TINY_PNG_CRC;
        } else if (name.endsWith(DOT_XML)) {
            switch (format) {
                case BINARY:
                    bytes = TINY_BINARY_XML;
                    crc = TINY_BINARY_XML_CRC;
                    break;
                case PROTO:
                    bytes = TINY_PROTO_XML;
                    crc = TINY_PROTO_XML_CRC;
                    break;
                default:
                    throw new IllegalStateException("");
            }
        } else {
            bytes = new byte[0];
            crc = 0L;
        }
        JarEntry outEntry = new JarEntry(name);
        if (entry.getTime() != -1L) {
            outEntry.setTime(entry.getTime());
        }
        if (entry.getMethod() == JarEntry.STORED) {
            outEntry.setMethod(JarEntry.STORED);
            outEntry.setSize(bytes.length);
            outEntry.setCrc(crc);
        }
        zos.putNextEntry(outEntry);
        zos.write(bytes);
        zos.closeEntry();

        if (isVerbose() || mDebugPrinter != null) {
            String message =
                    "Skipped unused resource "
                            + name
                            + ": "
                            + entry.getSize()
                            + " bytes (replaced with small dummy file of size "
                            + bytes.length
                            + " bytes)";
            if (isVerbose()) {
                System.out.println(message);
            }
            if (mDebugPrinter != null) {
                mDebugPrinter.println(message);
            }
        }
    }

    private static void copyToOutput(
            JarInputStream zis, JarOutputStream zos, ZipEntry entry, String name, boolean directory)
            throws IOException {
        // We can't just compress all files; files that are not
        // compressed in the source .ap_ file must be left uncompressed
        // here, since for example RAW files need to remain uncompressed in
        // the APK such that they can be mmap'ed at runtime.
        // Preserve the STORED method of the input entry.
        JarEntry outEntry;
        if (entry.getMethod() == JarEntry.STORED) {
            outEntry = new JarEntry(entry);
        } else {
            // Create a new entry so that the compressed len is recomputed.
            outEntry = new JarEntry(name);
            if (entry.getTime() != -1L) {
                outEntry.setTime(entry.getTime());
            }
        }

        zos.putNextEntry(outEntry);

        if (!directory) {
            byte[] bytes = ByteStreams.toByteArray(zis);
            if (bytes != null) {
                zos.write(bytes);
            }
        }

        zos.closeEntry();
    }

     /** Writes the keep resources string to file specified by destination */
    public void emitKeepResources(Path destination) throws IOException {
        File destinationFile = destination.toFile();
        if (!destinationFile.exists()) {
            destinationFile.getParentFile().mkdirs();
            boolean success = destinationFile.createNewFile();
            if (!success) {
                throw new IOException("Could not create " + destination);
            }
        }
        Files.asCharSink(destinationFile, UTF_8).write(mModel.dumpKeepResources());
    }

    public void emitConfig(Path destination) throws IOException {
        File destinationFile = destination.toFile();
        if (!destinationFile.exists()) {
            destinationFile.getParentFile().mkdirs();
            boolean success = destinationFile.createNewFile();
            if (!success) {
                throw new IOException("Could not create " + destination);
            }
        }
        Files.asCharSink(destinationFile, UTF_8).write(mModel.dumpConfig());
    }

    /**
     * Remove resources (already identified by {@link #analyze()}).
     *
     * <p>This task will copy all remaining used resources over from the full resource directory to
     * a new reduced resource directory. However, it can't just delete the resources, because it has
     * no way to tell aapt to continue to use the same id's for the resources. When we re-run aapt
     * on the stripped resource directory, it will assign new id's to some of the resources (to fill
     * the gaps) which means the resource id's no longer match the constants compiled into the dex
     * files, and as a result, the app crashes at runtime.
     *
     * <p>Therefore, it needs to preserve all id's by actually keeping all the resource names. It
     * can still save a lot of space by making these resources tiny; e.g. all strings are set to
     * empty, all styles, arrays and plurals are set to not contain any children, and most
     * importantly, all file based resources like bitmaps and layouts are replaced by simple
     * resource aliases which just point to @null.
     *
     * @param destination directory to copy resources into; if null, delete resources in place
     */
    public void removeUnused(@Nullable File destination)
            throws IOException, ParserConfigurationException, SAXException {
        if (TWO_PASS_AAPT) {
            assert mUnused != null; // should always call analyze() first

            int resourceCount = mUnused.size()
                    * 4; // *4: account for some resource folder repetition
            boolean inPlace = destination == null;
            Set<File> skip = inPlace ? null : Sets.newHashSetWithExpectedSize(resourceCount);
            Set<File> rewrite = Sets.newHashSetWithExpectedSize(resourceCount);
            for (Resource resource : mUnused) {
                if (resource.declarations != null) {
                    for (Path path : resource.declarations) {
                        File file = path.toFile();
                        String folder = file.getParentFile().getName();
                        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
                        if (folderType != null && folderType != ResourceFolderType.VALUES) {
                            if (isVerbose()) {
                                System.out.println("Deleted unused resource " + file);
                            }
                            if (inPlace) {
                                if (!isDryRun()) {
                                    boolean delete = file.delete();
                                    if (!delete) {
                                        System.err.println("Could not delete " + file);
                                    }
                                }
                            } else {
                                assert skip != null;
                                skip.add(file);
                            }
                        } else {
                            // Can't delete values immediately; there can be many resources
                            // in this file, so we have to process them all
                            rewrite.add(file);
                        }
                    }
                }
            }

            // Special case the base values.xml folder
            File values =
                    new File(
                            Iterables.get(mResourceDirs, 0),
                            FD_RES_VALUES + File.separatorChar + "values.xml");
            boolean valuesExists = values.exists();
            if (valuesExists) {
                rewrite.add(values);
            }

            Map<File, String> rewritten = Maps.newHashMapWithExpectedSize(rewrite.size());

            // Delete value resources: Must rewrite the XML files
            for (File file : rewrite) {
                String xml = Files.toString(file, UTF_8);
                Document document = XmlUtils.parseDocument(xml, true);
                Element root = document.getDocumentElement();
                if (root != null && TAG_RESOURCES.equals(root.getTagName())) {
                    List<String> removed = Lists.newArrayList();
                    stripUnused(root, removed);
                    if (isVerbose()) {
                        System.out.println("Removed " + removed.size() +
                                " unused resources from " + file + ":\n  " +
                                Joiner.on(", ").join(removed));
                    }

                    String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
                    rewritten.put(file, formatted);
                }
            }

            if (isDryRun()) {
                return;
            }

            if (valuesExists) {
                String xml = rewritten.get(values);
                if (xml == null) {
                    xml = Files.toString(values, UTF_8);
                }
                Document document = XmlUtils.parseDocument(xml, true);

                assert false;
                /* This doesn't work; we don't need this when we have stable aapt id's anyway
                Element root = document.getDocumentElement();
                for (Resource resource : mModel.getAllResources()) {
                    if (resource.type == ResourceType.ID && !resource.hasDefault) {
                        Element item = document.createElement(TAG_ITEM);
                        item.setAttribute(ATTR_TYPE, resource.type.getName());
                        item.setAttribute(ATTR_NAME, resource.name);
                        root.appendChild(item);
                    } else if (!resource.reachable
                            && !resource.hasDefault
                            && resource.type != ResourceType.DECLARE_STYLEABLE
                            && resource.type != ResourceType.STYLE
                            && resource.type != ResourceType.PLURALS
                            && resource.type != ResourceType.ARRAY
                            && resource.isRelevantType()) {
                        Element item = document.createElement(TAG_ITEM);
                        item.setAttribute(ATTR_TYPE, resource.type.getName());
                        item.setAttribute(ATTR_NAME, resource.name);
                        root.appendChild(item);
                        String s = "@null";
                        item.appendChild(document.createTextNode(s));
                    }
                }
                */

                String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
                rewritten.put(values, formatted);
            }

            if (inPlace) {
                for (Map.Entry<File, String> entry : rewritten.entrySet()) {
                    File file = entry.getKey();
                    String formatted = entry.getValue();
                    Files.asCharSink(file, UTF_8).write(formatted);
                }
            } else {
                for (File dir : mResourceDirs) {
                    filteredCopy(dir, destination, skip, rewritten);
                }
            }
        } else {
            assert false;
        }
    }

    /**
     * Copies one resource directory tree into another; skipping some files, replacing
     * the contents of some, and passing everything else through unmodified
     */
    private static void filteredCopy(File source, File destination, Set<File> skip,
            Map<File, String> replace) throws IOException {
        if (TWO_PASS_AAPT) {
            if (source.isDirectory()) {
                File[] children = source.listFiles();
                if (children != null) {
                    if (!destination.exists()) {
                        boolean success = destination.mkdirs();
                        if (!success) {
                            throw new IOException("Could not create " + destination);
                        }
                    }
                    for (File child : children) {
                        filteredCopy(child, new File(destination, child.getName()), skip, replace);
                    }
                }
            } else if (!skip.contains(source) && source.isFile()) {
                String contents = replace.get(source);
                if (contents != null) {
                    Files.asCharSink(destination, UTF_8).write(contents);
                } else {
                    Files.copy(source, destination);
                }
            }
        } else {
            assert false;
        }
    }

    private void stripUnused(Element element, List<String> removed) {
        if (TWO_PASS_AAPT) {
            ResourceType type = ResourceType.fromXmlTag(element);
            if (type == ResourceType.ATTR) {
                // Not yet properly handled
                return;
            }

            Resource resource = mModel.getResource(element);
            if (resource != null) {
                if (resource.type == ResourceType.STYLEABLE || resource.type == ResourceType.ATTR) {
                    // Don't strip children of declare-styleable; we're not correctly
                    // tracking field references of the R_styleable_attr fields yet
                    return;
                }

                if (!resource.isReachable() &&
                        (resource.type == ResourceType.STYLE ||
                                resource.type == ResourceType.PLURALS ||
                                resource.type == ResourceType.ARRAY)) {
                    NodeList children = element.getChildNodes();
                    for (int i = children.getLength() - 1; i >= 0; i--) {
                        Node child = children.item(i);
                        element.removeChild(child);
                    }
                    return;
                }
            }

            NodeList children = element.getChildNodes();
            for (int i = children.getLength() - 1; i >= 0; i--) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    stripUnused((Element) child, removed);
                }
            }

            if (resource != null && !resource.isReachable()) {
                if (mVerbose) {
                    removed.add(resource.getUrl());
                }
                // for themes etc where .'s have been replaced by _'s
                String name = element.getAttribute(ATTR_NAME);
                if (name.isEmpty()) {
                    name = resource.name;
                }
                Node nextSibling = element.getNextSibling();
                Node parent = element.getParentNode();
                NodeList oldChildren = element.getChildNodes();
                parent.removeChild(element);
                Document document = element.getOwnerDocument();
                element = document.createElement("item");
                for (int i = 0; i < oldChildren.getLength(); i++) {
                    element.appendChild(oldChildren.item(i));
                }

                element.setAttribute(ATTR_NAME, name);
                element.setAttribute(ATTR_TYPE, resource.type.getName());
                final String text;
                switch (resource.type) {
                    case BOOL:
                        text = "true";
                        break;
                    case DIMEN:
                        text = "0dp";
                        break;
                    case INTEGER:
                        text = "0";
                        break;
                    default:
                        text = null;
                        break;
                }
                element.setTextContent(text);
                parent.insertBefore(element, nextSibling);
            }
        } else {
            assert false;
        }
    }

    @Nullable
    private Resource getResourceByJarPath(String path) {
        if (!path.startsWith("res/")) {
            return null;
        }

        // Jars use forward slash paths, not File.separator
        int folderStart = 4; // "res/".length
        int folderEnd = path.indexOf('/', folderStart);
        if (folderEnd == -1) {
            return null;
        }

        String folderName = path.substring(folderStart, folderEnd);
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folderName);
        if (folderType == null) {
            return null;
        }

        int nameStart = folderEnd + 1;
        int nameEnd = path.indexOf('.', nameStart);
        if (nameEnd == -1) {
            nameEnd = path.length();
        }

        String name = path.substring(nameStart, nameEnd);
        for (ResourceType type : FolderTypeRelationship.getRelatedResourceTypes(folderType)) {
            if (type == ResourceType.ID) {
                continue;
            }

            Resource resource = mModel.getResource(type, name);
            if (resource != null) {
                return resource;
            }
        }

        return null;
    }

    private void dumpReferences() {
        if (mDebugPrinter != null) {
            mDebugPrinter.print(mModel.dumpReferences());
        }
    }

    private void keepPossiblyReferencedResources() {
        if ((!mFoundGetIdentifier && !mFoundWebContent) || mStrings == null) {
            // No calls to android.content.res.Resources#getIdentifier; no need
            // to worry about string references to resources
            return;
        }

        if (!mModel.isSafeMode()) {
            // User specifically asked for us not to guess resources to keep; they will
            // explicitly mark them as kept if necessary instead
            return;
        }

        if (mDebugPrinter != null) {
            List<String> strings = new ArrayList<>(mStrings);
            Collections.sort(strings);
            mDebugPrinter.println("android.content.res.Resources#getIdentifier present: "
                    + mFoundGetIdentifier);
            mDebugPrinter.println("Web content present: " + mFoundWebContent);
            mDebugPrinter.println("Referenced Strings:");
            for (String s : strings) {
                s = s.trim().replace("\n", "\\n");
                if (s.length() > 40) {
                    s = s.substring(0, 37) + "...";
                } else if (s.isEmpty()) {
                    continue;
                }
                mDebugPrinter.println("  " + s);
            }
        }

        int shortest = Integer.MAX_VALUE;
        Set<String> names = Sets.newHashSetWithExpectedSize(50);
        for (Resource resource : mModel.getResources()) {
            String name = resource.name;
            names.add(name);
            int length = name.length();
            if (length < shortest) {
                shortest = length;
            }
        }

        for (String string : mStrings) {
            if (string.length() < shortest) {
                continue;
            }

            // Check whether the string looks relevant
            // We consider four types of strings:
            //  (1) simple resource names, e.g. "foo" from @layout/foo
            //      These might be the parameter to a getIdentifier() call, or could
            //      be composed into a fully qualified resource name for the getIdentifier()
            //      method. We match these for *all* resource types.
            //  (2) Relative source names, e.g. layout/foo, from @layout/foo
            //      These might be composed into a fully qualified resource name for
            //      getIdentifier().
            //  (3) Fully qualified resource names of the form package:type/name.
            //  (4) If mFoundWebContent is true, look for android_res/ URL strings as well

            if (mFoundWebContent) {
                Resource resource = mModel.getResourceFromFilePath(string);
                if (resource != null) {
                    ResourceUsageModel.markReachable(resource);
                    continue;
                } else {
                    int start = 0;
                    int slash = string.lastIndexOf('/');
                    if (slash != -1) {
                        start = slash + 1;
                    }
                    int dot = string.indexOf('.', start);
                    String name = string.substring(start, dot != -1 ? dot : string.length());
                    if (names.contains(name)) {
                        for (ListMultimap<String, Resource> map : mModel.getResourceMaps()) {
                            for (Resource currentResource : map.get(name)) {
                                resource = currentResource;
                                if (mDebug && resource != null) {
                                    mDebugPrinter.println("Marking " + resource + " used because "
                                            + "it matches string pool constant " + string);
                                }
                                ResourceUsageModel.markReachable(resource);
                            }
                        }
                    }
                }
            }

            // Look for normal getIdentifier resource URLs
            int n = string.length();
            boolean justName = true;
            boolean formatting = false;
            boolean haveSlash = false;
            for (int i = 0; i < n; i++) {
                char c = string.charAt(i);
                if (c == '/') {
                    haveSlash = true;
                    justName = false;
                } else if (c == '.' || c == ':' || c == '%') {
                    justName = false;
                    if (c == '%') {
                        formatting = true;
                    }
                } else if (!Character.isJavaIdentifierPart(c)) {
                    // This shouldn't happen; we've filtered out these strings in
                    // the {@link #referencedString} method
                    assert false : string;
                    break;
                }
            }

            String name;
            if (justName) {
                // Check name (below)
                name = string;

                // Check for a simple prefix match, e.g. as in
                // getResources().getIdentifier("ic_video_codec_" + codecName, "drawable", ...)
                for (Resource resource : mModel.getResources()) {
                    if (resource.name.startsWith(name)) {
                        if (mDebugPrinter != null) {
                            mDebugPrinter.println("Marking " + resource + " used because its "
                                    + "prefix matches string pool constant " + string);
                        }
                        ResourceUsageModel.markReachable(resource);
                    }
                }
            } else if (!haveSlash) {
                if (formatting) {
                    // Possibly a formatting string, e.g.
                    //   String name = String.format("my_prefix_%1d", index);
                    //   int res = getContext().getResources().getIdentifier(name, "drawable", ...)

                    try {
                        Pattern pattern = Pattern.compile(convertFormatStringToRegexp(string));
                        for (Resource resource : mModel.getResources()) {
                            if (pattern.matcher(resource.name).matches()) {
                                if (mDebugPrinter != null) {
                                    mDebugPrinter.println("Marking " + resource + " used because "
                                            + "it format-string matches string pool constant "
                                            + string);
                                }
                                ResourceUsageModel.markReachable(resource);
                            }
                        }
                    } catch (PatternSyntaxException ignored) {
                        // Might not have been a formatting string after all!
                    }
                }

                // If we have more than just a symbol name, we expect to also see a slash
                //noinspection UnnecessaryContinue
                continue;
            } else {
                // Try to pick out the resource name pieces; if we can find the
                // resource type unambiguously; if not, just match on names
                int slash = string.indexOf('/');
                assert slash != -1; // checked with haveSlash above
                name = string.substring(slash + 1);
                if (name.isEmpty() || !names.contains(name)) {
                    continue;
                }
                // See if have a known specific resource type
                if (slash > 0) {
                    int colon = string.indexOf(':');
                    String typeName = string.substring(colon != -1 ? colon + 1 : 0, slash);
                    ResourceType type = ResourceType.fromClassName(typeName);
                    if (type == null) {
                        continue;
                    }
                    Resource resource = mModel.getResource(type, name);
                    if (mDebug && resource != null) {
                        mDebugPrinter.println("Marking " + resource + " used because it "
                                + "matches string pool constant " + string);
                    }
                    ResourceUsageModel.markReachable(resource);
                    continue;
                }

                // fall through and check the name
            }

            if (names.contains(name)) {
                for (ListMultimap<String, Resource> map : mModel.getResourceMaps()) {
                    for (Resource resource : map.get(name)) {
                        if (mDebug && resource != null) {
                            mDebugPrinter.println("Marking " + resource + " used because it "
                                    + "matches string pool constant " + string);
                        }
                        ResourceUsageModel.markReachable(resource);
                    }
                }
            } else if (Character.isDigit(name.charAt(0))) {
                // Just a number? There are cases where it calls getIdentifier by
                // a String number; see for example SuggestionsAdapter in the support
                // library which reports supporting a string like "2130837524" and
                // "android.resource://com.android.alarmclock/2130837524".
                try {
                    int id = Integer.parseInt(name);
                    if (id != 0) {
                        ResourceUsageModel.markReachable(mModel.getResource(id));
                    }
                } catch (NumberFormatException e) {
                    // pass
                }
            }
        }
    }

    // Copied from StringFormatDetector
    // See java.util.Formatter docs
    public static final Pattern FORMAT = Pattern.compile(
            // Generic format:
            //   %[argument_index$][flags][width][.precision]conversion
            //
            "%" +
                    // Argument Index
                    "(\\d+\\$)?" +
                    // Flags
                    "([-+#, 0(<]*)?" +
                    // Width
                    "(\\d+)?" +
                    // Precision
                    "(\\.\\d+)?" +
                    // Conversion. These are all a single character, except date/time conversions
                    // which take a prefix of t/T:
                    "([tT])?" +
                    // The current set of conversion characters are
                    // b,h,s,c,d,o,x,e,f,g,a,t (as well as all those as upper-case characters), plus
                    // n for newlines and % as a literal %. And then there are all the time/date
                    // characters: HIKLm etc. Just match on all characters here since there should
                    // be at least one.
                    "([a-zA-Z%])");

    @VisibleForTesting
    static String convertFormatStringToRegexp(String formatString) {
        StringBuilder regexp = new StringBuilder();
        int from = 0;
        boolean hasEscapedLetters = false;
        Matcher matcher = FORMAT.matcher(formatString);
        int length = formatString.length();
        while (matcher.find(from)) {
            int start = matcher.start();
            int end = matcher.end();
            if (start == 0 && end == length) {
                // Don't match if the entire string literal starts with % and ends with
                // the a formatting character, such as just "%d": this just matches absolutely
                // everything and is unlikely to be used in a resource lookup
                return NO_MATCH;
            }
            if (start > from) {
                hasEscapedLetters |= appendEscapedPattern(formatString, regexp, from, start);
            }
            String pattern = ".*";
            String conversion = matcher.group(6);
            String timePrefix = matcher.group(5);

            //noinspection VariableNotUsedInsideIf,StatementWithEmptyBody: for readability.
            if (timePrefix != null) {
                // date notation; just use .* to match these
            } else if (conversion != null && conversion.length() == 1) {
                char type = conversion.charAt(0);
                switch (type) {
                    case 's':
                    case 'S':
                    case 't':
                    case 'T':
                        // Match everything
                        break;
                    case '%':
                        pattern = "%"; break;
                    case 'n':
                        pattern = "\n"; break;
                    case 'c':
                    case 'C':
                        pattern = "."; break;
                    case 'x':
                    case 'X':
                        pattern = "\\p{XDigit}+"; break;
                    case 'd':
                    case 'o':
                        pattern = "\\p{Digit}+"; break;
                    case 'b':
                        pattern = "(true|false)"; break;
                    case 'B':
                        pattern = "(TRUE|FALSE)"; break;
                    case 'h':
                    case 'H':
                        pattern = "(null|\\p{XDigit}+)"; break;
                    case 'f':
                        pattern = "-?[\\p{XDigit},.]+"; break;
                    case 'e':
                        pattern = "-?\\p{Digit}+[,.]\\p{Digit}+e\\+?\\p{Digit}+"; break;
                    case 'E':
                        pattern = "-?\\p{Digit}+[,.]\\p{Digit}+E\\+?\\p{Digit}+"; break;
                    case 'a':
                        pattern = "0x[\\p{XDigit},.+p]+"; break;
                    case 'A':
                        pattern = "0X[\\p{XDigit},.+P]+"; break;
                    case 'g':
                    case 'G':
                        pattern = "-?[\\p{XDigit},.+eE]+"; break;
                }

                // Allow space or 0 prefix
                if (!".*".equals(pattern)) {
                    String width = matcher.group(3);
                    //noinspection VariableNotUsedInsideIf
                    if (width != null) {
                        String flags = matcher.group(2);
                        if ("0".equals(flags)) {
                            pattern = "0*" + pattern;
                        } else {
                            pattern = " " + pattern;
                        }
                    }
                }

                // If it's a general .* wildcard which follows a previous .* wildcard,
                // just skip it (e.g. don't convert %s%s into .*.*; .* is enough.)
                int regexLength = regexp.length();
                if (!".*".equals(pattern)
                        || regexLength < 2
                        || regexp.charAt(regexLength - 1) != '*'
                        || regexp.charAt(regexLength - 2) != '.') {
                    regexp.append(pattern);
                }
            }
            from = end;
        }

        if (from < length) {
            hasEscapedLetters |= appendEscapedPattern(formatString, regexp, from, length);
        }

        if (!hasEscapedLetters) {
            // If the regexp contains *only* formatting characters, e.g. "%.0f%d", or
            // if it contains only formatting characters and punctuation, e.g. "%s_%d",
            // don't treat this as a possible resource name pattern string: it is unlikely
            // to be intended for actual resource names, and has the side effect of matching
            // most names.
            return NO_MATCH;
        }

        return regexp.toString();
    }

    /**
     * Appends the characters in the range [from,to> from formatString as escaped
     * regexp characters into the given string builder. Returns true if there were
     * any letters in the appended text.
     */
    private static boolean appendEscapedPattern(@NonNull String formatString,
            @NonNull StringBuilder regexp, int from, int to) {
        regexp.append(Pattern.quote(formatString.substring(from, to)));

        for (int i = from; i < to; i++) {
            if (Character.isLetter(formatString.charAt(i))) {
                return true;
            }
        }

        return false;
    }

    private void recordResources(Iterable<File> resources)
            throws IOException, SAXException, ParserConfigurationException {
        for (File resDir : resources) {
            File[] resourceFolders = resDir.listFiles();
            if (resourceFolders != null) {
                for (File folder : resourceFolders) {
                    ResourceFolderType folderType =
                            ResourceFolderType.getFolderType(folder.getName());
                    if (folderType != null) {
                        recordResources(folderType, folder);
                    }
                }
            }
        }
    }

    private void recordResources(@NonNull ResourceFolderType folderType, File folder)
            throws ParserConfigurationException, SAXException, IOException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String path = file.getPath();
                mModel.file = file;
                try {
                    boolean isXml = endsWithIgnoreCase(path, DOT_XML);
                    if (isXml) {
                        String xml = Files.toString(file, UTF_8);
                        Document document = XmlUtils.parseDocument(xml, true);
                        mModel.visitXmlDocument(file, folderType, document);
                    } else {
                        mModel.visitBinaryResource(folderType, file);
                    }
                } finally {
                    mModel.file = null;
                }
            }
        }
    }

    @VisibleForTesting
    void recordMapping(@Nullable File mapping) throws IOException {
        if (mapping == null || !mapping.exists()) {
            return;
        }
        final String ARROW = " -> ";
        final String RESOURCE = ".R$";
        Map<String, String> nameMap = null;
        for (String line : Files.readLines(mapping, UTF_8)) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (nameMap != null) {
                    // We're processing the members of a resource class: record names into the map
                    int n = line.length();
                    int i = 0;
                    for (; i < n; i++) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            break;
                        }
                    }
                    if (i < n && line.startsWith("int", i)) { // int or int[]
                        int start = line.indexOf(' ', i + 3) + 1;
                        int arrow = line.indexOf(ARROW);
                        if (start > 0 && arrow != -1) {
                            int end = line.indexOf(' ', start + 1);
                            if (end != -1) {
                                String oldName = line.substring(start, end);
                                String newName = line.substring(arrow + ARROW.length()).trim();
                                if (!newName.equals(oldName)) {
                                    nameMap.put(newName, oldName);
                                }
                            }
                        }
                    }
                }
                continue;
            } else {
                nameMap = null;
            }
            int index = line.indexOf(RESOURCE);
            if (index == -1) {
                // Record obfuscated names of a few known appcompat usages of
                // Resources#getIdentifier that are unlikely to be used for general
                // resource name reflection
                if (line.startsWith("android.support.v7.widget.SuggestionsAdapter ")) {
                    mSuggestionsAdapter = line.substring(line.indexOf(ARROW) + ARROW.length(),
                            line.indexOf(':') != -1 ? line.indexOf(':') : line.length())
                                .trim().replace('.','/') + DOT_CLASS;
                } else if (line.startsWith("android.support.v7.internal.widget.ResourcesWrapper ")
                        || line.startsWith("android.support.v7.widget.ResourcesWrapper ")
                        || (mResourcesWrapper == null // Recently wrapper moved
                           && line.startsWith("android.support.v7.widget.TintContextWrapper$TintResources "))) {
                    mResourcesWrapper = line.substring(line.indexOf(ARROW) + ARROW.length(),
                            line.indexOf(':') != -1 ? line.indexOf(':') : line.length())
                            .trim().replace('.','/') + DOT_CLASS;
                }
                continue;
            }
            int arrow = line.indexOf(ARROW, index + 3);
            if (arrow == -1) {
                continue;
            }
            String typeName = line.substring(index + RESOURCE.length(), arrow);
            ResourceType type = ResourceType.fromClassName(typeName);
            if (type == null) {
                continue;
            }
            int end = line.indexOf(':', arrow + ARROW.length());
            if (end == -1) {
                end = line.length();
            }
            String target = line.substring(arrow + ARROW.length(), end).trim();
            String ownerName = target.replace('.', '/');

            nameMap = Maps.newHashMap();
            Pair<ResourceType, Map<String, String>> pair = Pair.of(type, nameMap);
            mResourceObfuscation.put(ownerName, pair);
            // For fast lookup in isResourceClass
            mResourceObfuscation.put(ownerName + DOT_CLASS, pair);
        }
    }

    private void recordManifestUsages(File manifest)
            throws IOException, ParserConfigurationException, SAXException {
        String xml = Files.toString(manifest, UTF_8);
        Document document = XmlUtils.parseDocument(xml, true);
        mModel.visitXmlDocument(manifest, null, document);
    }

    private Set<String> mStrings;
    private boolean mFoundGetIdentifier;
    private boolean mFoundWebContent;

    private void referencedString(@NonNull String string) {
        // See if the string is at all eligible; ignore strings that aren't
        // identifiers (has java identifier chars and nothing but .:/), or are empty or too long
        // We also allow "%", used for formatting strings.
        if (string.isEmpty() || string.length() > 80) {
            return;
        }
        boolean haveIdentifierChar = false;
        for (int i = 0, n = string.length(); i < n; i++) {
            char c = string.charAt(i);
            boolean identifierChar = Character.isJavaIdentifierPart(c);
            if (!identifierChar && c != '.' && c != ':' && c != '/' && c != '%') {
                // .:/ are for the fully qualified resource names, or for resource URLs or
                // relative file names
                return;
            } else if (identifierChar) {
                haveIdentifierChar = true;
            }
        }
        if (!haveIdentifierChar) {
            return;
        }

        if (mStrings == null) {
            mStrings = Sets.newHashSetWithExpectedSize(300);
        }
        mStrings.add(string);

        if (!mFoundWebContent && string.contains(ANDROID_RES)) {
            mFoundWebContent = true;
        }
    }

    private void recordClassUsages(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    recordClassUsages(child);
                }
            }
        } else if (file.isFile()) {
            if (file.getPath().endsWith(DOT_CLASS) || file.getPath().endsWith(DOT_DEX)) {
                byte[] bytes = Files.toByteArray(file);
                recordClassUsages(file, file.getName(), bytes);
            } else if (file.getPath().endsWith(DOT_JAR)) {
                ZipInputStream zis = null;
                try {
                    FileInputStream fis = new FileInputStream(file);
                    try {
                        zis = new ZipInputStream(fis);
                        ZipEntry entry = zis.getNextEntry();
                        while (entry != null) {
                            String name = entry.getName();
                            if ((name.endsWith(DOT_CLASS)
                                            &&
                                            // Skip resource type classes like R$drawable; they will
                                            // reference the integer id's we're looking for, but
                                            // these aren't actual usages we need to track;
                                            // if somebody references the field elsewhere, we'll
                                            // catch that
                                            !isResourceClass(name))
                                    || name.endsWith(DOT_DEX)) {
                                byte[] bytes = ByteStreams.toByteArray(zis);
                                if (bytes != null) {
                                    recordClassUsages(file, name, bytes);
                                }
                            }

                            entry = zis.getNextEntry();
                        }
                    } finally {
                        Closeables.close(fis, true);
                    }
                } finally {
                    Closeables.close(zis, true);
                }
            }
        }
    }

    private void recordClassUsages(File file, String name, byte[] bytes) {
        if (name.endsWith(DOT_CLASS)) {
            ClassReader classReader = new ClassReader(bytes);
            classReader.accept(new UsageVisitor(file, name), SKIP_DEBUG | SKIP_FRAMES);
        } else {
            assert name.endsWith(DOT_DEX);
            AnalysisCallback callback =
                    new AnalysisCallback() {

                        Boolean isRClass = null;
                        final MethodVisitingStatus visitingMethod = new MethodVisitingStatus();

                        @Override
                        public boolean shouldProcess(@NonNull String internalName) {
                            isRClass = isResourceClass(internalName);
                            return true;
                        }

                        @Override
                        public void referencedInt(int value) {
                            if (shouldIgnoreField()) {
                                return;
                            }
                            ResourceUsageAnalyzer.this.referencedInt("dex", value, file, name);
                        }

                        @Override
                        public void referencedString(@NonNull String value) {
                            // Avoid marking R class fields as reachable.
                            if (shouldIgnoreField()) {
                                return;
                            }
                            ResourceUsageAnalyzer.this.referencedString(value);
                        }

                        @Override
                        public void referencedStaticField(
                                @NonNull String internalName, @NonNull String fieldName) {
                            // Avoid marking R class fields as reachable.
                            if (shouldIgnoreField()) {
                                return;
                            }
                            Resource resource = getResourceFromCode(internalName, fieldName);
                            if (resource != null) {
                                ResourceUsageModel.markReachable(resource);
                            }
                        }

                        @Override
                        public void referencedMethod(
                                @NonNull String internalName,
                                @NonNull String methodName,
                                @NonNull String methodDescriptor) {
                            if (isRClass
                                    && visitingMethod.isVisiting()
                                    && visitingMethod.getMethodName().equals("<clinit>")) {
                                return;
                            }
                            ResourceUsageAnalyzer.this.referencedMethodInvocation(
                                    internalName,
                                    methodName,
                                    methodDescriptor,
                                    internalName + DOT_CLASS);
                        }

                        @Override
                        public void startMethodVisit(@NotNull MethodReference methodReference) {
                            visitingMethod.setVisiting(true);
                            visitingMethod.setMethodName(methodReference.getMethodName());
                        }

                        @Override
                        public void endMethodVisit(@NotNull MethodReference methodReference) {
                            visitingMethod.setVisiting(false);
                            visitingMethod.setMethodName(null);
                        }

                        private boolean shouldIgnoreField() {
                            boolean visitingFromStaticInitRClass =
                                    isRClass
                                            && visitingMethod.isVisiting()
                                            && visitingMethod.getMethodName().equals("<clinit>");
                            return visitingFromStaticInitRClass
                                    || isRClass && !visitingMethod.isVisiting();
                        }
                    };
            R8ResourceShrinker.runResourceShrinkerAnalysis(bytes, file, callback);
        }
    }

    /** Returns whether the given class file name points to an aapt-generated compiled R class. */
    @VisibleForTesting
    boolean isResourceClass(@NonNull String name) {
        if (mResourceObfuscation.containsKey(name)) {
            return true;
        }
        int index = name.lastIndexOf('/');
        if (index != -1 && name.startsWith("R$", index + 1) && name.endsWith(DOT_CLASS)) {
            String typeName = name.substring(index + 3, name.length() - DOT_CLASS.length());
            return ResourceType.fromClassName(typeName) != null;
        }
        return false;
    }

    @VisibleForTesting
    @Nullable
    Resource getResourceFromCode(@NonNull String owner, @NonNull String name) {
        Pair<ResourceType, Map<String, String>> pair = mResourceObfuscation.get(owner);
        if (pair != null) {
            ResourceType type = pair.getFirst();
            Map<String, String> nameMap = pair.getSecond();
            String renamedField = nameMap.get(name);
            if (renamedField != null) {
                name = renamedField;
            }
            return mModel.getResource(type, name);
        }
        if (isValidResourceType(owner)) {
            ResourceType type =
                    ResourceType.fromClassName(owner.substring(owner.lastIndexOf('$') + 1));
            if (type != null) {
                return mModel.getResource(type, name);
            }
        }
        return null;
    }

    private Boolean isValidResourceType(String candidateString) {
        return candidateString.contains("/")
                && candidateString.substring(candidateString.lastIndexOf('/') + 1).contains("$");
    }

    private void gatherResourceValues(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    gatherResourceValues(child);
                }
            }
        } else if (file.isFile()) {
            if (file.getName().equals(SdkConstants.FN_RESOURCE_CLASS)) {
                parseResourceSourceClass(file);
            }
            if (file.getName().equals(SdkConstants.FN_R_CLASS_JAR)) {
                parseResourceRJar(file);
            }
            if (file.getName().equals(FN_RESOURCE_TEXT)) {
                addResourcesFromRTxtFile(file);
            }
        }
    }

    private void addResourcesFromRTxtFile(File file) {
        try {
            SymbolTable st = readFromAapt(file, null);
            for (Symbol symbol : st.getSymbols().values()) {
                String symbolValue = symbol.getValue();
                if (symbol.getResourceType() == ResourceType.STYLEABLE) {
                    if (symbolValue.trim().startsWith("{")) {
                        // Only add the styleable parent, styleable children are not yet supported.
                        mModel.addResource(symbol.getResourceType(), symbol.getName(), null);
                    }
                } else {
                    mModel.addResource(symbol.getResourceType(), symbol.getName(), symbolValue);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ResourceType extractResourceType(String entryName) {
        String rClassName = entryName.substring(entryName.lastIndexOf('/') + 1);
        if (!rClassName.startsWith("R$")) {
            return null;
        }
        String resourceTypeName =
                rClassName.substring("R$".length(), rClassName.length() - DOT_CLASS.length());
        return ResourceType.fromClassName(resourceTypeName);
    }

    private void parseResourceRJar(File jarFile) throws IOException {
        try (ZipFile zFile = new ZipFile(jarFile)) {
            Enumeration<? extends ZipEntry> entries = zFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(DOT_CLASS)) {
                    ResourceType resourceType = extractResourceType(entryName);
                    if (resourceType == null) {
                        continue;
                    }
                    String owner = entryName.substring(0, entryName.length() - DOT_CLASS.length());
                    byte[] classData = ByteStreams.toByteArray(zFile.getInputStream(entry));
                    parseResourceCompiledClass(classData, owner, resourceType);
                }
            }
        }
    }

    private void parseResourceCompiledClass(
            byte[] classData, String owner, ResourceType resourceType) {
        ClassReader classReader = new ClassReader(classData);
        ClassVisitor fieldVisitor =
                new ClassVisitor(ASM_VERSION) {
                    @Override
                    public FieldVisitor visitField(
                            int access, String name, String desc, String signature, Object value) {
                        // We only want integer or integer array (styleable) fields
                        if (desc.equals("I") || desc.equals("[I")) {
                            String resourceValue =
                                    resourceType == ResourceType.STYLEABLE
                                            ? null
                                            : value.toString();
                            mModel.addResource(resourceType, name, resourceValue);
                            addOwner(owner, resourceType);
                        }
                        return null;
                    }
                };
        classReader.accept(fieldVisitor, SKIP_DEBUG | SKIP_FRAMES);
    }

    private void addOwner(@NonNull String owner, @NonNull ResourceType type) {
        Pair<ResourceType, Map<String, String>> pair = mResourceObfuscation.get(owner);
        if (pair == null) {
            Map<String, String> nameMap = Maps.newHashMap();
            pair = Pair.of(type, nameMap);
        }
        mResourceObfuscation.put(owner, pair);
    }

    // TODO: Use PSI here
    private void parseResourceSourceClass(File file) throws IOException {
        String s = Files.toString(file, UTF_8);
        // Simple parser which handles only aapt's special R output
        String pkg = null;
        int index = s.indexOf("package ");
        if (index != -1) {
            int end = s.indexOf(';', index);
            pkg = s.substring(index + "package ".length(), end).trim().replace('.', '/');
        }
        index = 0;
        int length = s.length();
        String classDeclaration = "public static final class ";
        while (true) {
            index = s.indexOf(classDeclaration, index);
            if (index == -1) {
                break;
            }
            int start = index + classDeclaration.length();
            int end = s.indexOf(' ', start);
            if (end == -1) {
                break;
            }
            String typeName = s.substring(start, end);
            ResourceType type = ResourceType.fromClassName(typeName);
            if (type == null) {
                break;
            }

            if (pkg != null) {
                addOwner(pkg + "/R$" + type.getName(), type);
            }

            index = end;

            // Find next declaration
            for (; index < length - 1; index++) {
                char c = s.charAt(index);
                if (Character.isWhitespace(c)) {
                    //noinspection UnnecessaryContinue
                    continue;
                }

                if (c == '/') {
                    char next = s.charAt(index + 1);
                    if (next == '*') {
                        // Scan forward to comment end
                        end = index + 2;
                        while (end < length -2) {
                            c = s.charAt(end);
                            if (c == '*' && s.charAt(end + 1) == '/') {
                                end++;
                                break;
                            } else {
                                end++;
                            }
                        }
                        index = end;
                    } else if (next == '/') {
                        // Scan forward to next newline
                        assert false : s.substring(index - 1, index + 50); // we don't put line comments in R files
                    } else {
                        assert false : s.substring(index - 1, index + 50); // unexpected division
                    }
                } else if (c == 'p' && s.startsWith("public ", index)) {
                    if (type == ResourceType.STYLEABLE) {
                        start = s.indexOf(" int", index);
                        if (s.startsWith(" int[] ", start)) {
                            start += " int[] ".length();
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String styleable = s.substring(start, end).trim();
                            mModel.addResource(ResourceType.STYLEABLE, styleable, null);
                            // TODO: Read in all the action bar ints!
                            // For now, we're simply treating all R.attr fields as used
                            index = s.indexOf(';', index);
                            if (index == -1) {
                                break;
                            }
                        } else if (s.startsWith(" int ", start)) {
                            // Read these fields in and correlate with the attr R's. Actually
                            // we don't need this for anything; the local attributes are
                            // found by the R attr thing. I just need to record the class
                            // (style).
                            // public static final int ActionBar_background = 10;
                            // ignore - jump to end
                            index = s.indexOf(';', index);
                            if (index == -1) {
                                break;
                            }
                            // For now, we're simply treating all R.attr fields as used
                        }
                    } else {
                        start = s.indexOf(" int ", index);
                        if (start != -1) {
                            start += " int ".length();
                            // e.g. abc_fade_in=0x7f040000;
                            end = s.indexOf('=', start);
                            assert end != -1;
                            String name = s.substring(start, end).trim();
                            start = end + 1;
                            end = s.indexOf(';', start);
                            assert end != -1;
                            String value = s.substring(start, end).trim();
                            mModel.addResource(type, name, value);
                        }
                    }
                } else if (c == '}') {
                    // Done with resource class
                    break;
                }
            }
        }
    }

    @Override
    public int getUnusedResourceCount() {
        return mUnused.size();
    }

    @VisibleForTesting
    ResourceUsageModel getModel() {
        return mModel;
    }

    /**
     * Class visitor responsible for looking for resource references in code.
     * It looks for R.type.name references (as well as inlined constants for these,
     * in the case of non-library code), as well as looking both for Resources#getIdentifier
     * calls and recording string literals, used to handle dynamic lookup of resources.
     */
    private class UsageVisitor extends ClassVisitor {
        private final File mJarFile;
        private final String mCurrentClass;

        public UsageVisitor(File jarFile, String name) {
            super(ASM_VERSION);
            mJarFile = jarFile;
            mCurrentClass = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name,
                String desc, String signature, String[] exceptions) {
            return new MethodVisitor(api) {
                @Override
                public void visitLdcInsn(Object cst) {
                    handleCodeConstant(cst, "ldc");
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    if (opcode == Opcodes.GETSTATIC) {
                        Resource resource = getResourceFromCode(owner, name);
                        if (resource != null) {
                            ResourceUsageModel.markReachable(resource);
                        }
                    }
                }

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String name, String desc, boolean itf) {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    referencedMethodInvocation(owner, name, desc, mCurrentClass);
                }

                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return new AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new AnnotationUsageVisitor();
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        int parameter, String desc, boolean visible) {
                    return new AnnotationUsageVisitor();
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            return new AnnotationUsageVisitor();
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature,
                Object value) {
            handleCodeConstant(value, "field");
            return new FieldVisitor(api) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    return new AnnotationUsageVisitor();
                }
            };
        }

        private class AnnotationUsageVisitor extends AnnotationVisitor {
            public AnnotationUsageVisitor() {
                super(ASM_VERSION);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String name, String desc) {
                return new AnnotationUsageVisitor();
            }

            @Override
            public AnnotationVisitor visitArray(String name) {
                return new AnnotationUsageVisitor();
            }

            @Override
            public void visit(String name, Object value) {
                handleCodeConstant(value, "annotation");
                super.visit(name, value);
            }
        }

        /** Invoked when an ASM visitor encounters a constant: record corresponding reference */
        private void handleCodeConstant(@Nullable Object cst, @NonNull String context) {
            if (cst instanceof Integer) {
                Integer value = (Integer) cst;
                referencedInt(context, value, mJarFile, mCurrentClass);
            } else if (cst instanceof Long) {
                Long value = (Long) cst;
                referencedInt(context, value.intValue(), mJarFile, mCurrentClass);
            } else if (cst instanceof int[]) {
                int[] values = (int[]) cst;
                for (int value : values) {
                    referencedInt(context, value, mJarFile, mCurrentClass);
                }
            } else if (cst instanceof String) {
                String string = (String) cst;
                referencedString(string);
            }
        }
    }

    private void referencedInt(@NonNull String context, int value, File file, String currentClass) {
        Resource resource = mModel.getResource(value);
        if (ResourceUsageModel.markReachable(resource) && mDebug) {
            assert mDebugPrinter != null : "mDebug is true, but mDebugPrinter is null.";
            mDebugPrinter.println(
                    "Marking "
                            + resource
                            + " reachable: referenced from "
                            + context
                            + " in "
                            + file
                            + ":"
                            + currentClass);
        }
    }

    private void referencedMethodInvocation(
            @NonNull String owner,
            @NonNull String name,
            @NonNull String desc,
            @NonNull String currentClass) {
        if (owner.equals("android/content/res/Resources")
                && name.equals("getIdentifier")
                && desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I")) {

            if (currentClass.equals(mResourcesWrapper)
                    || currentClass.equals(mSuggestionsAdapter)) {
                // "benign" usages: don't trigger reflection mode just because
                // the user has included appcompat
                return;
            }

            mFoundGetIdentifier = true;
            // TODO: Check previous instruction and see if we can find a literal
            // String; if so, we can more accurately dispatch the resource here
            // rather than having to check the whole string pool!
        }
        if (owner.equals("android/webkit/WebView") && name.startsWith("load")) {
            mFoundWebContent = true;
        }
    }

    private final ResourceShrinkerUsageModel mModel =
            new ResourceShrinkerUsageModel();

    private class ResourceShrinkerUsageModel extends ResourceUsageModel {
        public File file;

        /**
         * Whether we should ignore tools attribute resource references.
         * <p>
         * For example, for resource shrinking we want to ignore tools attributes,
         * whereas for resource refactoring on the source code we do not.
         *
         * @return whether tools attributes should be ignored
         */
        @Override
        protected boolean ignoreToolsAttributes() {
            return true;
        }

        @Override
        protected void onRootResourcesFound(@NonNull List<Resource> roots) {
            if (mDebugPrinter != null) {
                mDebugPrinter.println("\nThe root reachable resources are:\n" +
                        Joiner.on(",\n   ").join(roots));
            }
        }

        @Override
        protected Resource declareResource(ResourceType type, String name, Node node) {
            Resource resource = super.declareResource(type, name, node);
            resource.addLocation(file);
            return resource;
        }

        @Override
        protected void referencedString(@NonNull String string) {
            ResourceUsageAnalyzer.this.referencedString(string);
            mFoundWebContent = true;
        }
    }
}
