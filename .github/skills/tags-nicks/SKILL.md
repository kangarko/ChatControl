---
name: tags-nicks
description: 'ChatControl tags and nicks: player prefix, suffix, nick, /tag command, vault integration, tag rules, tag colors, tag formatting, permission-based tags, nick validation. Use when diagnosing tag display, nick setting, or player name customization issues.'
---

# Tags & Nicks

## Overview

ChatControl allows players to set custom tags (prefix, nick, suffix) that display in chat, tab list, and above head. Tags can be color-formatted with permission-based restrictions and rule validation.

## Architecture

### Key Classes
- `Tag` (model/Tag.java) — tag management logic
- `TagType` — enum: `PREFIX`, `NICK`, `SUFFIX`
- `CommandTag` (command/CommandTag.java) — `/tag` command
- `PlayerCache` — stores tag data: `playerTag` / `playerNick` / `playerSuffix`

### Tag Types
| Type | Description | Display Position |
|------|-------------|-----------------|
| `PREFIX` | Text before name | `[PREFIX] Name` |
| `NICK` | Custom display name | Replaces actual name |
| `SUFFIX` | Text after name | `Name [SUFFIX]` |

### Tag Flow
```
/tag <type> <value>
  → CommandTag validates args
    → Tag.setTag(sender, type, value)
      1. Check permission (chatcontrol.command.tag.<type>)
      2. Strip unauthorized colors
      3. Validate against tag rules (rules/tag.rs)
      4. Check min/max length
      5. Check banned words/patterns
      6. Store in PlayerCache
      7. Update display (chat format, tab, nametag)
      8. Save to database
```

## Configuration (`settings.yml` → `Tag`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | false | Master toggle |
| `Apply_On.Chat` | true | Show tag in chat |
| `Apply_On.Tab` | false | Show tag in tab list |
| `Apply_On.Nametag` | false | Show tag above head |
| `Min_Length` | 2 | Minimum tag length |
| `Max_Length` | 16 | Maximum tag length |
| `Max_Nick_Length` | 16 | Max nick length |

### Color Permissions
Players need explicit permissions for tag colors:
- `chatcontrol.tag.color.<color>` — specific color (e.g., `chatcontrol.tag.color.red`)
- `chatcontrol.tag.color.*` — all colors
- `chatcontrol.tag.color.bold`, `.italic`, `.underline`, `.strikethrough` — decorations
- `chatcontrol.tag.color.gradient` — gradient colors
- `chatcontrol.tag.color.hex` — hex colors (#RRGGBB)

### Tag Rules (`rules/tag.rs`)
```
# Block offensive tags
match \b(badword|admin|mod)\bi
then deny
warn &cThat tag is not allowed!

# Enforce tag format
match ^[a-zA-Z0-9_&§ ]+$
require true
then deny
warn &cTags may only contain letters, numbers, and colors.
```

Tag rules use the same `.rs` rule engine as chat rules but apply specifically to tag input.

## Commands

### `/tag <type> <value>`
Set your own tag:
- `/tag prefix &a[VIP]` — set prefix
- `/tag nick &bCoolName` — set nick
- `/tag suffix &7[Builder]` — set suffix
- Permission: `chatcontrol.command.tag.<type>`

### `/tag <type> off`
Remove your tag:
- `/tag prefix off` — remove prefix
- Permission: `chatcontrol.command.tag.<type>`

### `/tag <type> <player> <value>`
Set another player's tag:
- `/tag nick Steve &6KingSteve`
- Permission: `chatcontrol.command.tag.<type>.others`

### Tab Completion
- Colors: completes available color codes based on permissions
- Types: completes prefix/nick/suffix
- Players: completes online player names

## Tag Variables

Tags are available as variables in formats:
- `{player_tag_prefix}` or `{tag_prefix}` — player's prefix tag
- `{player_tag_nick}` or `{tag_nick}` — player's nick tag  
- `{player_tag_suffix}` or `{tag_suffix}` — player's suffix tag
- `{player_nick}` — nick if set, otherwise real name

### In Format Files
```yaml
# formats/chat.yml
Parts:
  prefix:
    Message: '{player_tag_prefix} '
    Condition: '{player_tag_prefix} != ""'
  name:
    Message: '{player_tag_nick|player_name}'  # nick or real name
  suffix:
    Message: ' {player_tag_suffix}'
    Condition: '{player_tag_suffix} != ""'
```

## Storage

Tags are stored in `PlayerCache` and persisted via database:
- `playerTag` (String) — prefix tag value
- `playerNick` (String) — nick value
- `playerSuffix` (String) — suffix value

Database columns: `tag_prefix`, `tag_nick`, `tag_suffix` in player data table.

### Vault Integration
If Vault is present and configured, tags can integrate with Vault's prefix/suffix system:
- ChatControl tags can override Vault values
- Or Vault values used as fallback when no tag set

## Common Issues & Solutions

### "Tag not showing in chat"
1. Check `Tag.Enabled: true`
2. Check `Tag.Apply_On.Chat: true`
3. Format file must use `{player_tag_prefix}` / `{player_tag_nick}` / `{player_tag_suffix}` variables
4. Check format condition isn't hiding empty tags incorrectly

### "Tag colors not working"
1. Player needs `chatcontrol.tag.color.<color>` permission
2. `&` codes must match server's color system
3. Hex colors need `chatcontrol.tag.color.hex` permission
4. Gradients need `chatcontrol.tag.color.gradient` permission

### "'Tag is not allowed' error"
1. Check `rules/tag.rs` for blocking rules
2. Check min/max length limits
3. Tag may contain banned word matching a rule pattern
4. Special characters may be blocked by rules

### "Nick not replacing name in tab/nametag"
1. Check `Tag.Apply_On.Tab: true` for tab list
2. Check `Tag.Apply_On.Nametag: true` for above-head
3. Tab/nametag has 16 char limit (Minecraft protocol)
4. Color codes count toward character limit in tab/nametag

### "Tag lost on rejoin"
1. Check database is configured and working
2. Tag should persist via PlayerCache → database save
3. Check database.yml connection settings
4. Look for database errors in console on join

### "Tag breaks chat format alignment"
- Tags with variable lengths cause alignment issues in fixed-width formats
- Use condition parts to hide empty tags
- Consider padding or consistent tag lengths

## Permissions Summary

| Permission | Purpose |
|------------|---------|
| `chatcontrol.command.tag.prefix` | Set own prefix |
| `chatcontrol.command.tag.nick` | Set own nick |
| `chatcontrol.command.tag.suffix` | Set own suffix |
| `chatcontrol.command.tag.<type>.others` | Set others' tags |
| `chatcontrol.tag.color.<color>` | Use specific color |
| `chatcontrol.tag.color.*` | All colors |
| `chatcontrol.tag.color.hex` | Hex colors |
| `chatcontrol.tag.color.gradient` | Gradients |
| `chatcontrol.bypass.tag` | Bypass tag rules |

## Key File Paths

- Tag: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Tag.java`
- CommandTag: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandTag.java`
- Tag rules: `chatcontrol-bukkit/src/main/resources/rules/tag.rs`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Tag section)
- Player data: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/PlayerCache.java`

## Foundation Integration

- `PlayerCache` — tag storage and persistence
- `CompChatColor` — color permission checking
- `Variables` — tag variable registration
- `Database` — tag persistence
