# OpenClaw Control Plane API

This service is a small Fastify HTTP API that manages tenant "instances" (one VM/microVM per tenant).

Runtime mode:

- `CONTROL_PLANE_MODE=mongo`: persistent tenants in MongoDB + provisioning via BullMQ worker.
- `CONTROL_PLANE_MODE=memory`: in-memory tenants map (dev/smoke testing only, state lost on restart).

Note: `/instances/*` endpoints are aliases for client apps and mirror the `/tenants/*` behavior.

## Base URL

- Local default: `http://127.0.0.1:3000`

## Authentication

Protected endpoints accept two auth methods (checked in priority order):

### 1. Firebase Auth (primary)

Pass a Firebase ID Token obtained after Apple/Google Sign-In:

```
Authorization: Bearer <firebase_id_token>
```

The user is automatically created in MongoDB on first login (find-or-create by Firebase UID). Firebase must be configured via `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, `FIREBASE_PRIVATE_KEY` env vars (Firebase Console → Project Settings → Service Accounts → Generate new private key).

Errors:

- 401: `{ "error": "unauthorized" }` — no auth provided
- 401: `{ "error": "invalid_token" }` — token invalid or expired
- 503: `{ "error": "firebase_not_configured" }` — Firebase env vars not set

### 2. Legacy X-User-Id (dev / CLI tools)

```
X-User-Id: <userId>
```

Use any stable non-empty `userId` from the client (UUID recommended). The backend auto-creates/fetches the user by this id.

`POST /users` still exists only as a deprecated legacy helper.

Errors:

- 401: `{ "error": "unauthorized" }` — no Bearer token and no `X-User-Id`

## Subscription check

When `REVENUECAT_API_KEY` and `REVENUECAT_PROJECT_ID` are set, `POST /tenants` (and its alias `POST /instances`) verifies that the user has an active RevenueCat subscription before provisioning. The `userId` is used as the RevenueCat `app_user_id`.

If the subscription is not active:

- 403: `{ "error": "subscription_required" }`

### Debug bypass

Set `REVENUECAT_BYPASS_TOKEN` in env to enable bypass. Pass the token in the `X-Bypass-Token` header to skip the subscription check:

```
X-Bypass-Token: <REVENUECAT_BYPASS_TOKEN>
```

The bypass is completely disabled when `REVENUECAT_BYPASS_TOKEN` is not set.

## Models

Dates in responses are `Date` objects in code and are serialized to JSON strings (ISO 8601) over HTTP.

### User

```ts
type UserResponse = {
  userId: string;
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
};
```

### TenantStatus

```ts
type TenantStatus =
  | "queued"
  | "provisioning"
  | "installing"
  | "ready"
  | "failed"
  | "suspended";
```

### TenantInstance

Provider-specific fields are optional and depend on the provisioner.

```ts
type TenantInstance = {
  provider?: "hetzner" | "fly";

  // Hetzner
  serverId?: number;
  serverName?: string;
  vmIp?: string;
  sshUser?: string;
  gatewayPort?: number;
  gatewayUrl?: string;   // typically: wss://<host>
  gatewayToken?: string; // shared secret for gateway

  // Fly.io
  flyAppName?: string;
  flyMachineId?: string;
  flyVolumeId?: string;
};
```

### CreateTenantResponse (201)

```ts
type CreateTenantResponse = {
  tenantId: string;
  status: "queued";
};
```

### GetTenantResponse (200)

```ts
type GetTenantResponse = {
  tenantId: string;
  userId?: string;
  status: TenantStatus;
  lastError?: string;
  instance?: TenantInstance;
  createdAt: string; // ISO 8601
  updatedAt: string; // ISO 8601
};
```

### ErrorResponse

Most errors are returned as:

```ts
type ErrorResponse = { error: string };
```

Common values:

- `not_found`
- `user_not_found`
- `missing_x_user_id`
- `invalid_user_id`
- `forbidden` — tenant does not belong to the user
- `subscription_required` — no active RevenueCat subscription
- `missing_gateway_url`
- `missing_FLY_API_TOKEN`, `missing_HETZNER_API_TOKEN` (mostly on cleanup)

## Endpoints

### GET /health

Health check.

Response (200):

```json
{ "ok": true }
```

Implementation: `src/api/routes/health.ts`

### POST /users

Deprecated legacy helper. Creates a new random user id and returns it.

For guest flow, this endpoint is not required: send your own stable `X-User-Id`.

Request body: none required.

Response (201):

```json
{ "userId": "..." }
```

Example:

```bash
curl -X POST http://127.0.0.1:3000/users
```

Implementation: `src/api/routes/users.ts`

### GET /users/:userId

Get user info including current credit balance.

Response (200):

```json
{
  "userId": "abc123",
  "createdAt": "2024-01-15T10:00:00.000Z",
  "updatedAt": "2024-01-15T10:00:00.000Z",
  "credits": 1000000000
}
```

Errors:

- 404: `{ "error": "not_found" }`

Example:

```bash
curl http://127.0.0.1:3000/users/<userId>
```

Implementation: `src/api/routes/users.ts`

### GET /me

Get current authenticated user info. Requires Firebase Auth token.

Response (200): `UserResponse`

```bash
curl http://127.0.0.1:3000/me \
  -H 'Authorization: Bearer <firebase_id_token>'
