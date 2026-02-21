package com.smartcommit

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

/**
 * i18n message bundle for the Smart Commit plugin.
 *
 * Loads messages from `messages/SmartCommitBundle.properties`.
 * Use [message] to retrieve localized strings with optional parameter substitution.
 *
 * Example usage:
 * ```kotlin
 * val text = SmartCommitBundle.message("notification.success")
 * val error = SmartCommitBundle.message("notification.error.ai-failed", "timeout")
 * ```
 */
object SmartCommitBundle {

    @NonNls
    private const val BUNDLE = "messages.SmartCommitBundle"

    private val instance = DynamicBundle(SmartCommitBundle::class.java, BUNDLE)

    /**
     * Retrieve a localized message by key, with optional parameter substitution.
     *
     * @param key    The property key from SmartCommitBundle.properties
     * @param params Optional parameters to substitute into the message template
     * @return The localized string
     */
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any
    ): String {
        return instance.getMessage(key, *params)
    }
}
