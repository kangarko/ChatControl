#!/usr/bin/env python3

import asyncio
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timedelta
from pathlib import Path

from pydantic import BaseModel, Field
from copilot import CopilotClient, define_tool

CHATCONTROL_DIR = "chatcontrol"
FOUNDATION_DIR = "foundation"
MAX_FILE_SIZE = 50_000
MAX_SEARCH_FILES = 20
MAX_SEARCH_RESULTS = 50
MAX_DIFF_SIZE = 30_000
RESPONSE_FILE = "response.md"
CONVERSATION_FILE = "conversation.json"
MAX_CONVERSATION_SIZE = 50_000
INSIGHTS_FILE = f"{CHATCONTROL_DIR}/.github/insights/learned_insights.json"
INSIGHT_EXPIRY_DAYS = 90
MAX_INSIGHTS = 50

STOP_WORDS = frozenset({
    "the", "is", "at", "which", "on", "a", "an", "in", "to", "for",
    "of", "and", "or", "but", "not", "with", "this", "that", "it",
    "be", "as", "are", "was", "were", "been", "has", "had", "have",
    "do", "does", "did", "will", "would", "could", "should", "may",
    "might", "can", "shall", "very", "much", "more", "most", "any",
    "all", "each", "every", "some", "no", "one", "two", "three",
    "than", "then", "also", "just", "only", "so", "if", "when",
    "how", "what", "why", "where", "who", "whom", "whose", "get",
    "got", "set", "use", "using", "used", "like", "make", "want",
    "need", "know", "new", "see", "try", "still", "about", "from",
    "into", "over", "after", "before", "between", "under", "above",
    "such", "here", "there", "yes", "yeah", "version", "title",
    "please", "help", "thanks", "thank", "hello", "issue", "bug",
    "question", "problem", "work", "working", "doesn", "don",
    "didn", "isn", "aren", "won", "haven", "hasn", "wouldn",
    "couldn", "shouldn", "report", "file", "think", "sure",
    "server", "plugin", "minecraft", "paper", "spigot", "really",
    "already", "actually", "something", "anything", "everything",
    "nothing", "thing", "other", "same", "different", "many",
})

KEY_FILES = [
    f"{CHATCONTROL_DIR}/chatcontrol-bukkit/src/main/resources/settings.yml",
    f"{CHATCONTROL_DIR}/chatcontrol-bukkit/src/main/resources/database.yml",
    f"{CHATCONTROL_DIR}/chatcontrol-bukkit/src/main/resources/proxy.yml",
]

WRITABLE_PREFIXES = (
    "chatcontrol-bukkit/src/main/",
    "chatcontrol-core/src/main/",
    "chatcontrol-proxy-core/src/main/",
    "chatcontrol-bungeecord/src/main/",
    "chatcontrol-velocity/src/main/",
)

WRITABLE_EXTENSIONS = frozenset({".java", ".yml", ".yaml", ".rs", ".json"})

BLOCKED_FILENAMES = frozenset({"pom.xml", "build.xml"})

written_files = []
new_insights  = []

