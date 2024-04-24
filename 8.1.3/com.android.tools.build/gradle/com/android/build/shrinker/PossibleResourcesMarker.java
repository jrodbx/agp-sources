/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.build.shrinker;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.Character.isDigit;

import com.android.annotations.NonNull;
import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Streams;
import com.google.common.primitives.Ints;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/** Marks resources which are possibly referenced by string constants as reachable. */
public class PossibleResourcesMarker {

    // Copied from StringFormatDetector
    // See java.util.Formatter docs
    public static final Pattern FORMAT =
            Pattern.compile(
                    // Generic format:
                    //   %[argument_index$][flags][width][.precision]conversion
                    //
                    "%"
                            +
                            // Argument Index
                            "(\\d+\\$)?"
                            +
                            // Flags
                            "([-+#, 0(<]*)?"
                            +
                            // Width
                            "(\\d+)?"
                            +
                            // Precision
                            "(\\.\\d+)?"
                            +
                            // Conversion. These are all a single character, except date/time
                            // conversions
                            // which take a prefix of t/T:
                            "([tT])?"
                            +
                            // The current set of conversion characters are
                            // b,h,s,c,d,o,x,e,f,g,a,t (as well as all those as upper-case
                            // characters), plus
                            // n for newlines and % as a literal %. And then there are all the
                            // time/date
                            // characters: HIKLm etc. Just match on all characters here since there
                            // should
                            // be at least one.
                            "([a-zA-Z%])");

    static final String NO_MATCH = "-nomatch-";

    private final ShrinkerDebugReporter debugReporter;
    private final ResourceStore resourceStore;
    private final Set<String> strings;
    private final boolean foundWebContent;

    public PossibleResourcesMarker(ShrinkerDebugReporter debugReporter,
                                   ResourceStore resourceStore,
                                   Set<String> strings,
                                   boolean foundWebContent) {
        this.debugReporter = debugReporter;
        this.resourceStore = resourceStore;
        this.strings = strings;
        this.foundWebContent = foundWebContent;
    }

    public void markPossibleResourcesReachable() {
        Set<String> names =
                resourceStore.getResources().stream()
                        .map(resource -> resource.name)
                        .collect(toImmutableSet());

        int shortest = names.stream().mapToInt(String::length).min().orElse(Integer.MAX_VALUE);

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
        //  (4) If foundWebContent is true, look for android_res/ URL strings as well
        strings.stream()
                .filter(string -> string.length() >= shortest)
                .flatMap(
                        string -> {
                            int n = string.length();
                            boolean justName = true;
                            boolean formatting = false;
                            boolean haveSlash = false;
                            for (int i = 0; i < n; i++) {
                                char c = string.charAt(i);
                                haveSlash |= c == '/';
                                formatting |= c == '%';
                                justName =
                                        justName && !(c == '.' || c == ':' || c == '%' || c == '/');
                            }

                            Stream<Resource> reachable = Streams.concat(
                                    foundWebContent
                                            ? possibleWebResources(names, string)
                                            : Stream.empty(),
                                    justName ? possiblePrefixMatch(string) : Stream.empty(),
                                    formatting && !haveSlash
                                            ? possibleFormatting(string)
                                            : Stream.empty(),
                                    haveSlash
                                            ? possibleTypedResource(names, string)
                                            : Stream.empty(),
                                    possibleIntResource(string));

                            return reachable
                                    .peek(resource -> debugReporter.debug(() -> "Marking "
                                    + resource + " used because it matches string pool constant "
                                    + string));
                        })
                .forEach(ResourceUsageModel::markReachable);
    }

    private Stream<Resource> possibleWebResources(
            Set<String> names, String string) {
        // Look for android_res/ URL strings.
        List<Resource> resources = resourceStore.getResourcesFromWebUrl(string);
        if (!resources.isEmpty()) {
            return resources.stream();
        }

        int start = Math.max(string.lastIndexOf('/'), 0);
        int dot = string.indexOf('.', start);
        String name = string.substring(start, dot != -1 ? dot : string.length());

        if (names.contains(name)) {
            return resourceStore.getResourceMaps().stream()
                    .filter(map -> map.containsKey(name))
                    .flatMap(map -> map.get(name).stream());
        }
        return Stream.empty();
    }

