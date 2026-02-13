---
name: rules-engine
description: 'ChatControl rules engine: .rs rule files, regex matching, operators, conditions (require/ignore), actions (then replace/rewrite/warn/deny/kick), groups, rule types (chat/command/sign/book/anvil/tag), data persistence, delays. Use when working on rule code, writing custom rules, or diagnosing rule matching issues.'
---

# Rules Engine

## Overview

The rules engine processes chat messages, commands, signs, books, anvils, and tags through regex-based rules. Each rule has a match pattern, optional conditions (require/ignore), and action handlers (then). Rules are defined in `.rs` files under `rules/`. Reusable operator sets are called "groups" (defined in `groups.rs`).

## Architecture

### Key Classes
- `Rule` (operator/Rule.java) — individual rule with regex pattern, extends `RuleOperator`
- `RuleOperator` (operator/RuleOperator.java) — base providing require/ignore conditions and replace/rewrite actions
- `Operator` (operator/Operator.java) — universal base for all operators (timing, actions, data persistence)
- `Rules` (operator/Rules.java) — singleton loader/registry for all rules
- `Group` (operator/Group.java) — reusable operator set referenced by `group <name>`
- `Groups` (operator/Groups.java) — singleton loader for groups from `groups.rs`
- `RuleType` (core: model/RuleType.java) — enum: GLOBAL, CHAT, COMMAND, SIGN, BOOK, ANVIL, TAG

### Rule Processing Flow
```
Message/command/sign/book/anvil/tag input
  → Rules.getRules(type) loads rules for this type
    → For each rule (top to bottom):
      1. @import: imported rules checked first
      2. Compile matcher (apply strip colors/accents if set)
      3. Match regex against message
      4. If match: fire PreRuleMatchEvent (API — cancellable)
      5. Check require/ignore conditions (canFilter())
      6. Execute rule operators (executeOperators())
      7. Execute group operators if `group <name>` set
      8. If `then abort`: stop processing remaining rules
```

## Rule File Syntax (.rs)

### Basic Rule Structure
```
match <regex>
name <optional-identifier>
group <group-name>
<operators...>
```

### Rule-Specific Operators
```
name <identifier>          — name for logging/variables
group <groupName>          — reference operators from groups.rs
ignore event <type>        — skip for this rule type (chat|command|sign|...)
strip colors [true/false]  — strip colors before matching
strip accents [true/false] — strip accents before matching
before replace <regex> with <replacement>  — pre-process message before matching
```

### Condition Operators (require = must match, ignore = skip if matches)

```
require perm <permission> [message]
require variable <varName> [true/false]
require script <javascript>
require command <label>
require gamemode <mode>
require world <worldName>
require region <regionName>
require channel <channelName> [read|write]

ignore perm <permission>
ignore string <regex>
ignore script <javascript>
ignore command <label>
ignore gamemode <mode>
ignore world <world>
ignore region <region>
ignore channel <channel> [mode]
ignore players
ignore commandprefix
ignore discord
ignore muted
```

### Action Operators (then)

**Replacement:**
```
then replace <text|@prolong *>    — replace matched portion (@prolong * = asterisks matching length)
then rewrite <fullMessage>        — replace entire message
then rewritein <world> <message>  — rewrite for specific world
```

**Commands:**
```
then command <command>   — run as player
then console <command>   — run as console
then proxy <server> <command>  — send to proxy server
```

**Messages:**
```
then warn <message>              — send warning to player
then log <consoleMessage>        — log to console
then notify <permission> <msg>   — notify staff with permission
then discord <channelId> <msg>   — send to Discord channel
then write <fileName> <msg>      — append to file
then kick <reason>               — kick the player
```

**Visual:**
```
then toast <material> <style> <message>  — achievement toast
then title <title>|<subtitle>|<fadeIn>|<stay>|<fadeOut>
then actionbar <message>
then bossbar <color> <style> <seconds> <message>
then sound <sound> <volume> <pitch>
then book <bookFile>
```

