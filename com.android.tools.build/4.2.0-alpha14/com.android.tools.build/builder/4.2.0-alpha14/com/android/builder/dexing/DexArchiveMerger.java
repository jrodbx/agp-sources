package com.android.builder.dexing;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.dx.command.dexer.DxContext;
import com.android.ide.common.blame.MessageReceiver;
import com.android.tools.r8.CompilationMode;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * A dex archive merger that can merge dex files from multiple dex archives into one or more dex
 * files.
 */
public interface DexArchiveMerger {

    /** Creates an instance of dex archive merger that is using dx to merge dex files. */
    @NonNull
    static DexArchiveMerger createDxDexMerger(
            @NonNull DxContext dxContext, @NonNull ForkJoinPool executor, boolean isDebuggable) {
        return new DxDexArchiveMerger(dxContext, executor, isDebuggable);
    }

    /** Creates an instance of dex archive merger that is using d8 to merge dex files. */
    @NonNull
    static DexArchiveMerger createD8DexMerger(
            @NonNull MessageReceiver messageReceiver,
            int minSdkVersion,
            boolean isDebuggable,
            @Nullable ForkJoinPool forkJoinPool) {
        return new D8DexArchiveMerger(
                messageReceiver,
                minSdkVersion,
                isDebuggable ? CompilationMode.DEBUG : CompilationMode.RELEASE,
                forkJoinPool);
    }

    /**
     * Merges the specified dex archive entries into one or more dex files under the specified
     * directory.
     *
     * <p>Dexing type specifies how files will be merged:
     *
     * <ul>
     *   <li>if it is {@link DexingType#MONO_DEX}, a single dex file is written, named classes.dex
     *   <li>if it is {@link DexingType#LEGACY_MULTIDEX}, there can be more than 1 dex files. Files
     *       are named classes.dex, classes2.dex, classes3.dex etc. In this mode, path to a file
     *       containing the list of classes to be placed in the main dex file must be specified.
     *   <li>if it is {@link DexingType#NATIVE_MULTIDEX}, there can be 1 or more dex files.
     * </ul>
     *
     * @param dexArchiveEntries the dex archive entries to merge
     * @param dexRootsForDx the dex roots (directories or jars) containing the dex archive entries
     *     to merge. It is currently used only by the DX tool as we do not support merging a subset
     *     of dex archive entries inside the dex roots when DX is used.
     * @param outputDir directory where merged dex file(s) will be written, must exist
     * @param mainDexClasses file containing list of classes to be merged in the main dex file. It
     *     is {@code null} for native and mono dex, and must be non-null for legacy dex.
     * @param dexingType specifies how to merge dex files
     */
    void mergeDexArchives(
            @NonNull List<DexArchiveEntry> dexArchiveEntries,
            @NonNull List<Path> dexRootsForDx,
            @NonNull Path outputDir,
            @Nullable Path mainDexClasses,
            @NonNull DexingType dexingType)
            throws DexArchiveMergerException;
}
