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
package com.android.ddmlib.jdwp;

import com.android.annotations.NonNull;

/**
 * JDWP command constants as specified <a
 * href="http://docs.oracle.com/javase/7/docs/platform/jpda/jdwp/jdwp-protocol.html">here</a>
 */
@SuppressWarnings("SpellCheckingInspection")
public class JdwpCommands {
    public static final int SET_VM = 1;
    public static final int CMD_VM_VERSION = 1;
    public static final int CMD_VM_CLASSESBYSIGNATURE = 2;
    public static final int CMD_VM_ALLCLASSES = 3;
    public static final int CMD_VM_ALLTHREADS = 4;
    public static final int CMD_VM_TOPLEVELTHREADGROUPS = 5;
    public static final int CMD_VM_DISPOSE = 6;
    public static final int CMD_VM_IDSIZES = 7;
    public static final int CMD_VM_SUSPEND = 8;
    public static final int CMD_VM_RESUME = 9;
    public static final int CMD_VM_EXIT = 10;
    public static final int CMD_VM_CREATESTRING = 11;
    public static final int CMD_VM_CAPABILITIES = 12;
    public static final int CMD_VM_CLASSPATHS = 13;
    public static final int CMD_VM_DISPOSEOBJECTS = 14;
    public static final int CMD_VM_HOLDEVENTS = 15;
    public static final int CMD_VM_RELEASEEVENTS = 16;
    public static final int CMD_VM_CAPABILITIESNEW = 17;
    public static final int CMD_VM_REDEFINECLASSES = 18;
    public static final int CMD_VM_SETDEFAULTSTRATUM = 19;
    public static final int CMD_VM_ALLCLASSESWITHGENERIC = 20;

    public static final int SET_REFTYPE = 2;
    public static final int CMD_REFTYPE_SIGNATURE = 1;
    public static final int CMD_REFTYPE_CLASSLOADER = 2;
    public static final int CMD_REFTYPE_MODIFIERS = 3;
    public static final int CMD_REFTYPE_FIELDS = 4;
    public static final int CMD_REFTYPE_METHODS = 5;
    public static final int CMD_REFTYPE_GETVALUES = 6;
    public static final int CMD_REFTYPE_SOURCEFILE = 7;
    public static final int CMD_REFTYPE_NESTEDTYPES = 8;
    public static final int CMD_REFTYPE_STATUS = 9;
    public static final int CMD_REFTYPE_INTERFACES = 10;
    public static final int CMD_REFTYPE_CLASSOBJECT = 11;
    public static final int CMD_REFTYPE_SOURCEDEBUGEXTENSION = 12;
    public static final int CMD_REFTYPE_SIGNATUREWITHGENERIC = 13;
    public static final int CMD_REFTYPE_FIELDSWITHGENERIC = 14;
    public static final int CMD_REFTYPE_METHODSWITHGENERIC = 15;

    public static final int SET_CLASSTYPE = 3;
    public static final int CMD_CLASSTYPE_SUPERCLASS = 1;
    public static final int CMD_CLASSTYPE_SETVALUES = 2;
    public static final int CMD_CLASSTYPE_INVOKEMETHOD = 3;
    public static final int CMD_CLASSTYPE_NEWINSTANCE = 4;

    public static final int SET_ARRAYTYPE = 4;
    public static final int CMD_ARRAYTYPE_NEWINSTANCE = 1;

    public static final int SET_INTERFACETYPE = 5;

    public static final int SET_METHOD = 6;
    public static final int CMD_METHOD_LINETABLE = 1;
    public static final int CMD_METHOD_VARIABLETABLE = 2;
    public static final int CMD_METHOD_BYTECODES = 3;
    public static final int CMD_METHOD_ISOBSOLETE = 4;
    public static final int CMD_METHOD_VARIABLETABLEWITHGENERIC = 5;

    public static final int SET_FIELD = 8;

