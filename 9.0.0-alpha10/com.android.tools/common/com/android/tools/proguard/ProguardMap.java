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

package com.android.tools.proguard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

// Class used to deobfuscate classes, fields, and stack frames.
public class ProguardMap {
    private static final String ARRAY_SYMBOL = "[]";

    private static class FrameData {
        public FrameData(String clearMethodName, int lineDelta) {
            this.clearMethodName = clearMethodName;
            this.lineDelta = lineDelta;
        }

        public String clearMethodName;
        public int lineDelta;   // lineDelta = obfuscatedLine - clearLine
    }

    private static class ClassData {
        private String mClearName;

        // Mapping from obfuscated field name to clear field name.
        private Map<String, String> mFields = new HashMap<String, String>();

        // obfuscatedMethodName + clearSignature -> FrameData
        private Map<String, FrameData> mFrames = new HashMap<String, FrameData>();

        // Constructs a ClassData object for a class with the given clear name.
        public ClassData(String clearName) {
            mClearName = clearName;
        }

        // Returns the clear name of the class.
        public String getClearName() {
            return mClearName;
        }

        public void addField(String obfuscatedName, String clearName) {
            mFields.put(obfuscatedName, clearName);
        }

        // Get the clear name for the field in this class with the given
        // obfuscated name. Returns the original obfuscated name if a clear
        // name for the field could not be determined.
        // TODO: Do we need to take into account the type of the field to
        // propery determine the clear name?
        public String getField(String obfuscatedName) {
            String clearField = mFields.get(obfuscatedName);
            return clearField == null ? obfuscatedName : clearField;
        }

        // TODO: Does this properly interpret the meaning of line numbers? Is
        // it possible to have multiple frame entries for the same method
        // name and signature that differ only by line ranges?
        public void addFrame(String obfuscatedMethodName, String clearMethodName,
                String clearSignature, int obfuscatedLine, int clearLine) {
            String key = obfuscatedMethodName + clearSignature;
            mFrames.put(key, new FrameData(clearMethodName, obfuscatedLine - clearLine));
        }

        public Frame getFrame(String clearClassName, String obfuscatedMethodName,
                String clearSignature, String obfuscatedFilename, int obfuscatedLine) {
            String key = obfuscatedMethodName + clearSignature;
            FrameData frame = mFrames.get(key);
            if (frame == null) {
                frame = new FrameData(obfuscatedMethodName, 0);
            }
            return new Frame(
                    frame.clearMethodName,
                    clearSignature,
                    getFileName(clearClassName),
                    obfuscatedLine - frame.lineDelta);
        }
    }

    private Map<String, ClassData> mClassesFromClearName = new HashMap<String, ClassData>();
    private Map<String, ClassData> mClassesFromObfuscatedName = new HashMap<String, ClassData>();

    public static class Frame {
        public Frame(String methodName, String signature, String filename, int line) {
            this.methodName = methodName;
            this.signature = signature;
            this.filename = filename;
            this.line = line;
        }

        public final String methodName;
        public final String signature;
        public final String filename;
        public final int line;
    }

    public ProguardMap() {
    }

    private static void parseException(String msg) throws ParseException {
        throw new ParseException(msg, 0);
    }

    // Read in proguard mapping information from the given file.
    public void readFromFile(File mapFile) throws FileNotFoundException,
                                                  IOException,
                                                  ParseException {
        readFromReader(new FileReader(mapFile));
    }

