---
name: groups
description: 'ChatControl permission groups: group-based setting overrides, chatcontrol.group.{name} permissions, per-group message delays, similarity thresholds, channel limits, MOTD, sound notify format. Use when configuring group overrides or diagnosing group permission issues.'
---

# Permission Groups

## Overview

ChatControl groups override global settings per-player based on the `chatcontrol.group.<name>` permission. Groups are NOT Vault permission groups — they are ChatControl-specific permission-based overrides.

**Important distinction:** There are TWO unrelated "group" concepts:
1. **PlayerGroup** (this skill) — permission-based setting overrides in `settings.yml` → `Groups`
2. **Rule Group** — reusable operator sets in `rules/groups.rs` (see rules-engine skill)

## Architecture

### Key Classes
- `PlayerGroup<T>` (model/PlayerGroup.java) — generic class holding a setting type and default
- `PlayerGroup.Type` — enum of overridable settings

### Resolution Logic
```
PlayerGroup.getFor(player)
  → Iterate Settings.Groups.LIST (order matters)
    → For each group: check if player has chatcontrol.group.<name>
      → First match wins → return group value
  → No match → return global default
```

## Configuration (`settings.yml` → `Groups`)

### Overridable Settings

| Setting | Type | PlayerGroup.Type | Default Source |
|---------|------|------------------|--------------|
| `Max_Read_Channels` | Integer | MAX_READ_CHANNELS | `Channels.Max_Read_Channels` |
| `Message_Delay` | SimpleTime | MESSAGE_DELAY | `Anti_Spam.Chat.Delay` |
| `Message_Similarity` | Double | MESSAGE_SIMILARITY | `Anti_Spam.Chat.Similarity` |
| `Command_Delay` | SimpleTime | COMMAND_DELAY | `Anti_Spam.Commands.Delay` |
| `Command_Similarity` | Double | COMMAND_SIMILARITY | `Anti_Spam.Commands.Similarity` |
| `Sound_Notify_Format` | String | SOUND_NOTIFY_FORMAT | global format |
| `Motd` | String | MOTD | global MOTD |

### Example Configuration
```yaml
Groups:
  vip:
    Max_Read_Channels: 5
    Message_Delay: 0.5 seconds
    Message_Similarity: 60
  admin:
    Max_Read_Channels: 10
    Message_Delay: 0 seconds
    Message_Similarity: 0
    Command_Similarity: 0
```

## Permissions

- `chatcontrol.group.<name>` — assigns player to group
- First matching group wins if player has multiple group permissions
- Groups are checked in the order they appear in settings.yml

## Common Issues & Solutions

### "Group override not applying"
1. Verify permission: `chatcontrol.group.<name>` (not Vault group name)
2. Check group order in settings.yml — first match wins
3. Ensure the setting key is spelled correctly (case-sensitive)
4. Use `/chc info <player>` to check resolved values

### "Vault groups vs ChatControl groups"
- ChatControl does NOT use Vault group names
- Assign `chatcontrol.group.vip` permission to your Vault VIP group
- This is a deliberate design: works with any permissions plugin

### "Multiple groups conflicting"
- Player gets the FIRST matching group only
- Order groups from most specific (highest rank) to least
- Admin should be listed before VIP if admin has VIP permission too

### "Group delay set to 0 but still delayed"
- Check channel-specific `Message_Delay` override (takes priority)
- Check `chatcontrol.bypass.delay.chat` permission instead

## Key File Paths

- PlayerGroup class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/PlayerGroup.java`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Groups section)
- Permissions: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/model/Permissions.java`

## Foundation Integration

- `HookManager` — uses Vault for offline permission checks (`getForUUID()`)
- `SimpleTime` — parses delay durations
