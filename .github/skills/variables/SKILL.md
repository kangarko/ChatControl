---
name: variables
description: 'ChatControl variable and placeholder system: PlaceholderAPI expansion, custom JavaScript variables, {variable} syntax, variable files in variables/ folder, sender/receiver placeholders, PlaceholderPrefix, variable conditions. Use when working on variables/placeholders or diagnosing placeholder issues.'
---

# Variable & Placeholder System

## Overview

ChatControl provides two placeholder systems:
1. **Internal variables** — `{variable_name}` syntax used in formats, rules, messages
2. **PlaceholderAPI expansion** — `%chatcontrol_*%` for use by other plugins

Custom variables can be defined in `variables/*.yml` files with JavaScript conditions.

## Architecture

### Key Classes
- `Placeholders` (model/Placeholders.java) — PlaceholderAPI expansion class
- `PlaceholderPrefix` (core: model/PlaceholderPrefix.java) — prefix enum for sender/receiver variables
- `Variable` (Foundation: model/Variable.java) — custom variable definition
- `Variables` (Foundation: model/Variables.java) — variable replacement engine

### Variable Resolution Flow
```
Text with {variables}
  → Variables.builder() creates replacement context
    → Replace sender variables (sender_*)
    → Replace receiver variables (receiver_*)
    → Replace custom variables (from variables/*.yml)
    → Replace PlaceholderAPI %placeholders%
    → Return final text
```

## PlaceholderAPI Placeholders (%chatcontrol_*%)

### Command Labels
- `%chatcontrol_label_channel%`, `%chatcontrol_label_tell%`, `%chatcontrol_label_reply%`, etc.

### Server Status
- `%chatcontrol_server_unmute_remaining%` — server mute time
- `%chatcontrol_network_unmute_remaining%` — network mute time

### Player Identity
- `%chatcontrol_player_server%` — player's server name
- `%chatcontrol_player_newcomer%` — true/false
- `%chatcontrol_player_last_active%` — formatted last activity

### Channels
- `%chatcontrol_player_channel%` — current write channel
- `%chatcontrol_player_in_channel_<name>%` — true/false
- `%chatcontrol_player_channel_mode_<name>%` — read/write/none
- `%chatcontrol_player_channel_range%` — channel range or "none"

### Tags
- `%chatcontrol_player_nick%` — colored nick with prefix
- `%chatcontrol_player_nick_section%` — nick with `§` codes
- `%chatcontrol_player_nick_mini%` — nick with MiniMessage tags
- `%chatcontrol_player_prefix%` — tag prefix
- `%chatcontrol_player_suffix%` — tag suffix

### Chat Customization
- `%chatcontrol_player_chat_color%` — color code
- `%chatcontrol_player_chat_color_name%` — "red" / "none"
- `%chatcontrol_player_chat_color_letter%` — `&c` or hex name
- `%chatcontrol_player_chat_decoration%` — decoration code
- (similar `_name` and `_letter` variants)

### Spying
- `%chatcontrol_player_is_spying_<type>%` — chat/command/private_message/mail/sign/book/anvil
- `%chatcontrol_player_is_spying_<channel>%` — per-channel

### Toggles
- `%chatcontrol_player_is_ignoring_<type>%` — mail/pm/announcement/etc (ToggleType)
- `%chatcontrol_is_ignoring_<message_type>%` — broadcast/bossbar/actionbar (PlayerMessageType)

### Moderation
- `%chatcontrol_player_unmute_remaining%` — player mute time
- `%chatcontrol_player_channel_unmute_remaining%` — channel mute time
- `%chatcontrol_player_reply_target%` — last PM target

### Rule Data
- `%chatcontrol_player_data_<key>%` — custom data from `save key` rules

### Discord
- `%chatcontrol_sender_is_dynmap%` — true for DynMap senders

## Internal Variables ({variable})

