package com.codepilot.repo.dto;

/**
 * Where a repository's code came from.
 *
 * LOCAL  - a folder the user already has on disk. CodePilot only reads it.
 * GITHUB - a remote CodePilot cloned itself into its managed workspace.
 *          CodePilot owns this directory and may reset/overwrite it on sync.
 *
 * The distinction matters later: for a LOCAL repo we edit files in place
 * (what EditApplier does today), but for a GITHUB repo we will branch and
 * commit instead. Recording the origin now means step 4 needs no migration.
 */
public enum SourceType {
    LOCAL,
    GITHUB
}