---
name: database
description: 'ChatControl database system: SQLite, MySQL, MariaDB, database.yml, player cache persistence, mail storage, log storage, UUID/name mapping, data migration, table schema, encoding. Use when diagnosing database connection issues, data migration, or storage problems.'
---

# Database System

## Overview

ChatControl uses a SQL database (SQLite default, MySQL/MariaDB optional) to persist player data, mail, logs, server settings, and UUID↔name mappings. All database access goes through the singleton `Database` class extending Foundation's `SimpleDatabase`.

## Architecture

### Key Classes
- `Database` (model/db/Database.java) — singleton database manager
- `PlayerCache` (model/db/PlayerCache.java) — player data row (extends `Row`)
- `Log` (model/db/Log.java) — log entry row
- `Mail` (model/db/Mail.java) — mail message row
- `ServerSettings` (model/db/ServerSettings.java) — per-server/network settings
- `ChatControlTable` (model/db/ChatControlTable.java) — table name enum
- `Migrator` (model/Migrator.java) — data.db → SQL migration

### Database Tables (ChatControlTable)

| Table | Purpose |
|-------|---------|
| `PLAYERS` | Player settings (channels, colors, tags, ignore, mute, spy, etc.) |
| `LOG` | Chat/command/sign action logs |
| `MAIL` | Mail messages |
| `SETTINGS` | Server-wide and network-wide settings (mutes, etc.) |

### PlayerCache Schema

| Column | Type | Purpose |
|--------|------|---------|
| `UUID` | VARCHAR | Player UUID (primary when UUID_LOOKUP=true) |
| `Name` | VARCHAR | Player name (primary when UUID_LOOKUP=false) |
| `Nick` | VARCHAR | Nickname (indexed for `/realname` lookups) |
| `Data` | TEXT/JSON | JSON blob containing all player settings |
| `LastModified` | BIGINT | Timestamp for inactive purging |

**Data JSON contains:**
```json
{
  "Chat_Color": "RED",
  "Chat_Decoration": "BOLD",
  "Tags": {"NICK": "<red>CoolNick", "PREFIX": "[VIP]"},
  "Tags_Colorless": {"NICK": "CoolNick"},
  "Channels": {"global": "WRITE", "admin": "READ"},
  "Left_Channels": ["ranged"],
  "Ignored_Players": ["uuid1", "uuid2"],
  "Toggled_Off_Parts": ["PRIVATE_MESSAGE"],
  "Ignored_Messages": {"TIMED": ["tips"]},
  "Warn_Points": {"global": 3, "spam": 1},
  "Unmute_Time": 1734567890000,
  "Spying_Sectors": ["CHAT", "COMMAND"],
  "Spying_Channels": ["admin"],
  "Reply_Player_Name": "Steve",
  "Rule_Data": {"chatbot_name": "Bot1"},
  "Auto_Responder": {"Book": "...", "Expiration": 1234}
}
```

## Configuration (`database.yml`)

| Key | Default | Purpose |
|-----|---------|---------|
| `Type` | `local` | `local` (SQLite) or `remote` (MySQL/MariaDB) |
| `Host` | `localhost:3306` | MySQL host:port |
| `Database` | `''` | Database name |
| `User` | `''` | MySQL username |
| `Password` | `''` | MySQL password |
| `Line` | JDBC template | Custom connection string |

**database.yml does NOT support live reloading — restart required.**

### JDBC Connection String Template
```
jdbc:{driver}://{host}/{database}?autoReconnect=true&characterEncoding=UTF-8&tcpKeepAlive=true&useSSL=false
```
Placeholders `{driver}`, `{host}`, `{database}` are auto-replaced.

## Data Lifecycle

### Player Join
```
Player joins
  → SenderCache marks as loading
  → Database.loadAndStoreCache(player) — async
    → Query PLAYERS table by UUID/Name
    → Deserialize Data JSON → PlayerCache object
    → PlayerCache.onJoin() — channel auto-join, check limits, MOTD, mail notify
    → SenderCache marks as loaded
    → ProxyChat begins syncing
```

### Player Quit
```
Player quits
  → PlayerCache.upsert() — save to database
  → Remove from memory cache
  → SenderCache cleanup
```

### Auto-Purge
```
On startup (prepareTables):
  → Purge inactive players (Settings.CLEAR_DATA_IF_INACTIVE threshold)
  → Purge old logs (Settings.Log.CLEAN_AFTER)
  → Purge old mail (Settings.Mail.CLEAN_AFTER)
```

## UUID vs Name Lookup

`Settings.UUID_LOOKUP` controls the primary key:
- `true` (default) — UUID is primary, name is indexed
- `false` — Name is primary, UUID is indexed
- Affects all lookups, including proxy sync

## Key Database Operations

### Async Cache Lookup
```java
PlayerCache.poll("playerName", cache -> {
    // cache may be null if not found
    // Checks: memory cache → online players → database
});
```

### Name/UUID Resolution
```java
Database.getInstance().getUniqueId("Steve", uuid -> { ... });
Database.getInstance().getPlayerName(uuid, name -> { ... });
```

### Batch Operations
```java
Database.getInstance().getPlayerNamesSync(uuidCollection); // BLOCKING - use carefully
Database.getInstance().getUniqueIds(nameCollection, uuidList -> { ... });
```

## Common Issues & Solutions

### "Database connection failed"
1. Check `database.yml` Type/Host/Database/User/Password
2. MySQL requires the database to exist (created manually)
3. Ensure MySQL user has full permissions on the database
4. MySQL 8.0+ recommended
5. Restart required after config changes

### "Unicode/emoji characters broken"
- MySQL must use `utf8mb4_unicode_520_ci` collation
- Check `characterEncoding=UTF-8` in connection string
- SQLite handles unicode natively

### "Slow database warnings"
- Connections > 100ms are logged
- Causes proxy server-switch delays
- Solution: co-locate MySQL server, reduce latency, check MySQL performance
- SQLite is faster for single-server setups

### "Player data lost on restart"
- Check `Settings.CLEAR_DATA_IF_INACTIVE` — may be purging data
- Verify database file exists (SQLite: `chatcontrol.db` in plugin folder)
- For MySQL: verify table contents directly
- Multiple PlayerCache entries logged as warnings (indicates bugs)

### "Migration from SQLite to MySQL"
- Change `Type` to `remote`, fill MySQL details
- `Migrator` handles automatic data migration on startup
- Monitor console for migration progress

### "Nick lookup not finding player"
- Nick column is indexed — `Database.getCache(name)` checks both Name and Nick
- `PlayerCache.poll()` searches memory → online → database

## Key File Paths

- Database: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/Database.java`
- PlayerCache: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/PlayerCache.java`
- Log: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/Log.java`
- Mail: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/Mail.java`
- Tables: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/db/ChatControlTable.java`
- Migrator: `chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/model/Migrator.java`
- Config: `chatcontrol-bukkit/src/main/resources/database.yml`

## Foundation Integration

- `SimpleDatabase` — base database class (connection, query, CRUD)
- `Row` — base class for database rows
- `Table` — table definition and schema
- `SimpleResultSet` — result set wrapper
- `DatabaseType` — LOCAL/REMOTE enum
