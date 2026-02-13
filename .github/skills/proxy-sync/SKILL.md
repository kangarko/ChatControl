---
name: proxy-sync
description: 'ChatControl proxy/network support: BungeeCord, Velocity, proxy.yml, cross-server messaging, data sync (channels, nicks, mutes, groups, ignore lists), plugin messaging channel, server name configuration. Use when diagnosing proxy sync, cross-server chat, or network setup issues.'
---

# Proxy Sync System

## Overview

ChatControl synchronizes player data (channels, nicks, mutes, ignore lists, spy, etc.) across a BungeeCord or Velocity network via plugin messaging. The Bukkit plugin sends data to a proxy plugin (BungeeCord/Velocity module), which relays it to other servers.

## Architecture

### Key Classes
- `ProxyChat` (model/ProxyChat.java) — main sync manager, compiles and sends data
- `ChatControlProxyListenerBukkit` (listener/) — receives incoming proxy messages
- `ChatControlProxyMessage` (core: model/) — enum of message types
- `SyncedCache` (core: SyncedCache.java) — stores received proxy data
- `SyncType` (core: model/SyncType.java) — enum of data fields to sync

### Components
```
[Server A - ChatControl Bukkit]     [Proxy - BungeeCord/Velocity Plugin]     [Server B - ChatControl Bukkit]
        ProxyChat                              Relay                               ProxyChat
    ↓ sends data every 2s ↓                                                    ↓ sends data every 2s ↓
    Plugin Message Channel: "plugin:chcred"    ←──────────────→                Plugin Message Channel
```

### Sync Flow
```
Every 2 seconds (SyncTask):
  → For each online player:
    1. Check database loaded
    2. Compile all SyncType values from PlayerCache + hooks
    3. Send via ProxyUtil.sendPluginMessage()

On player join (PendingProxyJoinTask):
  → Every 0.5 seconds until database ready:
    → Send DATABASE_READY message to proxy
    → Proxy relays player data to this server
```

## Configuration (`proxy.yml`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | false | Master toggle |
| `Prefix` | `<dark_gray>[<yellow>{server_name}<dark_gray>] <gray>` | Prefix for cross-server messages |
| `Server_Name` | `''` | **Must match** velocity.toml or BungeeControl config |
| `Allow_Console_Forward_Command` | false | Allow `/chc forward` from console |

**proxy.yml does NOT support live reloading — restart required.**

## Synced Data Types (SyncType)

| Type | Data | Source |
|------|------|--------|
| `SERVER` | Server name | Platform config |
| `AFK` | AFK status | Hook (Essentials, CMI) |
| `CHANNELS` | Channel memberships (name:mode pairs) | PlayerCache |
| `IGNORE` | Ignored player UUIDs | PlayerCache |
| `TOGGLED_OFF_PARTS` | Disabled features (mail, PMs, etc.) | PlayerCache |
| `IGNORED_MESSAGES` | Muted broadcast groups | PlayerCache |
| `NICK_COLORED_PREFIXED` | Colored nick with prefix | PlayerCache tags |
| `NICK_COLORLESS` | Plain nickname | PlayerCache tags |
| `VANISH` | Vanish status | Hook (SuperVanish, etc.) |
| `GROUP` | Primary permission group | Vault |
| `PREFIX` | Chat prefix | Vault |
| `SUFFIX` | Chat suffix | Vault |
| `MUTE_BYPASS` | Has mute bypass permission | Permissions check |

## Proxy Message Types (ChatControlProxyMessage)

Key message types:
- `PLAYER_DATA` — periodic sync (SyncType values)
- `DATABASE_READY` — player database loaded, ready for data
- `MESSAGE` — private message forwarding
- `ANNOUNCEMENT` — network-wide announcements
- `MUTE` / `UNMUTE` — network mute commands
- `SPY_UUID` — spy message forwarding
- `CHANNEL_MESSAGE` — cross-server channel messages
- `FORWARD` — `/chc forward` command

## Channel Proxy Support

Per-channel in `settings.yml`:
```yaml
Channels:
  List:
    global:
      Format: global-chat
      Proxy: true        # Forward messages to other servers
      Proxy_Spy: true    # Forward spy messages too
```

## Common Issues & Solutions

### "Proxy sync not working"
1. Verify `proxy.yml` → `Enabled: true`
2. `Server_Name` must EXACTLY match proxy config (velocity.toml `[servers]` key or BungeeControl config)
3. Check proxy plugin (chatcontrol-bungeecord or chatcontrol-velocity) is installed
4. Alphanumeric + underscores only for server names
5. Restart required after proxy.yml changes (no live reload)

### "Player data missing on server switch"
- Database must load before data syncs (check for slow DB warnings > 100ms)
- `PendingProxyJoinTask` retries every 0.5s until DB ready
- If using MySQL, ensure all servers connect to SAME database

### "Cross-server messages not showing"
1. Channel must have `Proxy: true`
2. Both servers need the same channel name
3. Check `Prefix` format in proxy.yml
4. Console forward requires at least 1 player online (Bukkit limitation)

### "Data exceeds size limit"
- Plugin messages limited to 32,767 bytes
- Extremely large player data (many channels, ignore lists) may be skipped
- Logged once per 5 minutes when truncated

### "Server name shows wrong"
- Empty `Server_Name` defaults to Bukkit's server name
- Use `{server_name}` placeholder in `Prefix` format
- `Platform.getCustomServerName()` returns the configured name

## Key File Paths

- ProxyChat: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/ProxyChat.java`
- Proxy listener: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/listener/ChatControlProxyListenerBukkit.java`
- Proxy config: `chatcontrol-bukkit/src/main/resources/proxy.yml`
- SyncedCache: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/SyncedCache.java`
- Proxy message types: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/model/ChatControlProxyMessage.java`
- BungeeCord module: `chatcontrol-bungeecord/`
- Velocity module: `chatcontrol-velocity/`

## Foundation Integration

- `ProxyUtil.sendPluginMessage()` — sends data to proxy
- `ProxyListener` / `ProxyMessage` — Foundation's proxy abstraction
- `IncomingMessage` / `OutgoingMessage` — message serialization
- `FoundationPlugin.getPluginMessageChannel()` — returns plugin channel name