SYSTEM_PROMPT = """You are a support agent for ChatControl, a Minecraft chat plugin for Spigot/Paper/BungeeCord/Velocity.

## Project Layout
- chatcontrol-bukkit/src/main/resources/ — Config files (settings.yml, database.yml, proxy.yml, formats/, rules/, messages/, lang/)
- chatcontrol-bukkit/src/main/java/ — Main plugin code
- chatcontrol-core/ — Shared logic
- chatcontrol-proxy-core/, chatcontrol-bungeecord/, chatcontrol-velocity/ — Proxy code
- Foundation library (org.mineacademy.fo.*) — Separate framework, NOT ChatControl code

## Knowledge Base
Topic-specific skill files with architecture, config keys, common issues, and file paths:
- chatcontrol/.github/skills/chat-formatting/SKILL.md — Format files, parts, placeholders, hover/click, gradients, images, MiniMessage
- chatcontrol/.github/skills/channels/SKILL.md — Channel creation, types, ranged, party, permissions, auto-join, modes, proxy
- chatcontrol/.github/skills/rules-engine/SKILL.md — .rs rule files, regex, operators, conditions, actions, groups, imports
- chatcontrol/.github/skills/chat-filter/SKILL.md — Anti-spam, anti-caps, anti-bot, similarity, delay, grammar, newcomer
- chatcontrol/.github/skills/groups/SKILL.md — Permission groups, rule groups, overrides, chatcontrol.group.{name}
- chatcontrol/.github/skills/proxy-sync/SKILL.md — BungeeCord, Velocity, proxy.yml, cross-server sync, plugin messaging
- chatcontrol/.github/skills/database/SKILL.md — SQLite, MySQL, database.yml, player cache, mail, logs, UUID mapping
- chatcontrol/.github/skills/commands/SKILL.md — /chc subcommands, /channel, /tell, /reply, all commands, permissions
- chatcontrol/.github/skills/variables/SKILL.md — PlaceholderAPI, JavaScript variables, {variable} syntax, variable files
- chatcontrol/.github/skills/messages/SKILL.md — Join/quit/kick/death/timed messages, .rs files, conditions, message groups
- chatcontrol/.github/skills/private-messaging/SKILL.md — /tell, /reply, /ignore, PM formatting, social spy, vanish, mail
- chatcontrol/.github/skills/books-announcements/SKILL.md — Books, timed announcements, broadcast, MOTD, announcement types
- chatcontrol/.github/skills/mute-warn/SKILL.md — Mute hierarchy, warning points, /mute, temp mute, point decay
- chatcontrol/.github/skills/tags-nicks/SKILL.md — Player tags, nick, prefix/suffix, /tag command, tag rules
- chatcontrol/.github/skills/menus/SKILL.md — Color picker, spy toggle, channel GUI, Foundation Menu system
Read the 1-3 most relevant skill files FIRST before answering — they contain detailed troubleshooting guides.

## Learned Insights
You may receive supplementary insights learned from previous issue resolutions. Skill files are authoritative; insights are supplementary hints for edge cases and common pitfalls discovered through real issues.

## Purchase Links — CRITICAL
ChatControl is a premium plugin sold on BuiltByBit. The GitHub source code is provided as-is for reference only.
- **Never** help users compile, build, or run the source code. Do NOT provide Maven/Gradle commands, build instructions, or troubleshoot compilation errors
- **Never** suggest "dropping a jar", "building from source", "compiling", or imply the user can produce their own jars
- If someone asks how to compile or build, tell them this is a premium plugin and they should purchase it from BuiltByBit
- When referring to the proxy modules, tell users to get them separately (don't use the word "purchase"):
  - ChatControl (Bukkit/Spigot/Paper): https://builtbybit.com/resources/chatcontrol-format-filter-chat.18217/
  - BungeeControl (BungeeCord proxy add-on): https://builtbybit.com/resources/bungeecontrol-cross-network-chat.24248/
  - VelocityControl (Velocity proxy add-on): https://builtbybit.com/resources/velocitycontrol-cross-network-chat.43226/
- When explaining proxy setup, say "install the VelocityControl/BungeeControl plugin" and link to the relevant purchase page
- If someone asks where to get the plugin or proxy module, link them to the BuiltByBit pages above
- **Bukkit limitation:** Proxy sync requires at least one online player on the sending backend server to work. This is a Bukkit/Spigot platform limitation (plugin messaging channels only function when a player is connected). Always mention this when explaining proxy setup

## Your Behavior
- Use tools to explore the codebase — never guess at code behavior or hallucinate paths
- For config questions, reference the exact YAML file and key path
- For stacktraces, trace through the relevant source files
- If the issue lacks info, ask for: server version, ChatControl version, config snippets, error logs, `/chc debug` ZIP
- NEVER suggest downgrading the plugin or Java version
- NEVER tell users to write code, create plugins, or implement things themselves — your users are server owners, not developers. If a feature needs code, implement it yourself via `patch_codebase_file` (for existing files) or `write_codebase_file` (for new files) and propose a PR

## Response Style
Your readers are Minecraft server owners — busy people who want answers, not essays. Match the length to the complexity: a one-line config fix gets a one-line answer; a multi-layered bug gets a thorough walkthrough. Never pad, never ramble.

- **Lead with the fix.** Solution first, context second. If someone can solve their problem by reading only your first sentence, you did it right.
- **Show only what they need to change** — the relevant config key or code snippet, not the entire file.
- **No greetings, no filler, no sign-offs, no meta-commentary.** Jump straight in. Never start with phrases like "Changes look good", "Here's the summary", "Let me explain", "Sure!", "Great question", or any preamble about what you're about to say. Start directly with the substance — e.g. "I've added…", "Set `X: true`…", "This happens because…".
- **Never expose code internals.** Users are server owners, not developers. Don't mention polling intervals, messaging channel names, internal data structures, class names, or how the code works under the hood. Even if someone asks "how does X work?", explain only what they need to *do* (setup steps, config keys, what features it enables) — not the implementation.
- **Never tell users to write code.** Don't suggest creating Java plugins, using APIs, registering classes, or calling methods. If a feature needs code changes, implement it yourself and propose a PR. If you can't implement it confidently, say it needs to be implemented by the development team — never ask the user to do it.
- **Bold the key action:** e.g. **set `X: true` in settings.yml**
- If you need more info, ask a few specific questions in a bullet list at the end.
- Use GitHub Markdown with `yaml` or `java` language tags for code blocks.
- Skip headers (##) unless you're genuinely covering multiple distinct topics.

## Fix & Feature Capability
When you can fix a bug or implement a requested feature, propose changes via a draft PR for human review — you are NOT deploying to production.

**Two tools for writing code:**
- `patch_codebase_file` — **Use this for ALL edits to existing files.** Provide the exact `old_text` to find and `new_text` to replace it with. Include 2-3 lines of unchanged context around the target text so the match is unique. You MUST read the file first to get the exact text.
- `write_codebase_file` — **Only for creating brand-new files** that don't exist yet. Provide the full content.

**CRITICAL: NEVER use `write_codebase_file` on an existing file.** It will reject the call. For existing files, always use `patch_codebase_file` to surgically edit only the lines that need to change.

**When to propose changes:**
- Config fixes (YAML corrections, missing keys, new config options)
- Bug fixes (single-file or multi-file Java fixes)
- New integrations (third-party plugin hooks, party providers, placeholder expansions)
- Small-to-medium feature additions that fit naturally into the existing architecture

**Do NOT:**
- Modify Foundation code (separate repository)
- Rewrite large unrelated sections of code
- Make speculative or uncertain changes
- Touch build files (pom.xml, build.xml)

Always explain what you changed and why in your response, so the reviewer can verify.

## Follow-Up Conversations
When responding to a follow-up comment on a thread you already answered:
- Read the full conversation to understand what was discussed
- Don't repeat your previous answer — build on it, clarify, or address new information
- If the user provided logs, config, or errors, analyze them specifically
- If a maintainer already resolved the issue in a previous comment, respond with exactly SKIP and nothing else
- If the comment is just a thank-you, acknowledgment, or confirmation with no further question, respond with exactly SKIP and nothing else
- SKIP means no comment will be posted — use it to avoid unnecessary bot noise"""

