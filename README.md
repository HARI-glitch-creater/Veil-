# Veil v1.0.1 — Zero-Knowledge Encrypted Messenger

Veil is a native Android zero-knowledge end-to-end encrypted messenger built with **Kotlin**, **Jetpack Compose**, **Material 3**, **AndroidKeyStore** (`EC P-256` + `ECDH` + `HKDF-SHA256` + `AES-256-GCM`), **Room**, **WorkManager**, and audio-only **WebRTC**. Bring your own **Supabase** database or **self-hosted server**—even the database owner only ever sees encrypted ciphertext.

## What's New in v1.0.1
- **Single-Device Account Binding & Anti-Duplicate Reinstall Redirect:** One Veil profile per physical phone using `device_binding_id`, Android `AccountManager`, and scoped recovery anchors. Reinstalling Veil restores your existing account automatically.
- **First-Time Profile Onboarding & Room Gateway:** Create your encrypted `512x512` PFP, Username (`3–24` chars), Motto (hard `20`-word limit), and Gender, confirm permanent device binding, then host or join your first Veil Room.
- **Room-Scoped 3-Tab Home Shell:** Clean dark glass top bar (`Veil` + active Room Name with lock badge, inline search, and Three-Dot menu) paired with 3 focused tabs: **Profiles**, **Communities**, and **Calls**.
- **Profile Directory, Inspector & Moderation:** Single-tap to chat, tap avatar to inspect 60-digit Safety Numbers & QR, or long-press for haptic moderation actions (`Inspect Profile`, `Create Community`, `Mute`, `Block`, `Report`).
- **Default & Strictly Isolated 3+ Profile Private Communities:** Automatic `<Room Name> — General` room community plus private communities enforcing a hard minimum of 3 profiles with ECDH-wrapped community keys and strict visibility isolation.
- **Strictly Voice-Only Encrypted Calls & 90-Day Call History:** Audio-only WebRTC calls with encrypted SDP/ICE signaling (`veil_call_signals`), lock-screen ringing notifications, and automatic 90-day call history purging via `WorkManager`.
- **6-Tile Attachment Drawer & 1x / 2x Disappearing Messages:** Share Live Photos, Gallery items, Documents, Browse Files, Location, and toggle `1x View` / `2x View` ephemeral messages rendered in a `FLAG_SECURE` viewer that overwrites and deletes local files and purges remote ciphertext after viewing.
- **Obsidian Black + Custom Accent Color Theme Customizer:** 7 curated dark presets plus a full interactive HSV Color Wheel and Hex input persisted in DataStore.
- **Zero Unicode Emojis:** Clean monochrome vector iconography (`Icons.Rounded.*` / `Icons.Outlined.*`) across every screen.
- **In-App GitHub Release Updater:** Automatically checks `HARI-glitch-creater/Veil-` releases and installs raw `.apk` updates in-place.

## Supabase Zero-Knowledge Setup (8 Tables)
Run the SQL schema below once in your **Supabase SQL Editor** (also copyable inside Veil via **Settings → Copy v1.0.1 Supabase SQL Schema**):

```sql
create table if not exists public.veil_device_bindings (
  device_binding_id text primary key,
  public_key text not null,
  username text not null,
  motto text not null default '',
  gender text not null default 'Prefer not to say',
  pfp_cipher_url text not null default '',
  updated_at bigint not null
);

create table if not exists public.veil_rooms (
  room_code text primary key,
  room_name text not null,
  host_public_key text not null,
  host_device_id text not null default '',
  created_at bigint not null
);

create table if not exists public.veil_members (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  device_binding_id text not null,
  username text not null,
  motto text not null default '',
  gender text not null default 'Prefer not to say',
  pfp_cipher_url text not null default '',
  public_key text not null,
  joined_at bigint not null,
  unique (room_code, device_binding_id)
);

create table if not exists public.veil_communities (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  name text not null,
  description text not null default '',
  is_default boolean not null default false,
  created_by_public_key text not null,
  created_at bigint not null
);

create table if not exists public.veil_community_members (
  community_id text not null references public.veil_communities(id) on delete cascade,
  member_public_key text not null,
  encrypted_community_key text not null,
  primary key (community_id, member_public_key)
);

create table if not exists public.veil_messages (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  channel_type text not null check (channel_type in ('DIRECT', 'COMMUNITY')),
  target_id text not null,
  sender_public_key text not null,
  iv text not null,
  ciphertext text not null,
  max_views integer not null default 0,
  views_consumed integer not null default 0,
  created_at bigint not null
);

create table if not exists public.veil_call_signals (
  id text primary key,
  room_code text not null references public.veil_rooms(room_code) on delete cascade,
  caller_public_key text not null,
  callee_public_key text not null,
  signal_type text not null,
  encrypted_payload text not null,
  created_at bigint not null
);

create table if not exists public.veil_reports (
  id text primary key,
  room_code text not null,
  reporter_public_key text not null,
  reported_public_key text not null,
  reason text not null,
  notes text not null default '',
  created_at bigint not null
);
```

## Automated APK Builds & GitHub Releases
Every push builds `Veil-debug.apk` via `.github/workflows/build-apk.yml`. Pushing a version tag (e.g. `git tag v1.0.1 && git push origin v1.0.1`) automatically builds and publishes a raw **`Veil-v1.0.1.apk`** release asset directly on GitHub Releases for Veil's in-app updater.
