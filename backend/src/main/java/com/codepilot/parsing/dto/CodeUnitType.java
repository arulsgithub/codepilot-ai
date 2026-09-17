package com.codepilot.parsing.dto;

public enum CodeUnitType {
    CLASS,
    INTERFACE,
    ENUM,
    METHOD,
    CONSTRUCTOR,
    FIELD,
    FILE     // a whole non-Java file (no AST available) - see TextFileParser
}