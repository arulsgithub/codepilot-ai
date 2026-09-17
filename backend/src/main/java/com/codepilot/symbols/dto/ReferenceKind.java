package com.codepilot.symbols.dto;

public enum ReferenceKind {
    METHOD_CALL,       // foo.bar()
    CONSTRUCTOR_CALL,  // new Foo()
    TYPE_USE           // Foo x; / implements Foo / List<Foo>
}