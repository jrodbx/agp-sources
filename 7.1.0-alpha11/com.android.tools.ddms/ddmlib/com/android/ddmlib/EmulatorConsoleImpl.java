/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ddmlib;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.concurrency.GuardedBy;
import com.android.prefs.AndroidLocationsSingleton;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides control over emulated hardware of the Android emulator.
 *
 * <p>This is basically a wrapper around the command line console normally used with telnet.
 *
 * <p>Regarding line termination handling:<br>
 * One of the issues is that the telnet protocol <b>requires</b> usage of <code>\r\n</code>. Most
 * implementations don't enforce it (the dos one does). In this particular case, this is mostly
 * irrelevant since we don't use telnet in Java, but that means we want to make sure we use the same
 * line termination than what the console expects. The console code removes <code>\r</code> and
 * waits for <code>\n</code>.
 *
 * <p>However this means you <i>may</i> receive <code>\r\n</code> when reading from the console.
 *
 * <p><b>This API will change in the near future.</b>
 */
public final class EmulatorConsoleImpl extends EmulatorConsole {

    private static final String DEFAULT_ENCODING = "ISO-8859-1"; //$NON-NLS-1$

    private static final int WAIT_TIME = 5; // spin-wait sleep, in ms

    private static final int STD_TIMEOUT = 5000; // standard delay, in ms

    private static final String HOST = "127.0.0.1";  //$NON-NLS-1$

    private static final String COMMAND_PING = "help\r\n"; //$NON-NLS-1$
    private static final String COMMAND_AVD_NAME = "avd name\r\n"; //$NON-NLS-1$
    private static final String COMMAND_AVD_PATH = "avd path\r\n";
    private static final String COMMAND_KILL = "kill\r\n"; //$NON-NLS-1$
    private static final String COMMAND_AUTH = "auth %1$s\r\n"; //$NON-NLS-1$
    private static final String COMMAND_SCREENRECORD_START =
            "screenrecord start %1$s\r\n"; //$NON-NLS-1$
    private static final String COMMAND_SCREENRECORD_STOP = "screenrecord stop\r\n"; //$NON-NLS-1$

    private static final Pattern RE_KO = Pattern.compile("KO:\\s+(.*)"); //$NON-NLS-1$
    private static final String RE_AUTH_REQUIRED = "Android Console: Authentication required"; //$NON-NLS-1$

    private static final String EMULATOR_CONSOLE_AUTH_TOKEN = ".emulator_console_auth_token";

    public static final String RESULT_OK = null;

    private static final Pattern sEmulatorRegexp = Pattern.compile(IDevice.RE_EMULATOR_SN);

    @GuardedBy(value = "sEmulators")
    private static final HashMap<Integer, EmulatorConsoleImpl> sEmulators = new HashMap<>();

    private static final String LOG_TAG = "EmulatorConsole";

    private final int mPort;

    private SocketChannel mSocketChannel;

    private final byte[] mBuffer = new byte[8192];

    /**
     * Returns an {@link EmulatorConsoleImpl} object for the given {@link IDevice}. This can be an
     * already existing console, or a new one if it hadn't been created yet. Note: emulator consoles
     * don't automatically close when an emulator exists. It is the responsibility of higher level
     * code to explicitly call {@link #close()} when the emulator corresponding to a open console is
     * killed.
     *
     * @param d The device that the console links to.
     * @return an <code>EmulatorConsole</code> object or <code>null</code> if the connection failed.
     */
    @Nullable
    static EmulatorConsoleImpl createConsole(IDevice d) {
        // we need to make sure that the device is an emulator
        // get the port number. This is the console port.
        Integer port = getEmulatorPort(d.getSerialNumber());
        if (port == null) {
            Log.w(LOG_TAG, "Failed to find emulator port from serial: " + d.getSerialNumber());
            return null;
        }

        EmulatorConsoleImpl console = retrieveConsole(port);

        if (!console.checkConnection()) {
            console.close();
            return null;
        }

        return console;
    }

    /**
     * Return port of emulator given its serial number.
     *
     * @param serialNumber the emulator's serial number
     * @return the integer port or <code>null</code> if it could not be determined
     */
    public static Integer getEmulatorPort(String serialNumber) {
        Matcher m = sEmulatorRegexp.matcher(serialNumber);
        if (m.matches()) {
            // get the port number. This is the console port.
            int port;
            try {
                port = Integer.parseInt(m.group(1));
                if (port > 0) {
                    return port;
                }
            } catch (NumberFormatException e) {
                // looks like we failed to get the port number. This is a bit strange since
                // it's coming from a regexp that only accept digit, but we handle the case
                // and return null.
            }
        }
        return null;
    }