```

### GET /me/tenants

Get current user's tenants. Requires Firebase Auth token.

Response (200): `GetTenantResponse[]`

```bash
curl http://127.0.0.1:3000/me/tenants \
  -H 'Authorization: Bearer <firebase_id_token>'
```

### POST /me/sync-purchases

Sync RevenueCat purchases and top up credits. Call this after a successful in-app purchase.

Requires auth (same as other protected endpoints):

- Firebase: `Authorization: Bearer <firebase_id_token>`
- Guest: `X-User-Id: <userId>` (for guest flow, keep using the same `userId`)

Also requires `REVENUECAT_API_KEY` and `REVENUECAT_PROJECT_ID` env vars.

Response (200):

```json
{ "credits": 1000000000, "added": 1000000000, "newPurchases": 1 }
```

Errors:

- 503: `{ "error": "revenuecat_not_configured" }` — RevenueCat env vars not set
- 502: upstream RevenueCat error

Example:

```bash
# Firebase user
curl -X POST http://127.0.0.1:3000/me/sync-purchases \
  -H 'Authorization: Bearer <firebase_id_token>'

# Guest user
curl -X POST http://127.0.0.1:3000/me/sync-purchases \
  -H 'x-user-id: <userId>'
```

Implementation: `src/api/routes/users.ts`

### GET /users/:userId/tenants

List all tenants belonging to a user, sorted by creation date (newest first).

Response (200): `GetTenantResponse[]`

Errors:

- 404: `{ "error": "user_not_found" }`

Example:

```bash
curl http://127.0.0.1:3000/users/<userId>/tenants
```

Implementation: `src/api/routes/users.ts`

### POST /tenants

Create a tenant and start provisioning.

Requires `X-User-Id` header. The user must exist. If RevenueCat is configured, the user must have an active subscription.

Request body:

```json
{}
```

Response (201): `CreateTenantResponse`

Errors:

- 403: `{ "error": "subscription_required" }` — no active subscription

Example:

```bash
curl -X POST http://127.0.0.1:3000/tenants \
  -H 'content-type: application/json' \
  -H 'x-user-id: <userId>' \
  -d '{}'
```

With bypass (debug only):

```bash
curl -X POST http://127.0.0.1:3000/tenants \
  -H 'content-type: application/json' \
  -H 'x-user-id: <userId>' \
  -H 'x-bypass-token: <REVENUECAT_BYPASS_TOKEN>' \
  -d '{}'
```

Implementation: `src/api/routes/tenants.ts`

### POST /instances

Alias for `POST /tenants`.

Request/response are identical. Requires `X-User-Id` header.

Example:

```bash
curl -X POST http://127.0.0.1:3000/instances \
  -H 'content-type: application/json' \
  -H 'x-user-id: <userId>' \
  -d '{}'
