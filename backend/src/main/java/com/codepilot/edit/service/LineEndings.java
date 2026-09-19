package com.codepilot.edit.service;

/**
 * Line-ending handling for the edit pipeline.
 *
 * Windows files use CRLF. Files.readString preserves that, but text coming back from a language
 * model uses LF only - so an exact-match check between them fails on text that looks identical in
 * every editor. That was silently breaking every edit on a Windows checkout.
 *
 * The rule: normalise to LF for all matching and replacing, then restore the file's original
 * convention when writing. Skipping the restore would rewrite every line of a CRLF file, turning
 * a two-line change into a whole-file diff in git.
 */
public final class LineEndings {

    private LineEndings() {
    }

    /** Collapses CRLF and lone CR to LF, so comparisons only ever deal with '\n'. */
    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace("\r", "\n");
    }

    /** True if the text uses Windows line endings - decided by the file, not the platform. */
    public static boolean usesCrlf(String text) {
        return text != null && text.contains("\r\n");
    }

    /** Converts LF-normalised text back to the file's original convention. */
    public static String restore(String normalizedText, boolean crlf) {
        return crlf ? normalizedText.replace("\n", "\r\n") : normalizedText;
    }
}