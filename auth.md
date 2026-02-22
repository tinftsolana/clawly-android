# Auth API

## POST /auth/login

Аутентифицирует пользователя по Firebase ID Token. Если передан заголовок `X-User-Id`, гостевой аккаунт привязывается к Firebase-аккаунту.

### Headers

| Header | Required | Description |
|---|---|---|
| `Authorization` | yes | `Bearer <firebase_id_token>` |
| `X-User-Id` | no | ID гостевого пользователя для привязки |

### Response

`200 OK` — пустое тело.

### Errors

| Code | Error | Description |
|---|---|---|
| 401 | `unauthorized` | Нет или неверный формат заголовка Authorization |
| 401 | `invalid_token` | Firebase токен невалиден или истёк |
| 503 | `firebase_not_configured` | Firebase не настроен на сервере |

---

## Сценарии

### Сценарий 1: пользователь сразу логинится через Firebase

Пользователь открывает приложение и сразу проходит Apple/Google Sign-In, без гостевого режима.

**Шаг 1.** Получить Firebase ID Token после Sign-In.

**Шаг 2.** Вызвать `/auth/login` — сервер создаст пользователя (или найдёт существующего по Firebase UID).

```http
POST /auth/login
Authorization: Bearer <firebase_id_token>
```

**Шаг 3.** Для всех последующих запросов к API передавать `Authorization: Bearer <firebase_id_token>`.

```http
POST /v1/chat/completions
Authorization: Bearer <firebase_id_token>
```

---

### Сценарий 2: пользователь был гостем, потом авторизовался

Пользователь начал работу как гость (с локальным `X-User-Id`), накопил историю, затем решил войти через Apple/Google Sign-In.

**Шаг 1.** При первом запуске сгенерировать и сохранить локально случайный `guestUserId` (например, UUID). Использовать его во всех запросах как `X-User-Id`.

```http
POST /v1/chat/completions
X-User-Id: guest_xyz789
```

**Шаг 2.** Когда пользователь прошёл Sign-In — получить Firebase ID Token.

**Шаг 3.** Вызвать `/auth/login` с обоими заголовками. Сервер привяжет гостевой аккаунт к Firebase-аккаунту, и все данные гостя сохранятся.

```http
POST /auth/login
Authorization: Bearer <firebase_id_token>
X-User-Id: guest_xyz789
```

**Шаг 4.** Больше не использовать `X-User-Id`. Для всех последующих запросов использовать только `Authorization: Bearer <firebase_id_token>`.

```http
POST /v1/chat/completions
Authorization: Bearer <firebase_id_token>
```
