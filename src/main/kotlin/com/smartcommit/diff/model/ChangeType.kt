package com.smartcommit.diff.model

/**
 * Represents the type of VCS change for a single file.
 * Maps directly to the operations Git tracks.
 */
enum class ChangeType {
    /** A new file added to version control. */
    NEW,

    /** An existing tracked file with content modifications. */
    MODIFIED,

    /** A tracked file removed from version control. */
    DELETED,

    /** A file moved to a different directory (path changed, possibly content too). */
    MOVED,

    /** A file renamed in the same directory (name changed, possibly content too). */
    RENAMED;

    val isAddition: Boolean get() = this == NEW
    val isRemoval: Boolean get() = this == DELETED
    val isRelocation: Boolean get() = this == MOVED || this == RENAMED
    val isContentChange: Boolean get() = this == MODIFIED || this == MOVED || this == RENAMED
}
