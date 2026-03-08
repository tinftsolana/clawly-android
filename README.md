# Clawly - AI-Powered DeFi Assistant for Solana

Clawly is a native Android AI chat assistant that lets users manage DeFi entirely through natural conversation. Powered by [OpenClaw AI](http://x.com/clawly_agent), it brings the full Solana DeFi experience into a single chat interface.

## Pitch and Demo

Check out our pitch deck and demo video:
[Google Drive - Pitch + Demo](https://drive.google.com/drive/folders/1_rjPqbfuYsWEzLwiCM0_94G4-IVC0qai?usp=sharing)

Follow us on X: [@clawly_agent](http://x.com/clawly_agent)

## What It Does

Instead of switching between multiple DeFi apps, users simply tell Clawly what they want in plain language:

- **Send tokens** - "Send 2 SOL to Alex"
- **Swap** - "Swap 10 USDC to SOL"
- **Lend** - "Lend 500 USDT on Kamino"
- **Borrow** - "Borrow SOL against my USDC"
- **Stake** - "Stake my SOL for best yield"
- **DCA** - "DCA $50 into SOL weekly"
- **Portfolio** - "Show my portfolio and balances"

Clawly generates the transaction, presents it for review in the chat, and the user signs it directly with their Solana wallet — all without leaving the conversation.

## How It Works

### Architecture

1. **User sends a message** - Natural language request goes to the OpenClaw AI backend
2. **AI processes the intent** - OpenClaw understands the DeFi action, builds the unsigned transaction on the server
3. **In-chat transaction signing** - The transaction appears as an inline message bubble with transaction details (from, to, tx hash) and Sign/Reject buttons
4. **Mobile Wallet Adapter** - On sign, the app uses Solana Mobile Wallet Adapter (MWA) to sign the transaction with the user's wallet (Phantom, Solflare, etc.)
5. **On-chain confirmation** - The signed transaction is submitted and Clawly polls for confirmation, showing success with a Solscan link or failure reason

### Per-User AI Infrastructure

Each wallet gets its own isolated OpenClaw instance spun up on the backend. This ensures privacy, personalized context, and secure transaction handling.

### Authentication

The app uses Sign-In with Solana (SIWS) — users connect their wallet and sign a message to authenticate. No emails, no passwords.

## Build Flavors

The app supports two build flavors:

- **web3** - Solana wallet authentication, credit-based usage, DeFi features
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
- **WebSocket** - Real-time gateway connection for chat streaming and sign requests
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