REVIEW_SYSTEM_PROMPT = """You are a senior engineer performing a thorough code review of proposed changes to ChatControl, a Minecraft plugin.

Think deeply and exhaustively before approving. You are the last line of defense before these changes go into a draft PR.

## What to check
- **DRY violations**: Is there duplicated logic? Multiple functions or components doing the same thing? Does the change copy-paste something that already exists elsewhere?
- **Broken code**: Will this compile? Are all imports present? Are types compatible? Are method signatures correct?
- **Hidden bugs**: Null pointers, off-by-one errors, encoding issues, race conditions, resource leaks, unclosed streams?
- **Edge cases**: What happens with empty input? Null values? Very large values? Special characters?
- **Overengineering**: Is the change the minimum needed to fix the issue? Does it add unnecessary abstraction?
- **Missed consistency**: Should similar changes be applied to other files or methods? Are there parallel implementations that need the same fix?
- **Error handling**: If the code handles API responses or user input, does it log/surface unexpected values instead of swallowing them silently?

## How to review
1. Read each changed file in full to understand context
2. Search the codebase for similar patterns that might need the same change
3. If you find problems, fix them using patch_codebase_file (for existing files) or write_codebase_file (for new files)
4. If you get an unexpected response from any tool, include the raw response in your output
5. If everything looks correct, respond with "LGTM" and nothing else"""

INSIGHT_SYSTEM_PROMPT = """You analyze resolved GitHub issues for ChatControl to extract reusable support insights.

Your goal: identify NEW knowledge that wasn't already in the skill files but was needed to answer this issue.

## What qualifies as an insight
- A specific config key behavior or default that users commonly misunderstand
- A non-obvious interaction between two features
- A common user mistake with a concrete fix
- An error message and its actual root cause
- A setup step users frequently miss

## What does NOT qualify
- Generic advice like "check your config" or "update the plugin"
- Information already clearly documented in the skill files
- Issue-specific details that won't help anyone else
- Anything you're uncertain or speculating about
- Restating what skill files already say

## Rules
- Store at most 1-2 insights per issue. Most issues teach nothing new — that's fine.
- If nothing is genuinely new or useful, do NOT call store_insight at all.
- Check the existing insights list to avoid duplicates or near-duplicates.
- Each insight must be specific enough that reading it alone tells you what to do.
- Prefer insights about config keys, permissions, and common error causes."""


def extract_keywords(title, body):
    text = f"{title} {body}"
    text = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    text = re.sub(r"https?://\S+", "", text)
    text = re.sub(r"\[.*?\]\(.*?\)", "", text)

    keywords = set()

    for match in re.finditer(r"\b[A-Z][a-z]+(?:[A-Z][a-z]+)+\b", text):
        keywords.add(match.group())

    for match in re.finditer(r"\b[A-Za-z]+(?:\.[A-Za-z_]+){2,}\b", text):
        keywords.add(match.group())

    for match in re.finditer(r"\b\w*(?:Exception|Error|Throwable)\b", text):
        keywords.add(match.group())

    words = re.findall(r"\b[a-zA-Z]{3,}\b", text)
    for word in words:
        if word.lower() not in STOP_WORDS:
            keywords.add(word)

    return list(keywords)


def extract_stacktrace_classes(body):
    classes = set()

    for match in re.finditer(r"at\s+([\w.]+)\.\w+\(", body):
        full_class = match.group(1)

        if "mineacademy" in full_class:
            class_name = full_class.split(".")[-1]
            classes.add(class_name)

    return list(classes)


def extract_mentioned_files(body):
    found = []

    for match in re.finditer(r"\b([\w/.-]+\.(?:yml|yaml|java|rs|json))\b", body):
        filename = match.group(1)

        try:
            result = subprocess.run(
                ["find", CHATCONTROL_DIR, FOUNDATION_DIR,
                 "-path", f"*{filename}", "-type", "f",
                 "-not", "-path", "*/target/*"],
                capture_output=True, text=True, timeout=10,
            )

            for path in result.stdout.strip().split("\n"):
                if path:
                    found.append(path)
        except (subprocess.TimeoutExpired, Exception):
            continue

    return found


def find_class_files(class_names):
    found = []

    for name in class_names:
        try:
            result = subprocess.run(
                ["find", CHATCONTROL_DIR, FOUNDATION_DIR,
                 "-name", f"{name}.java", "-not", "-path", "*/target/*"],
                capture_output=True, text=True, timeout=10,
            )

            for path in result.stdout.strip().split("\n"):
                if path:
                    found.append(path)
        except (subprocess.TimeoutExpired, Exception):
            continue

    return found


