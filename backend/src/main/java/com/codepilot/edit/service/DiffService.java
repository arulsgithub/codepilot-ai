package com.codepilot.edit.service;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiffService {

    private static final int CONTEXT_LINES = 3;

    /** Renders a standard unified diff so the UI can display it with familiar +/- formatting. */
    public String unifiedDiff(String relativeFilePath, String before, String after) {
        List<String> beforeLines = before.lines().toList();
        List<String> afterLines = after.lines().toList();

        Patch<String> patch = DiffUtils.diff(beforeLines, afterLines);
        List<String> diffLines = UnifiedDiffUtils.generateUnifiedDiff(
                relativeFilePath, relativeFilePath, beforeLines, patch, CONTEXT_LINES);

        return String.join("\n", diffLines);
    }
}