    private Stream<Resource> possiblePrefixMatch(String string) {
        // Check for a simple prefix match, e.g. as in
        // getResources().getIdentifier("ic_video_codec_" + codecName, "drawable", ...)
        return resourceStore.getResources().stream()
                .filter(resource -> resource.name.startsWith(string));
    }

    private Stream<Resource> possibleFormatting(String string) {
        // Possibly a formatting string, e.g.
        //   String name = String.format("my_prefix_%1d", index);
        //   int res = getContext().getResources().getIdentifier(name, "drawable", ...)
        try {
            Pattern pattern = Pattern.compile(convertFormatStringToRegexp(string));
            return resourceStore.getResources().stream()
                    .filter(resource -> pattern.matcher(resource.name).matches());
        } catch (PatternSyntaxException ignored) {
            return Stream.empty();
        }
    }

    private Stream<Resource> possibleTypedResource(
            Set<String> names, String string) {
        // Try to pick out the resource name pieces; if we can find the
        // resource type unambiguously; if not, just match on names
        int slash = string.indexOf('/');
        String name = string.substring(slash + 1);
        if (name.isEmpty() || !names.contains(name)) {
            return Stream.empty();
        }
        // See if have a known specific resource type
        if (slash > 0) {
            int colon = string.indexOf(':');
            String typeName = string.substring(colon + 1, slash);
            ResourceType type = ResourceType.fromClassName(typeName);
            return type != null
                    ? resourceStore.getResources(type, name).stream()
                    : Stream.empty();
        }
        return resourceStore.getResourceMaps().stream()
                .filter(map -> map.containsKey(name))
                .flatMap(map -> map.get(name).stream());
    }

    private Stream<Resource> possibleIntResource(String string) {
        // Just a number? There are cases where it calls getIdentifier by
        // a String number; see for example SuggestionsAdapter in the support
        // library which reports supporting a string like "2130837524" and
        // "android.resource://com.android.alarmclock/2130837524".
        String withoutSlash = string.substring(string.lastIndexOf('/') + 1);
        if (withoutSlash.isEmpty() || !isDigit(withoutSlash.charAt(0))) {
            return Stream.empty();
        }
        Integer id = Ints.tryParse(withoutSlash);
        Resource resource = null;
        if (id != null) {
            resource = resourceStore.getResource(id);
        }
        return resource != null ? Stream.of(resource) : Stream.empty();
    }

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
                        pattern = "%";
                        break;
                    case 'n':
                        pattern = "\n";
                        break;
                    case 'c':
                    case 'C':
                        pattern = ".";
                        break;
                    case 'x':
                    case 'X':
                        pattern = "\\p{XDigit}+";
                        break;
                    case 'd':
                    case 'o':
                        pattern = "\\p{Digit}+";
                        break;
                    case 'b':
                        pattern = "(true|false)";
                        break;
                    case 'B':
                        pattern = "(TRUE|FALSE)";
                        break;
                    case 'h':
                    case 'H':
                        pattern = "(null|\\p{XDigit}+)";
                        break;
                    case 'f':
                        pattern = "-?[\\p{XDigit},.]+";
                        break;
                    case 'e':
                        pattern = "-?\\p{Digit}+[,.]\\p{Digit}+e\\+?\\p{Digit}+";
                        break;
                    case 'E':
                        pattern = "-?\\p{Digit}+[,.]\\p{Digit}+E\\+?\\p{Digit}+";
                        break;
                    case 'a':
                        pattern = "0x[\\p{XDigit},.+p]+";
                        break;
                    case 'A':
                        pattern = "0X[\\p{XDigit},.+P]+";
                        break;
                    case 'g':
                    case 'G':
                        pattern = "-?[\\p{XDigit},.+eE]+";
                        break;
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
     * Appends the characters in the range [from,to> from formatString as escaped regexp characters
     * into the given string builder. Returns true if there were any letters in the appended text.
     */
    private static boolean appendEscapedPattern(
            @NonNull String formatString, @NonNull StringBuilder regexp, int from, int to) {
        regexp.append(Pattern.quote(formatString.substring(from, to)));

        for (int i = from; i < to; i++) {
            if (Character.isLetter(formatString.charAt(i))) {
                return true;
            }
        }

        return false;
    }
}