def search_repos_by_keywords(keywords):
    file_hits = {}

    for keyword in keywords[:25]:
        if len(keyword) < 3:
            continue

        try:
            result = subprocess.run(
                ["grep", "-rli",
                 "--include=*.java", "--include=*.yml",
                 "--include=*.yaml", "--include=*.rs",
                 "--include=*.json",
                 f"--exclude-dir=target",
                 keyword, CHATCONTROL_DIR, FOUNDATION_DIR],
                capture_output=True, text=True, timeout=10,
            )

            for path in result.stdout.strip().split("\n"):
                if path and "/target/" not in path:
                    file_hits[path] = file_hits.get(path, 0) + 1
        except (subprocess.TimeoutExpired, Exception):
            continue

    sorted_files = sorted(file_hits.items(), key=lambda x: -x[1])
    return [f for f, _ in sorted_files[:MAX_SEARCH_FILES]]


def load_conversation():
    path = Path(CONVERSATION_FILE)

    if not path.exists():
        return []

    try:
        data = json.loads(path.read_text())

        return [
            {
                "author": c["user"]["login"],
                "body":   c["body"],
                "is_bot": c["user"]["type"] == "Bot",
            }
            for c in data
        ]
    except Exception as e:
        print(f"Warning: Failed to load conversation: {e}")
        return []


def format_conversation(issue_body, comments):
    parts     = [f"**Original issue:**\n{issue_body}"]
    last_user = None

    for i, c in enumerate(comments):
        if not c["is_bot"]:
            last_user = i

    for i, c in enumerate(comments):
        if c["is_bot"]:
            label = "Bot response"
        elif i == last_user:
            label = f"Latest comment by @{c['author']} (respond to this)"
        else:
            label = f"Comment by @{c['author']}"

        parts.append(f"**{label}:**\n{c['body']}")

    text = "\n\n---\n\n".join(parts)

    if len(text) > MAX_CONVERSATION_SIZE:
        text = "... (earlier conversation truncated)\n\n" + text[-MAX_CONVERSATION_SIZE:]

    return text


def load_insights():
    path = Path(INSIGHTS_FILE)

    if not path.exists():
        return []

    try:
        return json.loads(path.read_text())
    except Exception as e:
        print(f"Warning: Failed to load insights: {e}")
        return []


def prune_insights(insights):
    cutoff = (datetime.now() - timedelta(days=INSIGHT_EXPIRY_DAYS)).strftime("%Y-%m-%d")
    valid  = [i for i in insights if i.get("date", "") >= cutoff]

    if len(valid) > MAX_INSIGHTS:
        valid = valid[-MAX_INSIGHTS:]

    return valid


def save_insights(insights):
    path = Path(INSIGHTS_FILE)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(insights, indent=2))


def format_insights_for_prompt(insights):
    if not insights:
        return ""

    lines = ["## Learned Insights (from previous issues \u2014 supplementary to skill files)"]

    for i in insights:
        topic = i.get("topic", "general")
        text  = i.get("insight", "")
        issue = i.get("issue", "?")
        lines.append(f"- **[{topic}]** (#{issue}): {text}")

    return "\n".join(lines)


def read_field(value, field):
    if isinstance(value, dict):
        return value.get(field)

    return getattr(value, field, None)


def normalize_role(value):
    if value is None:
        return ""

    role_value = read_field(value, "value")

    if role_value is not None:
        return str(role_value).strip().lower()

    return str(value).strip().lower()


def extract_text(value):
    if value is None:
        return ""

    if isinstance(value, str):
        return value.strip()

    if isinstance(value, (int, float, bool)):
        return str(value)

    if isinstance(value, list):
        parts = [extract_text(item) for item in value]
        parts = [part for part in parts if part]

        return "".join(parts).strip()

    if isinstance(value, dict):
        parts = []

        for key in ("content", "text", "value", "delta_content", "message", "output_text", "parts", "items"):
            part = extract_text(value.get(key))

            if part:
                parts.append(part)

        return "\n".join(parts).strip()

    parts = []

    for attr in ("content", "text", "value", "delta_content", "message", "output_text"):
        part = extract_text(getattr(value, attr, None))

        if part:
            parts.append(part)

    return "\n".join(parts).strip()


def extract_last_response(messages):
    msg_list = list(messages)

    for i, msg in enumerate(msg_list):
        msg_type   = read_field(msg, "type")
        type_value = read_field(msg_type, "value") if msg_type is not None else None
        print(f"  msg[{i}]: type={type_value}")

    for msg in reversed(msg_list):
        msg_type   = read_field(msg, "type")
        type_value = str(read_field(msg_type, "value") or "") if msg_type is not None else ""

        if type_value.lower() != "assistant.message":
            continue

        data = read_field(msg, "data")

        if data is None:
            continue

        text = extract_text(read_field(data, "content"))

        if text and len(text) > 10:
            return text

    for msg in reversed(msg_list):
        role = normalize_role(read_field(msg, "role"))

        if role != "assistant":
            continue

        text = extract_text(read_field(msg, "content"))

        if text and len(text) > 10:
            return text

    return ""


