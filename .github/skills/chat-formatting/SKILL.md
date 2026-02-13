---
name: chat-formatting
description: 'ChatControl format system: format files, parts, placeholders, hover/click events, gradients, images, MiniMessage, colors, decorations. Use when working on format code, diagnosing format issues, or configuring chat appearance.'
---

# Chat Formatting System

## Overview

Formats define how messages appear in chat. Each format is a YAML file in `formats/` containing ordered "parts" — conditional sections with text, hover events, click actions, and images. Formats support MiniMessage, legacy `&` colors, gradients, and JavaScript conditions.

## Architecture

### Key Classes
- `Format` (model/Format.java) — loads from `formats/*.yml`, builds `SimpleComponent` via `Format.build()`
- `Format.Part` — one section of a format with message, conditions, hover/click
- `Colors` (model/Colors.java) — manages color permissions and application
- `TagResolverColorsForPerm` — resolves which MiniMessage tags a player can use based on permissions
- `SimpleComponent` (Foundation) — the component builder that assembles the final output

### Format Resolution Flow
```
Channel/Command specifies Format name
  → Format.parse(name) loads formats/{name}.yml (or creates literal format)
    → For each Part (top to bottom):
      1. Check Sender_Permission / Sender_Condition / Sender_Variable
      2. Replace variables in Message text
      3. Apply Hover / Click / Image
      4. Set Receiver_Permission / Receiver_Condition / Receiver_Variable (view conditions)
    → Append all passing parts into SimpleComponent
  → Return final component
```

## Configuration

### Format File (`formats/*.yml`)

Root keys:
- `New_Line_Per_Part` (boolean) — insert newline between parts
- `Parts` (map) — named format parts, processed top-to-bottom

### Part Keys

| Key | Type | Purpose |
|-----|------|---------|
| `Message` | String/List | **Required**. Text to display. Supports MiniMessage + `{variables}` |
| `Sender_Permission` | String | Permission sender must have for this part to show |
| `Sender_Condition` | String | JavaScript expression returning boolean (sender context) |
| `Sender_Variable` | String | Variable equality check (e.g., `{var} value`) |
| `Receiver_Permission` | String | Permission receiver must have to see this part |
| `Receiver_Condition` | String | JavaScript expression per-receiver |
| `Receiver_Variable` | String | Variable check per-receiver |
| `Hover` | String/List | Tooltip text on hover |
| `Hover_Item` | String | JavaScript returning ItemStack for hover |
| `Open_Url` | String | URL to open on click |
| `Suggest_Command` | String | Suggest command on click |
| `Run_Command` | String | Execute command on click (**only 1 allowed** — MC limitation) |
| `Insertion` | String | Shift+click insertion text |
| `Copy_To_Clipboard` | String | Copy on click (MC 1.16+) |
| `Gradient` | String | Color gradient: `COLOR - COLOR` (e.g., `RED - GOLD`) |
| `Image_File` | String | Image from `images/` folder |
| `Image_Head` | String | Player head as ASCII art |
| `Image_Url` | String | Remote image URL |
| `Image_Height` | Integer | Image height in lines (default 8) |
| `Image_Type` | Enum | `BLOCK`, `DARK_SHADE`, etc. |

### Color Permissions

Colors are permission-gated in `settings.yml` under `Colors`:

- `Colors.Apply_On` — list of contexts: `chat`, `book`, `sign`, `anvil`, `me`, `say`, `private_message`, `prefix`, `nick`, `suffix`
- `chatcontrol.color.{name}` — allow specific `&` color or `<red>` MiniMessage tag
- `chatcontrol.hexcolor.{hexcode}` — allow `<#CC11FF>` hex colors
- `chatcontrol.action.{action}` — allow `<hover>`, `<click>`, `<insertion>`, `<rainbow>`, `<font>`
- `chatcontrol.use.color.{type}` — per-context toggle (default: true)

### Default Format Files

| File | Used By | Description |
|------|---------|-------------|
| `chat.yml` | `standard` channel | Full format with delete button, VIP prefix, hover, click |
| `global-chat.yml` | `global` channel | Simple `[channel] name: message` format |
| `admin-chat.yml` | `admin` channel | Admin chat format |
| `pm-sender.yml` | PM system | Private message sender view |
| `pm-receiver.yml` | PM system | Private message receiver view |
| `motd.yml` | MOTD | Message of the day |
| `spy.yml` / `spy-*.yml` | Spy system | Various spy format overrides |
| `helpop.yml` | Helpop | Help request format |

## Common Variables in Formats

- `{player_name}`, `{player_nick}` — username or nickname
- `{player_prefix}`, `{player_suffix}` — from Vault
- `{player_prefix+}` — prefix with auto-space (space only if prefix exists)
- `{player_chat_color}`, `{player_chat_decoration}` — from `/chc color`
- `{player_group}` — primary permission group
- `{player_channel}` — current channel name
- `{message}` — the chat message content
- `{message_uuid}` — for message removal feature
- `{date}` — current timestamp
- `{sender_is_discord}` — true if from Discord
- `{player_channel_mode_{channel}}` — read/write/none
- `{player_is_spying_{channel}}` — spy status

## Common Issues & Solutions

### "Format part not showing"
1. Check sender has required permission (`Sender_Permission`)
2. Check receiver has required permission (`Receiver_Permission`)
3. Test JavaScript conditions with `/chc script` command
4. Verify variable syntax: `{player_prefix+}` vs `{player_prefix}`

### "Colors not working"
1. Verify `Colors.Apply_On` contains the context (e.g., `chat`)
2. Check player has `chatcontrol.color.{name}` permission
3. Rules may strip colors — look for `then replace` operators in rules
4. Hex colors require explicit `chatcontrol.hexcolor.*` permission

### "Gradient breaks with inner colors"
Gradients are "unsafe" — MiniMessage tags inside `Message` break the gradient rendering. Use gradients only on plain text without inner color tags.

### "Run_Command only works once"
Minecraft limitation: only ONE `Run_Command` per part. Split into separate parts if multiple commands needed.

### "Hover not showing on old versions"
Pre-1.13: max 21 characters per hover line. Use shorter text.

## Key File Paths

- Format files: `chatcontrol-bukkit/src/main/resources/formats/`
- Format class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Format.java`
- Colors class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Colors.java`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Colors section)

## Foundation Integration

- `SimpleComponent` — Foundation's component builder, used by `Format.build()`
- `Variables` — Foundation's placeholder replacement system
- `ChatImage` — Foundation's ASCII art image renderer
- `JavaScriptExecutor` — evaluates `Sender_Condition` / `Receiver_Condition`
- `CompChatColor` — Foundation's cross-version color abstraction
