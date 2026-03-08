# Clawly - Android Client for OpenClaw AI

Clawly is a native Android client for [OpenClaw](http://x.com/clawly_agent) — an open-source AI infrastructure that spins up isolated AI instances for each user. Built on top of OpenClaw, Clawly brings the full Solana DeFi experience into a single chat interface.

## Pitch and Demo

Check out our pitch deck and demo video:
[Google Drive - Pitch + Demo](https://drive.google.com/drive/folders/1_rjPqbfuYsWEzLwiCM0_94G4-IVC0qai?usp=sharing)

Follow us on X: [@clawly_agent](http://x.com/clawly_agent)

## Built on OpenClaw

[OpenClaw](http://x.com/clawly_agent) is the backbone of Clawly. It provides:

- **Per-user AI instances** - Each wallet gets its own isolated OpenClaw instance spun up on the backend, ensuring privacy and personalized context
- **DeFi transaction building** - OpenClaw understands natural language DeFi intents and builds unsigned Solana transactions
- **Tool orchestration** - OpenClaw connects to Jupiter, Kamino, Marinade, MarginFi and other Solana protocols to execute user requests
- **Real-time communication** - WebSocket gateway for streaming responses and sign request delivery

Clawly is a thin Android client — all the intelligence lives in OpenClaw.

## What It Does

Instead of switching between multiple DeFi apps, users simply tell Clawly what they want in plain language:

- **Send tokens** - "Send 2 SOL to Alex"
- **Swap** - "Swap 10 USDC to SOL"
- **Lend** - "Lend 500 USDT on Kamino"
- **Borrow** - "Borrow SOL against my USDC"
- **Stake** - "Stake my SOL for best yield"
- **DCA** - "DCA $50 into SOL weekly"
- **Portfolio** - "Show my portfolio and balances"

OpenClaw generates the transaction, Clawly presents it for review in the chat, and the user signs it directly with their Solana wallet — all without leaving the conversation.

## How It Works

### Architecture

1. **User sends a message** - Natural language request goes to the user's OpenClaw instance
2. **OpenClaw processes the intent** - Understands the DeFi action, connects to the right protocol, builds the unsigned transaction
3. **In-chat transaction signing** - The transaction appears as an inline message bubble with details (from, to, tx hash) and Sign/Reject buttons
4. **Mobile Wallet Adapter** - On sign, Clawly uses Solana MWA to sign the transaction with the user's wallet (Phantom, Solflare, etc.)
5. **On-chain confirmation** - The signed transaction is submitted and Clawly polls for confirmation, showing success with a Solscan link or failure reason

### Authentication

The app uses Sign-In with Solana (SIWS) — users connect their wallet and sign a message to authenticate. No emails, no passwords. OpenClaw provisions a dedicated instance for each authenticated wallet.

## Build Flavors

The app supports two build flavors:

- **web3** - Solana wallet authentication, credit-based usage, full DeFi features powered by OpenClaw
- **web2** - Traditional subscription-based (RevenueCat), general AI assistant

### Build Commands

```bash
# Web3 build (wallet auth + DeFi)
./gradlew :app:assembleWeb3Debug

# Web2 build (subscription auth)
./gradlew :app:assembleWeb2Debug
```

## Tech Stack

- **Kotlin** + **Jetpack Compose** - Modern Android UI
- **Hilt** - Dependency injection
- **Solana Mobile Wallet Adapter** - Wallet connection and transaction signing
- **WebSocket** - Real-time gateway connection to OpenClaw for chat streaming and sign requests
- **OkHttp** - Networking and WebSocket client
- **DataStore** - Local persistence for wallet and session data

## Project Structure

```
app/src/main/java/ai/clawly/app/
├── data/               # Repository implementations, API services, gateway
├── domain/             # Use cases, repository interfaces, models
├── navigation/         # Nav routes and NavHost
├── presentation/
│   ├── chat/           # Chat screen, ViewModel, message components
│   ├── onboarding/     # Onboarding flow
│   ├── paywall/        # Web2 (subscription) and Web3 (credits) paywalls
│   ├── settings/       # Settings, auth provider config
│   └── wallet/         # Wallet connection UI
└── ui/theme/           # Colors, typography, theme
```

## License

Proprietary. All rights reserved.