def resolve_cli_path():
    cli_path = shutil.which("copilot")

    if not cli_path:
        raise RuntimeError("Copilot CLI executable was not found on PATH.")

    return cli_path


ALLOWED_ROOTS = (CHATCONTROL_DIR, FOUNDATION_DIR)


def validate_path(path_str):
    normalized = os.path.normpath(path_str)

    if not any(normalized == root or normalized.startswith(root + os.sep) for root in ALLOWED_ROOTS):
        return None

    resolved = Path(normalized).resolve()

    for root in ALLOWED_ROOTS:
        root_resolved = Path(root).resolve()

        try:
            resolved.relative_to(root_resolved)
            return resolved
        except ValueError:
            continue

    return None


class ReadFileParams(BaseModel):
    path: str = Field(description="Relative file path, e.g. 'chatcontrol/chatcontrol-bukkit/src/main/resources/settings.yml'")


@define_tool(description="Read a source file from the ChatControl or Foundation repository. Path must start with 'chatcontrol/' or 'foundation/'. Excludes build output directories.")
def read_codebase_file(params: ReadFileParams) -> str:
    resolved = validate_path(params.path)

    if not resolved:
        return "Error: Path must start with 'chatcontrol/' or 'foundation/' and stay within those directories."

    if "/target/" in params.path:
        return "Error: Cannot read files from target/ (build output) directories."

    if not resolved.exists():
        return f"Error: File not found: {params.path}"

    if not resolved.is_file():
        return f"Error: Not a file: {params.path}"

    try:
        content = resolved.read_text(errors="replace")

        if len(content) > MAX_FILE_SIZE:
            content = content[:MAX_FILE_SIZE] + f"\n... (truncated at {MAX_FILE_SIZE:,} characters)"

        return content
    except Exception as e:
        return f"Error reading file: {e}"


class SearchParams(BaseModel):
    query: str = Field(description="Search term or keyword to grep for in source files")
    file_types: str = Field(default="java,yml,yaml,rs,json", description="Comma-separated file extensions to search")


@define_tool(description="Search the ChatControl and Foundation codebases for files containing a keyword. Returns matching file paths with line numbers and snippets. Excludes build output (target/) directories.")
def search_codebase(params: SearchParams) -> str:
    if len(params.query) < 2:
        return "Error: Search query must be at least 2 characters."

    extensions = [f"--include=*.{ext.strip()}" for ext in params.file_types.split(",")]

    try:
        result = subprocess.run(
            ["grep", "-rn", "--max-count=3"] + extensions +
            ["--exclude-dir=target", params.query, CHATCONTROL_DIR, FOUNDATION_DIR],
            capture_output=True, text=True, timeout=15,
        )

        lines = result.stdout.strip().split("\n")
        lines = [line for line in lines if line and "/target/" not in line]

        if not lines:
            return f"No matches found for '{params.query}'"

        if len(lines) > MAX_SEARCH_RESULTS:
            lines = lines[:MAX_SEARCH_RESULTS]
            lines.append(f"... (showing {MAX_SEARCH_RESULTS} of many matches)")

        return "\n".join(lines)
    except subprocess.TimeoutExpired:
        return "Error: Search timed out after 15 seconds."
    except Exception as e:
        return f"Error: {e}"


class ListDirParams(BaseModel):
    path: str = Field(description="Relative directory path, e.g. 'chatcontrol/chatcontrol-bukkit/src/main/resources/'")


@define_tool(description="List files and subdirectories in a directory of the ChatControl or Foundation repository. Path must start with 'chatcontrol/' or 'foundation/'.")
def list_directory(params: ListDirParams) -> str:
    resolved = validate_path(params.path)

    if not resolved:
        return "Error: Path must start with 'chatcontrol/' or 'foundation/' and stay within those directories."

    if not resolved.exists():
        return f"Error: Directory not found: {params.path}"

    if not resolved.is_dir():
        return f"Error: Not a directory: {params.path}"

    try:
        entries = sorted(resolved.iterdir())
        entries = [e for e in entries if e.name != "target"]
        result  = []

        for entry in entries[:100]:
            if entry.is_dir():
                result.append(f"  {entry.name}/")
            else:
                size = entry.stat().st_size
                result.append(f"  {entry.name} ({size:,} bytes)")

        if len(entries) > 100:
            result.append(f"... and {len(entries) - 100} more entries")

        return "\n".join(result)
    except Exception as e:
        return f"Error: {e}"


class WriteFileParams(BaseModel):
    path: str = Field(description="Relative file path within chatcontrol/, e.g. 'chatcontrol/chatcontrol-bukkit/src/main/java/org/mineacademy/chatcontrol/MyClass.java'. Must be a NEW file that does not exist yet.")
    content: str = Field(description="The complete content for the new file")
    reason: str = Field(description="Brief explanation of why this new file is needed")


