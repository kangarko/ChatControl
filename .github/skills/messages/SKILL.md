---
name: messages
description: 'ChatControl message broadcasting: join/quit/kick/death/timed messages, .rs message files, conditions (require/ignore sender/receiver), message groups, formats, random/sequential messages, proxy broadcasting. Use when configuring join/quit/death/timed messages or diagnosing broadcast issues.'
---

# Message Broadcasting System

## Overview

ChatControl replaces vanilla join/quit/kick/death messages and adds scheduled timed broadcasts. Messages are defined in `.rs` files under `messages/` with conditional logic, formatting, and proxy support.

## Architecture

### Key Classes
- `PlayerMessage` (operator/PlayerMessage.java) — abstract base for all message types
- `PlayerMessages` (operator/PlayerMessages.java) — singleton loader and broadcaster
- `JoinQuitKickMessage` (operator/JoinQuitKickMessage.java) — wrapper for join/quit/kick
- `DeathMessage` (operator/DeathMessage.java) — death-specific conditions (killer, cause, etc.)
- `TimedMessage` (operator/TimedMessage.java) — scheduled broadcast with delay
- `PlayerMessageType` (core: model/PlayerMessageType.java) — enum: JOIN, QUIT, KICK, DEATH, TIMED

### Broadcast Flow
```
Event triggers (join/quit/death/timer)
  → PlayerMessages.broadcast(type, wrapped, message)
    → For each message group (top to bottom):
      1. Check sender conditions (require/ignore sender perm/script/world/etc.)
      2. Check timing (begins/expires/delay)
      3. Select message text (random or sequential)
      4. Build format using Format.parse()
      5. For each receiver: check receiver conditions
      6. Send formatted component
      7. Fire PlayerMessageEvent (API)
      8. If Settings.Messages.STOP_ON_FIRST_MATCH: stop after first match
```

## Message File Syntax (.rs)

### Basic Structure
```
group first_join
require sender script {statistic_PLAY_ONE_MINUTE} == 0
message:
- Welcome {player} to the server!
- First time here? Type /help

group default_join
message:
- {player} joined the game
```

### Message-Specific Operators

**Messages:**
```
group <name>           — unique group identifier
message:               — message list (following lines indented with space)
- Line 1               — individual messages (random or sequential)
- Line 2
prefix <text>          — prepend to every message
suffix <text>          — append to every message
random true            — random selection (default: false = sequential)
proxy true             — broadcast to proxy network
```

**Sender Conditions:**
```
require sender perm <permission>
require sender script <javascript>
require sender variable <var> <value>
require sender gamemode <mode>
require sender world <world>
require sender region <region>
require sender channel <channel> [mode]
require self                     — only show to the sender
```

**Receiver Conditions:**
```
require receiver perm <permission>
require receiver script <javascript>
require receiver variable <var> <value>
require receiver gamemode <mode>
require receiver world <world>
require receiver region <region>
require receiver channel <channel> [mode]
ignore self                      — don't show to the sender
```

**Ignore variants:** Same structure with `ignore` prefix.

### Death Message Extra Operators
```
require cause <DamageCause>      — FALL, DROWNING, ENTITY_ATTACK, etc.
require projectile <EntityType>  — ARROW, TRIDENT, etc.
require block <Material>         — falling block type
require killer <EntityType>      — killer entity type
require boss <name>              — Boss/MythicMobs boss name
require damage <number>          — minimum damage taken
require npc / ignore npc         — Citizens NPC check

require killer perm <perm>       — killer-specific conditions
require killer script <js>
require killer world <world>
require killer item <material>   — killer's held item (supports wildcards: *_SWORD)
```

**Death Variables:**
- `{killer}`, `{killer_name}`, `{killer_type}` — killer info
- `{killer_item}`, `{killer_item_name}` — killer weapon
- `{block_type}`, `{cause}`, `{projectile}` — death details
- `{boss_name}` — boss name (if applicable)

### All Operators from Operator Base
Messages support all base operators: `then command`, `then console`, `then sound`, `then title`, `then actionbar`, `then bossbar`, `then toast`, `delay`, `begins`, `expires`, etc.

## Configuration (`settings.yml` → `Messages`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Apply_On` | `[join, quit, kick, death, timed]` | Which types are enabled |
| `Stop_On_First_Match` | true | Stop after first matching group |
| `Join_Quit_Cooldown` | `0 seconds` | Cooldown period to prevent join/quit spam |
| `Timed_Delay` | `3 minutes` | Delay between timed broadcasts |
| `Prefix.Join` / `.Quit` / etc. | format strings | Default message prefix per type |

