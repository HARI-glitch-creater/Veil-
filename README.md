# Veil

**Veil** is a minimalist, dark glassmorphic Android messenger built for complete data sovereignty. Instead of storing your conversations on a centralized company database, you connect Veil to **your own Supabase project** or **your own self-hosted homelab server** and chat inside **12-digit Veil Rooms** protected by client-side end-to-end encryption.

> Even the owner of the Supabase database or private server can only see opaque ciphertext — never your messages, attachments, filenames, or locations.

---

## Features

- **Host or Join with 12-Digit Veil Codes**
  - **Host a Veil Room:** Connect your Supabase project (URL + API key) or your self-hosted server to generate a unique **12-digit Veil Code** (`XXXX - XXXX - XXXX`) and scannable Room QR invite.
  - **Join a Veil Room:** Enter a 12-digit Veil Code, paste an invite link, or scan the host's Room QR offline to connect and derive encryption keys automatically.
- **Zero-Knowledge End-to-End Encryption**
  - Auto-generated on-device **EC P-256** identity keypair protected by **Android Keystore**.
  - Per-room **ECDH + HKDF-SHA256** key derivation and **AES-256-GCM** authenticated encryption with unique random 96-bit IVs.
  - **60-digit Safety Number** and QR verification inside every room so peers can confirm zero server tampering.
- **Bring Your Own Database (Simple or Homelab)**
  - **Supabase Mode:** Connect in seconds with just your Supabase Project URL and API Key, plus an in-app one-tap SQL schema copy helper.
  - **Self-Hosted Mode:** Connect to your own homelab or VPS via configurable REST API paths, Bearer / API Key / Basic auth, custom reverse-proxy headers (e.g., Cloudflare Access, Tailscale), optional WebSocket real-time relay, S3/custom media endpoint, and outbound webhooks.
- **Rich Encrypted Media & Data Sharing**
  - **Photos & Videos** via the zero-permission Android Photo Picker (inline image preview & full-screen video player).
  - **Documents** (PDF, TXT, JSON, Markdown, ZIP, etc.) via Storage Access Framework (inline text reader + scoped `FileProvider` external viewer).
  - **Locations** via foreground GPS coordinates (dark radar coordinate card + one-tap external maps launch).
  - **Voice Notes** recorded in AAC `.m4a` with a live amplitude waveform bar and inline playback scrubbing.

---

## Downloading the APK via GitHub Actions

This repository includes an automated GitHub Actions build pipeline (`.github/workflows/build-apk.yml`) that compiles the Android APK automatically on every push to `main`/`master` or manual trigger.

1. Open the **Actions** tab in your GitHub repository.
2. Select the latest **Build Veil Android APK** workflow run (or tap **Run workflow** to trigger one manually).
3. Scroll down to **Artifacts** and download **Veil-Android-APK**.
4. Unzip the downloaded file on your Android phone and install `Veil-debug.apk` (allow installing from unknown sources if prompted).

> **Tip:** Pushing a Git tag like `v1.0.0` automatically creates a GitHub Release with `Veil-debug.apk` attached for direct download without unzipping.

---

## Quick Supabase Setup (Zero-Knowledge Tables)

Create a free project at [supabase.com](https://supabase.com), open the **SQL Editor**, and run the snippet below (also available inside Veil's **SQL Schema** button):

```sql
create table if not exists public.veil_rooms (
  room_code text primary key,
  host_public_key text not null,
  host_handle text not null,
  created_at bigint not null
);

create table if not exists public.veil_members (
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  public_key text not null,
  member_handle text not null,
  joined_at bigint not null,
  primary key (room_code, public_key)
);

create table if not exists public.veil_messages (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  sender_public_key text not null,
  iv text not null,
  ciphertext text not null,
  created_at bigint not null
);

alter table public.veil_rooms enable row level security;
alter table public.veil_members enable row level security;
alter table public.veil_messages enable row level security;

create policy "veil_rooms_anon" on public.veil_rooms for all using (true) with check (true);
create policy "veil_members_anon" on public.veil_members for all using (true) with check (true);
create policy "veil_messages_anon" on public.veil_messages for all using (true) with check (true);
```

*(Optional)* Create a Storage bucket named `veil-media` for larger encrypted photos, videos, and files. Veil encrypts all binary files client-side before upload.

---

## Self-Hosted Homelab API Contract

When using **Self-Hosted** mode, point Veil to your server's base URL (LAN/WireGuard/Tailscale HTTP or public HTTPS). All paths are customizable in **Advanced** settings:

| Method | Default Path | Purpose |
| :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Connection & latency check |
| `POST` / `GET` | `/api/v1/rooms` | Register or look up a room by `room_code` |
| `POST` / `GET` | `/api/v1/members` | Register or list room participant public keys (`?room_code=...`) |
| `POST` / `GET` | `/api/v1/messages` | Store or fetch encrypted message envelopes (`id`, `room_code`, `sender_public_key`, `iv`, `ciphertext`, `created_at`) |
| `WS` *(optional)* | Custom `wss://` URL | Push real-time encrypted message JSON envelopes for `?room_code=...` |
| `PUT` *(optional)* | Custom S3/Media URL | Store encrypted binary media blobs (`/{room_code}/{blob_id}.veil`) |

---

## Building Locally

Requires **JDK 21**, **Android SDK (API 36.1)**, and **Gradle 9.3.1**:

```bash
gradle :app:assembleDebug
```

The built APK will be output to `app/build/outputs/apk/debug/app-debug.apk`.
