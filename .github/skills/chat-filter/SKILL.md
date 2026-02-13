---
name: chat-filter
description: 'ChatControl built-in chat filters: anti-spam, anti-caps, anti-bot, parrot detection, similarity checking, message delay, grammar correction, newcomer restrictions, unicode filtering. Use when diagnosing spam protection, caps issues, bot detection, or message rate limiting.'
---

# Chat Filter System

## Overview

The built-in chat filter (Checker) runs BEFORE the rules engine. It handles anti-spam, anti-caps, anti-bot, parrot detection, similarity checking, and grammar corrections. Configured in `settings.yml` under `Anti_Spam`, `Anti_Caps`, `Anti_Bot`, and `Newcomer` sections.

## Architecture

### Key Classes
- `Checker` (model/Checker.java) — main filter entry point
- `Newcomer` (model/Newcomer.java) — newcomer restriction logic
- `WarningPoints` (model/WarningPoints.java) — warning point tracking and thresholds

### Filter Pipeline (order matters)
```
Message received
  → Anti-Bot checks (moved? cooldown expired?)
    → Newcomer restrictions (playtime threshold checks)
      → Anti-Spam delay check (time between messages)
        → Anti-Spam period limit (max messages in time window)
          → Similarity check (compared to past messages)
            → Parrot detection (copying other players)
              → Anti-Caps processing
                → Grammar corrections (capitalize, period)
                  → Rules engine (separate system)
```

## Configuration

### Anti-Spam (`settings.yml` → `Anti_Spam`)

**Chat:**

| Key | Default | Purpose |
|-----|---------|---------|
| `Delay` | `1 second` | Min time between messages |
| `Similarity` | 80% | Threshold for duplicate detection (0-100) |
| `Similarity_Past_Messages` | 5 | How many past messages to compare against |
| `Similarity_Start_At` | 1 | Number of breaches before blocking |
| `Similarity_Forgive_Time` | `5 minutes` | Reset breach counter after this time |
| `Whitelist_Delay` | `[]` | Regex patterns to bypass delay |
| `Whitelist_Similarity` | `[]` | Regex patterns to bypass similarity |
| `Limit.Period` | `5 seconds` | Time window for rate limiting |
| `Limit.Max_Messages` | 5 | Max messages in period |
| `Parrot.Enabled` | false | Detect copying others |
| `Parrot.Delay` | `5 seconds` | Time window for parrot check |
| `Parrot.Similarity` | 80% | Match threshold |
| `Parrot.Whitelist` | `[hi, hello, ...]` | Allowed repeated phrases |

**Commands:** Same structure as Chat, plus:
- `Similarity_Min_Args` — ignore commands with fewer arguments than this

### Anti-Caps (`settings.yml` → `Anti_Caps`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | true | Toggle caps filtering in chat |
| `Enabled_In_Commands` | `[]` | Commands to filter (e.g., `/tell`) |
| `Min_Message_Length` | 5 | Min chars before checking |
| `Min_Caps_Percentage` | 50% | % caps to trigger |
| `Min_Caps_In_A_Row` | 5 | Consecutive caps to trigger |
| `Whitelist` | `[LOL, OMG, ...]` | Allowed all-caps words |

**Processing behavior:**
- Auto-lowercases while preserving sentence structure
- Preserves player names and URLs
- Whitelisted words stay uppercase

### Anti-Bot (`settings.yml` → `Anti_Bot`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Block_Chat_Until_Moved` | false | Require movement before chatting |
| `Block_Commands_Until_Moved` | `[]` | Commands blocked until movement |
| `Block_Same_Text_Signs` | false | Block sequential identical signs |
| `Disallowed_Usernames` | regex list | Block/kick invalid usernames |
| `Cooldown.Chat_After_Login` | `0 seconds` | Chat delay after join |
| `Cooldown.Command_After_Login` | `0 seconds` | Command delay after join |
| `Join_Flood.Enabled` | false | Detect bot swarms |
| `Join_Flood.Join_Threshold` | `4 players / 1 second` | Trigger threshold |
| `Join_Flood.Min_Players` | 2 | Min spam players to trigger |
| `Join_Flood.Commands` | `[kick {player}]` | Actions on detected bots |

