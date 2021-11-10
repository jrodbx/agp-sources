/**
 * The {@code merge} package provides the implementation of an incremental merger. The merger reads
 * files from one or more input trees and produces one output tree. The merger produces perfect
 * incremental outputs in the case of incremental input changes. It supports inputs from multiple
 * sources (file system directories and zip files) and supports outputs into multiple forms (file
 * system directories and zip files).
 *
 * <p>The merger is split into three main concepts discussed in the sections below: the merger,
 * inputs and output.
 *
 * <h3>Main Concepts and Merge Invariant</h3>
 *
 * <p>A fundamental concept in merging is a <i>relative tree</i>. A relative tree is a set of {@link
 * com.android.builder.files.RelativeFile} with the restriction that each relative file is uniquely
 * identified by the OS-independent path of the relative file (see {@link
 * com.android.builder.files.RelativeFile#getRelativePath()}.
 *
 * <p>Conceptually, a relative tree can be seen as a file system tree. However, in practice, files
 * can come from multiple directories and zips, as long as there are no clashes in OS-independent
 * paths. So, for example, directories {@code x} and {@code y} in the example below structures could
 * be used to build a relative tree:
 *
 * <pre>
 * +---.home
 * +---.user
 * |   +---.x
 * |   |   +---.foo
 * |   |   +---.bar
 * |   |   +---.subdir
 * |   |   |   +---.file1
 * |   +---.y
 * |   |   +---.extra
 * |   |   +---.subdir
 * |   |   |   +---.file2
 * |   |   |   +---.file3
 * |   |   +---.additional
 * </pre>
 *
 * <p>The relative tree would contain files with the following relative paths:
 *
 * <pre>
 * foo           -> x /foo
 * bar           -> x /bar
 * subdir/file1  -> x /subdir/file1
 * extra         -> y /extra
 * subdir/file2  -> y /subdir/file2
 * subdir/file3  -> y /subdir/file3
 * additional    -> y /additional
 * </pre>
 *
 * <p>The purpose of the incremental merger is to maintain the <i>merge invariant</i>. The merge
 * invariant is the relation between an ordered list of input relative trees and a single output
 * relative tree. The invariant is expressed as:
 *
 * <ul>
 *   <li>The set of OS-independent paths in the output relative tree is the union of the set of
 *       OS-independent paths in the input relative trees.
 *   <li>The contents of each file in the output relative tree is computed by a function based on
 *       the list of files in the input relative tree that have the same OS-independent path.
 * </ul>
 *
 * <p>Now consider the following relative tree obtained from zip file {@code z.zip}:
 *
 * <pre>
 * foo           -> z.zip /foo
 * bar1          -> z.zip /bar1
 * bar2          -> z.zip /bar2
 * sub/sub/deep  -> z.zip /sub/sub/deep
 * subdir/file1  -> z.zip /subdir/file1
 * </pre>
 *
 * <p>The only output relative tree that verifies the merge invariant using the previous two input
 * files in order is:
 *
 * <pre>
 * foo           -> f(x /foo, z.zip /foo)
 * bar           -> f(x /bar)
 * bar1          -> f(z.zip /bar1)
 * bar2          -> f(z.zip /bar2)
 * extra         -> f(y /extra)
 * additional    -> f(y /additional)
 * subdir/file1  -> f(x /subdir/file1, z.zip /subdir/file1)
 * subdir/file2  -> f(y /subdir/file2)
 * subdir/file3  -> f(y /subdir/file3)
 * sub/sub/deep  -> f(z.zip /sub/sub/deep)
 * </pre>
 *
 * <p>Where {@code f()} is the computation function for the merge function.
 *
 * <h3>Incremental Merging and State</h3>
 *
 * <p>The description in the previous section shows how the merge works as a relation between inputs
 * and outputs, but does not describe how the output is actually computed.
 *
 * <p>A key idea behind the incremental merger is that it is perfectly incremental, that is, if an
 * input relative tree changes, the output relative tree changes only the minimum necessary.
 * Additionally, no useless operations such as rewriting files that don't change are done.
 *
 * <p>To achieve these goals, the incremental merger only does incremental merges. A full merge is
 * an incremental merge from scratch. The incremental merger maintains knowledge of which files
 * exist in the output and directs the output to change with respect to changed inputs. This has
 * some important implications:
 *
 * <ul>
 *   <li>Incremental merge will never clean the output.
 *   <li>The inputs must be able to tell which changes to relative files have been made.
 *   <li>Intermediate state must be saved between merges.
 * </ul>
 *
 * <p>Also, because the merge output function may be dependent on the order of the inputs, each
 * input relative tree is named so, if the order of the inputs changes, the output may need to
 * change even if no files were actually changed.
 *
 * <h3>API</h3>
 *
 * The merge algorithm can be invoked in {@link
 * com.android.builder.merge.IncrementalFileMerger#merge( com.google.common.collect.ImmutableList,
 * com.android.builder.merge.IncrementalFileMergerOutput,
 * com.android.builder.merge.IncrementalFileMergerState)}. This algorithm requires the previous
 * state of the merge. In a full merge (no previous state) an empty state can be created (see {@link
 * com.android.builder.merge.IncrementalFileMergerState}.
 *
 * <p>To invoke the merger, it is necessary to provide the set of inputs and an output. See {@link
 * com.android.builder.merge.IncrementalFileMergerInput} for a discussion on inputs. See {@link
 * com.android.builder.merge.IncrementalFileMergerOutput} for a discussion on outputs.
 */
package com.android.builder.merge;