### Commonly Used in Formats
- `{player_name}`, `{player_nick}` — identity
- `{player_prefix}`, `{player_suffix}` — Vault prefix/suffix
- `{player_prefix+}` — prefix with auto-space (adds space only if not empty)
- `{player_chat_color}`, `{player_chat_decoration}` — custom colors
- `{player_group}` — primary Vault group
- `{player_channel}` — current channel
- `{message}` — chat message content
- `{message_uuid}` — unique ID for message deletion
- `{date}` — timestamp
- `{server_name}` — server name

### Sender/Receiver System (PlaceholderPrefix)
Variables can be prefixed for sender vs receiver context:
- `{sender_name}` → sender's name
- `{receiver_name}` → receiver's name
- `{player_name}` → context-dependent (sender in most cases)

### Rule Variables
- `{matched_message}` — regex match
- `$0, $1, $2...` — capture groups
- `{rule_name}`, `{rule_group}`, `{rule_match}`, `{rule_type}`
- `{original_message}` — unmodified message

### Channel Variables
- `{player_channel_mode_{channel}}` — read/write/none
- `{player_is_spying_{channel}}` — spy status
- `{sender_is_discord}` — from Discord

## Custom Variable Files (`variables/*.yml`)

Define variables with JavaScript conditions:

### Example: `variables/difficulty-color.yml`
```yaml
Key: difficulty_color
Condition: "{world_difficulty}"
Values:
  PEACEFUL: "<green>"
  EASY: "<yellow>"
  NORMAL: "<gold>"
  HARD: "<red>"
Default: "<white>"
```

### Variable File Keys
| Key | Purpose |
|-----|---------|
| `Key` | Variable name (used as `{key}`) |
| `Condition` | JavaScript expression or placeholder to evaluate |
| `Values` | Map of condition results → replacement values |
| `Default` | Fallback if no value matches |
| `Permission` | Required permission to see this variable |
| `Script` | JavaScript returning the value directly |
| `Hover` | Hover text for this variable |

### Default Variable Files
| File | Purpose |
|------|---------|
| `difficulty-color.yml` | World difficulty color code |
| `item.yml` | Player's held item display |
| `player-has-nick.yml` | Show nick or name |
| `player-item-in-hand.yml` | Item in hand with count |
| `player-vanished-colored.yml` | Colored vanish status |
| `receiver-or-you.yml` | "You" for self, name for others |

## Common Issues & Solutions

### "Placeholder returns empty"
1. Database must be loaded — check `SenderCache.isDatabaseLoaded()`
2. PlaceholderAPI must be installed for `%chatcontrol_*%` variables
3. Offline players return empty (most placeholders are player-only)
4. Check variable name spelling (case-sensitive)

### "Custom variable not working"
1. Verify file is in `variables/` folder with `.yml` extension
2. Check `Key` matches what you use in formats: `{key}`
3. Ensure JavaScript `Condition` evaluates correctly
4. Use `/chc script` to test JavaScript expressions

### "Sender vs receiver variables"
- Use `{sender_*}` / `{receiver_*}` prefixes in formats
- `{player_*}` defaults to sender in most contexts
- Receiver variables only work with per-receiver component rendering

### "PlaceholderAPI from other plugins not parsed"
- ChatControl parses PAPI variables in formats and messages automatically
- Variables are replaced via Foundation's `Variables` class
- Nested PAPI variables work: `{player_prefix}` reads Vault's PAPI output

## Key File Paths

- Placeholders: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Placeholders.java`
- Variable files: `chatcontrol-bukkit/src/main/resources/variables/`
- PlaceholderPrefix: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/model/PlaceholderPrefix.java`
- Foundation Variable: `foundation-core/src/main/java/org/mineacademy/fo/model/Variable.java`
- Foundation Variables: `foundation-core/src/main/java/org/mineacademy/fo/model/Variables.java`

## Foundation Integration

- `Variables.builder()` — creates replacement context with sender/receiver
- `Variable` — loads custom variables from YAML
- `SimpleExpansion` — base class for PlaceholderAPI expansion
- `JavaScriptExecutor` — evaluates JavaScript conditions in variables
- `PAPIPlaceholders` — Foundation's PAPI hook for replacing %% placeholders