### Newcomer Restrictions (`settings.yml` → `Newcomer`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Threshold` | `15 minutes` | Playtime before full access |
| `Worlds` | `["*"]` | Which worlds check playtime |
| `Permissions` | `[]` | Auto-applied permissions (removed after threshold) |
| `Restrict_Seeing_Chat` | false | Hide chat from newcomers |
| `Restrict_Chat` | false | Block newcomer chat |
| `Restrict_Commands.Enabled` | false | Block newcomer commands |
| `Restrict_Commands.Whitelist` | `[]` | Allowed commands during newcomer period |

**Important:** `Threshold` uses **playtime** (from world stats), NOT time since first join.

### Warning Points (`settings.yml` → `Warning_Points`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | false | Toggle warning points system |
| `Sets` | map | Named threshold groups with actions |
| `Reset_Task.Period` | `30 minutes` | How often points decay |
| `Reset_Task.Remove` | map per set | Points to remove per cycle |
| `Triggers` | map | Which filter breaches add points |

**Trigger format:** `<set> <points>` or `<set> <javascript formula>`
```yaml
Chat_Similarity: spam 4 * ({similarity_percentage_double} / 2)
```

**Sets define thresholds with commands:**
```yaml
global:
  5: 'tell {player} Warning: you have {warn_points} points'
  10: 'kick {player} Too many violations'
```

## Bypass Permissions

- `chatcontrol.bypass.delay.chat` / `.command` — skip delay
- `chatcontrol.bypass.similarity.chat` / `.command` — skip similarity
- `chatcontrol.bypass.caps` — skip caps filter
- `chatcontrol.bypass.move` — skip must-move check
- `chatcontrol.bypass.newcomer` — skip newcomer restrictions
- `chatcontrol.bypass.warning.points` — skip all warning points
- `chatcontrol.bypass.parrot` — skip parrot detection
- `chatcontrol.bypass.period.chat` / `.command` — skip rate limit

## Common Issues & Solutions

### "Messages blocked but player isn't spamming"
1. Check `Similarity` threshold — may be too low (80% is usually good)
2. Check `Similarity_Past_Messages` — compare against fewer messages
3. Add pattern to `Whitelist_Similarity` if it's a false positive
4. `Similarity_Start_At` > 1 allows first few duplicates through

### "Parrot detection false positives"
- Add common phrases to `Parrot.Whitelist` (greetings like "hi", "gg")
- Increase `Parrot.Similarity` threshold
- Decrease `Parrot.Delay` window

### "Anti-caps breaks abbreviations"
- Add abbreviations to `Anti_Caps.Whitelist` (e.g., `PVP`, `OMG`)
- Increase `Min_Caps_In_A_Row` for less aggressive detection

### "Newcomer threshold wrong"
- Uses `PLAY_ONE_MINUTE` statistic (actually measures ticks, not minutes)
- Requires world stats file: `world/stats/<uuid>.json`
- Does NOT count time on other servers in a proxy network

### "Warning points not accumulating"
- Enable `Warning_Points.Enabled: true`
- Verify trigger formulas are valid JavaScript
- Check set names match between `Sets` and rule `then points` calls
- Points reset on reload (stored in memory)

## Key File Paths

- Checker class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Checker.java`
- Newcomer: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Newcomer.java`
- Warning points: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/WarningPoints.java`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Anti_Spam, Anti_Caps, Anti_Bot, Newcomer, Warning_Points)

## Foundation Integration

- `ChatUtil.getSimilarityPercentage()` — string similarity comparison
- `SimpleTime` — time duration parsing
- `ExpiringMap` — delay tracking with automatic expiration
- `SenderCache` — stores past messages for similarity comparison