    /** Retrieve a console object for this port, creating if necessary. */
    @NonNull
    private static EmulatorConsoleImpl retrieveConsole(int port) {
        synchronized (sEmulators) {
            EmulatorConsoleImpl console = sEmulators.get(port);
            if (console == null) {
                Log.v(LOG_TAG, "Creating emulator console for " + port);
                console = new EmulatorConsoleImpl(port);
                sEmulators.put(port, console);
            }
            return console;
        }
    }

    @Override
    public void close() {
        synchronized (sEmulators) {
            Log.v(LOG_TAG, "Removing emulator console for " + mPort);
            sEmulators.remove(mPort);
        }

        try {
            if (mSocketChannel != null) {
                mSocketChannel.close();
            }
            mSocketChannel = null;
        } catch (IOException e) {
            Log.w(LOG_TAG, "Failed to close EmulatorConsole channel");
        }
    }

    private EmulatorConsoleImpl(int port) {
        mPort = port;
    }

    /**
     * Determine if connection to emulator console is functioning. Starts the connection if
     * necessary
     * @return true if success.
     */
    private synchronized boolean checkConnection() {
        if (mSocketChannel == null) {
            // connection not established, try to connect
            InetSocketAddress socketAddr;
            try {
                InetAddress hostAddr = InetAddress.getByName(HOST);
                socketAddr = new InetSocketAddress(hostAddr, mPort);
                mSocketChannel = SocketChannel.open(socketAddr);
                mSocketChannel.configureBlocking(false);

                // read initial output from console
                String[] welcome = readLines();
                if (welcome == null) {
                    return false;
                }

                // the first line starts with a bunch of telnet noise, just check the end
                if (welcome[0].endsWith(RE_AUTH_REQUIRED)) {
                    // we need to send an authentication message before any other
                    if (RESULT_OK != sendAuthentication()) {
                        Log.w(LOG_TAG, "Emulator console auth failed (is the emulator running as a different user?)");
                        return false;
                    }
                }
            } catch (IOException e) {
                Log.w(LOG_TAG, "Failed to start Emulator console for " + mPort);
                return false;
            } catch (Throwable e) {
                Log.w(LOG_TAG, "Failed to get emulator console auth token");
                return false;
            }
        }

        return ping();
    }

    /**
     * Ping the emulator to check if the connection is still alive.
     * @return true if the connection is alive.
     */
    private synchronized boolean ping() {
        // it looks like we can send stuff, even when the emulator quit, but we can't read
        // from the socket. So we check the return of readLines()
        if (sendCommand(COMMAND_PING)) {
            return readLines() != null;
        }

        return false;
    }

    @Override
    public synchronized void kill() {
        sendCommand(COMMAND_KILL);
    }

    @Override
    @Nullable
    public synchronized String getAvdName() {
        try {
            return getOutput(COMMAND_AVD_NAME);
        } catch (CommandFailedException exception) {
            return exception.getMessage();
        } catch (Exception exception) {
            // noinspection ConstantConditions
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return null;
        }
    }

    @Override
    @NonNull
    public synchronized String getAvdPath() throws CommandFailedException {
        return getOutput(COMMAND_AVD_PATH);
    }

    @NonNull
    private String getOutput(@NonNull String command) throws CommandFailedException {
        if (!sendCommand(command)) {
            throw new CommandFailedException();
        }

        return processOutput(Objects.requireNonNull(readLines()));
    }

    @NonNull
    @VisibleForTesting
    static String processOutput(@NonNull String[] lines) throws CommandFailedException {
        if (lines.length == 0) {
            throw new IllegalArgumentException();
        }

        Matcher matcher = RE_KO.matcher(lines[lines.length - 1]);

        if (matcher.matches()) {
            throw new CommandFailedException(matcher.group(1));
        }

        if (lines.length >= 2 && lines[lines.length - 1].equals("OK")) {
            return lines[lines.length - 2];
        }

        CharSequence separator = System.lineSeparator();

        throw new IllegalArgumentException(
                "The last line doesn't equal \"OK\" nor start with \"KO:  \". lines = "
                        + separator
                        + String.join(separator, lines));
    }

    public synchronized String sendAuthentication() throws IOException {
        Path useHomeLocation = AndroidLocationsSingleton.INSTANCE.getUserHomeLocation();
        File emulatorConsoleAuthTokenFile =
                useHomeLocation.resolve(EMULATOR_CONSOLE_AUTH_TOKEN).toFile();
        String authToken =
                Files.asCharSource(emulatorConsoleAuthTokenFile, Charsets.UTF_8).read().trim();
        String command = String.format(COMMAND_AUTH, authToken);

        return processCommand(command);
    }

