package com.codepilot.parsing.dto;

import java.util.List;

public record ParseResponse(
        String rootPath,
        int unitCount,
        List<CodeUnit> units
) {
}