    // Read in proguard mapping information from the given Reader.
    public void readFromReader(Reader mapReader) throws IOException, ParseException {
        BufferedReader reader = new BufferedReader(mapReader);
        String line = reader.readLine();
        while (line != null) {
            // Line may start with '#' as part of R8 markers, e.g.,
            //   '# compiler: R8'
            // Allow comments or empty lines in class mapping lines.
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line = reader.readLine();
                continue;
            }
            // Class lines are of the form:
            //   'clear.class.name -> obfuscated_class_name:'
            int sep = line.indexOf(" -> ");
            if (sep == -1 || sep + 5 >= line.length()) {
                parseException("Error parsing class line: '" + line + "'");
            }
            String clearClassName = line.substring(0, sep);
            String obfuscatedClassName = line.substring(sep + 4, line.length()-1);

            ClassData classData = new ClassData(clearClassName);
            mClassesFromClearName.put(clearClassName, classData);
            mClassesFromObfuscatedName.put(obfuscatedClassName, classData);

            // After the class line comes zero or more field/method lines of the form:
            //   '    type clearName -> obfuscatedName'
            line = reader.readLine();
            while (line != null) {
                trimmed = line.trim();
                // Allow comments or empty lines in field/method mapping lines.
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    line = reader.readLine();
                    continue;
                }
                // After skipping comments or empty line,
                // make sure this is a field/method mapping line.
                if (!line.startsWith("    ")) {
                    break;
                }
                int ws = trimmed.indexOf(' ');
                sep = trimmed.indexOf(" -> ");
                if (ws == -1 || sep == -1) {
                    parseException("Error parse field/method line: '" + line + "'");
                }

                String type = trimmed.substring(0, ws);
                String clearName = trimmed.substring(ws+1, sep);
                String obfuscatedName = trimmed.substring(sep+4, trimmed.length());

                // If the clearName contains '(', then this is for a method instead of a
                // field.
                if (clearName.indexOf('(') == -1) {
                    classData.addField(obfuscatedName, clearName);
                } else {
                    // For methods, the type is of the form: [#:[#:]]<returnType>
                    int obfuscatedLine = 0;
                    int colon = type.indexOf(':');
                    if (colon != -1) {
                        obfuscatedLine = Integer.parseInt(type.substring(0, colon));
                        type = type.substring(colon+1);
                    }
                    colon = type.indexOf(':');
                    if (colon != -1) {
                        type = type.substring(colon+1);
                    }

                    // For methods, the clearName is of the form: <clearName><sig>[:#[:#]]
                    int op = clearName.indexOf('(');
                    int cp = clearName.indexOf(')');
                    if (op == -1 || cp == -1) {
                        parseException("Error parse method line: '" + line + "'");
                    }

                    String sig = clearName.substring(op, cp+1);

                    int clearLine = obfuscatedLine;
                    colon = clearName.lastIndexOf(':');
                    if (colon != -1) {
                        clearLine = Integer.parseInt(clearName.substring(colon+1));
                        clearName = clearName.substring(0, colon);
                    }

                    colon = clearName.lastIndexOf(':');
                    if (colon != -1) {
                        clearLine = Integer.parseInt(clearName.substring(colon+1));
                        clearName = clearName.substring(0, colon);
                    }

                    clearName = clearName.substring(0, op);

                    String clearSig = fromProguardSignature(sig+type);
                    classData.addFrame(obfuscatedName, clearName, clearSig,
                            obfuscatedLine, clearLine);
                }

                line = reader.readLine();
            }
        }
        reader.close();
    }

    // Returns the deobfuscated version of the given class name. If no
    // deobfuscated version is known, the original string is returned.
    public String getClassName(String obfuscatedClassName) {
        // Class names for arrays may have trailing [] that need to be
        // stripped before doing the lookup.
        String baseName = obfuscatedClassName;
        String arraySuffix = "";
        while (baseName.endsWith(ARRAY_SYMBOL)) {
            arraySuffix += ARRAY_SYMBOL;
            baseName = baseName.substring(0, baseName.length() - ARRAY_SYMBOL.length());
        }

        ClassData classData = mClassesFromObfuscatedName.get(baseName);
        String clearBaseName = classData == null ? baseName : classData.getClearName();
        return clearBaseName + arraySuffix;
    }

    // Returns the deobfuscated version of the given field name for the given
    // (clear) class name. If no deobfuscated version is known, the original
    // string is returned.
    public String getFieldName(String clearClass, String obfuscatedField) {
        ClassData classData = mClassesFromClearName.get(clearClass);
        if (classData == null) {
            return obfuscatedField;
        }
        return classData.getField(obfuscatedField);
    }

    // Returns the deobfuscated frame for the given obfuscated frame and (clear)
    // class name. As much of the frame is deobfuscated as can be.
    public Frame getFrame(String clearClassName, String obfuscatedMethodName,
            String obfuscatedSignature, String obfuscatedFilename, int obfuscatedLine) {
        String clearSignature = getSignature(obfuscatedSignature);
        ClassData classData = mClassesFromClearName.get(clearClassName);
        if (classData == null) {
            return new Frame(obfuscatedMethodName, clearSignature,
                    obfuscatedFilename, obfuscatedLine);
        }
        return classData.getFrame(clearClassName, obfuscatedMethodName, clearSignature,
                obfuscatedFilename, obfuscatedLine);
    }

    // Converts a proguard-formatted method signature into a Java formatted
    // method signature.
    static private String fromProguardSignature(String sig) throws ParseException {
        if (sig.startsWith("(")) {
            int end = sig.indexOf(')');
            if (end == -1) {
                parseException("Error parsing signature: " + sig);
            }

            StringBuilder converted = new StringBuilder();
            converted.append('(');
            if (end > 1) {
                for (String arg : sig.substring(1, end).split(",")) {
                    converted.append(fromProguardSignature(arg));
                }
            }
            converted.append(')');
            converted.append(fromProguardSignature(sig.substring(end+1)));
            return converted.toString();
        } else if (sig.endsWith(ARRAY_SYMBOL)) {
            return "[" + fromProguardSignature(sig.substring(0, sig.length()-2));
        } else if (sig.equals("boolean")) {
            return "Z";
        } else if (sig.equals("byte")) {
            return "B";
        } else if (sig.equals("char")) {
            return "C";
        } else if (sig.equals("short")) {
            return "S";
        } else if (sig.equals("int")) {
            return "I";
        } else if (sig.equals("long")) {
            return "J";
        } else if (sig.equals("float")) {
            return "F";
        } else if (sig.equals("double")) {
            return "D";
        } else if (sig.equals("void")) {
            return "V";
        } else {
            return "L" + sig.replace('.', '/') + ";";
        }
    }

    // Return a clear signature for the given obfuscated signature.
    private String getSignature(String obfuscatedSig) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < obfuscatedSig.length(); i++) {
            if (obfuscatedSig.charAt(i) == 'L') {
                int e = obfuscatedSig.indexOf(';', i);
                builder.append('L');
                String cls = obfuscatedSig.substring(i+1, e).replace('/', '.');
                builder.append(getClassName(cls).replace('.', '/'));
                builder.append(';');
                i = e;
            } else {
                builder.append(obfuscatedSig.charAt(i));
            }
        }
        return builder.toString();
    }

    // Return a file name for the given clear class name.
    private static String getFileName(String clearClass) {
        String filename = clearClass;
        int dot = filename.lastIndexOf('.');
        if (dot != -1) {
            filename = filename.substring(dot+1);
        }

        int dollar = filename.indexOf('$');
        if (dollar != -1) {
            filename = filename.substring(0, dollar);
        }
        return filename + ".java";
    }
}