    @Override
    public synchronized String startEmulatorScreenRecording(String args) {
        String command = String.format(COMMAND_SCREENRECORD_START, args);
        return processCommand(command);
    }

    @Override
    public synchronized String stopScreenRecording() {
        return processCommand(COMMAND_SCREENRECORD_STOP);
    }

    /**
     * Sends a command to the emulator console.
     * @param command The command string. <b>MUST BE TERMINATED BY \n</b>.
     * @return true if success
     */
    private boolean sendCommand(String command) {
        boolean result = false;
        try {
            byte[] bCommand;
            try {
                bCommand = command.getBytes(DEFAULT_ENCODING);
            } catch (UnsupportedEncodingException e) {
                Log.w(LOG_TAG, "wrong encoding when sending " + command + " to " + mPort);
                // wrong encoding...
                return result;
            }

            // write the command
            AdbHelper.write(mSocketChannel, bCommand, bCommand.length, DdmPreferences.getTimeOut());

            result = true;
        } catch (Exception e) {
            Log.d(LOG_TAG, "Exception sending command " + command + " to " + mPort);
            return false;
        }

        return result;
    }

    /**
     * Sends a command to the emulator and parses its answer.
     * @param command the command to send.
     * @return {@link #RESULT_OK} if success, an error message otherwise.
     */
    private String processCommand(String command) {
        if (sendCommand(command)) {
            String[] result = readLines();

            if (result != null && result.length > 0) {
                Matcher m = RE_KO.matcher(result[result.length-1]);
                if (m.matches()) {
                    return m.group(1);
                }
                return RESULT_OK;
            }

            return "Unable to communicate with the emulator";
        }

        return "Unable to send command to the emulator";
    }

    /**
     * Reads line from the console socket. This call is blocking until we read the lines:
     * <ul>
     * <li>OK\r\n</li>
     * <li>KO<msg>\r\n</li>
     * </ul>
     * @return the array of strings read from the emulator.
     */
    private String[] readLines() {
        try {
            ByteBuffer buf = ByteBuffer.wrap(mBuffer, 0, mBuffer.length);
            int numWaits = 0;
            boolean stop = false;

            while (buf.position() != buf.limit() && !stop) {
                int count;

                count = mSocketChannel.read(buf);
                if (count < 0) {
                    return null;
                } else if (count == 0) {
                    if (numWaits * WAIT_TIME > STD_TIMEOUT) {
                        return null;
                    }
                    // non-blocking spin
                    try {
                        Thread.sleep(WAIT_TIME);
                    } catch (InterruptedException ie) {
                    }
                    numWaits++;
                } else {
                    numWaits = 0;
                }

                // check the last few char aren't OK. For a valid message to test
                // we need at least 4 bytes (OK/KO + \r\n)
                if (buf.position() >= 4) {
                    int pos = buf.position();
                    if (endsWithOK(pos) || lastLineIsKO(pos)) {
                        stop = true;
                    }
                }
            }

            String msg = new String(mBuffer, 0, buf.position(), DEFAULT_ENCODING);
            return msg.split("\r\n"); //$NON-NLS-1$
        } catch (IOException e) {
            Log.d(LOG_TAG, "Exception reading lines for " + mPort);
            return null;
        }
    }

    /**
     * Returns true if the 4 characters *before* the current position are "OK\r\n"
     * @param currentPosition The current position
     */
    private boolean endsWithOK(int currentPosition) {
        return mBuffer[currentPosition - 1] == '\n' &&
                mBuffer[currentPosition - 2] == '\r' &&
                mBuffer[currentPosition - 3] == 'K' &&
                mBuffer[currentPosition - 4] == 'O';

    }

    /**
     * Returns true if the last line starts with KO and is also terminated by \r\n
     * @param currentPosition the current position
     */
    private boolean lastLineIsKO(int currentPosition) {
        // first check that the last 2 characters are CRLF
        if (mBuffer[currentPosition-1] != '\n' ||
                mBuffer[currentPosition-2] != '\r') {
            return false;
        }

        // now loop backward looking for the previous CRLF, or the beginning of the buffer
        int i;
        for (i = currentPosition-3 ; i >= 0; i--) {
            if (mBuffer[i] == '\n') {
                // found \n!
                if (i > 0 && mBuffer[i-1] == '\r') {
                    // found \r!
                    break;
                }
            }
        }

        // here it is either -1 if we reached the start of the buffer without finding
        // a CRLF, or the position of \n. So in both case we look at the characters at i+1 and i+2
        if (mBuffer[i+1] == 'K' && mBuffer[i+2] == 'O') {
            // found error!
            return true;
        }

        return false;
    }
}
