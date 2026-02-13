#!/usr/bin/env python3

import asyncio
import os
import re
import shutil
import subprocess
from pathlib import Path

from copilot import CopilotClient

CHATCONTROL_DIR = "chatcontrol"
FOUNDATION_DIR = "foundation"
MAX_FILE_SIZE = 60_000
MAX_CONTEXT_CHARS = 600_000
MAX_SEARCH_FILES = 40
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

SYSTEM_PROMPT = """You are an expert support agent for ChatControl, a premium Minecraft (Spigot/Paper/BungeeCord/Velocity) plugin for chat management, formatting, filtering, and moderation.

## Project Structure
This is a multi-module Maven project:
- chatcontrol-bukkit/ — Main Bukkit/Spigot/Paper implementation
  - Entry point: src/main/java/org/mineacademy/chatcontrol/ChatControl.java
  - Commands: command/
  - Listeners: listener/
  - Settings/config: settings/
  - Operators (rules engine): operator/
  - Config files: src/main/resources/ (settings.yml, rules/, formats/, messages/, lang/)
- chatcontrol-core/ — Shared core logic across platforms
- chatcontrol-proxy-core/ — Shared proxy logic
- chatcontrol-bungeecord/ — BungeeCord implementation
- chatcontrol-velocity/ — Velocity implementation

## Dependencies
- Foundation library (github.com/kangarko/foundation) provides the core framework
- Classes in org.mineacademy.fo.* are from Foundation, NOT ChatControl
- Classes in org.mineacademy.chatcontrol.* are from ChatControl

## Key Config Files
- settings.yml — Main configuration (channels, formatting, rules, groups, etc.)
- database.yml — Database configuration (SQLite/MySQL)
- proxy.yml — Proxy (BungeeCord/Velocity) settings
- formats/*.yml — Chat format templates
- rules/*.rs — Rule files for chat filtering
- messages/*.rs — Join/quit/death/timed message files
- lang/ — Localization files

## Common Issue Categories
1. Chat Formatting — channels, formats, placeholders, colors, MiniMessage
2. Rules/Filters — regex rules, swear filters, anti-spam, anti-caps
3. Channels — channel creation, joining, permissions, ranged channels
4. Groups — permission groups, group-based formatting
5. Proxy Sync — BungeeCord/Velocity setup, cross-server messaging
6. Database — MySQL/MariaDB connection, encoding, migration
7. Permissions — permission nodes, operator behavior
8. PlaceholderAPI — variable integration, custom placeholders

## Your Behavior
- Analyze the provided codebase context to find relevant classes, configs, and handlers
- Reference actual file paths, class names, and config keys — NEVER hallucinate
- Distinguish between ChatControl code and Foundation code
- If a bug likely originates in Foundation, say so and reference the Foundation repo
- For config questions, reference the exact YAML file and key path
- When a stacktrace is provided, trace through the code to identify the root cause
- NEVER suggest downgrading the plugin or Java version
- If the issue lacks info to diagnose, ask for: server version, ChatControl version, relevant config snippets, error logs, and `/chc debug` ZIP output

## Response Format
- Be concise and direct — no greetings, no filler
- Use GitHub-flavored Markdown
- Use code blocks for config examples with yaml/java language tags
- Reference file paths relative to the repository root
- Structure your response with clear headers if covering multiple points
- If you need more information, list exactly what you need at the end"""


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


def read_file_safe(path):
    try:
        content = Path(path).read_text(errors="replace")

        if len(content) > MAX_FILE_SIZE:
            content = content[:MAX_FILE_SIZE] + "\n... (truncated)"

        return content
    except Exception:
        return ""


def compile_context(priority_files, search_files):
    parts     = []
    total     = 0
    included  = set()

    for key_file in KEY_FILES:
        if total >= MAX_CONTEXT_CHARS:
            break

        if not Path(key_file).exists():
            continue

        content = read_file_safe(key_file)

        if content:
            parts.append(f"### {key_file}\n```\n{content}\n```")
            total += len(content)
            included.add(key_file)

    for file_path in priority_files:
        if total >= MAX_CONTEXT_CHARS:
            break

        if file_path in included:
            continue

        content = read_file_safe(file_path)

        if content:
            parts.append(f"### {file_path} (priority match)\n```\n{content}\n```")
            total += len(content)
            included.add(file_path)

    for file_path in search_files:
        if total >= MAX_CONTEXT_CHARS:
            break

        if file_path in included:
            continue

        content = read_file_safe(file_path)

        if content:
            parts.append(f"### {file_path}\n```\n{content}\n```")
            total += len(content)
            included.add(file_path)

    return "\n\n".join(parts)


