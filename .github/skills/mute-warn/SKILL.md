---
name: mute-warn
description: 'ChatControl mute and warn system: /mute, /unmute, temp mute, channel mute, server mute, proxy mute, warning points, point sets, point triggers, punishment escalation, point decay. Use when diagnosing mute behavior, warning points, punishments, or mute hierarchy issues.'
---

# Mute & Warning System

## Overview

ChatControl provides a hierarchical mute system (player, channel, server, proxy) and a warning points system with configurable thresholds, triggers, and decay. Mutes and warns integrate with rules, spy, and proxy sync.

## Mute Architecture

### Mute Hierarchy (highest to lowest priority)
```
Proxy mute (all servers)
  ↓
Server mute (all channels on one server)
  ↓
Channel mute (one channel)
  ↓
Player mute (one player)
```

### Key Classes
- `Mute` (model/Mute.java) — mute state management
- `CommandMute` (command/CommandMute.java) — `/mute` command
- `CommandUnmute` (command/CommandUnmute.java) — `/unmute` command
- `PlayerCache` — stores mute state per player

### Mute Types
| Scope | Description | Storage |
|-------|-------------|---------|
| Player mute | Individual player can't chat | `PlayerCache.muteData` |
| Channel mute | Entire channel silenced | `Channel.mutedUntil` |
| Server mute | All chat silenced | Global flag |
| Proxy mute | All servers silenced | Proxy broadcast |

### Mute Data Structure
```java
class MuteData {
    UUID mutedBy;           // Who muted
    long mutedUntil;        // Expiration timestamp (-1 = permanent)
    String reason;          // Mute reason
    MuteType type;          // PLAYER, CHANNEL, SERVER, PROXY
    String channelName;     // For channel mutes
    boolean anonymous;      // Hide muter identity
}
```

## Mute Commands

### `/mute <player> [duration] [reason] [-s] [-a]`
- No duration = permanent mute
- Duration format: `30m`, `2h`, `1d`, `30s`
- `-s` = silent (no broadcast)
- `-a` = anonymous (hide who muted)
- Permission: `chatcontrol.command.mute`

### `/unmute <player>`
- Removes active mute
- Permission: `chatcontrol.command.unmute`

### `/mute channel <channel>`
- Mutes entire channel for everyone
- Permission: `chatcontrol.command.mute.channel`

### `/mute server`
- Mutes all chat server-wide
- Permission: `chatcontrol.command.mute.server`