## Join/Quit Message Cooldown

To prevent chat flooding when players repeatedly join/leave (e.g., due to lag or poor connection), you can configure a cooldown period:

```yaml
Messages:
  Join_Quit_Cooldown: 10 seconds
```

### How It Works
- When a player joins or quits, the message is shown normally
- If the same player joins or quits again within the cooldown period, the message is silently suppressed
- Each message type (JOIN/QUIT) has its own independent cooldown per player
- Set to `0 seconds` to disable cooldown (default behavior)

### Example Scenario
With `Join_Quit_Cooldown: 10 seconds`:
1. Player joins at 0:00 → message shown
2. Player quits at 0:02 → message shown
3. Player joins at 0:05 → message suppressed (within 10s of last join)
4. Player quits at 0:07 → message suppressed (within 10s of last quit)
5. Player joins at 0:15 → message shown (cooldown expired)

### Debugging
Enable `player-message` debug category to see cooldown activity:
```yaml
Debug: [player-message]
```

Log output example:
```
[DEBUG] Recording join message for Player123 (cooldown: 10s)
[DEBUG] Suppressing quit message for Player123 (cooldown: 7s remaining)
```

## Message Files

| File | Type | Purpose |
|------|------|---------|
| `messages/join.rs` | JOIN | Join messages |
| `messages/quit.rs` | QUIT | Quit messages |
| `messages/kick.rs` | KICK | Kick messages |
| `messages/death.rs` | DEATH | Death messages |
| `messages/timed.rs` | TIMED | Scheduled broadcasts |

## Timed Messages

Timed messages run on a global timer (`Settings.Messages.TIMED_DELAY`). Each group can override with its own `delay`:

```
group tips
delay 5 minutes
message:
- Tip: Use /help for commands
- Tip: Join our Discord at discord.gg/example

group world_specific
require sender world hardcore
delay 10 minutes
message:
- Hardcore tip: Sleep sets your respawn point
```

## Common Issues & Solutions

### "Join/quit messages not showing"
1. Check `Messages.Apply_On` contains `join`/`quit`
2. Verify no other plugin cancels the event
3. Check `Stop_On_First_Match` — first match stops all further groups
4. Ensure message group order: most specific on top
5. If messages appear then disappear, check if `Join_Quit_Cooldown` is enabled

### "Join/quit messages being spammed by laggy players"
- Set `Join_Quit_Cooldown` to prevent message spam (e.g., `10 seconds`)
- This suppresses repeated messages when players quickly rejoin
- Enable `player-message` debug to verify cooldown is working

### "First-join detection not working"
- Use `require sender script {statistic_PLAY_ONE_MINUTE} == 0`
- MC 1.13+: `PLAY_ONE_MINUTE` (misleading name — counts in ticks)
- MC 1.7-1.12: `PLAY_ONE_TICK`
- Place first-join group ABOVE default join (Stop_On_First_Match)

### "Death message shows wrong format"
1. Check `require cause` matches the death type
2. Entity names changed in MC 1.20.5+ (LIGHTNING_BOLT, TNT, END_CRYSTAL)
3. Killer item supports wildcards (`*_SWORD`, `DIAMOND_*`)
4. `{killer}` is null for environmental deaths — check for it

### "Timed messages not cycling"
- Default is sequential (not random). Use `random true` for random.
- `delay` per-group overrides global `Timed_Delay`
- Timed messages skip first run after reload (prevents spam)
- Timer resets on `/chc reload`

### "Message shows to wrong players"
- Use `require receiver world/perm/script` to filter recipients
- `require self` / `ignore self` controls self-visibility
- Channel conditions restrict to channel members

## Key File Paths

- PlayerMessage: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/PlayerMessage.java`
- PlayerMessages: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/PlayerMessages.java`
- DeathMessage: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/DeathMessage.java`
- TimedMessage: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/TimedMessage.java`
- Message files: `chatcontrol-bukkit/src/main/resources/messages/`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Messages section)

## API Events

- `PlayerMessageEvent` — fired for all message broadcasts (cancellable)

## Foundation Integration

- `RuleSetReader` — parses .rs file format
- `Operator` — base class providing timing, commands, and visual actions
- `SimpleComponent` — builds formatted output
- `Format` — resolves format references