@define_tool(description="Create a NEW source/config file. Only for files that don't exist yet. For editing existing files, use patch_codebase_file instead. Works for files in chatcontrol/*/src/main/. Cannot modify Foundation, build files, or .github/. Changes are submitted as a draft PR for human review.")
def write_codebase_file(params: WriteFileParams) -> str:
    if not params.path.startswith(CHATCONTROL_DIR + "/"):
        return "Error: Can only write files in the chatcontrol/ repository, not foundation/."

    relative = params.path[len(CHATCONTROL_DIR) + 1:]

    if not any(relative.startswith(prefix) for prefix in WRITABLE_PREFIXES):
        return f"Error: Can only write to source/resource directories under src/main/. Got: {relative}"

    filename = Path(params.path).name

    if filename in BLOCKED_FILENAMES:
        return f"Error: Cannot modify build file: {filename}"

    ext = Path(params.path).suffix.lower()

    if ext not in WRITABLE_EXTENSIONS:
        return f"Error: Cannot write {ext} files. Allowed: {', '.join(sorted(WRITABLE_EXTENSIONS))}"

    if "/target/" in params.path:
        return "Error: Cannot write to target/ (build output) directories."

    resolved = validate_path(params.path)

    if not resolved:
        return "Error: Invalid path."

    if resolved.exists():
        return f"Error: File already exists: {params.path}. Use patch_codebase_file to edit existing files."

    resolved.parent.mkdir(parents=True, exist_ok=True)

    if len(params.content) > MAX_FILE_SIZE:
        return f"Error: Content too large ({len(params.content):,} chars). Max: {MAX_FILE_SIZE:,}."

    try:
        resolved.write_text(params.content)
        written_files.append({"path": params.path, "reason": params.reason, "new": True})
        return f"Created {params.path} ({len(params.content):,} chars)"
    except Exception as e:
        return f"Error writing file: {e}"


class PatchFileParams(BaseModel):
    path: str = Field(description="Relative file path within chatcontrol/, e.g. 'chatcontrol/chatcontrol-bukkit/src/main/resources/settings.yml'")
    old_text: str = Field(description="The exact text to find in the file (must match uniquely). Include 2-3 lines of surrounding context to ensure a unique match.")
    new_text: str = Field(description="The replacement text that will replace old_text")
    reason: str = Field(description="Brief explanation of what this change does")


@define_tool(description="Edit an existing source/config file by replacing a specific text snippet. Use this instead of write_codebase_file for all edits to existing files. Provide the exact old text and the new text. The old_text must appear exactly once in the file. Include 2-3 lines of context around the change to ensure uniqueness.")
def patch_codebase_file(params: PatchFileParams) -> str:
    if not params.path.startswith(CHATCONTROL_DIR + "/"):
        return "Error: Can only edit files in the chatcontrol/ repository, not foundation/."

    relative = params.path[len(CHATCONTROL_DIR) + 1:]

    if not any(relative.startswith(prefix) for prefix in WRITABLE_PREFIXES):
        return f"Error: Can only edit source/resource directories under src/main/. Got: {relative}"

    filename = Path(params.path).name

    if filename in BLOCKED_FILENAMES:
        return f"Error: Cannot modify build file: {filename}"

    ext = Path(params.path).suffix.lower()

    if ext not in WRITABLE_EXTENSIONS:
        return f"Error: Cannot edit {ext} files. Allowed: {', '.join(sorted(WRITABLE_EXTENSIONS))}"

    if "/target/" in params.path:
        return "Error: Cannot edit files in target/ (build output) directories."

    resolved = validate_path(params.path)

    if not resolved:
        return "Error: Invalid path."

    if not resolved.exists() or not resolved.is_file():
        return f"Error: File not found: {params.path}. Use write_codebase_file to create new files."

    try:
        content = resolved.read_text(errors="replace")
    except Exception as e:
        return f"Error reading file: {e}"

    count = content.count(params.old_text)

    if count == 0:
        return f"Error: old_text not found in {params.path}. Make sure it matches exactly (including whitespace and indentation). Read the file first to get the exact text."

    if count > 1:
        return f"Error: old_text matches {count} locations in {params.path}. Include more surrounding context lines to make the match unique."

    new_content = content.replace(params.old_text, params.new_text, 1)

    if len(new_content) > MAX_FILE_SIZE:
        return f"Error: Resulting file too large ({len(new_content):,} chars). Max: {MAX_FILE_SIZE:,}."

    try:
        resolved.write_text(new_content)
        written_files.append({"path": params.path, "reason": params.reason, "new": False})
        return f"Patched {params.path}: replaced {len(params.old_text)} chars with {len(params.new_text)} chars"
    except Exception as e:
        return f"Error writing file: {e}"


class StoreInsightParams(BaseModel):
    topic: str = Field(description="Topic category: channels, chat-formatting, rules-engine, chat-filter, groups, proxy-sync, database, commands, variables, messages, private-messaging, books-announcements, mute-warn, tags-nicks, menus, or 'general'")
    insight: str = Field(description="Specific, actionable insight in 1-3 sentences. Must be concrete enough to help resolve similar future issues.")
    related_skill: str = Field(default="", description="Skill file this supplements, e.g. 'channels'. Empty if general.")


@define_tool(description="Store a learned insight from this issue. Only call if you found genuinely new knowledge not in skill files. Most issues teach nothing new — do not force insights.")
def store_insight(params: StoreInsightParams) -> str:
    if len(params.insight) < 20:
        return "Error: Insight too short. Must be specific and actionable."

    if len(params.insight) > 500:
        return "Error: Insight too long. Keep to 1-3 concise sentences."

    new_insights.append({
        "topic":         params.topic,
        "insight":       params.insight,
        "related_skill": params.related_skill,
    })

    return f"Insight stored for topic '{params.topic}'."


