/*
 * Copyright (C) 2017 The Android Open Source Project
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

/**
 *
 *
 * <h1>Symbols</h1>
 *
 * The {@code Symbols} package contains classes used for parsing and processing android resources
 * and generating the R.java files.
 *
 * <p>The {@link com.android.ide.common.symbols.Symbol} class is used to represent a single android
 * resource by a resource type, a name, a java type and a value. A set of Symbols with unique
 * type/name pairs can be represented by a {@link com.android.ide.common.symbols.SymbolTable}.
 *
 * <h1>Resource parsers</h1>
 *
 * <p>Various parsers in this package were introduced to enable resource parsing without the use of
 * AAPT for libraries. They provide means to scan through the resource directory, parse XML files in
 * search for declared resources and find non-XML files, such as drawables.
 *
 * <p>The parsers' flow is as follows:
 *
 * <p>Library resources are passed to a {@link
 * com.android.ide.common.symbols.ResourceDirectoryParser}. There the parser goes through each of
 * the directories and takes different paths depending on the directories' names and their files'
 * types:
 *
 * <ol>
 *   <li>If we are in a {@code values} directory (directory name starts with a "values" prefix and
 *       is followed by optional qualifiers, like "-v21" or "-w820dp"), all files inside are XML
 *       files with declared values inside of them (for example {@code values/strings.xml}). Parse
 *       each file with a {@link com.android.ide.common.symbols.ResourceValuesXmlParser}.
 *   <li>If we are in a non-values directory, create a Symbol for each file inside the directory,
 *       with the Symbol's name as the filename without the optional extension and the Symbol's type
 *       as the directory's name without extra qualifiers. For example for file {@code
 *       drawable-v21/a.png} we would create a new Symbol with name {@code "a"} and type {@code
 *       "drawable"}.
 *   <li>Additionally, if we are in a non-values directory and are parsing a file that is an XML
 *       file, we will parse the contents of the file in search of inline declared values. For
 *       example, a file {@code layout/activity_main.xml} could contain an inline declaration of an
 *       {@code id} such as {code android:id="@+id/activity_main"}. From such a line a new Symbol
 *       should be created with a name {@code "activity_main"} and type {@code "id"}. Such inline
 *       declarations are identified by the "@+" prefix and follow a "@+type/name" pattern. This is
 *       done by calling the {@code parse} method in {@link
 *       com.android.ide.common.symbols.ResourceExtraXmlParser}
 * </ol>
 *
 * <p>The {@link com.android.ide.common.symbols.ResourceDirectoryParser} collects all {@code
 * Symbols} from aforementioned cases and collects them in a {@code SymbolTable} which is later used
 * to create the R.txt and R.java files for the library as well as R.java files for all the
 * libraries it depends on.
 *
 * <p>It is worth mentioning that with this new flow, the new pipeline needs to also create minify
 * rules in the {@code aapt_rules.txt} file since we are not calling AAPT anymore. It is done by
 * parsing the library's android manifest, creating keep rules and writing the file in method {@link
 * com.android.ide.common.symbols.SymbolUtils#generateMinifyKeepRules}.
 *
 * <p>Naming conventions:
 *
 * <ol>
 *   <li>Resource names declared in XML files inside the {@code values} directories are allowed to
 *       contain lower- and upper-case letters, numbers and the underscore character. Dots and
 *       colons are allowed to accommodate AAPT's old behaviour, but are deprecated and the support
 *       for them might end in the near future.
 *   <li>File names are allowed to contain lower- and upper-case letters, numbers and the underscore
 *       character. A dot is only allowed to separate the name from the extension (for example
 *       {@code "a.png"}), the usage of two dots is only allowed for 9-patch image extension (for
 *       example {@code "a.9.png"}). It is also worth noting that some resources can be declared
 *       with a prefix like {@code aapt:} or {@code android:}. Following aapt's original behaviour,
 *       we strip the type names from those prefixes. This behaviour is deprecated and might be the
 *       support for it might end in the near future.
 * </ol>
 *
 * <p>Example:
 *
 * <p>Assume in the resources directory we have the following sub-directories and files:
 *
 * <pre>
 * +---.drawable
 * |   +---.a.png
 * +---.layout
 * |   +---.activity_main.xml
 * +---.values
 * |   +---.colors.xml
 * </pre>
 *
 * <p>Contents of {@code activity_main,xml} include a {@code FloatingActionButton}:
 *
 * <pre>
 *     (...)
 *     <android.support.design.widget.FloatingActionButton
 *         android:id="@+id/fab"
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         android:layout_gravity="bottom|end"
 *         android:layout_margin="@dimen/fab_margin"
 *         app:srcCompat="@android:drawable/ic_dialog_email" />
 *     (...)
 * </pre>
 *
 * <p>And {@code colors.xml} contains:
 *
 * <pre>
 * <resources  xmlns:android="http://schemas.android.com/apk/res/android"
 *     xmlns:aapt="http://schemas.android.com/aapt">
 *     <color name="colorPrimary">#3F51B5</color>
 *     <color name="colorPrimaryDark">#303F9F</color>
 *     <color name="colorAccent">#FF4081</color>
 * </resources>
 * </pre>
 *
 * <p>Then the parsers would create a following SymbolTable:
 *
 * <table>
 *     <caption>Symbol table</caption>
 *     <tr><th>Java type  </th><th>Resource type  </th><th>Resource name    </th><th>ID</th></tr>
 *     <tr><td>int        </td><td>drawable       </td><td>a                </td><td>1 </td></tr>
 *     <tr><td>int        </td><td>layout         </td><td>activity_main    </td><td>2 </td></tr>
 *     <tr><td>int        </td><td>id             </td><td>fab              </td><td>3 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorPrimary     </td><td>4 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorPrimaryDark </td><td>5 </td></tr>
 *     <tr><td>int        </td><td>color          </td><td>colorAccent      </td><td>6 </td></tr>
 * </table>
 *
 * <p>See {@code ResourceValuesXmlParserTest} and {@code ResourceDirectoryParserTest} for more
 * examples of the parsers' behaviour.
 *
 * <h1>Partial R files generation</h1>
 *
 * <p>Historically, processing resources using AAPT was a one-step process: the android gradle
 * plugin merged the values files, handled overlays and placed all merged files into an intermediate
 * directory, which was later passed to AAPT 'package' command.
 *
 * <p>Now, AAPT2 is used by default. Instead of one call to 'package', a two-step process is used:
 * first during merging the resources are compiled by AAPT2 using the 'compile' command (this is
 * where PNG crunching and pseudo-localization take place) and then all compiled resources are
 * passed to AAPT2 for the 'link' command to create the AP_ file (APK file with the manifest and the
 * resources only). R.txt and R.java files are generated at this point.
 *
 * <p>In oder to generate R.java earlier in the build process (and, in turn, to faster start java
 * compilation tasks) the concept of partial R files was introduced. The idea is to generate a
 * partial R.txt file for each resource during compilation and then merge them to generate the R.txt
 * and R.java files before we reach the linking stage.
 *
 * <p>There will be two ways of generating the partial R.txt files:
 *
 * <ol>
 *   <li>Non-XML files - they do not define any resources other than themselves, so we can add them
 *       to the non-XML R.txt file early and send off to compile in parallel (for example: PNG
 *       files).
 *   <li>XML files - they can define other resources inside of them, so we need to compile them with
 *       AAPT2 and receive the partial R.txt using the '--output-text-symbols' flag.
 * </ol>
 *
 * <p>The line format of the partial R.txt files will be: {@code <access qualifier> <java type>
 * <symbol type> <resource name>}.
 *
 * <p>For example: {@code default int[] styleable MyStyleable}.
 *
 * <p>Access qualifiers can be one of: {@code default}, {@code public}, {@code private}.
 *
 * <p>For more information about java types and symbol types look at {@link
 * com.android.ide.common.symbols.ResourceValuesXmlParser}.
 *
 * <p>Resources defined with the {@code public} keyword will have the access qualifier {@code
 * public} in the partial R.txt, resources defined using {@code java-symbol} will have the {@code
 * private} access qualifier, and all other resources will have the {@code default} qualifier.
 *
 * <p>If a string is declared in {@code values/strings.xml} as {@code <string name="s1">v1</string>}
 * then the partial R.txt will contain {@code default int string s1}. If the same string is also
 * marked as public in the {@code values/public.xml} as: {@code <public type="string" name="foo"
 * id="0x7f000001"/>} then the partial R.txt for that file will contain: {@code public int string
 * foo}. If both are in the same file, then the partial R.txt will contain {@code public int string
 * foo} as the resource will be already marked as public.
 *
 * <p>The resource IDs will be skipped as this is only for compilation, proper IDs will be generated
 * at linking phase.
 *
 * <p>More information about resource visibility can be found in the javadoc for {@link
 * com.android.resources.ResourceVisibility}.
 *
 * <h1>Partial R files merging</h1>
 *
 * <p>After the partial R files generation phase, we should have a set of partial R files for each
 * source-set.
 *
 * <p>The merging strategies will depend on the type of the original resource:
 *
 * <ol>
 *   <li>Non-Xml files - it should be fairly simple to merge non-xml-R.txt files, a simple sum of
 *       all the resources should be sufficient since none of those resources can define other
 *       resources inside of them.
 *   <li>Non-values XML files - each of those files is a resource itself and can define ID resources
 *       inside itself. Therefore if, for example, a layout is overridden then the IDs defined in
 *       the original file need to be replaced by the ones from the new layout. Note: we can do the
 *       replacement at the original file level without looking at the file contents (if two files
 *       from different source-sets have the same name, choose the one from the 'overriding'
 *       source-set). Then the contents of those partial R files can be merged by a simple sum like
 *       in non-XML files.
 *   <li>Values XML files - are a bit tricky, since the strategy is a mix of the two previous ones.
 *       For non-styleable resources the strategy is to simply sum all of the inputs, but for
 *       styleable resources (from declare-styleables and its' children) we need to use the 'newest'
 *       strategy. When a declare-styleable is overridden then all of its children are overridden by
 *       the new children as well. Also, if a resource is marked both as {@code default} and {@code
 *       private} (or {@code public}) then it should be de-duplicated and only exist as {@code
 *       private} (or {@code public}).
 * </ol>
 *
 * <p>To make all of these cases work, the algorithm is as follows:
 *
 * <ol>
 *   <li>Start with the highest-priority source-set (in this case the opposite of the base
 *       source-set) and start adding symbols from the partial files into the Symbol Table. After
 *       we're done with the current source-set, move on to an older one until we have nothing else
 *       left to do.
 *   <li>Before adding resources from a non-values XML file, add the filename to a set of visited
 *       file names.
 *       <ol>
 *         <li>If the filename wasn't present in the set yet, add all of the resources to the symbol
 *             table.
 *         <li>If the filename was already present in the set, then the resource is overridden and
 *             we should ignore its contents.
 *       </ol>
 *   <li>If a resource is already present in the table when we want to add it, compare the access
 *       modifiers of the two symbols (declare-styleable's children should be nested under the
 *       styleable's {@link com.android.ide.common.symbols.Symbol}).
 *       <ol>
 *         <li>If they are the same, ignore and move on.
 *         <li>If one of them is {@code default} and the other one is {@code private} (or {@code
 *             public}), keep the non-{@code default} one.
 *         <li>If one is {@code public} and the other {@code private}, abort with error. A resource
 *             cannot be both private and public.
 *       </ol>
 *   <li>All other resource validation will be left to AAPT2 during linking stage.
 *   <li>At the end we can grab all {@code public} resources from the table to generate the {@code
 *       public.txt} file, {@code private} resources for the private.txt and all of the resources
 *       regardless of the access modifier to generate the R.txt.
 * </ol>
 */
package com.android.ide.common.symbols;
