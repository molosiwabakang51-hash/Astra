# Astra private AI gateway

This backend is an **optional owner-operated service** for Astra's Android app. The Android app connects to this service over HTTPS. The gateway authenticates the request, applies a per-process quota, calls the OpenAI Responses API, then returns JSON of the form:

```json
{"reply":"Hello from Astra."}
```

It is **not a public, hosted AI service**. No model keys or gateway tokens belong in the repository or inside the APK. A ChatGPT Plus/Pro subscription does not include OpenAI API usage; API billing is separate.

## Requirements

- Node.js 20+ (the CI uses Node.js 22)
- An OpenAI API project/key and API spending limits
- A **cryptographically random** client access token (32+ bytes)
- A server under your control with an **HTTPS endpoint** and TLS reverse proxy

## Local setup

Generate a private token on your own computer:

```sh
openssl rand -hex 32
```

Place the generated value in the backend environment as `ASTRA_CLIENT_TOKEN`. Set `OPENAI_API_KEY` separately. Never commit either value or share them in screenshots or chat.

```sh
cd gateway
export ASTRA_CLIENT_TOKEN='<64-character-random-hex-value>'
export OPENAI_API_KEY='<your-provider-key>'
export OPENAI_MODEL='gpt-4.1-mini'
npm test
npm start
```

The server listens on `127.0.0.1:8080` by default. It intentionally does **not** expose itself to your network automatically.

## Deployment

1. Deploy this service onto a private server, behind a TLS termination reverse proxy with a valid certificate and a domain name. Configure your proxy to forward **only** `POST /chat` (and optionally `GET /health`) to `127.0.0.1:8080`. **Do not publicly expose the backend's HTTP port** or permit plaintext HTTP access to the Android app.
2. Supply `OPENAI_API_KEY`, `ASTRA_CLIENT_TOKEN`, and optionally `OPENAI_MODEL` through your host's secret manager. Keep the secret manager and server access private.
3. Configure external rate limiting and daily/monthly provider spend caps. The included 30-request/hour default is **per process** and resets on restart; it is not a distributed or persistent budget enforcement system. A single shared client token is suitable only for a private personal deployment—not a multiuser service.
4. In the Astra Android app, open **Settings → Cloud Intelligence**. Enable it, paste the public HTTPS URL ending in `/chat`, and enter the *client token*, not the model-provider key.
5. Submit an open-ended question. Astra runs supported local commands first; only unsupported questions use the cloud gateway. The explicit `ask ai ...` command always requests cloud AI.

For security, the Android client keeps the gateway token **only in memory**. Re-enter it after the app process restarts. Do not put it in backups or store it as a preference. The URL and enabled flag may persist. The app rejects local URLs, user-info in URLs, non-443 ports and non-HTTPS endpoints.

## Privacy and limitations

- Up to 16 recent chat messages may be transmitted with a request; this may include text from local tasks or notes that were displayed in the chat. Do not enable cloud AI if those messages should remain on-device.
- The gateway does not deliberately log conversations, request bodies or provider responses; external hosting, networking and provider services may maintain their own logs and retention policies.
- The OpenAI API request includes `store: false` to avoid response application-state storage. This does not necessarily mean zero data retention for all processing.
- The gateway grants **no access to Android tasks, contacts, camera, notifications or files**; model responses are plain text, not executable device commands.
- Requests are limited to 16 messages, 3,000 characters each, with a ~64 KB input cap, a ~16,000-character output cap and timeouts.
- The gateway performs basic quota and concurrency limiting, but real deployment still requires monitoring and stronger infrastructure-level abuse protection.
- A manual end-to-end test with a real API key, HTTPS deployment and Android device **has not been performed**.

## Test

```sh
cd gateway
npm test
```

The test suite uses a mocked provider and checks request authentication, secret non-disclosure, message validation, quotas, error handling and the Responses API request/response mapping. GitHub Actions runs it independently of the Android build.

## API format

```
POST https://your-private-gateway.example/chat
Authorization: Bearer <your-private-client-token>
Content-Type: application/json

{"messages":[{"role":"user","content":"What is modified duration?"}]}
```

An authenticated successful response is `{"reply":"..."}`. Unauthenticated requests return `401`, invalid payloads `400`, quota failures `429`, and upstream model failures `502`. No model key is returned to the client.

For any production deployment, use a managed identity provider and per-user tokens rather than extending this shared-token prototype into an unauthenticated public service.
