# ChatControl

ChatControl is a premium Minecraft (Spigot/Paper/BungeeCord/Velocity) plugin for chat management, formatting, filtering, and moderation.

## Project Structure

This is a multi-module Maven project:

- `chatcontrol-bukkit/` — Main Bukkit/Spigot/Paper implementation
  - Entry point: `src/main/java/org/mineacademy/chatcontrol/ChatControl.java`
  - Commands: `command/`
  - Listeners: `listener/`
  - Settings/config: `settings/`
  - Operators (rules engine): `operator/`
  - Config files: `src/main/resources/` (settings.yml, rules/, formats/, messages/, lang/)
- `chatcontrol-core/` — Shared core logic across platforms
- `chatcontrol-proxy-core/` — Shared proxy logic
- `chatcontrol-bungeecord/` — BungeeCord implementation
- `chatcontrol-velocity/` — Velocity implementation

## Dependencies

- This project depends on the **Foundation** library: https://github.com/kangarko/foundation
- Foundation provides the core MVC framework, commands, events, menus, databases, and utilities
- Classes from `org.mineacademy.fo.*` are from Foundation, not ChatControl

## When responding to issues

- Reference actual file paths, class names, and config keys
- Distinguish between ChatControl code (`org.mineacademy.chatcontrol.*`) and Foundation code (`org.mineacademy.fo.*`)
- If a bug likely originates in Foundation, say so and reference the Foundation repo
- Config questions should reference the relevant YAML file in `chatcontrol-bukkit/src/main/resources/`
- Most user issues relate to: chat formatting, rules/filters, channels, groups, or proxy sync
- If the issue lacks information to diagnose, ask for: server version, ChatControl version, relevant config snippets, and error logs

## Domain Knowledge Skills

Detailed skill files are in `.github/skills/*/SKILL.md` — each covers a specific subsystem with architecture, configuration, common issues, and file paths:

| Skill | Covers |
|-------|--------|
| `chat-formatting` | Format files, parts, placeholders, hover/click, gradients, MiniMessage |
| `channels` | Channel creation, types, modes, permissions, proxy channels |
| `rules-engine` | .rs rule files, regex, operators, conditions, actions |
| `chat-filter` | Anti-spam, anti-caps, anti-bot, similarity, grammar |
| `groups` | Permission groups, rule groups, group overrides |
| `proxy-sync` | BungeeCord/Velocity, proxy.yml, cross-server sync |
| `database` | SQLite/MySQL, database.yml, player cache, logs |
| `commands` | /chc subcommands, /channel, all commands, permissions |
| `variables` | PlaceholderAPI, JavaScript variables, {variable} syntax |
| `messages` | Join/quit/death/timed messages, .rs format |
| `private-messaging` | /tell, /reply, /ignore, PM formats, spy, mail |
| `books-announcements` | Books, announcements, broadcast, MOTD |
| `mute-warn` | Mute hierarchy, warning points, punishments |
| `tags-nicks` | Player tags, nick, prefix/suffix, /tag |
| `menus` | Color picker, spy toggle, channel GUI |
