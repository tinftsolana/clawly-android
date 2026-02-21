package ai.clawly.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * OpenClaw Design System - Hacker-Premium Aesthetic
 * Matches iOS Theme.swift exactly
 */
object ClawlyColors {
    // Primary Background
    val background = Color(0xFF0A0A0C)           // near-black, main app bg
    val surface = Color(0xFF111114)               // card/container bg
    val surfaceElevated = Color(0xFF1A1A1E)       // elevated cards, input fields
    val surfaceBorder = Color(0xFF2A2A2E)         // subtle borders

    // Accent — Claude Coral (Anthropic brand color)
    val accentPrimary = Color(0xFFDA7756)         // Claude coral, CTAs
    val accentPrimaryHover = Color(0xFFE08B6A)    // hover/pressed state
    val accentGlow = Color(0x26DA7756)            // subtle glow (15% opacity)

    // Secondary Accent — Terminal Green (hacker aesthetic)
    val terminalGreen = Color(0xFF4ADE80)         // success states, "connected"
    val terminalGreenDim = Color(0x1A4ADE80)      // green glow bg (10% opacity)

    // Text
    val textPrimary = Color(0xFFFFFFFF)           // headings, primary text
    val secondaryText = Color(0xFFA1A1AA)         // body text, descriptions
    val textTertiary = Color(0xFF52525B)          // placeholders, timestamps
    val textMuted = Color(0xFF3F3F46)             // disabled states

    // Semantic
    val error = Color(0xFFEF4444)
    val warning = Color(0xFFF59E0B)
    val success = Color(0xFF4ADE80)
    val info = Color(0xFF3B82F6)

    // Chat Bubbles
    val bubbleUser = Color(0xFF1E293B)            // user message bg
    val bubbleAssistant = Color(0xFF18181B)       // assistant message bg
    val bubbleAssistantBorder = Color(0xFF27272A) // 1px border

    // Code blocks (markdown)
    val codeBackground = Color(0xFF1E1E2E)        // dark purple-ish bg for code blocks
    val codeText = Color(0xFFE2E8F0)              // light gray text in code
    val inlineCodeBackground = Color(0xFF2D2D3D) // inline `code` background
    val codeKeyword = Color(0xFFFF79C6)           // pink for keywords
    val codeString = Color(0xFFF1FA8C)            // yellow for strings
    val codeComment = Color(0xFF6272A4)           // muted blue for comments

    // Legacy/aliases
    val accentSecondary = Color(0xFFFFEB3B)       // Yellow
    val buttonDisabled = Color(0xFF3F3F46)
}

object ClawlySpacing {
    val xs = 4
    val sm = 8
    val md = 12
    val lg = 16
    val xl = 20
    val xxl = 24
    val xxxl = 32
}

object ClawlyRadius {
    val sm = 8    // small buttons, tags
    val md = 12   // cards, inputs
    val lg = 16   // large cards, modals
    val xl = 20   // chat bubbles
    val full = 9999 // pills, avatars
}
