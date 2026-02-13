---
name: books-announcements
description: 'ChatControl books and announcements: timed announcements, MOTD, broadcast, announcement types (CHAT/TITLE/ACTIONBAR/BOSSBAR/TOAST/IMAGE), books, auto-broadcast scheduling. Use when diagnosing announcement display, book authoring, MOTD, or scheduled broadcast issues.'
---

# Books & Announcements

## Overview

ChatControl supports timed announcements in 6 display types, book-based rich text (MOTD, help books), and broadcast commands. Announcements cycle through message groups on configurable schedules.

## Architecture

### Key Classes
- `Announce` (model/Announce.java) — announcement scheduling engine
- `AnnounceType` — enum: `CHAT`, `IMAGE`, `TITLE`, `ACTIONBAR`, `BOSSBAR`, `TOAST`
- `CommandAnnounce` (command/CommandAnnounce.java) — `/chc announce` subcommand
- `Book` (model/Book.java) — book loading and display
- `CommandMotd` (command/CommandMotd.java) — MOTD display
- `SimpleBook` (Foundation) — low-level book API

### Announcement Types
| Type | Description | Example |
|------|-------------|---------|
| `CHAT` | Regular chat message(s) | Multi-line text with formatting |
| `IMAGE` | ASCII art image in chat | Logo/banner display |
| `TITLE` | Full-screen title + subtitle | Big announcement overlay |
| `ACTIONBAR` | Text above hotbar | Subtle notification |
| `BOSSBAR` | Boss bar at top | Timed bar with color/style |
| `TOAST` | Achievement popup | Toast notification |

### Announcement Flow
```
Server startup
  → Announce.start() schedules repeating task
    → Every tick period:
      1. Select next announcement group (round-robin per player OR random)
      2. Filter eligible players (permissions, worlds, toggles)
      3. For each player, check conditions
      4. Display using AnnounceType handler
      5. Track last-shown index per player
```

## Configuration

### Announcement Files (`settings.yml` → `Announcements`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Enabled` | false | Master toggle |
| `Delay` | `5 minutes` | Time between announcements |
| `Random` | false | Random vs sequential order |
| `Header` / `Footer` | none | Message prefix/suffix |
| `Worlds` | `[]` | Limit to specific worlds |
| `Permission` | none | Require permission to see |

### Announcement Message Files
Located in `messages/` directory with `.rs` format:

```
# messages/announcements.rs
group tips
type chat
message:
- &eTip: Use /help for commands!
- &eTip: Visit our website!

group rules_reminder
type title
title: &cRemember
subtitle: &7Follow the server rules!
stay: 40

group image_ad
type image
image: logo
message:
- &bWelcome to our server!
```

### Announcement Conditions
```
group vip_announce
type toast
permission chatcontrol.announce.vip
condition: {player_world} == world_vip
message:
- &6VIP Event starting soon!
```

### Boss Bar Options
```
group bossbar_demo
type bossbar
message:
- &aServer restarting in 5 minutes
color: RED
style: SEGMENTED_6
seconds: 30
```

## Books

### Book System
Books are YAML files in `books/` directory loaded as Minecraft book items:

```yaml
# books/help.yml
Title: Help Book
Author: Server
Pages:
  - |
    &lWelcome!
    
    &7This is page 1
    [Click here](run_command:/spawn)
  - |
    &lPage 2
    
    &7More content here
```

### Book Features
- **Rich text**: Colors, bold, italic, formatting
- **Click actions**: `run_command`, `suggest_command`, `open_url`
- **Hover text**: Tooltips on text
- **Placeholders**: All ChatControl variables work
- **Pages**: Multi-page support

### MOTD (Message of the Day)
```yaml
# settings.yml → Motd
Motd:
  Enabled: true
  Delay: 1 second
  Format: motd_book  # references books/motd_book.yml
```

Players see the MOTD book on join after the configured delay.

### Built-in Books
- `books/help.yml` — help book (shown via `/chc book help`)
- Custom books: Add any `.yml` to `books/` folder

### Book Commands
- `/chc book <name>` — open a book
- `/chc book <name> <player>` — open book for player
- Permission: `chatcontrol.command.book`

## Broadcast Command

`/chc announce <type>` — manual broadcast:
- `/chc announce chat <message>` — send chat announcement
- `/chc announce title <message>` — show title
- `/chc announce actionbar <message>` — actionbar text
- `/chc announce bossbar <message>` — boss bar
- `/chc announce toast <message>` — toast popup

Permission: `chatcontrol.command.announce`

## Common Issues & Solutions

### "Announcements not showing"
1. Check `Announcements.Enabled: true` in settings.yml
2. Verify `Delay` isn't too long for testing
3. Check player is in correct `Worlds` (if configured)
4. Check player has required `Permission` (if configured)
5. Check player hasn't toggled off: `/toggle announcement`
6. At least 2 players needed? No — works with 1+

### "Announcements show to wrong players"
- Check `Worlds` filter in announcement config
- Check `permission` in announcement group
- Check `condition` operators in .rs file

### "Book formatting broken"
- YAML multi-line: use `|` for pages
- Colors: `&` codes or MiniMessage
- Click actions: `[text](run_command:/cmd)`
- Max 100 pages, 256 chars per page (Minecraft limit)

### "MOTD not showing on join"
1. `Motd.Enabled: true`
2. `Motd.Delay: 1 second` — needs slight delay for client readiness
3. Book file must exist in `books/` directory
4. Book name in `Motd.Format` must match filename (without .yml)

### "Boss bar disappears too fast"
- Set `seconds` in the bossbar announcement group
- Default is short; increase for longer display

### "Image announcement not rendering"
- Image must be defined in image map
- ASCII art rendered from stored pixel data
- Check console for image loading errors

## Timed Announcement Scheduling

The announcer uses Foundation's `SimpleRunnable` for scheduling:
- Each announcement group has an index tracked per-player
- Sequential mode: increments index, wraps around
- Random mode: picks random group each cycle
- Per-player tracking ensures all groups shown before repeating (sequential)

## Key File Paths

- Announce: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Announce.java`
- Book: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Book.java`
- CommandAnnounce: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandAnnounce.java`
- CommandMotd: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandMotd.java`
- Book files: `chatcontrol-bukkit/src/main/resources/books/`
- Announcement messages: `chatcontrol-bukkit/src/main/resources/messages/`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Announcements, Motd)

## Foundation Integration

- `SimpleBook` — book ItemStack builder
- `SimpleComponent` — component formatting for book pages
- `SimpleRunnable` — scheduled task for announcement cycling
- `CompChatColor` — color/formatting support