```

Implementation: `src/api/routes/tenants.ts`

### GET /tenants/:tenantId

Get tenant status.

Response (200): `GetTenantResponse`

Errors:

- 404: `{ "error": "not_found" }`

Example:

```bash
curl http://127.0.0.1:3000/tenants/<tenantId>
```

Implementation: `src/api/routes/tenants.ts`

### GET /instances/:tenantId

Alias for `GET /tenants/:tenantId`.

Example:

```bash
curl http://127.0.0.1:3000/instances/<tenantId>
```

Implementation: `src/api/routes/tenants.ts`

### POST /instances/:tenantId/refresh

Manual readiness refresh. Probes the gateway and updates tenant status to `ready` if reachable.

Request body:

Some HTTP clients require a JSON body for POST. `-d '{}'` is safe.

```json
{}
```

Response:

- 200: same as `GET /tenants/:tenantId`

Errors:

- 400: `{ "error": "missing_gateway_url" }`
- 404: `{ "error": "not_found" }`

Example:

```bash
curl -X POST http://127.0.0.1:3000/instances/<tenantId>/refresh \
  -H 'content-type: application/json' \
  -d '{}'
```

Implementation: `src/api/routes/tenants.ts`

### DELETE /instances/:tenantId

Cleanup endpoint. Requires `X-User-Id` header — only the tenant owner can delete.

Deletes provisioned resources (Fly app / Hetzner server) when possible and removes the tenant record.

Response:

- 204: empty body

Errors:

- 400: `{ "error": "missing_x_user_id" }`
- 403: `{ "error": "forbidden" }` — tenant belongs to a different user
- 404: `{ "error": "not_found" }`
- 500: `{ "error": "missing_FLY_API_TOKEN" }` or `{ "error": "missing_HETZNER_API_TOKEN" }` if cleanup needs credentials

Example:

```bash
curl -X DELETE http://127.0.0.1:3000/instances/<tenantId> \
  -H 'x-user-id: <userId>'
