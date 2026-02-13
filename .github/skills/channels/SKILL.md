---
name: channels
description: 'ChatControl channel system: channel creation, types, ranged channels, party channels, permissions, auto-join, channel modes (read/write), proxy channels, Discord linking. Use when working on channel code or diagnosing channel-related issues.'
---

# Channel System

## Overview

Channels route chat messages to specific groups of players. Each channel has a format, optional range limit, party integration, proxy support, and Discord linking. Players join channels in READ (receive-only) or WRITE (send+receive) mode. Only one WRITE channel at a time.

## Architecture

### Key Classes
- `Channel` (model/Channel.java) — extends `YamlConfig`, loaded from `settings.yml` `Channels.List`
- `Channel.State` — encapsulates message state (sender, message, component, variables)
- `ChannelMode` (core: model/ChannelMode.java) — enum: `WRITE` (gold), `READ` (green)
- `ChannelCommands` (command/channel/) — `/channel` subcommands
- `ChatHandler` (listener/chat/) — routes chat to channels

### Message Send Pipeline
```
Player types message
  → ChatHandler routes to write channel
    → Channel.sendMessage(State)
      1. Compile receivers (range, party, vanish, ignore checks)
      2. Fire ChannelPreChatEvent (API — cancellable)
      3. Check mute status (player, channel, server, proxy)
      4. Remove unauthorized colors
      5. Run rules/filters via Checker.filterChannel()
      6. Process @mentions and sound notifications
      7. Format.build() — compile format parts into component
      8. Send to each receiver (with per-receiver view conditions)
      9. Log to console and database
      10. Forward to Discord (if Discord_Channel_Id set)
      11. Forward over proxy (if Proxy: true)
      12. Fire ChannelPostChatEvent
```

## Configuration

### Global Settings (`settings.yml` → `Channels`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | false | Master toggle — must be `true` |
| `Ignore_Worlds` | `[]` | Worlds excluded from channel formatting |
| `Command_Aliases` | `[channel, ch]` | Aliases for `/channel` command |
| `Max_Read_Channels` | 3 | Max simultaneous READ channels (overridable per group) |
| `Join_Read_Old` | true | Auto-set old write channel to READ when switching |
| `Format_Console` | `[{channel}] {player}: {message}` | Default console log format |
| `Format_Discord` | `**{player}**: {message}` | Default Discord format |

### Per-Channel Settings (`settings.yml` → `Channels.List.<name>`)

| Key | Type | Purpose |
|-----|------|---------|
| `Format` | String | **Required**. Format file name OR literal MiniMessage string |
| `Format_Console` | String | Console format override. `"none"` = cancel event, `"default"` = use channel output |
| `Format_To_Discord` | String | MC→Discord message format |
| `Format_From_Discord` | String | Discord→MC message format |
| `Format_Discord_Webhook_Name` | String | Discord webhook sender name override |
| `Format_Spy` | String | Spy message format override |
| `Range` | String/Number | Distance in blocks, or `*` for world-wide |
| `Range_Worlds` | List | Linked worlds when `Range: "*"` |
| `Min_Players_For_Range` | Integer | Min players before range activates |
| `Party` | String | Party plugin type (e.g., `towny-town`, `factions-faction`) |
| `Message_Delay` | SimpleTime | Per-channel message cooldown (overrides global) |
| `Proxy` | Boolean | Forward messages to BungeeCord/Velocity |
| `Proxy_Spy` | Boolean | Forward spy messages over proxy |
| `Discord_Channel_Id` | Long | Linked Discord channel ID |
| `Discord_Spy_Channel_Id` | Long | Discord spy channel ID |
| `Sound` | SimpleSound | Sound played to channel recipients |
| `Cancel_Event` | Boolean | Cancel Bukkit event (hides from DynMap/other plugins) |

### Default Channels

| Channel | Format | Description |
|---------|--------|-------------|
| `standard` | `chat` | Default chat with spy |
| `admin` | `admin-chat` | Admin-only chat |
| `global` | `global-chat` | Global chat (no range) |
| `ranged` | literal | 100-block range, proxy disabled |
| `towny` | literal | Towny town party chat |
| `helpop` | `helpop` | Help request channel |

## Permissions

- `chatcontrol.channel.join.{channel}.{mode}` — join channel in read/write
- `chatcontrol.channel.autojoin.{channel}.{mode}` — auto-join on login
- `chatcontrol.channel.send.{channel}` — send messages to channel
- `chatcontrol.channel.sendas.{channel}` — send as another player
- `chatcontrol.channel.leave.{channel}` — leave channel
- `chatcontrol.bypass.range` — reach all players in ranged channels

## Common Issues & Solutions

### "Messages not showing in channel"
1. Check `Channels.Enabled: true` in settings.yml
2. Verify player has `chatcontrol.channel.join.{channel}.write`
3. Run `/channel list` to check membership
4. Check `Ignore_Worlds` doesn't include current world
5. Enable `Rules.Verbose: true` to see if rules deny the message

### "Ranged channel not working"
1. Check `Range` value is a number (blocks) or `*` (world-wide)
2. `Min_Players_For_Range` may prevent range from activating
3. `Range_Worlds` only applies when `Range: "*"`
4. `chatcontrol.bypass.range` reaches everyone — check staff perms
5. Party option may override range behavior

### "Player can't leave/switch channels"
- Players can only be in ONE write channel. Joining a new write channel leaves the old one.
- `Join_Read_Old: true` auto-converts old write channel to READ mode
- Manually left channels (`/channel leave`) are remembered — won't auto-rejoin

### "Console format shows wrong output"
- `"none"` cancels the event (bad for DynMap, other plugins)
- `"default"` uses the channel's formatted output as-is

### "Discord messages showing wrong format"
- Old `Format_Discord` was split: use `Format_To_Discord` (MC→Discord) and `Format_From_Discord` (Discord→MC)

## Key File Paths

- Channel class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Channel.java`
- Channel mode: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/model/ChannelMode.java`
- Channel commands: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/channel/`
- Chat handler: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/listener/chat/ChatHandler.java`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Channels section)

## API Events

- `ChannelJoinEvent` / `ChannelLeaveEvent` — fired on join/leave
- `ChannelPreChatEvent` — before message processing (cancellable)
- `ChannelPostChatEvent` — after message sent to all receivers
- `ChatChannelProxyEvent` — for incoming proxy messages

## Foundation Integration

- `YamlConfig` — base config class for channel definitions
- `ConfigItems` — loads channels from settings.yml list
- `SimpleComponent` — component output for formatted messages
- `ProxyUtil` — cross-server message sending
