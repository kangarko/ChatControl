#!/usr/bin/env python3

import asyncio
import os
import re
import shutil
import subprocess
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

## Your Behavior
- Use tools to explore the codebase — never guess at code behavior or hallucinate paths
- For config questions, reference the exact YAML file and key path
- For stacktraces, trace through the relevant source files
- If the issue lacks info, ask for: server version, ChatControl version, config snippets, error logs, `/chc debug` ZIP
- NEVER suggest downgrading the plugin or Java version

## Response Style
Your readers are Minecraft server owners — busy people who want answers, not essays. Match the length to the complexity: a one-line config fix gets a one-line answer; a multi-layered bug gets a thorough walkthrough. Never pad, never ramble.

- **Lead with the fix.** Solution first, context second. If someone can solve their problem by reading only your first sentence, you did it right.
- **Show only what they need to change** — the relevant config key or code snippet, not the entire file.
- **No greetings, no filler, no sign-offs.** Jump straight in.
- **Don't explain internals** unless the issue specifically asks how something works.
- **Bold the key action:** e.g. **set `X: true` in settings.yml**
- If you need more info, ask a few specific questions in a bullet list at the end.
- Use GitHub Markdown with `yaml` or `java` language tags for code blocks.
- Skip headers (##) unless you're genuinely covering multiple distinct topics.

## Fix Capability
If you identify a clear, confident fix (config value correction, YAML fix, obvious single-file Java bug), use `write_codebase_file` to propose it. The change will be submitted as a draft PR for human review — you are NOT deploying to production.

Only propose fixes when you are confident. Do NOT:
- Modify Foundation code (separate repository)
- Rewrite large sections of code or refactor
- Add new files
- Make speculative or uncertain changes
- Touch build files (pom.xml, build.xml)

Always explain what you changed and why in your response, so the reviewer can verify."""

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
3. If you find problems, fix them using write_codebase_file
4. If you get an unexpected response from any tool, include the raw response in your output
5. If everything looks correct, respond with "LGTM" and nothing else"""


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
    path: str = Field(description="Relative file path within chatcontrol/, e.g. 'chatcontrol/chatcontrol-bukkit/src/main/resources/settings.yml'")
    content: str = Field(description="The complete new content for the file")
    reason: str = Field(description="Brief explanation of why this change fixes the issue")


@define_tool(description="Write a modified source or config file to propose a fix. Only works for existing files in chatcontrol/*/src/main/. Cannot modify Foundation, build files, or .github/. The change will be submitted as a draft PR for human review.")
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

    if not resolved.exists():
        return f"Error: File does not exist: {params.path}. Can only modify existing files."

    if not resolved.is_file():
        return f"Error: Not a file: {params.path}"

    if len(params.content) > MAX_FILE_SIZE:
        return f"Error: Content too large ({len(params.content):,} chars). Max: {MAX_FILE_SIZE:,}."

    try:
        resolved.write_text(params.content)
        written_files.append({"path": params.path, "reason": params.reason})
        return f"Successfully wrote {len(params.content):,} characters to {params.path}"
    except Exception as e:
        return f"Error writing file: {e}"


async def run_agent_session(client, model, system_prompt, user_prompt, tools, timeout=600):
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
        result = subprocess.run(
            ["git", "-C", CHATCONTROL_DIR, "diff"],
            capture_output=True, text=True, timeout=30,
        )
        return result.stdout.strip()
    except Exception as e:
        print(f"Warning: git diff failed: {e}")
        return ""


async def run():
    title  = os.environ["ISSUE_TITLE"]
    body   = os.environ.get("ISSUE_BODY", "") or "(No description provided)"
    labels = os.environ.get("ISSUE_LABELS", "")
    token  = os.environ.get("COPILOT_GITHUB_TOKEN")

    if not token:
        raise RuntimeError("Missing required environment variable: COPILOT_GITHUB_TOKEN")

    if len(body) > 100_000:
        body = body[:100_000] + "\n... (truncated)"

    print(f"Issue: {title}")

    keywords           = extract_keywords(title, body)
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

    user_prompt = f"""Help with this GitHub issue. Keep your response short and actionable.

**Title:** {title}{label_line}

{body}

## Possibly Relevant Files
{key_files_text}
{hints_text}

Read the most relevant files above, then give a short, direct answer. Lead with the fix. Skip unnecessary explanation."""

    all_tools = [read_codebase_file, search_codebase, list_directory, write_codebase_file]
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

                review_prompt = f"""Review these proposed changes for a ChatControl GitHub issue fix.

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

If you find problems, fix them with write_codebase_file. If everything looks correct, respond with "LGTM"."""

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

        if written_files:
            pr_lines = [
                "Automated fix proposed by AI analysis of the linked issue.\n",
                "## Changes\n",
            ]

            for wf in written_files:
                pr_lines.append(f"- `{wf['path']}`: {wf['reason']}")

            pr_lines.append("\n**This is a draft PR — human review required before merging.**")
            Path("pr_description.md").write_text("\n".join(pr_lines))
            print("PR description written")

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
