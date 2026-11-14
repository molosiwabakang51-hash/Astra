# Astra local AI — no paid AI APIs

Astra can hold natural-language conversations using an **Ollama model running on a computer you already own**, rather than a paid AI API. This repository contains the Node.js gateway, Android client, and security tests. The gateway uses Ollama's **local** HTTP API at `127.0.0.1:11434`. **No OpenAI API key, subscription, card, or model-provider account is required.**

The AI software and chosen downloadable model have no usage-based API fee. There are still **real-world costs** if you need to buy hardware, pay for electricity/data, or rent a server. This setup does not promise zero physical operating cost. The computer must be on and running Ollama for AI chat to work; Astra's offline commands work even if it is off.

## Architecture

```text
Android Astra app
    |  Private HTTPS; authenticated with your session-only token
    v
Tailscale Serve (personal tailnet; HTTPS, no public port forwarding)
    |
    v
Astra gateway on your computer — 127.0.0.1:8080
    |  Local HTTP, not exposed to the phone or public internet
    v
Ollama on the same computer — 127.0.0.1:11434
    |
    v
Downloaded local model — qwen3:0.6b by default
```

The default model `qwen3:0.6b` has a relatively small download (about **523 MB** on Ollama's model page). Larger models generally produce stronger responses but use more disk space, RAM, and compute. You can try `qwen3:1.7b` or `qwen3:4b` if your computer can run them. Model quality is not equivalent to a premium cloud AI.

## Step 1 — Install the free software

On your computer, install **Ollama** from https://ollama.com/download and **Node.js 20+** from https://nodejs.org/en/download. This repository's gateway has no npm dependencies.

Download a model once (this consumes internet data; local inference thereafter requires no external model API):

```sh
ollama pull qwen3:0.6b
```

Check that your model works:

```sh
ollama run qwen3:0.6b
```

Ollama normally listens on `127.0.0.1:11434`. **Keep it bound to loopback**: do not set `OLLAMA_HOST=0.0.0.0`, expose port 11434, or open it in your router.

## Step 2 — Start Astra's free gateway

Generate a random, private access token. This is **not an API key for a paid model**. Never commit or share it.

**Windows PowerShell:**

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:ASTRA_CLIENT_TOKEN = [BitConverter]::ToString($bytes).Replace("-", "").ToLowerInvariant()
$env:OLLAMA_MODEL = "qwen3:0.6b"
cd gateway
npm test
npm start
```

**Linux/macOS:**

```sh
export ASTRA_CLIENT_TOKEN="$(openssl rand -hex 32)"
export OLLAMA_MODEL="qwen3:0.6b"
cd gateway
npm test
npm start
```

**The token is only set for this terminal session.** To restart the gateway after closing the terminal, generate/set the same token again or retrieve it from an existing private credential manager. If you generate a new one, update it in Astra Settings. Never create an unprotected `.env` file in a public or shared location.

The gateway listens only on `127.0.0.1:8080` and accepts `POST /chat` and `GET /health`. You can test the health endpoint from your computer at `http://127.0.0.1:8080/health`. It returns `{"status":"ready"}` when the gateway itself is running; it does **not** guarantee Ollama or the model is ready.

## Step 3 — Reach your own computer from Android, without renting a server

**Recommended personal-use setup:** [Tailscale Personal](https://tailscale.com/pricing) currently offers a free tier for non-commercial personal use. Install Tailscale on both your computer and Android phone, and sign both into the same private tailnet. Enable MagicDNS and HTTPS certificates for the tailnet, then run on the computer:

```sh
tailscale serve --bg 8080
```

Tailscale Serve outputs a **private HTTPS URL**, usually similar to `https://computer.tailnet-name.ts.net/`, reachable only through your tailnet. Configure the Astra gateway address in Android as `https://computer.tailnet-name.ts.net/chat`, using the **actual URL** Tailscale gives you. Enter the same secret `ASTRA_CLIENT_TOKEN` in Astra's **Settings → Cloud Intelligence → Gateway access token**.

The Android client requires a trusted HTTPS hostname and port 443. It rejects plaintext HTTP, localhost URLs and direct provider endpoints. The phone must be connected to your Tailscale tailnet and the computer must be on. The gateway's bearer token is required **in addition** to tailnet access; the phone holds it only in memory, not in local backups.

**Do not use Tailscale Funnel**: that publishes a service to the public internet. Serve is restricted to your tailnet. The free Personal tier is subject to Tailscale's published terms and may change; confirm its availability for your use before signing up.

Alternative: use another owner-operated private HTTPS reverse proxy, but do not forward ports 8080 or 11434 directly from the public internet.

## Step 4 — Use Astra

In Android Settings:

1. Enable **Local Model Gateway** (the optional network AI connection).
2. Paste your private HTTPS URL ending in `/chat`.
3. Paste your gateway access token. This token is held in app memory **for this session only**.
4. Ask a normal question: `Explain bond convexity and show an example`. Unsupported local requests automatically go to your own model. You can also type `ask ai ...`.

Local tasks, notes, and deterministic calculators run **before** the model and remain available without the gateway. No model-generated response can directly control or edit device data.

## Privacy and costs

- **No usage-based AI charges**: no provider API or paid subscription is used. The model runs on your own hardware. A downloaded local model is not automatically installed on the phone.
- Up to 16 recent chat messages may travel **from your phone to your own computer**, including text previously displayed from notes/tasks. A private Tailscale connection encrypts this traffic, and the gateway does not intentionally log message contents. Disable cloud/network AI if you don't want these messages transmitted.
- The gateway's session token is not placed in Astra backups; the URL and AI-enabled preference may persist. It must be re-entered after app restart.
- Gateway responses are plain text; the model cannot open device apps, read contacts, send notifications, change tasks, or browse the internet.
- Client authentication, basic 30-request/hour per-process throttling, two concurrent request limit and 90-second model timeout guard against accidental overuse. They are not suitable for a public multiuser deployment.
- The gateway and Ollama inference are entirely on your own machine; there is no call to OpenAI or any other paid AI inference API.
- Ollama model downloads and Tailscale connection require internet access; your internet provider may charge for data and your hardware uses electricity.
- **No device-level end-to-end test or live Ollama inference has been performed by CI**. CI tests the gateway protocol using a mocked Ollama response.

## Gateway protocol

```http
POST https://your-tailnet-host.ts.net/chat
Authorization: Bearer <your-random-session-token>
Content-Type: application/json

{"messages":[{"role":"user","content":"What is modified duration?"}]}
```

Success: `{"reply":"..."}`. Unauthorized requests return 401, invalid payloads 400, throttled requests 429, and missing/unresponsive local models 502. Health check: `GET /health` on the private gateway.

## Verification

```sh
cd gateway
npm test
```

GitHub Actions separately runs Android unit tests/APK build and gateway protocol/security tests. No paid cloud deployment or billing account is required to build and test the project.