async def run_agent_session(client, model, system_prompt, user_prompt, tools, timeout=3600):
    session = await client.create_session({
        "model": model,
        "streaming": False,
        "system_message": {"content": system_prompt},
        "tools": tools,
        "infinite_sessions": {
            "enabled": True,
            "background_compaction_threshold": 0.80,
            "buffer_exhaustion_threshold": 0.95,
        },
    })

    try:
        done         = asyncio.Event()
        event_errors = []

        def on_event(event):
            try:
                event_type = normalize_role(read_field(event, "type"))
                event_data = read_field(event, "data")

                if event_type in ("error", "session.error", "assistant.error"):
                    error_text = extract_text(event_data)

                    if error_text:
                        event_errors.append(f"{event_type}: {error_text}")
                    else:
                        event_errors.append(event_type)

                elif event_type == "session.idle":
                    done.set()
            except Exception:
                done.set()

        session.on(on_event)
        await session.send({"prompt": user_prompt})
        await asyncio.wait_for(done.wait(), timeout=timeout)

        messages  = await session.get_messages()
        msg_list  = list(messages)
        print(f"  got {len(msg_list)} messages from session history")
        candidate = extract_last_response(msg_list)

        if not candidate:
            raise RuntimeError(
                f"Empty output. event_errors={event_errors[:3]}, messages={len(msg_list)}"
            )

        return candidate
    finally:
        await session.destroy()


def get_git_diff():
    try:
        subprocess.run(
            ["git", "-C", CHATCONTROL_DIR, "add", "-A"],
            capture_output=True, timeout=10,
        )
        result = subprocess.run(
            ["git", "-C", CHATCONTROL_DIR, "diff", "--cached"],
            capture_output=True, text=True, timeout=30,
        )
        subprocess.run(
            ["git", "-C", CHATCONTROL_DIR, "reset", "--quiet"],
            capture_output=True, timeout=10,
        )
        return result.stdout.strip()
    except Exception as e:
        print(f"Warning: git diff failed: {e}")
        return ""