    public static final int SET_OBJREF = 9;
    public static final int CMD_OBJREF_REFERENCETYPE = 1;
    public static final int CMD_OBJREF_GETVALUES = 2;
    public static final int CMD_OBJREF_SETVALUES = 3;
    public static final int CMD_OBJREF_MONITORINFO = 5;
    public static final int CMD_OBJREF_INVOKEMETHOD = 6;
    public static final int CMD_OBJREF_DISABLECOLLECTION = 7;
    public static final int CMD_OBJREF_ENABLECOLLECTION = 8;
    public static final int CMD_OBJREF_ISCOLLECTED = 9;

    public static final int SET_STRINGREF = 10;
    public static final int CMD_STRINGREF_VALUE = 1;

    public static final int SET_THREADREF = 11;
    public static final int CMD_THREADREF_NAME = 1;
    public static final int CMD_THREADREF_SUSPEND = 2;
    public static final int CMD_THREADREF_RESUME = 3;
    public static final int CMD_THREADREF_STATUS = 4;
    public static final int CMD_THREADREF_THREADGROUP = 5;
    public static final int CMD_THREADREF_FRAMES = 6;
    public static final int CMD_THREADREF_FRAMECOUNT = 7;
    public static final int CMD_THREADREF_OWNEDMONITORS = 8;
    public static final int CMD_THREADREF_CURRENTCONTENDEDMONITOR = 9;
    public static final int CMD_THREADREF_STOP = 10;
    public static final int CMD_THREADREF_INTERRUPT = 11;
    public static final int CMD_THREADREF_SUSPENDCOUNT = 12;

    public static final int SET_THREADGROUPREF = 12;
    public static final int CMD_THREADGROUPREF_NAME = 1;
    public static final int CMD_THREADGROUPREF_PARENT = 2;
    public static final int CMD_THREADGROUPREF_CHILDREN = 3;

    public static final int SET_ARRAYREF = 13;
    public static final int CMD_ARRAYREF_LENGTH = 1;
    public static final int CMD_ARRAYREF_GETVALUES = 2;
    public static final int CMD_ARRAYREF_SETVALUES = 3;

    public static final int SET_CLASSLOADERREF = 14;
    public static final int CMD_CLASSLOADERREF_VISIBLECLASSES = 1;

    public static final int SET_EVENTREQUEST = 15;
    public static final int CMD_EVENTREQUEST_SET = 1;
    public static final int CMD_EVENTREQUEST_CLEAR = 2;
    public static final int CMD_EVENTREQUEST_CLEARALLBREAKPOINTS = 3;

    public static final int SET_STACKFRAME = 16;
    public static final int CMD_STACKFRAME_GETVALUES = 1;
    public static final int CMD_STACKFRAME_SETVALUES = 2;
    public static final int CMD_STACKFRAME_THISOBJECT = 3;
    public static final int CMD_STACKFRAME_POPFRAMES = 4;

    public static final int SET_CLASSOBJECTREF = 17;
    public static final int CMD_CLASSOBJECTREF_REFLECTEDTYPE = 1;

    public static final int SET_EVENT = 64;
    public static final int CMD_EVENT_COMPOSITE = 100;

    @NonNull
    public static String commandSetToString(int cmdSet) {
        switch (cmdSet) {
            case SET_VM:
                return "SET_VM";
            case SET_REFTYPE:
                return "SET_REFTYPE";
            case SET_CLASSTYPE:
                return "SET_CLASSTYPE";
            case SET_ARRAYTYPE:
                return "SET_ARRAYTYPE";
            case SET_INTERFACETYPE:
                return "SET_INTERFACETYPE";
            case SET_METHOD:
                return "SET_METHOD";
            case SET_FIELD:
                return "SET_FIELD";
            case SET_OBJREF:
                return "SET_OBJREF";
            case SET_STRINGREF:
                return "SET_STRINGREF";
            case SET_THREADREF:
                return "SET_THREADREF";
            case SET_THREADGROUPREF:
                return "SET_THREADGROUPREF";
            case SET_ARRAYREF:
                return "SET_ARRAYREF";
            case SET_CLASSLOADERREF:
                return "SET_CLASSLOADERREF";
            case SET_EVENTREQUEST:
                return "SET_EVENTREQUEST";
            case SET_STACKFRAME:
                return "SET_STACKFRAME";
            case SET_CLASSOBJECTREF:
                return "SET_CLASSOBJECTREF";
            case SET_EVENT:
                return "SET_EVENT";
            default:
                return String.format("SET_%02X", cmdSet);
        }
    }

