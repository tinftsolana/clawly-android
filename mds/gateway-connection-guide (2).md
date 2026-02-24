# Gateway Connection Guide

Два кейса подключения к OpenClaw Gateway:

- **Self-hosted (без паиринга)** — ты сам поднимаешь гейтвей, знаешь токен, паиринг не нужен
- **Managed (с паирингом)** — гейтвей на облачном провайдере, первое подключение требует одобрения

---

## Содержание

1. [Протокол: JSON-RPC over WebSocket](#протокол-json-rpc-over-websocket)
2. [Кейс 1: Self-hosted (без паиринга)](#кейс-1-self-hosted-без-паиринга)
3. [Кейс 2: Managed с паирингом](#кейс-2-managed-с-паирингом)
4. [RPC: chat.send и события чата](#rpc-chatsend-и-события-чата)
5. [Таймауты и ошибки](#таймауты-и-ошибки)

---

## Протокол: JSON-RPC over WebSocket

Все сообщения — JSON через WebSocket. Три типа:

| `type` | Направление | Описание |
|--------|-------------|----------|
| `req` | Клиент → Гейтвей | Запрос с `id`, `method`, `params` |
| `res` | Гейтвей → Клиент | Ответ с тем же `id`, полями `ok` + `payload` или `error` |
| `event` | Гейтвей → Клиент | Одностороннее событие: `event`, `payload` |

`id` в запросе — случайная строка (hex/base36). По ней матчишь запрос с ответом.

**Важно:** перед любым RPC гейтвей шлёт событие `connect.challenge` — это сигнал, что можно отправлять `connect`.

---

## Кейс 1: Self-hosted (без паиринга)

### Когда применяется

- Ты сам поднял OpenClaw Gateway (Docker-образ) на своём сервере
- У тебя уже есть `gatewayUrl` и `gatewayToken`
- Контрол-плейн не используется
- Паиринг либо отключён на гейтвее, либо девайс уже одобрен

### Что нужно

| Параметр | Откуда |
|----------|--------|
| `gatewayUrl` | URL твоего гейтвея, например `wss://your-server.example.com` |
| `gatewayToken` | Токен из конфига гейтвея (`.env` на сервере) |

### Флоу

```
1. Открыть WebSocket → gatewayUrl
2. Дождаться события connect.challenge
3. Отправить connect RPC с gatewayToken
4. Получить ok: true → подключение установлено
5. Отправлять chat.send, получать chat events
```

### Шаг 1: Открыть WebSocket

```
WS → wss://your-server.example.com
Origin: https://your-server.example.com
```

Если URL начинается с `http://` или `https://` — замени схему на `ws://` / `wss://`.
`Origin` строится из хоста: `wss:` → `https://host`, `ws:` → `http://host`.

### Шаг 2: Дождаться connect.challenge

Гейтвей пришлёт:

```json
{
  "type": "event",
  "event": "connect.challenge",
  "payload": { "nonce": "..." }
}
```

Это сигнал — можно делать `connect`.

### Шаг 3: Отправить connect

Режим аутентификации: **`tokenOnly`** — поле `device` не передаётся.

```json
{
  "type": "req",
  "id": "a1b2c3d4e5f6",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 3,
    "client": {
      "id": "my-app",
      "version": "1.0.0",
      "platform": "ios",
      "mode": "webchat",
      "instanceId": "clawdbot"
    },
    "role": "operator",
    "scopes": ["operator.admin", "operator.approvals", "operator.pairing", "operator.write"],
    "caps": [],
    "auth": { "token": "<gatewayToken>" },
    "userAgent": "my-app/1.0",
    "locale": "ru-RU"
  }
}
```

Поля `device` нет — это и есть отличие от managed режима.

Приоритет токена: `gatewayToken` → `deviceToken` (если кешировался).

| Поле | Описание |
|------|----------|
| `client.instanceId` | Любая фиксированная строка, можно `"main"` |
| `auth.token` | `gatewayToken` (приоритет) или `deviceToken` |
| `device` | **Отсутствует** |

### Шаг 4: Ответ гейтвея

**Успех:**

```json
{
  "type": "res",
  "id": "a1b2c3d4e5f6",
  "ok": true,
  "payload": {
    "sessionKey": "agent:main:stable-device-uuid",
    "deviceToken": "dev_xxxxxxxxxxxxxxxx"
  }
}
```

→ Сохрани `deviceToken` в secure storage — используй его при следующих подключениях вместо `gatewayToken`.

**Ошибка (например, неверный токен):**

```json
{
  "type": "res",
  "id": "a1b2c3d4e5f6",
  "ok": false,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid token"
  }
}
```

### sessionKey

`sessionKey` — идентификатор сессии агента, нужен для `chat.send`. Формат: `agent:main:<instanceId>`.

Можно использовать любое фиксированное значение, например `agent:main:main` для CLI-клиентов.

---

## Кейс 2: Managed с паирингом

### Когда применяется

- Гейтвей создаётся через контрол-плейн (`POST /instances`)
- Первое подключение девайса требует одобрения
- После одобрения — работает как self-hosted

### Что нужно

| Параметр | Откуда |
|----------|--------|
| `tenantId` | Из ответа `POST /instances` |
| `gatewayUrl` | Из `GET /instances/:tenantId` после `status=ready` |
| `gatewayToken` | Из того же ответа |
| `instanceId` | Генерируется клиентом, хранится постоянно |

### instanceId — правила

- Генерируй один раз: `UUID v4`
- Храни в secure storage: iOS Keychain, Android EncryptedSharedPreferences, Desktop — файл конфига
- **Никогда не меняй** — каждый новый `instanceId` = новый запрос на паиринг
- Используй один `instanceId` на одно устройство/установку

### Флоу целиком

```
1. POST /instances → tenantId
2. GET /instances/:tenantId (поллинг) → status=ready, gatewayUrl, gatewayToken
3. WebSocket → gatewayUrl
4. connect.challenge → connect RPC
5. Ответ: NOT_PAIRED → requestId
6. POST /instances/:tenantId/pairing/devices/:requestId/approve
7. Новый WebSocket → connect RPC
8. Ответ: ok=true → онлайн
9. chat.send
```

### Шаг 1-2: Создание и ожидание инстанса

```
POST /instances
Headers: X-User-Id: <userId>
Body: {}

→ { tenantId: "abc123" }
```

Поллинг статуса каждые 2500 мс:

```
GET /instances/:tenantId

→ { status: "queued" | "provisioning" | "installing" | "ready" | "failed" | "suspended" }
```

Ждать пока `status === "ready"`. Таймаут — 12 минут.

При `ready` в ответе будут `instance.gatewayUrl` и `instance.gatewayToken`.

### Шаг 3-4: Первое WebSocket подключение

Открыть WS, дождаться `connect.challenge`, отправить `connect`.

Режим аутентификации: **`signedDevice`** — отличается от self-hosted наличием поля `device` и приоритетом токена.

```json
{
  "type": "req",
  "id": "a1b2c3d4e5f6",
  "method": "connect",
  "params": {
    "minProtocol": 3,
    "maxProtocol": 3,
    "client": {
      "id": "my-app",
      "version": "1.0.0",
      "platform": "ios",
      "mode": "webchat",
      "instanceId": "clawdbot"
    },
    "role": "operator",
    "scopes": ["operator.admin", "operator.approvals", "operator.pairing", "operator.write"],
    "caps": [],
    "auth": { "token": "<deviceToken или gatewayToken>" },
    "userAgent": "my-app/1.0",
    "locale": "ru-RU",
    "device": {
      "id": "<clientId>",
      "publicKey": "<публичный ключ устройства>",
      "signature": "<подпись>",
      "signedAt": 1700000000000,
      "nonce": "<nonce из connect.challenge>"
    }
  }
}
```

Приоритет токена: `deviceToken` (если есть) → `gatewayToken`.

| Поле | Описание |
|------|----------|
| `auth.token` | `deviceToken` (приоритет) или `gatewayToken` |
| `device` | **Обязателен** — подписанный объект устройства |
| `device.nonce` | Берётся из `connect.challenge` payload |

### Шаг 5: Получение NOT_PAIRED

```json
{
  "type": "res",
  "id": "a1b2c3d4e5f6",
  "ok": false,
  "error": {
    "code": "NOT_PAIRED",
    "requestId": "req_abc123xyz",
    "message": "Device not paired"
  }
}
```

→ Сохрани `requestId`. Закрой WS.

### Шаг 6: Одобрение паиринга

```
POST /instances/:tenantId/pairing/devices/:requestId/approve
Content-Type: application/json
Body: {}

→ { ok: true, approved: true, pairing: { pending: [], approved: [...] } }
```

Опционально — сначала посмотреть ожидающие запросы:

```
GET /instances/:tenantId/pairing/devices

→ { pairing: { pending: [{ requestId, clientId, instanceId, platform, timestamp }], approved: [...] } }
```

Также можно отклонить:

```
POST /instances/:tenantId/pairing/devices/:requestId/reject
```

### Шаг 7-8: Повторное подключение

Открыть новый WebSocket, пройти handshake заново. Теперь гейтвей вернёт `ok: true`.

### Ротация токенов

```
При подключении:
  если есть deviceToken → использовать его в auth.token (приоритет)
  иначе → использовать gatewayToken

Если гейтвей вернул ошибку "token mismatch":
  → удалить deviceToken из storage
  → переподключиться с gatewayToken
```

### Паиринг при уже активном соединении

Пока клиент онлайн, гейтвей может прислать событие о новом устройстве:

```json
{
  "type": "event",
  "event": "device.pair.requested",
  "payload": {
    "requestId": "req_xyz",
    "clientId": "my-app",
    "instanceId": "other-device-uuid",
    "platform": "android",
    "timestamp": "2024-01-15T10:00:00Z"
  }
}
```

→ Можно автоматически одобрить через тот же HTTP-эндпоинт. Текущее соединение разрывать не нужно.

---

## RPC: chat.send и события чата

После успешного `connect` можно отправлять сообщения.

### Запрос chat.send

```json
{
  "type": "req",
  "id": "f1e2d3c4b5a6",
  "method": "chat.send",
  "params": {
    "sessionKey": "agent:main:stable-device-uuid",
    "message": "Привет! Как дела?",
    "idempotencyKey": "unique-key-per-message"
  }
}
```

| Поле | Описание |
|------|----------|
| `sessionKey` | Из ответа `connect` или `agent:main:main` |
| `message` | Текст сообщения |
| `idempotencyKey` | Уникальный ключ запроса, случайная строка |

### Ответ на chat.send

```json
{
  "type": "res",
  "id": "f1e2d3c4b5a6",
  "ok": true,
  "payload": {}
}
```

`ok: true` означает что сообщение принято, не что ответ готов.

### События ответа агента

Ответ приходит через события `chat`:

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "state": "streaming",
    "message": {
      "content": [{ "type": "text", "text": "Привет! " }]
    }
  }
}
```

Когда ответ завершён:

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "state": "final",
    "message": {
      "role": "assistant",
      "content": [{ "type": "text", "text": "Привет! У меня всё хорошо." }]
    }
  }
}
```

Извлечение текста из `final`:

```
text = payload.message.content
  .filter(block => block.type === "text")
  .map(block => block.text)
  .join("")
```

---

## Таймауты и ошибки

### Таймауты

| Операция | Таймаут |
|----------|---------|
| Открытие WebSocket | 15 сек |
| Ожидание `connect.challenge` | 15 сек |
| RPC `connect` | 15 сек |
| RPC `pairing approve` | 20 сек |
| Поллинг статуса инстанса | 12 мин |
| HTTP-проба гейтвея (`/health`) | 5 мин |

### Ретраи

Ретраить только сетевые/транспортные ошибки:

- `gateway_ws_open_timeout` — таймаут открытия WS
- `gateway_ws_closed_before_open` — закрылся до открытия
- `gateway_connect_challenge_timeout` — не пришёл challenge
- `gateway_ws_closed_during_request` — закрылся во время запроса
- `ETIMEDOUT`, `socket hang up`

**Не ретраить** бизнес-ошибки: неверный `requestId`, неверный токен, `NOT_PAIRED`.

Параметры ретрая: 3 попытки, базовая задержка 700 мс (700 → 1400 мс).

### Ошибки HTTP контрол-плейна

| Статус | Ошибка | Причина |
|--------|--------|---------|
| 400 | `missing_gateway_url` | Инстанс ещё не готов |
| 400 | `missing_gateway_token` | Данные инстанса неполные |
| 400 | `pairing_request_failed` | Гейтвей вернул `ok=false` |
| 404 | `not_found` | Неверный `tenantId` |
| 502 | — | Сеть/таймаут/краш гейтвея |

### Условия для разрешения отправки сообщения

Перед `chat.send` все условия должны быть выполнены:

- [ ] Инстанс имеет `status === "ready"`
- [ ] WS соединение в состоянии `online`
- [ ] Паиринг не в процессе разрешения
- [ ] Кредиты есть (если используется proxy)

---

## Сравнение кейсов

| | Self-hosted | Managed |
|--|-------------|---------|
| Режим auth | `tokenOnly` | `signedDevice` |
| Поле `device` в connect | **Отсутствует** | **Присутствует** (с подписью) |
| Приоритет токена | `gatewayToken` → `deviceToken` | `deviceToken` → `gatewayToken` |
| `NOT_PAIRED` ошибка | Auth failure, дисконнект | Запускает pairing flow |
| Откуда `gatewayUrl` | Свой сервер | `GET /instances/:tenantId` |
| Откуда `gatewayToken` | Конфиг гейтвея | `GET /instances/:tenantId` |
| Паиринг | Не требуется | Требуется при первом подключении |
| Контрол-плейн | Не нужен | Нужен |
| `instanceId` | Любая фиксированная строка | Стабильная, хранится в secure storage |
| После первого подключения | Сразу `ok: true` | `NOT_PAIRED` → approve → reconnect |