```

Implementation: `src/api/routes/tenants.ts`

## Provider Auth

These endpoints configure AI model providers for a tenant instance. All require the instance to be provisioned on Fly.io.

### POST /instances/:tenantId/auth/openai/start

Initiate OpenAI OAuth (PKCE) flow.

Response (200):

```json
{ "authUrl": "https://auth.openai.com/oauth/authorize?..." }
```

Redirect the user to `authUrl`. After authorization, pass the callback URL to `/auth/openai/callback`.

### POST /instances/:tenantId/auth/openai/callback

Complete OpenAI OAuth flow. Exchanges the code, deploys tokens to the VM.

Request body:

```json
{ "callbackUrl": "http://localhost:1455/auth/callback?code=...&state=..." }
```

Response (200):

```json
{ "ok": true, "provider": "openai" }
```

Errors:

- 400: `{ "error": "oauth_state_not_found_or_expired" }`
- 400: `{ "error": "oauth_state_tenant_mismatch" }`
- 400: `{ "error": "instance_not_fly" }`

### POST /instances/:tenantId/auth/anthropic/key

Deploy an Anthropic API key (`sk-ant-...`) to the VM as `ANTHROPIC_API_KEY`.

Request body:

```json
{ "apiKey": "sk-ant-..." }
```

Response (200):

```json
{ "ok": true, "provider": "anthropic" }
```

Errors:

- 400: `{ "error": "invalid_anthropic_api_key" }`

### POST /instances/:tenantId/auth/anthropic/setup-token

Deploy an Anthropic org setup token (`sk-ant-oat01-...`) to the VM. Restarts the VM after deployment.

Request body:

```json
{ "setupToken": "sk-ant-oat01-..." }
```

Response (200):

```json
{ "ok": true, "provider": "anthropic" }
```

Errors:

- 400: `{ "error": "invalid_anthropic_setup_token" }`

### POST /instances/:tenantId/auth/glm/key

Deploy a GLM (Zhipu AI) API key to the VM as `GLM_API_KEY`.

Request body:

```json
{ "apiKey": "<glm-api-key>" }
```

Response (200):

```json
{ "ok": true, "provider": "glm" }
```

Errors:

- 400: `{ "error": "invalid_glm_api_key" }`

### POST /instances/:tenantId/auth/minimax/key

Deploy a MiniMax API key to the VM as `MINIMAX_API_KEY`.

Request body:

```json
{ "apiKey": "<minimax-api-key>" }
```

Response (200):

```json
{ "ok": true, "provider": "minimax" }
```

Errors:

- 400: `{ "error": "invalid_minimax_api_key" }`

### POST /instances/:tenantId/auth/openrouter/key

Deploy an OpenRouter API key (`sk-or-...`) to the VM as `OPENROUTER_API_KEY`.

Request body:

```json
{ "apiKey": "sk-or-..." }
```

Response (200):

```json
{ "ok": true, "provider": "openrouter" }
```

Errors:

- 400: `{ "error": "invalid_openrouter_api_key" }`

### POST /instances/:tenantId/auth/openclaw/activate

Generate an `oclaw_*` proxy token, store it in DB, deploy it to the VM as `OPENCLAW_PROXY_API_KEY`, and patch `openclaw.json` on the VM to use the OpenClaw proxy as the default model provider.

Requires `OPENCLAW_PROXY_BASE_URL` env var to be set to the public proxy URL.

Response (200):

```json
{ "ok": true, "provider": "openclaw" }
```

Errors:

- 400: `{ "error": "instance_not_fly" }`
- 500: `{ "error": "missing_FLY_API_TOKEN" }`
- 502: exec error from VM

## OpenClaw Proxy

The proxy exposes an OpenAI-compatible API. Requests are authenticated with the `oclaw_*` token generated by `/auth/openclaw/activate`. Credits are checked per request and debited based on token usage.

### GET /v1/models

List supported models.

Response (200):

```json
{
  "object": "list",
  "data": [
    { "id": "gpt-4o-mini", "object": "model" },
    { "id": "gpt-4o", "object": "model" },
    { "id": "o1-mini", "object": "model" },
    { "id": "o3-mini", "object": "model" }
  ]
}
```

### POST /v1/chat/completions

Proxy a chat completion request to OpenAI. Supports streaming (`"stream": true`).

```
Authorization: Bearer oclaw_<token>
```

Request body: standard OpenAI chat completions format.

Response: standard OpenAI chat completions response (or SSE stream).

Errors:

- 401: `{ "error": { "message": "Invalid token" } }` — missing or malformed token
- 401: `{ "error": { "message": "Token not found" } }` — token not in DB
- 402: `{ "error": { "code": "insufficient_credits" } }` — zero credits
- 503: `{ "error": { "message": "Proxy not configured" } }` — `OPENAI_MASTER_API_KEY` not set

Example:

```bash
curl http://127.0.0.1:3000/v1/chat/completions \
  -H 'Authorization: Bearer oclaw_<token>' \
  -H 'Content-Type: application/json' \
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"hello"}]}'
```

## Dev Endpoints

Available only when `NODE_ENV != production`.

### POST /dev/users/:userId/add-credits

Add credits to a user directly (bypasses RevenueCat).

Request body:

```json
{ "amount": 1000000000 }
```

Response (200):

```json
{ "userId": "...", "credits": 1000000000 }
```

### POST /dev/tenants/:tenantId/setup-openclaw

Generate and save an `oclaw_*` proxy token for a tenant without touching a real Fly machine. Useful for local proxy testing.

Response (200):

```json
{ "ok": true, "proxyToken": "oclaw_..." }
```

## Typical Flow

```bash
# 1. Pick a stable guest user id (persist it on client side)
USER_ID="guest_$(uuidgen | tr '[:upper:]' '[:lower:]')"

# 2. Create an instance
TENANT_ID=$(curl -s -X POST http://127.0.0.1:3000/instances \
  -H 'content-type: application/json' \
  -H "x-user-id: $USER_ID" \
  -d '{}' | jq -r .tenantId)

# 3. Poll for ready
curl -s http://127.0.0.1:3000/instances/$TENANT_ID | jq

# 4. List user's instances
curl -s http://127.0.0.1:3000/users/$USER_ID/tenants | jq

# 5. Cleanup
curl -X DELETE http://127.0.0.1:3000/instances/$TENANT_ID \
  -H "x-user-id: $USER_ID"
```