async def run():
    title          = os.environ["ISSUE_TITLE"]
    body           = os.environ.get("ISSUE_BODY", "") or "(No description provided)"
    labels         = os.environ.get("ISSUE_LABELS", "")
    comment_body   = os.environ.get("COMMENT_BODY", "")
    comment_author = os.environ.get("COMMENT_AUTHOR", "")
    issue_number   = os.environ.get("ISSUE_NUMBER", "0")
    is_reply       = bool(comment_body)
    token          = os.environ.get("COPILOT_GITHUB_TOKEN")

    if not token:
        raise RuntimeError("Missing required environment variable: COPILOT_GITHUB_TOKEN")

    if len(body) > 100_000:
        body = body[:100_000] + "\n... (truncated)"

    if is_reply:
        print(f"Reply on issue: {title} (by @{comment_author})")
    else:
        print(f"New issue: {title}")

    all_text           = f"{body}\n{comment_body}" if is_reply else body
    keywords           = extract_keywords(title, all_text)
    stacktrace_classes = extract_stacktrace_classes(body)
    class_files        = find_class_files(stacktrace_classes) if stacktrace_classes else []
    mentioned_files    = extract_mentioned_files(body)
    search_files       = search_repos_by_keywords(keywords)
    print(f"Pre-analysis: {len(keywords)} keywords, {len(class_files)} stacktrace files, {len(mentioned_files)} mentioned files, {len(search_files)} keyword matches")

    hints = []

    if class_files:
        hints.append("### Stacktrace-Related Files (read these first for error issues)")

        for f in class_files[:10]:
            hints.append(f"- {f}")

    if mentioned_files:
        hints.append("### Files Mentioned in the Issue")

        for f in mentioned_files[:10]:
            hints.append(f"- {f}")

    if search_files:
        hints.append("### Files With Keyword Matches (ranked by relevance)")

        for f in search_files[:MAX_SEARCH_FILES]:
            hints.append(f"- {f}")

    hints_text     = "\n".join(hints) if hints else "No specific files identified. Use the search_codebase tool to explore."
    key_files_text = "\n".join(f"- {f}" for f in KEY_FILES)
    label_line     = f"\n**Labels:** {labels}" if labels else ""

    existing_insights = prune_insights(load_insights())
    insights_text     = format_insights_for_prompt(existing_insights)

    if is_reply:
        conversation = load_conversation()
        thread       = format_conversation(body, conversation)

        user_prompt = f"""A user posted a follow-up comment on this issue. Respond to their latest comment.

**Issue Title:** {title}{label_line}

## Conversation Thread
{thread}

## Possibly Relevant Files
{key_files_text}
{hints_text}
{insights_text}

Respond to the latest comment. If it's just a thank-you with no question, respond with exactly SKIP and nothing else."""
    else:
        user_prompt = f"""Help with this GitHub issue. Keep your response short and actionable.

**Title:** {title}{label_line}

{body}

## Possibly Relevant Files
{key_files_text}
{hints_text}
{insights_text}

Read the most relevant files above, then give a short, direct answer. Lead with the fix. Skip unnecessary explanation."""

    all_tools = [read_codebase_file, search_codebase, list_directory, write_codebase_file, patch_codebase_file]
    models    = ["claude-opus-4.6"]

    cli_path = resolve_cli_path()
    print(f"Using Copilot CLI: {cli_path}")

    client = CopilotClient({
        "cli_path": cli_path,
        "github_token": token,
    })
    await client.start()

    try:
        text           = None
        model_failures = []

        for model in models:
            print(f"Phase 1 — trying model: {model}")

            try:
                text = await run_agent_session(client, model, SYSTEM_PROMPT, user_prompt, all_tools)
                print(f"Phase 1 — success with {model}")
                break
            except Exception as e:
                model_failures.append(f"{model}: {e}")
                print(f"Phase 1 — {model} failed: {e}")

        if not text:
            raise RuntimeError(f"All models failed. Details: {' | '.join(model_failures)}")

        if written_files:
            print(f"Phase 2 — self-reviewing {len(written_files)} changed file(s)")
            diff_output = get_git_diff()

            if diff_output:
                if len(diff_output) > MAX_DIFF_SIZE:
                    diff_output = diff_output[:MAX_DIFF_SIZE] + "\n... (diff truncated)"

                changed_summary = "\n".join(f"- `{wf['path']}`: {wf['reason']}" for wf in written_files)

                review_prompt = f"""Review these proposed changes for a ChatControl GitHub issue.

## Changed Files
{changed_summary}

## Diff
```diff
{diff_output}
```

Read each changed file and its surrounding code. Verify correctness, then check:
1. DRY violations — duplicated logic that already exists elsewhere in the codebase
2. Broken code — syntax errors, missing imports, wrong method signatures, type mismatches
3. Hidden bugs — null handling, edge cases, off-by-one, encoding, resource leaks
4. Overengineering — is the change the minimum needed to fix the issue?
5. Consistency — does it match patterns and style in the surrounding code?
6. Missed spots — should the same change be applied to other files or methods?
7. Error handling — are unexpected responses logged, not silently swallowed?

If you find problems, fix them with patch_codebase_file (for existing files) or write_codebase_file (for new files). If everything looks correct, respond with "LGTM"."""

                for model in models:
                    print(f"Phase 2 — trying model: {model}")

                    try:
                        review_text = await run_agent_session(client, model, REVIEW_SYSTEM_PROMPT, review_prompt, all_tools)
                        print(f"Phase 2 — complete: {review_text[:200]}")
                        break
                    except Exception as e:
                        print(f"Phase 2 — {model} failed: {e}")
                else:
                    print("Warning: Phase 2 self-review failed for all models — skipping review")

        if not (is_reply and text.strip().upper().startswith("SKIP")):
            print("Phase 3 — extracting insights")

            insight_prompt = f"""Analyze this resolved GitHub issue and the response given. Extract genuinely new support insights, if any.

**Issue #{issue_number}: {title}**

{body[:5000]}

**Response Given:**
{text[:5000]}

{format_insights_for_prompt(existing_insights) or "No existing insights yet."}

Read the 1-2 most relevant skill files to verify your insight isn't already documented. Then decide if there's genuinely new knowledge worth storing. If not, respond with "No new insights." without calling store_insight."""

            insight_tools = [read_codebase_file, search_codebase, store_insight]

            for model in models:
                try:
                    await run_agent_session(client, model, INSIGHT_SYSTEM_PROMPT, insight_prompt, insight_tools, timeout=180)
                    print("Phase 3 — complete")
                    break
                except Exception as e:
                    print(f"Phase 3 — {model} failed: {e}")

            if new_insights:
                for ni in new_insights:
                    ni["date"]  = datetime.now().strftime("%Y-%m-%d")
                    ni["issue"] = int(issue_number)

                merged = existing_insights + new_insights
                merged = prune_insights(merged)
                save_insights(merged)
                print(f"Phase 3 — stored {len(new_insights)} insight(s), total: {len(merged)}")
            else:
                print("Phase 3 — no new insights")

        if written_files:
            pr_lines = [
                "Automated fix proposed by AI analysis of the linked issue.\n",
                "## Changes\n",
            ]

            for wf in written_files:
                prefix = "**New:** " if wf.get("new") else ""
                pr_lines.append(f"- {prefix}`{wf['path']}`: {wf['reason']}")

            pr_lines.append("\n**This is a draft PR — human review required before merging.**")
            Path("pr_description.md").write_text("\n".join(pr_lines))
            print("PR description written")

        if is_reply and text.strip().upper().startswith("SKIP"):
            print("Bot decided to skip — no response needed")
        else:
            Path(RESPONSE_FILE).write_text(text)
            print("Response written to response.md")
    finally:
        await client.stop()


if __name__ == "__main__":
    try:
        asyncio.run(run())
    except Exception as fatal:
        print(f"FATAL: {fatal}")

        failure_body = (
            "The AI analysis was unable to generate a response for this issue.\n\n"
            f"**Error:** `{fatal}`\n\n"
            "A human maintainer will follow up."
        )

        Path("failure.md").write_text(failure_body)
        raise
