---
name: private-messaging
description: 'ChatControl private messaging: /tell, /reply, /ignore, PM formatting, social spy, vanish handling, proxy PMs, PM toggle, auto-responder, mail system. Use when diagnosing private message issues, PM formatting, ignore/spy, or mail delivery.'
---

# Private Messaging & Mail

## Overview

ChatControl provides `/tell` and `/reply` for private messages with custom formatting, social spy, ignore lists, vanish checking, and proxy support. The mail system allows offline message delivery.

## Architecture

### Key Classes
- `PrivateMessage` (model/PrivateMessage.java) — PM sending logic
- `CommandTell` (command/CommandTell.java) — `/tell` command
- `CommandReply` (command/CommandReply.java) — `/reply` command
- `CommandIgnore` (command/CommandIgnore.java) — `/ignore` command
- `CommandMail` (command/CommandMail.java) — `/mail` command
- `Mail` (model/db/Mail.java) — mail database row

### PM Send Flow
```
/tell <player> <message>
  → CommandTell validates args
    → PrivateMessage.send(sender, receiverCache, message)
      1. Check permissions (write, links)
      2. Strip unauthorized colors
      3. Check receiver not self
      4. Check receiver not vanished (unless bypass)
      5. Check ignore list (bidirectional)
      6. Check receiver has PMs enabled (ToggleType.PRIVATE_MESSAGE)
      7. Check sender has PMs enabled
      8. Filter through rules (Checker)
      9. Fire PrePrivateMessageEvent (API — cancellable)
      10. Format and send to sender (Format_Sender)
      11. Format and send to receiver (Format_Receiver)
      12. Format and send to console (Format_Console)
      13. Show toast notification (if enabled)
      14. Play sound (if configured)
      15. Update reply targets
      16. Log and broadcast to spies
      17. Send to proxy (if enabled)
```

## Configuration (`settings.yml` → `Private_Messages`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Command_Aliases` | `[tell, msg, pm, whisper, w]` | Tell aliases |
| `Reply_Aliases` | `[reply, r]` | Reply aliases |
| `Format_Sender` | `pm-sender` | Format for sender view |
| `Format_Receiver` | `pm-receiver` | Format for receiver view |
| `Format_Console` | string | Console log format |
| `Format_Toast` | string | Toast notification format |
| `Sound` | configured | Sound played to receiver |
| `Toasts` | false | Enable toast notifications |
| `Proxy` | false | Enable cross-server PMs |
| `Sender_Overrides_Receiver_Reply` | false | Reply target tracking |

### PM Format Files
- `formats/pm-sender.yml` — what the sender sees
- `formats/pm-receiver.yml` — what the receiver sees

### PM Variables
- `{sender_*}` — sender's name, prefix, suffix, etc.
- `{receiver_*}` — receiver's name, prefix, suffix, etc.
- `{message}` — the PM content

## Ignore System

`/ignore <player>` toggles bidirectional blocking:
- Ignored players can't send PMs to you
- You can't send PMs to ignored players
- Stored in `PlayerCache.ignoredPlayers` (Set<UUID>)

### Permissions
- `chatcontrol.command.ignore` — use /ignore
- `chatcontrol.command.ignore.list` — list ignored
- `chatcontrol.command.ignore.others` — view others' ignore lists
- `chatcontrol.bypass.reach` — bypass ignore restrictions

## Toggle System

`/toggle <type>` allows players to disable features:
- `private_message` — disable receiving PMs
- `mail` — disable receiving mail
- `announcement` — disable announcements
- `me` — disable /me messages
- `death` / `join` / `kick` / `quit` — disable event messages
- `sound_notify` — disable @mention sounds

Stored in `PlayerCache.toggledOffParts` (Set<ToggleType>)

## Mail System

### Overview
Offline mail delivery with book-based messages, expiration, and auto-responder.

### Key Features
- Send mail to offline players (delivered on next login)
- Book format for rich text
- Auto-purge configurable (`Settings.Mail.CLEAN_AFTER`)
- Recipient notification on login
- UUID-based mail lookup

### Mail Configuration (`settings.yml` → `Mail`)
| Key | Default | Purpose |
|-----|---------|---------|
| `Command_Aliases` | `[mail]` | Mail command aliases |
| `Forward_Using_Proxy` | false | Proxy mail support |
| `Clean_After` | `30 days` | Auto-purge old mail |

### Permissions
- `chatcontrol.command.mail` — basic mail access
- `chatcontrol.command.mail.send.online` — send to online players
- `chatcontrol.command.mail.send.all` — send to offline players

### Auto-Responder
```java
cache.setAutoResponder(book, expirationTime);
```
Automatically replies to incoming mail with a book message until expiration.

## Social Spy

Staff with `chatcontrol.spy.private_message` see all PMs:
- Uses spy format (`Settings.Spy.FORMAT_PRIVATE_MESSAGE`)
- Configurable Discord webhook
- Proxy spy forwarding available

## Common Issues & Solutions

### "Can't send PM — 'player is ignoring you'"
1. Check if sender is in receiver's ignore list
2. Check if receiver is in sender's ignore list
3. `chatcontrol.bypass.reach` bypasses ignore

### "PM not delivered — 'player has PMs disabled'"
- Receiver toggled off PMs: `/toggle private_message`
- `chatcontrol.bypass.reach` bypasses toggle

### "Can't PM vanished player"
- Vanished players appear offline to non-staff
- `chatcontrol.bypass.vanish` sees vanished players
- Proxy: vanish status synced via SyncType.VANISH

### "Reply not working"
- Reply target expires on disconnect
- `Sender_Overrides_Receiver_Reply: true` — sender PM updates receiver's reply target
- Both sender and receiver track reply separately in PlayerCache

### "Proxy PMs not working"
1. `Private_Messages.Proxy: true` in settings.yml
2. Proxy plugin installed on BungeeCord/Velocity
3. Same `Server_Name` in proxy.yml across servers
4. Uses `ChatControlProxyMessage.MESSAGE` for forwarding

### "Mail not delivered to offline player"
1. Check `Mail.Forward_Using_Proxy` if on network
2. Verify player exists in database
3. Check `Mail.Clean_After` hasn't purged old mail
4. Recipient gets notification on next login

## Key File Paths

- PrivateMessage: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/PrivateMessage.java`
- Tell: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandTell.java`
- Reply: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandReply.java`
- Ignore: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandIgnore.java`
- Mail: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/CommandMail.java`
- Mail storage: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/Mail.java`
- PM formats: `chatcontrol-bukkit/src/main/resources/formats/pm-sender.yml`, `pm-receiver.yml`
- Settings: `chatcontrol-bukkit/src/main/resources/settings.yml` (Private_Messages, Mail)

## API Events

- `PrePrivateMessageEvent` — before PM delivery (cancellable, can modify message)
- `SpyEvent` — fired when spy broadcast includes this PM

## Foundation Integration

- `Format` — format loading and building
- `SimpleComponent` — component output
- `SimpleBook` — book-based mail content
- `Variables.builder()` — sender/receiver placeholder replacement
