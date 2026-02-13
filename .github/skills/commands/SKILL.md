---
name: commands
description: 'ChatControl command system: /chc subcommands, /channel, /tell, /reply, /ignore, /mail, /mute, /spy, /tag, /toggle, /me, /say, /motd, /list, /realname, permissions, tab completion. Use when working on command code, diagnosing command issues, or looking up command syntax.'
---

# Command System

## Overview

ChatControl registers a main `/chc` (ChatControl) command group with many subcommands, plus standalone commands like `/tell`, `/channel`, `/mail`, etc. All command aliases are configurable in `settings.yml`.

## Architecture

### Key Classes
- `ChatControlCommands` (command/chatcontrol/) — main `/chc` command group
- `ChannelCommands` (command/channel/) — `/channel` command group
- `SharedChatControlCommandCore` — shared helper interface with lookup utilities
- Various `Command*.java` files — standalone commands

### Foundation Command Framework
- `SimpleCommandGroup` — groups subcommands under one label
- `SimpleCommand` / `SimpleSubCommand` — individual command implementations
- `@Permission` / `@PermissionGroup` annotations — permission declarations

## Command Reference

### Main Commands

| Command | Class | Permission | Purpose |
|---------|-------|------------|---------|
| `/tell <player> <msg>` | CommandTell | `chatcontrol.command.tell` | Private message |
| `/reply <msg>` | CommandReply | `chatcontrol.command.reply` | Reply to last PM |
| `/ignore <player>` | CommandIgnore | `chatcontrol.command.ignore` | Ignore player |
| `/mail` | CommandMail | `chatcontrol.command.mail` | Mail system |
| `/me <msg>` | CommandMe | `chatcontrol.command.me` | /me action message |
| `/say <msg>` | CommandSay | `chatcontrol.command.say` | Broadcast as server |
| `/motd` | CommandMotd | `chatcontrol.command.motd` | Show MOTD |
| `/list` | CommandList | `chatcontrol.command.list` | Player list |
| `/mute <type>` | CommandMute | `chatcontrol.command.mute` | Mute player/channel/server |
| `/spy` | CommandSpy | `chatcontrol.command.spy` | Toggle spy mode |
| `/tag <type>` | CommandTag | `chatcontrol.command.tag` | Set prefix/nick/suffix |
| `/toggle <type>` | CommandToggle | `chatcontrol.command.toggle` | Toggle features on/off |
| `/realname <nick>` | CommandRealName | `chatcontrol.command.realname` | Lookup real name from nick |

### Channel Commands (`/channel`)

| Subcommand | Class | Permission | Purpose |
|-----------|-------|------------|---------|
| `/channel join <ch> [mode]` | JoinChannelSubCommand | `chatcontrol.channel.join.*` | Join channel |
| `/channel leave <ch>` | LeaveChannelSubCommand | `chatcontrol.channel.leave.*` | Leave channel |
| `/channel list` | ListChannelSubCommand | `chatcontrol.channel.list` | List channels |
| `/channel send <ch> <msg>` | SendChannelSubCommand | `chatcontrol.channel.send.*` | Send to channel |
| `/channel sendas <ch> <player> <msg>` | SendAsChannelSubCommand | `chatcontrol.channel.sendas.*` | Send as player |
| `/channel set <ch>` | SetChannelSubCommand | `chatcontrol.channel.set` | Set write channel |

### ChatControl Subcommands (`/chc`)

| Subcommand | Class | Permission | Purpose |
|-----------|-------|------------|---------|
| `/chc announce <type> <msg>` | AnnounceSubCommand | `chatcontrol.command.announce.*` | Broadcast announcement |
| `/chc book <file>` | BookSubCommand | `chatcontrol.command.book` | Open book file |
| `/chc clear [type]` | ClearSubCommand | `chatcontrol.command.clear` | Clear chat |
| `/chc color` | ColorSubCommand | `chatcontrol.command.color` | Chat color picker |
| `/chc convert` | ConvertSubCommand | `chatcontrol.command.migrate` | Import from other plugins |
| `/chc forward <cmd>` | ForwardSubCommand | `chatcontrol.command.forward` | Run command on other servers |
| `/chc info <player>` | InfoSubCommand | `chatcontrol.command.info` | Player info panel |
| `/chc log` | LogSubCommand | `chatcontrol.command.log` | View/search logs |
| `/chc message <type> <group>` | MessageSubCommand | `chatcontrol.command.message` | Test messages |
| `/chc points <player>` | PointsSubCommand | `chatcontrol.command.points` | Warning points |
| `/chc purge <days>` | PurgeSubCommand | `chatcontrol.command.purge` | Purge old data |
| `/chc rule <action>` | RuleSubCommand | `chatcontrol.command.rule` | Manage rules |
| `/chc sendformat <format>` | SendFormatSubCommand | `chatcontrol.command.sendformat` | Test format display |
| `/chc tag <type> <player>` | TagSubCommand | `chatcontrol.command.tag` | Admin tag management |
| `/chc tour` | TourSubCommand | `chatcontrol.command.tour` | Plugin tour/tutorial |
| `/chc reload` | (Foundation) | `chatcontrol.command.reload` | Reload configs |
| `/chc debug` | (Foundation) | `chatcontrol.command.debug` | Generate debug ZIP |
| `/chc perms` | (Foundation) | `chatcontrol.command.permissions` | List all permissions |

## Command Aliases

Configurable in `settings.yml` under each feature section:
```yaml
Private_Messages:
  Command_Aliases: [tell, msg, pm, whisper, w]
  Reply_Aliases: [reply, r]
```

## Helper Methods (SharedChatControlCommandCore)

Utilities for all commands:
- `findChannel(name)` — lookup channel or throw error
- `findMode(name)` — parse ChannelMode or throw error
- `findRule(name)` — lookup rule by name
- `findRuleType(name)` — parse RuleType enum
- `findRuleGroup(name)` — lookup rule group
- `findMessage(type, group)` — find PlayerMessage by group
- `findMessageType(name)` — parse PlayerMessageType enum
- `pollDiskCacheOrSelf(name, callback)` — async player cache lookup
- `pollCache(name, callback)` — async lookup by name or nick

## Common Issues & Solutions

### "Command not found"
1. Check command aliases in settings.yml
2. Verify player has the permission node
3. `/chc perms` lists all available permissions
4. Other plugins may override command aliases

### "Tab completion not working"
1. ChatControl uses Foundation's tab completion system
2. Check `tabComplete()` method in the command class
3. Verify `chatcontrol.bypass.tab.complete` isn't interfering

### "Can't tell/reply to offline player"
- PMs require target to be online (or on proxy network)
- Reply requires recent PM exchange (`PlayerCache.replyPlayerName`)

### "Command conflicts with other plugins"
- Change aliases in settings.yml to avoid conflicts
- ChatControl registers commands at plugin load
- Use namespaced commands: `/chatcontrol:tell`

## Key File Paths

- Main command group: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/chatcontrol/`
- Channel commands: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/channel/`
- Standalone commands: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/`
- Shared utilities: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/command/SharedChatControlCommandCore.java`
- Permissions: `chatcontrol-core/src/main/java/org/mineacademy/chatcontrol/model/Permissions.java`

## Foundation Integration

- `SimpleCommandGroup` — groups subcommands, provides help pages
- `SimpleCommand` — base class with permission checks, argument parsing
- `SimpleSubCommand` — subcommand with auto tab completion
- `@Permission` — declares required permission