**Economy & Points:**
```
then fine <amount>
then points <warningSet> <amount>
```

**Control Flow:**
```
then abort          — stop processing remaining rules
then deny           — cancel the message
then deny silently  — cancel without warning
dont log            — skip logging
dont spy            — skip spy broadcast
dont verbose        — skip verbose logging
disabled            — rule exists but is inactive
```

### Timing Operators
```
begins <date>                — activate after date
expires <date>               — deactivate after date
delay <time>                 — global cooldown
player delay <time> [msg]    — per-player cooldown
```

### Data Persistence
```
save key <name> <jsExpression>     — save data per-player
require key <name> [jsCondition]   — check saved data
ignore key <name> [jsCondition]    — skip if data matches
```

## Rule Files

| File | RuleType | Applied To |
|------|----------|-----------|
| `rules/global.rs` | GLOBAL | All types (imported by others) |
| `rules/chat.rs` | CHAT | Chat messages |
| `rules/command.rs` | COMMAND | Player commands |
| `rules/sign.rs` | SIGN | Sign text |
| `rules/book.rs` | BOOK | Book content |
| `rules/anvil.rs` | ANVIL | Anvil item renaming |
| `rules/tag.rs` | TAG | Nick/prefix/suffix |
| `rules/groups.rs` | — | Shared operator groups |

### Import System
```
@import global
```
Place at top of rule file. Imported rules are checked FIRST (reversed order).

## Variables Available in Rules

- `{matched_message}` — the matched portion
- `$0, $1, $2...` — regex capture groups
- `{rule_name}` — rule name
- `{rule_group}` — group name
- `{rule_match}` — the regex pattern
- `{rule_type}` — chat/command/etc
- `{message}` — current message
- `{original_message}` — unmodified original message

## Groups (groups.rs)

Reusable operator collections:
```
group advertisement
ignore perm chatcontrol.bypass.ad
ignore command /register|/login
then warn &c[!] &7Advertising is not allowed.
then notify chatcontrol.notify.ad &8[&4Ad&8] &7{player}: &f{original_message}
then deny

group swear
ignore perm chatcontrol.bypass.swear
then warn &c[!] &7Swearing is not allowed.
then notify chatcontrol.notify.swear &8[&4Swear&8] &7{player}: &f{original_message}
then replace @prolong *
```

Rules reference groups: `match \bf+u+c+k+\ngroup swear`

## Common Issues & Solutions

### "Rule isn't matching"
1. Enable `Rules.Verbose: true` in settings.yml for match logs
2. Test regex at regex101.com (use Java/PCRE flavor)
3. Check `Rules.Apply_On` includes the type
4. Verify `strip colors` isn't removing needed characters
5. Check rule order — earlier rules with `then abort` may stop processing
6. Check `ignore string` isn't excluding the input

### "Group operators not executing"
1. Verify group name exists in `groups.rs` (case-sensitive)
2. Group operators execute AFTER rule-level operators
3. Groups cannot reference other groups (no nesting)
4. Groups don't support `ignore type` operator

### "Replace not working correctly"
- `then replace` replaces the MATCHED portion only, not full message
- `then rewrite` replaces the ENTIRE message
- `@prolong *` creates asterisks matching the matched text length
- Replacements happen in rule order (later rules see modified text)

### "JavaScript condition errors"
- Scripts see fully variable-replaced text, logged on error for debugging
- Must return boolean (`true`/`false`)
- Player context: `{player}` object available

### "Rules applying to Discord messages"
- Add `ignore discord` to rules that shouldn't affect Discord input
- Use `require discord` for Discord-only rules

## Key File Paths

- Rule class: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/Rule.java`
- Operator base: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/Operator.java`
- Rules manager: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/Rules.java`
- Groups: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/operator/Groups.java`
- Rule files: `chatcontrol-bukkit/src/main/resources/rules/`
- Foundation RuleSetReader: `foundation-core/src/main/java/org/mineacademy/fo/model/RuleSetReader.java`
