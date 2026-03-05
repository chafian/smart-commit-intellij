package com.smartcommit.onboarding

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/**
 * First-run welcome dialog shown once after plugin installation.
 *
 * Educates the user that Cloud works out of the box for free,
 * and provides clear CTAs: Connect IDE, Use OpenAI, or Close.
 *
 * Result:
 * - OK (0) = "Connect IDE" was clicked
 * - NEXT_USER_EXIT_CODE (1) = "Use OpenAI instead" was clicked
 * - CANCEL = "Close" or Esc
 */
class WelcomeDialog : DialogWrapper(true) {

    companion object {
        /** Custom exit code for "Use OpenAI instead" */
        const val USE_OPENAI_EXIT_CODE = NEXT_USER_EXIT_CODE
    }

    init {
        title = "Smart Commit Cloud"
        setOKButtonText("Connect IDE")
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            // Branded header panel
            row {
                cell(createHeaderPanel()).align(Align.FILL)
            }

            // Spacer
            row { comment("") }

            // Value props
            row {
                val bodyLabel = JLabel(
                    "<html>" +
                        "<div style='font-size:13pt; line-height:1.6;'>" +
                        "Generate commit messages instantly — <b>no API keys required</b>." +
                        "<br><br>" +
                        "<table cellpadding='3' cellspacing='0'>" +
                        "<tr><td>\u2705</td><td>Free plan includes <b>30 generations/month</b></td></tr>" +
                        "<tr><td>\u2705</td><td>Secure — <b>diffs are not stored</b></td></tr>" +
                        "<tr><td>\u2705</td><td><b>Works out of the box</b> — zero configuration</td></tr>" +
                        "</table>" +
                        "<br>" +
                        "Connect your IDE to start using Smart Commit Cloud." +
                        "</div>" +
                        "</html>"
                )
                cell(bodyLabel)
            }
        }.apply {
            preferredSize = Dimension(460, 320)
            border = EmptyBorder(0, 4, 0, 4)
        }
    }

    /**
     * Create the branded gradient header with plugin name.
     */
    private fun createHeaderPanel(): JPanel {
        return object : JPanel() {
            init {
                preferredSize = Dimension(0, 70)
                isOpaque = false
            }

            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

                // Gradient background
                val gradient = GradientPaint(
                    0f, 0f, Color(37, 99, 235),     // #2563eb
                    width.toFloat(), 0f, Color(124, 58, 237) // #7c3aed
                )
                g2.paint = gradient
                g2.fillRoundRect(0, 0, width, height, 12, 12)

                // Lightning bolt icon (simple)
                g2.color = Color.WHITE
                g2.font = Font("Dialog", Font.PLAIN, 28)
                g2.drawString("\u26A1", 20, 46)

                // Title text
                g2.font = Font("Dialog", Font.BOLD, 22)
                g2.drawString("Smart Commit Cloud", 56, 44)
            }
        }
    }

    /**
     * Add a custom "Use OpenAI instead" button alongside OK and Cancel.
     */
    override fun createActions(): Array<Action> {
        val openAiAction = DialogWrapperExitAction("Use OpenAI instead", USE_OPENAI_EXIT_CODE)
        return arrayOf(okAction, openAiAction, cancelAction)
    }
}
