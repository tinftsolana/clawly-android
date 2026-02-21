# Clawly - Project Soul

## What is Clawly?

Clawly is a **personal AI assistant** mobile app that connects users to powerful AI models through an intuitive chat interface. Think of it as your pocket-sized AI companion that can help with coding, writing, research, scheduling, and virtually any task you can describe in natural language.

---

## Vision

**"AI for everyone, everywhere."**

Clawly democratizes access to advanced AI capabilities by providing a beautiful, native mobile experience that works seamlessly across iOS and Android. We believe AI assistants should be:

- **Accessible** - Simple enough for anyone to use
- **Powerful** - Connected to the best AI models available
- **Personal** - Customizable to individual needs and workflows
- **Private** - Your conversations stay secure

---

## Core Concept

### The Gateway Architecture

Clawly doesn't run AI locally. Instead, it connects to an **OpenClaw Gateway** - a WebSocket-based backend that:

1. Handles authentication and session management
2. Routes requests to configured AI providers (Claude, GPT, etc.)
3. Streams responses in real-time for instant feedback
4. Manages skills and tool integrations (MCP protocol)

This architecture enables:
- **Managed Hosting** - Users get a provisioned instance (easy onboarding)
- **Self-Hosted** - Power users can connect to their own gateway
- **Flexibility** - Switch AI providers without changing the app

---

## Key Features

### Chat Experience
- Real-time streaming responses with typing indicators
- Markdown rendering for formatted content
- Code syntax highlighting
- Image attachments (up to 4 per message)
- Voice input (speech-to-text)
- Text-to-speech for responses
- Message history persistence

### Thinking Levels
Users can adjust AI response depth:
- **Quick** (Low) - Fast, concise answers
- **Balanced** (Medium) - Default, well-rounded responses
- **Deep** (High) - Thorough analysis, longer processing

### Skills & MCP
- Extensible through MCP (Model Context Protocol)
- Skills add capabilities: web search, code execution, API integrations
- Users can enable/disable skills as needed

### Premium Features
- Unlimited messages (free tier has limits)
- Access to premium AI models
- Priority processing
- Advanced skills

---

## Design Philosophy

### Visual Identity
- **Primary Color**: Pink (#FF86DF) - Playful, approachable
- **Dark Theme First**: Modern, eye-friendly dark interface
- **Clawly Mascot**: Friendly claw character for brand personality
- **Smooth Animations**: Delightful micro-interactions throughout

### UX Principles
1. **Instant Feedback** - Streaming responses, no waiting for full completion
2. **Minimal Friction** - Get chatting within seconds of opening the app
3. **Forgiving** - Easy to retry, abort, or modify requests
4. **Discoverable** - Features reveal themselves naturally

---

## Technical Soul

### Mobile-First
- Native Android (Kotlin/Compose) and iOS (Swift/SwiftUI)
- Optimized for touch, gestures, and mobile workflows
- Works offline for viewing history (online for new messages)

### Real-Time Communication
- WebSocket connection for instant message delivery
- Automatic reconnection with exponential backoff
- Connection status always visible to user

### Security
- Device-based authentication (Curve25519 signing)
- Token-based session management
- Encrypted API key storage
- No message content stored on our servers (for self-hosted)

---

## Target Users

### Primary: Everyday AI Users
- People who want AI help without technical complexity
- Students, professionals, creators, curious minds
- Mobile-first users who prefer apps over web interfaces

### Secondary: Power Users
- Developers who want AI coding assistance on-the-go
- Users with their own AI API keys
- Self-hosters who want full control

---

## What Makes Clawly Special

1. **Native Experience** - Not a web wrapper, truly native apps
2. **Gateway Flexibility** - Use our hosting or bring your own
3. **Skills Ecosystem** - Extensible through MCP protocol
4. **Thoughtful Design** - Every interaction carefully crafted
5. **Transparent Pricing** - Clear free tier, fair premium pricing

---

## The Name

**Clawly** = Claw + Friendly

The claw represents:
- Grabbing knowledge and answers
- Reaching out to help
- Playful, non-threatening AI persona

---

## Success Metrics

We know we're succeeding when:
- Users open Clawly daily as their go-to AI tool
- Messages sent per session increases over time
- Users recommend Clawly to friends
- Premium conversion comes from genuine value, not paywalls

---

## Future Direction

- **Multi-modal** - Image generation, vision analysis
- **Agents** - Complex multi-step task automation
- **Integrations** - Calendar, email, productivity tools
- **Collaboration** - Shared conversations and workspaces
- **Voice-First Mode** - Hands-free AI interaction

---

## Guiding Principles for Development

1. **User First** - Every feature should serve a real user need
2. **Performance Matters** - Fast, smooth, responsive always
3. **Design with Intent** - No arbitrary UI decisions
4. **Test on Device** - Simulators lie, real devices tell truth
5. **Iterate Quickly** - Ship, learn, improve

---

*"Clawly is not just an app - it's your AI companion that fits in your pocket and is always ready to help."*
