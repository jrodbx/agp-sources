# agp-sources
Contains the per-release bundled sources to the Android Gradle Plugin, useful for general Android development and pointing to open Google issues.

## Updating sources
To pull a new version:
 * Change `agpVersion` in `build.gradle`.
 * Run
 ```
 ./gradlew dumpSources
 ```
 * Check the changeset into source control.

The `dumpSources` Gradle task will automatically download AGP and its transitive dependencies from 
the Google repository and unzip them from your local Gradle cache directory.

From there, use your favorite diff tool to easily examine changes across versions:

![Diff example](/images/agp-diff.png)