def build_prompt(title, body, labels, context):
    label_line = f"\n**Labels:** {labels}" if labels else ""

    return f"""Analyze this GitHub issue and provide a helpful, accurate response.

## Issue
**Title:** {title}{label_line}

**Body:**
{body}

## Codebase Context
The following files from the ChatControl and Foundation repositories are relevant:

{context}

Provide a clear, actionable response. Reference specific file paths and config keys where applicable."""


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

        return "\n".join(parts).strip()

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


def extract_assistant_message_text(messages):
    assistant_texts = []

    for message in messages:
        role = normalize_role(read_field(message, "role"))

        if role != "assistant":
            continue

        content = read_field(message, "content")
        text    = extract_text(content)

        if text:
            assistant_texts.append(text)

    if not assistant_texts:
        return ""

    return assistant_texts[-1]


def resolve_cli_path():
    cli_path = shutil.which("copilot")

    if not cli_path:
        raise RuntimeError("Copilot CLI executable was not found on PATH.")

    return cli_path


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

    keywords = extract_keywords(title, body)
    print(f"Extracted {len(keywords)} keywords")

    stacktrace_classes = extract_stacktrace_classes(body)
    class_files        = find_class_files(stacktrace_classes) if stacktrace_classes else []
    mentioned_files    = extract_mentioned_files(body)
    priority_files     = list(dict.fromkeys(class_files + mentioned_files))

    print(f"Priority files: {len(priority_files)} (stacktrace: {len(class_files)}, mentioned: {len(mentioned_files)})")

    search_files = search_repos_by_keywords(keywords)
    print(f"Keyword search files: {len(search_files)}")

    context = compile_context(priority_files, search_files)
    print(f"Context size: {len(context):,} chars")

    user_prompt = build_prompt(title, body, labels, context)
    full_prompt = SYSTEM_PROMPT + "\n\n---\n\n" + user_prompt

    models = ["claude-opus-4.6-fast", "claude-opus-4.6"]

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
            print(f"Trying model: {model}")

            try:
                session = await client.create_session({
                    "model": model,
                    "infinite_sessions": {"enabled": False},
                })

                try:
                    done            = asyncio.Event()
                    response_chunks = []
                    event_errors    = []
                    callback_errors = []

                    def on_event(event):
                        try:
                            event_type = normalize_role(read_field(event, "type"))
                            event_data = read_field(event, "data")

                            if event_type == "assistant.message":
                                chunk = extract_text(read_field(event_data, "content"))

                                if chunk:
                                    response_chunks.append(chunk)

                            elif event_type == "assistant.message_delta":
                                chunk = extract_text(read_field(event_data, "delta_content"))

                                if chunk:
                                    response_chunks.append(chunk)

                            elif event_type in ("error", "session.error", "assistant.error"):
                                error_text = extract_text(event_data)

                                if error_text:
                                    event_errors.append(f"{event_type}: {error_text}")
                                else:
                                    event_errors.append(event_type)

                            elif event_type == "session.idle":
                                done.set()
                        except Exception as callback_error:
                            callback_errors.append(repr(callback_error))
                            done.set()

                    session.on(on_event)
                    await session.send({"prompt": full_prompt})
                    await asyncio.wait_for(done.wait(), timeout=300)

                    messages      = await session.get_messages()
                    history_text  = extract_assistant_message_text(messages)
                    streamed_text = "\n".join(response_chunks).strip()
                    candidate     = history_text if history_text else streamed_text

                    if candidate:
                        text = candidate
                        print(f"Success with model: {model}")
                        break

                    diagnostic = (
                        f"Model '{model}' returned empty assistant output. "
                        f"response_chunks={len(response_chunks)}, "
                        f"event_errors={event_errors[:3]}, "
                        f"callback_errors={callback_errors[:3]}, "
                        f"message_count={len(messages)}"
                    )
                    raise RuntimeError(diagnostic)
                finally:
                    await session.destroy()
            except Exception as e:
                failure = f"{model}: {e}"
                model_failures.append(failure)
                print(f"Model {model} failed: {e}")
                continue

        if not text:
            joined_failures = " | ".join(model_failures)
            raise RuntimeError(f"All models failed or returned empty output. Details: {joined_failures}")
    finally:
        await client.stop()

    disclaimer = (
        "\n\n---\n"
        "*This response was generated by AI using Claude Opus 4.6 with full codebase analysis of "
        "ChatControl and Foundation. A human maintainer will follow up if needed.*"
    )

    Path(RESPONSE_FILE).write_text(text + disclaimer)
    print("Response written to response.md")


if __name__ == "__main__":
    try:
        asyncio.run(run())
    except Exception as fatal:
        print(f"FATAL: {fatal}")

        failure_body = (
            "The AI analysis was unable to generate a response for this issue.\n\n"
            f"**Error:** `{fatal}`\n\n"
            "A human maintainer will follow up.\n\n"
            "---\n"
            "*Automated diagnostic from the AI Issue Support workflow.*"
        )

        Path("failure.md").write_text(failure_body)
        raise
