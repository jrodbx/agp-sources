/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.ide.common.util

import kotlin.math.max
import kotlin.math.min

/**
 * Utilities around diffs; creates diffs between strings, and can parse diffs to return information
 * about diff hunks.
 */
// LINT.IfChange
class Diffs {
  /**
   * Represents a single hunk in a diff output.
   *
   * @param originalFileStartLine The starting line number of the hunk in the original file.
   * @param originalFileLines The number of lines in the hunk from the original file.
   * @param newFileStartLine The starting line number of the hunk in the new file.
   * @param newFileLines The number of lines in the hunk from the new file.
   * @param header The raw hunk header string (e.g., "@@ -1,5 +1,6 @@").
   * @param content The content of the hunk, including added, removed, and context lines.
   */
  class Hunk(
    val originalFileStartLine: Int,
    val originalFileLines: Int,
    val newFileStartLine: Int,
    val newFileLines: Int,
    val header: String,
    val content: String,
  )

  enum class LineType {
    COMMON,
    ADDED,
    REMOVED,
  }

  data class DiffLine(
    /** Text on the line including any final line separator(s). */
    val text: String,
    /** Whether the line is common, added, or removed. */
    val type: LineType,
  )

  companion object {
    /**
     * Line String.lines(), but we *include* the final line separators on each line. This is useful
     * such that we can correctly match diff's treatment of line separators; we don't want to treat
     * a line that ends with \r\n as the same as a line that ends with \n, and similarly, for the
     * last line in the file, we want to detect a difference in whether the file ends with a newline
     * or not.
     */
    private fun String.splitWithLineSeparators(): List<String> {
      if (this.isEmpty()) {
        return listOf(this) // Consistent with String.lines() for empty string
      }
      return buildList {
        val lineSeparatorRegex = Regex("\r\n|\n|\r")
        var lastEnd = 0
        lineSeparatorRegex.findAll(this@splitWithLineSeparators).forEach { matchResult ->
          add(
            this@splitWithLineSeparators.substring(lastEnd, matchResult.range.first) +
              matchResult.value
          )
          lastEnd = matchResult.range.last + 1
        }
        if (lastEnd < this@splitWithLineSeparators.length) {
          add(this@splitWithLineSeparators.substring(lastEnd))
        }
        // If the list is empty after processing, it means the string had no separators.
        // (And was not empty, due to the initial check).
        // In this case, the original string itself is the single "line".
        if (isEmpty() && this@splitWithLineSeparators.isNotEmpty()) {
          add(this@splitWithLineSeparators)
        }
      }
    }

    /**
     * Computes the diff between two strings.
     *
     * @param originalText The original string.
     * @param newText The new string.
     * @return A list of lines that are common, added, or removed.
     */
    fun diffLines(originalText: String, newText: String): List<DiffLine> {
      // Treat empty strings as having zero lines, otherwise split by lines.
      val originalLines =
        if (originalText.isEmpty()) emptyList() else originalText.splitWithLineSeparators()
      val newLines = if (newText.isEmpty()) emptyList() else newText.splitWithLineSeparators()

      return diffLines(originalLines, newLines)
    }

    /**
     * Computes the diff between two strings, split into lines.
     *
     * @param originalLines The original string.
     * @param newLines The new string.
     * @return A list of lines that are common, added, or removed.
     */
    private fun diffLines(originalLines: List<String>, newLines: List<String>): List<DiffLine> {
      if (originalLines == newLines) {
        return emptyList()
      }

      // Use Longest Common Subsequence (LCS) to find differences.
      // Matrix stores lengths of LCS for prefixes.
      val lcsMatrix = Array(originalLines.size + 1) { IntArray(newLines.size + 1) }
      for (i in originalLines.indices) {
        for (j in newLines.indices) {
          if (originalLines[i] == newLines[j]) {
            lcsMatrix[i + 1][j + 1] = lcsMatrix[i][j] + 1
          } else {
            lcsMatrix[i + 1][j + 1] = max(lcsMatrix[i + 1][j], lcsMatrix[i][j + 1])
          }
        }
      }

      // Reconstruct the sequence of DiffLine objects from the LCS matrix.
      val diffLines = mutableListOf<DiffLine>()
      var i = originalLines.size
      var j = newLines.size
      while (i > 0 || j > 0) {
        when {
          i > 0 && j > 0 && originalLines[i - 1] == newLines[j - 1] -> {
            // Common line
            diffLines.add(DiffLine(originalLines[i - 1], LineType.COMMON))
            i--
            j--
          }
          j > 0 && (i == 0 || lcsMatrix[i][j - 1] >= lcsMatrix[i - 1][j]) -> {
            // Line added in newText
            diffLines.add(DiffLine(newLines[j - 1], LineType.ADDED))
            j--
          }
          j == 0 || lcsMatrix[i][j - 1] < lcsMatrix[i - 1][j] -> {
            // Line removed from originalText
            diffLines.add(DiffLine(originalLines[i - 1], LineType.REMOVED))
            i--
          }
          else -> break // Should not be reached if LCS is correct
        }
      }
      diffLines.reverse() // Lines were added in reverse order

      return diffLines
    }

    /**
     * Computes the diff between two strings.
     *
     * @param originalText The original string.
     * @param newText The new string.
     * @param windowSize The number of context lines to include around a difference.
     * @param trimEnds If true, trim any trailing whitespace on the diff lines
     * @return A string in a format similar to `git diff`, showing differences. Returns an empty
     *   string if texts are identical or both are empty.
     */
    fun diff(
      originalText: String,
      newText: String,
      windowSize: Int = 3,
      trimEnds: Boolean = false,
    ): String {
      // Treat empty strings as having zero lines, otherwise split by lines.
      val originalLines =
        if (originalText.isEmpty()) emptyList() else originalText.splitWithLineSeparators()
      val newLines = if (newText.isEmpty()) emptyList() else newText.splitWithLineSeparators()

      val diffLines = diffLines(originalLines, newLines)
      if (diffLines.isEmpty()) return ""

      return formatHunks(diffLines, originalLines.size, newLines.size, windowSize, trimEnds)
    }

    private fun formatHunks(
      diffLines: List<DiffLine>,
      numTotalOriginalLines: Int,
      numTotalNewLines: Int,
      contextLines: Int,
      trimEnds: Boolean,
    ): String {
      val resultHunks = StringBuilder()
      var overallProcessedIdx = 0 // Tracks our progress through diffLines across hunks

      while (overallProcessedIdx < diffLines.size) {
        // 1. Find the start of the *first* change in a potential new hunk
        var firstChangeStartInHunk = overallProcessedIdx
        while (
          firstChangeStartInHunk < diffLines.size &&
            diffLines[firstChangeStartInHunk].type == LineType.COMMON
        ) {
          firstChangeStartInHunk++
        }

        if (firstChangeStartInHunk == diffLines.size) break // No more changes left

        // 2. Find the end of this first change block
        var firstChangeEndInHunk = firstChangeStartInHunk
        while (
          firstChangeEndInHunk < diffLines.size &&
            diffLines[firstChangeEndInHunk].type != LineType.COMMON
        ) {
          firstChangeEndInHunk++
        }

        // 3. Determine initial hunk boundaries with context
        val currentHunkEffectiveStart = max(0, firstChangeStartInHunk - contextLines)
        var currentHunkEffectiveEnd = min(diffLines.size, firstChangeEndInHunk + contextLines)

        // 4. Attempt to merge subsequent change blocks
        var lastConsideredChangeEnd =
          firstChangeEndInHunk // Index *after* the last non-common line of the last change block
        // merged.

        while (true) {
          // Find the start of the *next* block of actual changes after the last one we incorporated
          var nextChangeBlockActualStart = lastConsideredChangeEnd
          while (
            nextChangeBlockActualStart < diffLines.size &&
              diffLines[nextChangeBlockActualStart].type == LineType.COMMON
          ) {
            nextChangeBlockActualStart++
          }

          if (nextChangeBlockActualStart == diffLines.size)
            break // No more change blocks anywhere to merge

          // Condition for merging:
          // The current hunk's effective end (context included) must overlap or touch
          // the start of the context of the *next* change block.
          val nextChangeBlockContextAllowedStart = nextChangeBlockActualStart - contextLines
          if (currentHunkEffectiveEnd >= nextChangeBlockContextAllowedStart) {
            // Merge:
            // Find the end of this next change block
            var nextChangeBlockActualEnd = nextChangeBlockActualStart
            while (
              nextChangeBlockActualEnd < diffLines.size &&
                diffLines[nextChangeBlockActualEnd].type != LineType.COMMON
            ) {
              nextChangeBlockActualEnd++
            }

            // Extend the current hunk's effective end to include this new block and its context
            currentHunkEffectiveEnd = min(diffLines.size, nextChangeBlockActualEnd + contextLines)
            // Update the end of the last actual change block we've processed for this hunk
            lastConsideredChangeEnd = nextChangeBlockActualEnd
          } else {
            // No overlap sufficient for merging, stop trying for this hunk
            break
          }
        }

        // 5. Hunk boundaries are now final (currentHunkEffectiveStart, currentHunkEffectiveEnd)
        // Calculate line numbers for the hunk header
        var hunkOriginalFileStartLine = 1
        var hunkNewFileStartLine = 1
        for (k in 0 until currentHunkEffectiveStart) {
          when (diffLines[k].type) {
            LineType.COMMON -> {
              hunkOriginalFileStartLine++
              hunkNewFileStartLine++
            }
            LineType.REMOVED -> hunkOriginalFileStartLine++
            LineType.ADDED -> hunkNewFileStartLine++
          }
        }

        val hunkContent = StringBuilder()
        var hunkOriginalNumEffectiveLines = 0
        var hunkNewNumEffectiveLines = 0

        fun emitLine(typeChar: Char, line: String) {
          var lineEnds = line.length
          while (lineEnds > 0 && (line[lineEnds - 1] == '\n' || line[lineEnds - 1] == '\r')) {
            lineEnds--
          }
          if (lineEnds == 0 && typeChar == ' ') {
            hunkContent.append('\n')
            return
          }
          hunkContent.append(typeChar)
          if (trimEnds) {
            hunkContent.append(line.trimEnd()).append('\n')
          } else {
            hunkContent.append(line.substring(0, lineEnds)).append('\n')
          }
        }
        for (k in currentHunkEffectiveStart until currentHunkEffectiveEnd) {
          val line = diffLines[k]
          when (line.type) {
            LineType.COMMON -> {
              emitLine(' ', line.text)
              hunkOriginalNumEffectiveLines++
              hunkNewNumEffectiveLines++
            }
            LineType.REMOVED -> {
              emitLine('-', line.text)
              hunkOriginalNumEffectiveLines++
            }
            LineType.ADDED -> {
              emitLine('+', line.text)
              hunkNewNumEffectiveLines++
            }
          }
        }

        var displayOriginalStart =
          if (hunkOriginalNumEffectiveLines == 0) max(0, hunkOriginalFileStartLine - 1)
          else hunkOriginalFileStartLine
        var displayNewStart =
          if (hunkNewNumEffectiveLines == 0) max(0, hunkNewFileStartLine - 1)
          else hunkNewFileStartLine

        if (numTotalOriginalLines == 0 && hunkOriginalNumEffectiveLines == 0) {
          displayOriginalStart = 0
        }
        if (numTotalNewLines == 0 && hunkNewNumEffectiveLines == 0) {
          displayNewStart = 0
        }

        val originalRangeStr =
          when (hunkOriginalNumEffectiveLines) {
            0 -> "${displayOriginalStart},0"
            1 -> displayOriginalStart.toString()
            else -> "${displayOriginalStart},${hunkOriginalNumEffectiveLines}"
          }

        val newRangeStr =
          when (hunkNewNumEffectiveLines) {
            0 -> "${displayNewStart},0"
            1 -> displayNewStart.toString()
            else -> "${displayNewStart},${hunkNewNumEffectiveLines}"
          }

        resultHunks.append("@@ -${originalRangeStr} +${newRangeStr} @@\n")
        resultHunks.append(hunkContent)

        // Advance to the end of the (potentially merged) hunk
        overallProcessedIdx = currentHunkEffectiveEnd
      }
      return resultHunks.toString().trimEnd()
    }

    /**
     * Parses the given diff string into a list of [Hunk] objects.
     *
     * @param diffText The diff output string.
     * @return A list of [Hunk] objects representing the parsed hunks.
     */
    fun parseDiff(diffText: String): List<Hunk> {
      val hunks = mutableListOf<Hunk>()
      // Basic regex to find hunk headers. This will need to be more robust.
      // A hunk header looks like: @@ -originalStart,originalLength +newStart,newLength @@
      val hunkHeaderRegex = Regex("""^@@\s*-(\d+)(?:,(\d+))?\s*\+(\d+)(?:,(\d+))?\s*@@""")
      val lines = diffText.lines()

      var currentHunkLines = mutableListOf<String>()
      var originalStart = 0
      var originalLength = 0
      var newStart = 0
      var newLength = 0
      var currentHeader = ""

      for (line in lines) {
        val matchResult = hunkHeaderRegex.find(line)
        if (matchResult != null) {
          // If we were already processing a hunk, save it.
          if (currentHunkLines.isNotEmpty()) {
            hunks.add(
              Hunk(
                originalFileStartLine = originalStart,
                originalFileLines = originalLength,
                newFileStartLine = newStart,
                newFileLines = newLength,
                header = currentHeader,
                content = currentHunkLines.joinToString("\n"),
              )
            )
            currentHunkLines = mutableListOf()
          }

          // Start a new hunk
          currentHeader = line
          originalStart = matchResult.groupValues[1].toInt()
          // Default length is 1 if not specified
          originalLength = matchResult.groupValues[2].ifEmpty { "1" }.toInt()
          newStart = matchResult.groupValues[3].toInt()
          newLength = matchResult.groupValues[4].ifEmpty { "1" }.toInt()
        } else if (currentHeader.isNotEmpty()) {
          // This line is part of the current hunk's content
          currentHunkLines.add(line)
        }
      }

      // Add the last hunk if there is one
      if (currentHunkLines.isNotEmpty() && currentHeader.isNotEmpty()) {
        hunks.add(
          Hunk(
            originalFileStartLine = originalStart,
            originalFileLines = originalLength,
            newFileStartLine = newStart,
            newFileLines = newLength,
            header = currentHeader,
            content = currentHunkLines.joinToString("\n"),
          )
        )
      }

      return hunks
    }
  }
}
// LINT.ThenChange(tools/vendor/google/ml/aiplugin/model/api/src/main/kotlin/com/android/ide/common/util/Diffs.kt)