    @NonNull
    public static String commandToString(int cmdSet, int cmd) {
        switch (cmdSet) {
            case SET_VM:
                {
                    switch (cmd) {
                        case CMD_VM_VERSION:
                            return "CMD_VM_VERSION";
                        case CMD_VM_CLASSESBYSIGNATURE:
                            return "CMD_VM_CLASSESBYSIGNATURE";
                        case CMD_VM_ALLCLASSES:
                            return "CMD_VM_ALLCLASSES";
                        case CMD_VM_ALLTHREADS:
                            return "CMD_VM_ALLTHREADS";
                        case CMD_VM_TOPLEVELTHREADGROUPS:
                            return "CMD_VM_TOPLEVELTHREADGROUPS";
                        case CMD_VM_DISPOSE:
                            return "CMD_VM_DISPOSE";
                        case CMD_VM_IDSIZES:
                            return "CMD_VM_IDSIZES";
                        case CMD_VM_SUSPEND:
                            return "CMD_VM_SUSPEND";
                        case CMD_VM_RESUME:
                            return "CMD_VM_RESUME";
                        case CMD_VM_EXIT:
                            return "CMD_VM_EXIT";
                        case CMD_VM_CREATESTRING:
                            return "CMD_VM_CREATESTRING";
                        case CMD_VM_CAPABILITIES:
                            return "CMD_VM_CAPABILITIES";
                        case CMD_VM_CLASSPATHS:
                            return "CMD_VM_CLASSPATHS";
                        case CMD_VM_DISPOSEOBJECTS:
                            return "CMD_VM_DISPOSEOBJECTS";
                        case CMD_VM_HOLDEVENTS:
                            return "CMD_VM_HOLDEVENTS";
                        case CMD_VM_RELEASEEVENTS:
                            return "CMD_VM_RELEASEEVENTS";
                        case CMD_VM_CAPABILITIESNEW:
                            return "CMD_VM_CAPABILITIESNEW";
                        case CMD_VM_REDEFINECLASSES:
                            return "CMD_VM_REDEFINECLASSES";
                        case CMD_VM_SETDEFAULTSTRATUM:
                            return "CMD_VM_SETDEFAULTSTRATUM";
                        case CMD_VM_ALLCLASSESWITHGENERIC:
                            return "CMD_VM_ALLCLASSESWITHGENERIC";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_REFTYPE:
                {
                    switch (cmd) {
                        case CMD_REFTYPE_SIGNATURE:
                            return "CMD_REFTYPE_SIGNATURE";
                        case CMD_REFTYPE_CLASSLOADER:
                            return "CMD_REFTYPE_CLASSLOADER";
                        case CMD_REFTYPE_MODIFIERS:
                            return "CMD_REFTYPE_MODIFIERS";
                        case CMD_REFTYPE_FIELDS:
                            return "CMD_REFTYPE_FIELDS";
                        case CMD_REFTYPE_METHODS:
                            return "CMD_REFTYPE_METHODS";
                        case CMD_REFTYPE_GETVALUES:
                            return "CMD_REFTYPE_GETVALUES";
                        case CMD_REFTYPE_SOURCEFILE:
                            return "CMD_REFTYPE_SOURCEFILE";
                        case CMD_REFTYPE_NESTEDTYPES:
                            return "CMD_REFTYPE_NESTEDTYPES";
                        case CMD_REFTYPE_STATUS:
                            return "CMD_REFTYPE_STATUS";
                        case CMD_REFTYPE_INTERFACES:
                            return "CMD_REFTYPE_INTERFACES";
                        case CMD_REFTYPE_CLASSOBJECT:
                            return "CMD_REFTYPE_CLASSOBJECT";
                        case CMD_REFTYPE_SOURCEDEBUGEXTENSION:
                            return "CMD_REFTYPE_SOURCEDEBUGEXTENSION";
                        case CMD_REFTYPE_SIGNATUREWITHGENERIC:
                            return "CMD_REFTYPE_SIGNATUREWITHGENERIC";
                        case CMD_REFTYPE_FIELDSWITHGENERIC:
                            return "CMD_REFTYPE_FIELDSWITHGENERIC";
                        case CMD_REFTYPE_METHODSWITHGENERIC:
                            return "CMD_REFTYPE_METHODSWITHGENERIC";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_CLASSTYPE:
                {
                    switch (cmd) {
                        case CMD_CLASSTYPE_SUPERCLASS:
                            return "CMD_CLASSTYPE_SUPERCLASS";
                        case CMD_CLASSTYPE_SETVALUES:
                            return "CMD_CLASSTYPE_SETVALUES";
                        case CMD_CLASSTYPE_INVOKEMETHOD:
                            return "CMD_CLASSTYPE_INVOKEMETHOD";
                        case CMD_CLASSTYPE_NEWINSTANCE:
                            return "CMD_CLASSTYPE_NEWINSTANCE";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_ARRAYTYPE:
                {
                    switch (cmd) {
                        case CMD_ARRAYTYPE_NEWINSTANCE:
                            return "CMD_ARRAYTYPE_NEWINSTANCE";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_INTERFACETYPE:
                {
                    switch (cmd) {
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_METHOD:
                {
                    switch (cmd) {
                        case CMD_METHOD_LINETABLE:
                            return "CMD_METHOD_LINETABLE";
                        case CMD_METHOD_VARIABLETABLE:
                            return "CMD_METHOD_VARIABLETABLE";
                        case CMD_METHOD_BYTECODES:
                            return "CMD_METHOD_BYTECODES";
                        case CMD_METHOD_ISOBSOLETE:
                            return "CMD_METHOD_ISOBSOLETE";
                        case CMD_METHOD_VARIABLETABLEWITHGENERIC:
                            return "CMD_METHOD_VARIABLETABLEWITHGENERIC";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_FIELD:
                {
                    switch (cmd) {
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_OBJREF:
                {
                    switch (cmd) {
                        case CMD_OBJREF_REFERENCETYPE:
                            return "CMD_OBJREF_REFERENCETYPE";
                        case CMD_OBJREF_GETVALUES:
                            return "CMD_OBJREF_GETVALUES";
                        case CMD_OBJREF_SETVALUES:
                            return "CMD_OBJREF_SETVALUES";
                        case CMD_OBJREF_MONITORINFO:
                            return "CMD_OBJREF_MONITORINFO";
                        case CMD_OBJREF_INVOKEMETHOD:
                            return "CMD_OBJREF_INVOKEMETHOD";
                        case CMD_OBJREF_DISABLECOLLECTION:
                            return "CMD_OBJREF_DISABLECOLLECTION";
                        case CMD_OBJREF_ENABLECOLLECTION:
                            return "CMD_OBJREF_ENABLECOLLECTION";
                        case CMD_OBJREF_ISCOLLECTED:
                            return "CMD_OBJREF_ISCOLLECTED";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_STRINGREF:
                {
                    switch (cmd) {
                        case CMD_STRINGREF_VALUE:
                            return "CMD_STRINGREF_VALUE";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_THREADREF:
                {
                    switch (cmd) {
                        case CMD_THREADREF_NAME:
                            return "CMD_THREADREF_NAME";
                        case CMD_THREADREF_SUSPEND:
                            return "CMD_THREADREF_SUSPEND";
                        case CMD_THREADREF_RESUME:
                            return "CMD_THREADREF_RESUME";
                        case CMD_THREADREF_STATUS:
                            return "CMD_THREADREF_STATUS";
                        case CMD_THREADREF_THREADGROUP:
                            return "CMD_THREADREF_THREADGROUP";
                        case CMD_THREADREF_FRAMES:
                            return "CMD_THREADREF_FRAMES";
                        case CMD_THREADREF_FRAMECOUNT:
                            return "CMD_THREADREF_FRAMECOUNT";
                        case CMD_THREADREF_OWNEDMONITORS:
                            return "CMD_THREADREF_OWNEDMONITORS";
                        case CMD_THREADREF_CURRENTCONTENDEDMONITOR:
                            return "CMD_THREADREF_CURRENTCONTENDEDMONITOR";
                        case CMD_THREADREF_STOP:
                            return "CMD_THREADREF_STOP";
                        case CMD_THREADREF_INTERRUPT:
                            return "CMD_THREADREF_INTERRUPT";
                        case CMD_THREADREF_SUSPENDCOUNT:
                            return "CMD_THREADREF_SUSPENDCOUNT";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_THREADGROUPREF:
                {
                    switch (cmd) {
                        case CMD_THREADGROUPREF_NAME:
                            return "CMD_THREADGROUPREF_NAME";
                        case CMD_THREADGROUPREF_PARENT:
                            return "CMD_THREADGROUPREF_PARENT";
                        case CMD_THREADGROUPREF_CHILDREN:
                            return "CMD_THREADGROUPREF_CHILDREN";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_ARRAYREF:
                {
                    switch (cmd) {
                        case CMD_ARRAYREF_LENGTH:
                            return "CMD_ARRAYREF_LENGTH";
                        case CMD_ARRAYREF_GETVALUES:
                            return "CMD_ARRAYREF_GETVALUES";
                        case CMD_ARRAYREF_SETVALUES:
                            return "CMD_ARRAYREF_SETVALUES";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_CLASSLOADERREF:
                {
                    switch (cmd) {
                        case CMD_CLASSLOADERREF_VISIBLECLASSES:
                            return "CMD_CLASSLOADERREF_VISIBLECLASSES";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_EVENTREQUEST:
                {
                    switch (cmd) {
                        case CMD_EVENTREQUEST_SET:
                            return "CMD_EVENTREQUEST_SET";
                        case CMD_EVENTREQUEST_CLEAR:
                            return "CMD_EVENTREQUEST_CLEAR";
                        case CMD_EVENTREQUEST_CLEARALLBREAKPOINTS:
                            return "CMD_EVENTREQUEST_CLEARALLBREAKPOINTS";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_STACKFRAME:
                {
                    switch (cmd) {
                        case CMD_STACKFRAME_GETVALUES:
                            return "CMD_STACKFRAME_GETVALUES";
                        case CMD_STACKFRAME_SETVALUES:
                            return "CMD_STACKFRAME_SETVALUES";
                        case CMD_STACKFRAME_THISOBJECT:
                            return "CMD_STACKFRAME_THISOBJECT";
                        case CMD_STACKFRAME_POPFRAMES:
                            return "CMD_STACKFRAME_POPFRAMES";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_CLASSOBJECTREF:
                {
                    switch (cmd) {
                        case CMD_CLASSOBJECTREF_REFLECTEDTYPE:
                            return "CMD_CLASSOBJECTREF_REFLECTEDTYPE";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            case SET_EVENT:
                {
                    switch (cmd) {
                        case CMD_EVENT_COMPOSITE:
                            return "CMD_EVENT_COMPOSITE";
                        default:
                            return unknownCommandToString(cmdSet, cmd);
                    }
                }

            default:
                {
                    return unknownCommandToString(cmdSet, cmd);
                }
        }
    }

    @NonNull
    private static String unknownCommandToString(int cmdSet, int command) {
        return String.format("CMD_%s_%02X", commandSetToString(cmdSet).substring(4), command);
    }
}