## Mute Configuration (`settings.yml` → `Mute`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Broadcast` | true | Announce mutes server-wide |
| `Broadcast_Anonymous` | true | Broadcast anonymous mutes |
| `Prevent_Writing` | true | Block muted player messages |
| `Prevent_Commands` | `[]` | Block these commands when muted |
| `Hide_Message` | false | Hide "you are muted" on every attempt |
| `Soft_Mute` | false | Only show own messages to self (others don't see) |

### Soft Mute
When `Soft_Mute: true`, the muted player still sees their own messages in chat, but no one else does. This prevents them from realizing they're muted.

### Command Blocking
```yaml
Prevent_Commands:
  - /me
  - /say
  - /tell
```
When muted, these commands are also blocked.

## Warning Points System

### Overview
Players accumulate warning points from rule violations. Points trigger escalating punishments when thresholds are reached.

### Key Classes
- `WarningPoints` — point tracking in PlayerCache
- `Checker.java` — assigns points on rule violations
- `RuleOperator` — `warn` operator in rules

### Point Sets
Points are grouped into named sets for different violation types:

```yaml
# settings.yml → Warning_Points
Warning_Points:
  Enabled: true
  
  Sets:
    swear:
      Trigger_Amount: 5
      Trigger_Actions:
        - "console /tempban {player} 1h Excessive swearing"
      Reset_Points: true
      
    spam:
      Trigger_Amount: 3
      Trigger_Actions:
        - "console /tempmute {player} 30m Spamming"
      Reset_Points: true
      
    caps:
      Trigger_Amount: 10
      Trigger_Actions:
        - "warn {player} Stop using excessive caps!"
      Reset_Points: false
```

### Rule Integration
Rules assign points via the `warn` operator:

```
# rules/chat.rs
match \b(badword1|badword2)\b
warn swear
then deny
```

This adds 1 point to the "swear" set each time the rule matches.

### Point Triggers
When a set reaches its `Trigger_Amount`, the `Trigger_Actions` execute:

| Action | Example | Purpose |
|--------|---------|---------|
| `console <cmd>` | `console /ban {player}` | Run console command |
| `player <cmd>` | `player /spawn` | Force player command |
| `warn <msg>` | `warn Stop swearing!` | Send warning message |
| `kick <reason>` | `kick Excessive violations` | Kick player |
| `toast <msg>` | `toast Warning!` | Show toast |
| `title <msg>` | `title &cWarning` | Show title |
| `actionbar <msg>` | `actionbar &eWatch it!` | Show actionbar |

### Point Decay
```yaml
Warning_Points:
  Reset_Task:
    Enabled: true
    Period: 24 hours
    Amount: 1
```

Points decay over time:
- `Period` — how often decay runs
- `Amount` — how many points removed per run
- Applies to all sets equally

### Point Commands
- `/chc points <player>` — view player's points
- `/chc points <player> <set> <amount>` — set points
- `/chc points <player> reset` — reset all points
- Permission: `chatcontrol.command.points`

## Mute + Warning Integration

Rules can both warn and mute:
```
# rules/chat.rs
match \b(terrible_word)\b
warn swear
then deny
handle quietly

# When swear points hit 5, trigger action mutes them:
# console /chc mute {player} 1h Accumulated swearing warnings
```

## Common Issues & Solutions

### "Muted player can still chat"
1. Check mute is active: `/chc points <player>` or check logs
2. Check `Mute.Prevent_Writing: true`
3. Check `Mute.Soft_Mute` — if true, they see own messages
4. Channel mute only affects that channel; player may chat elsewhere
5. Check `chatcontrol.bypass.mute` permission — bypasses mute

### "Mute expires immediately"
- Duration format: `30s`, `5m`, `2h`, `1d` (s/m/h/d suffixes)
- No spaces: `30m` not `30 m`
- No duration = permanent (`-1` timestamp)

### "Warning points not accumulating"
1. Check `Warning_Points.Enabled: true`
2. Check rule uses `warn <setname>` operator
3. Set name must match a `Sets.<name>` in config
4. Check rule actually matches (test with `/chc rule test <message>`)

### "Trigger action not firing"
1. Check `Trigger_Amount` threshold reached
2. Check action syntax (console/player/warn/kick)
3. Check `Reset_Points: true/false` — if true, points reset after trigger
4. Check command exists and works manually

### "Proxy mute not syncing"
1. Proxy plugin must be installed on BungeeCord/Velocity
2. `proxy.yml` configured with same `Server_Name`
3. Mute must be `proxy` scope (not just server/channel)
4. SyncType.MUTE must be enabled in proxy config

### "Points not decaying"
1. Check `Reset_Task.Enabled: true`
2. Check `Reset_Task.Period` isn't too long
3. Player must be online for decay (offline players retain points)

## Bypass Permissions

| Permission | Effect |
|------------|--------|
| `chatcontrol.bypass.mute` | Immune to mute |
| `chatcontrol.bypass.warn` | Immune to warning points |
| `chatcontrol.command.mute` | Can mute players |
| `chatcontrol.command.unmute` | Can unmute players |
| `chatcontrol.command.mute.channel` | Can mute channels |
| `chatcontrol.command.mute.server` | Can mute server |

## Key File Paths

- Mute: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Mute.java`
- CommandMute: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandMute.java`
- CommandUnmute: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandUnmute.java`
- Warning Points config: `chatcontrol-bukkit/src/main/resources/settings.yml` (Warning_Points section)
- Rule operators: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/RuleOperator.java`

## Foundation Integration

- `TimeUtil` — duration parsing (30m, 2h, 1d)
- `PlayerCache` — persistent mute/point storage
- `CompRunnable` — scheduled point decay task
