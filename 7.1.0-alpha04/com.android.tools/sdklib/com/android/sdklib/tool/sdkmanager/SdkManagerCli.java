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
package com.android.sdklib.tool.sdkmanager;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.prefs.AndroidLocationsSingleton;
import com.android.repository.api.Downloader;
import com.android.repository.api.License;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.legacy.LegacyDownloader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Simple tool for installing, uninstalling, etc. SDK packages.
 *
 * <p>Can be built with a convenient wrapper script from the commandline like gradlew
 * :base:sdkmanager-cli:installDist
 */
public class SdkManagerCli {

    private final SdkManagerCliSettings mSettings;

    public static void main(@NonNull String[] args) {
        try {
            main(Arrays.asList(args));
        } catch (CommandFailedException | UncheckedCommandFailedException e) {
            System.exit(1);
        }
    }

    private static void main(@NonNull List<String> args) throws CommandFailedException {
        SdkManagerCliSettings settings;
        try {
            settings = SdkManagerCliSettings.createSettings(args, FileSystems.getDefault());
        } catch (SdkManagerCliSettings.ShowUsageException showUsageException) {
            usage(System.err);
            throw new CommandFailedException();
        } catch (SdkManagerCliSettings.FailSilentlyException failSilentlyException) {
            throw new CommandFailedException();
        } catch (Exception exception) {
            System.err.println("Failed to create settings");
            throw exception;
        }

        Path localPath = settings.getLocalPath();
        if (!Files.exists(localPath)) {
            try {
                Files.createDirectories(localPath);
            } catch (IOException e) {
                System.err.println("Failed to create SDK root dir: " + localPath);
                System.err.println(e.getMessage());
                throw new CommandFailedException();
            }
        }
        AndroidSdkHandler handler =
                AndroidSdkHandler.getInstance(AndroidLocationsSingleton.INSTANCE, localPath);
        new SdkManagerCli(
                        settings,
                        System.out,
                        System.in,
                        new LegacyDownloader(FileOpUtils.create(), settings),
                        handler)
                .run(settings.getProgressIndicator());
        System.out.println();
    }

    public SdkManagerCli(
            @NonNull SdkManagerCliSettings settings,
            @NonNull PrintStream out,
            @Nullable InputStream in,
            @Nullable Downloader downloader,
            @NonNull AndroidSdkHandler handler) {
        mSettings = settings;
        mSettings.setInputStream(in);
        mSettings.setOutputStream(out);
        // TODO: this should probably be done when setting up the settings in the first place
        mSettings.setDownloader(downloader);
        mSettings.setSdkHandler(handler);
    }

    void run(@NonNull ProgressIndicator progress) throws CommandFailedException {
        if (mSettings == null) {
            throw new CommandFailedException();
        }

        mSettings.getAction().execute(progress);
    }

    static boolean askForLicense(
            @NonNull License license, @NonNull PrintStream out, @NonNull BufferedReader in) {
        printLicense(license, out);
        out.print("Accept? (y/N): ");
        return askYesNo(in);
    }

    static void printLicense(@NonNull License license, @NonNull PrintStream out) {
        out.printf("License %s:%n", license.getId());
        out.println("---------------------------------------");
        out.println(license.getValue());
        out.println("---------------------------------------");
    }

    static boolean askYesNo(@NonNull BufferedReader in) {
        try {
            String result = in.readLine();
            return result != null
                    && (result.equalsIgnoreCase("y") || result.equalsIgnoreCase("yes"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void usage(@NonNull PrintStream out) {
        out.println("Usage:");
        out.println(
                "  sdkmanager [--uninstall] [<common args>] "
                        + "[--package_file=<file>] [<packages>...]");
        out.println("  sdkmanager --update [<common args>]");
        out.println("  sdkmanager --list [<common args>]");
        out.println("  sdkmanager --list_installed [<common args>]");
        out.println("  sdkmanager --licenses [<common args>]");
        out.println("  sdkmanager --version");
        out.println();
        out.println("With --install (optional), installs or updates packages.");
        out.println("    By default, the listed packages are installed or (if already installed)");
        out.println("    updated to the latest version.");
        out.println("With --uninstall, uninstall the listed packages.");
        out.println();
        out.println("    <package> is a sdk-style path (e.g. \"build-tools;23.0.0\" or");
        out.println("             \"platforms;android-23\").");
        out.println("    <package-file> is a text file where each line is a sdk-style path");
        out.println("                   of a package to install or uninstall.");
        out.println("    Multiple --package_file arguments may be specified in combination");
        out.println("    with explicit paths.");
        out.println();
        out.println("With --update, all installed packages are updated to the latest version.");
        out.println();
        out.println("With --list, all installed and available packages are printed out.");
        out.println();
        out.println("With --list_installed, all installed packages are printed out.");
        out.println();
        out.println("With --licenses, show and offer the option to accept licenses for all");
        out.println("     available packages that have not already been accepted.");
        out.println();
        out.println("With --version, prints the current version of sdkmanager.");
        out.println();
        out.println("Common Arguments:");
        out.println("    --sdk_root=<sdkRootPath>: Use the specified SDK root instead of the SDK");
        out.println("                              containing this tool");
        out.println();
        out.println("    --channel=<channelId>: Include packages in channels up to <channelId>.");
        out.println("                           Common channels are:");
        out.println("                           0 (Stable), 1 (Beta), 2 (Dev), and 3 (Canary).");
        out.println();
        out.println("    --include_obsolete: With --list, show obsolete packages in the");
        out.println("                        package listing. With --update, update obsolete");
        out.println("                        packages as well as non-obsolete.");
        out.println();
        out.println("    --no_https: Force all connections to use http rather than https.");
        out.println();
        out.println("    --proxy=<http | socks>: Connect via a proxy of the given type.");
        out.println();
        out.println("    --proxy_host=<IP or DNS address>: IP or DNS address of the proxy to use.");
        out.println();
        out.println("    --proxy_port=<port #>: Proxy port to connect to.");
        out.println();
        out.println("    --verbose: Enable verbose output.");
        out.println();
        out.println(
                "* If the env var REPO_OS_OVERRIDE is set to \"windows\",\n"
                        + "  \"macosx\", or \"linux\", packages will be downloaded for that OS.");
    }

    public static final class CommandFailedException extends Exception {}

    public static final class UncheckedCommandFailedException extends RuntimeException {}
}
