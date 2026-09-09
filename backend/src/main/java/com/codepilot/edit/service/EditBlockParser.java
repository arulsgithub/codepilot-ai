package com.codepilot.edit.service;

import com.codepilot.edit.dto.EditPlan;
import com.codepilot.edit.dto.FileEdit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses the model's response into an EditPlan.
 *
 * Implemented as a line-scanning state machine rather than a regex: the SEARCH/REPLACE bodies
 * contain arbitrary source code (including braces, quotes and blank lines), and whitespace must
 * be preserved byte-for-byte for exact matching to work. Regex multiline matching over that is
 * fragile; scanning for marker lines is not.
 */
@Component
public class EditBlockParser {

    private static final String EDIT_MARKER = "### EDIT:";
    private static final String SEARCH_MARKER = "<<<<<<< SEARCH";
    private static final String DIVIDER_MARKER = "=======";
    private static final String REPLACE_MARKER = ">>>>>>> REPLACE";

    private enum State { OUTSIDE, EXPECT_SEARCH, IN_SEARCH, IN_REPLACE }

    public EditPlan parse(String modelResponse) {
        String summary = "";
        List<FileEdit> edits = new ArrayList<>();

        State state = State.OUTSIDE;
        String currentPath = null;
        List<String> searchLines = new ArrayList<>();
        List<String> replaceLines = new ArrayList<>();

        for (String line : modelResponse.split("\n", -1)) {
            String trimmed = line.trim();

            switch (state) {
                case OUTSIDE -> {
                    if (trimmed.startsWith("SUMMARY:")) {
                        summary = trimmed.substring("SUMMARY:".length()).trim();
                    } else if (trimmed.startsWith(EDIT_MARKER)) {
                        currentPath = trimmed.substring(EDIT_MARKER.length()).trim();
                        state = State.EXPECT_SEARCH;
                    }
                }
                case EXPECT_SEARCH -> {
                    if (trimmed.equals(SEARCH_MARKER)) {
                        searchLines.clear();
                        state = State.IN_SEARCH;
                    }
                }
                case IN_SEARCH -> {
                    if (trimmed.equals(DIVIDER_MARKER)) {
                        replaceLines.clear();
                        state = State.IN_REPLACE;
                    } else {
                        searchLines.add(line); // raw line - indentation must survive
                    }
                }
                case IN_REPLACE -> {
                    if (trimmed.equals(REPLACE_MARKER)) {
                        edits.add(new FileEdit(
                                currentPath,
                                String.join("\n", searchLines),
                                String.join("\n", replaceLines)));
                        state = State.OUTSIDE;
                        currentPath = null;
                    } else {
                        replaceLines.add(line);
                    }
                }
            }
        }

        return new EditPlan(summary, edits);
    }
}