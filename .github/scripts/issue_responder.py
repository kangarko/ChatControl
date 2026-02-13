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

SYSTEM_PROMPT = """You are a support agent for ChatControl, a Minecraft chat plugin for Spigot/Paper/BungeeCord/Velocity.

## Project Layout
- chatcontrol-bukkit/src/main/resources/ — Config files (settings.yml, database.yml, proxy.yml, formats/, rules/, messages/, lang/)
- chatcontrol-bukkit/src/main/java/ — Main plugin code
- chatcontrol-core/ — Shared logic
- chatcontrol-proxy-core/, chatcontrol-bungeecord/, chatcontrol-velocity/ — Proxy code
- Foundation library (org.mineacademy.fo.*) — Separate framework, NOT ChatControl code

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
- Skip headers (##) unless you're genuinely covering multiple distinct topics."""


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
    search_files       = search_repos_by_keywords(keywords)
    print(f"Pre-analysis: {len(class_files)} stacktrace files, {len(mentioned_files)} mentioned files, {len(search_files)} keyword matches")

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
                    "streaming": True,
                    "system_message": {"content": SYSTEM_PROMPT},
                    "tools": [read_codebase_file, search_codebase, list_directory],
                    "infinite_sessions": {
                        "enabled": True,
                        "background_compaction_threshold": 0.80,
                        "buffer_exhaustion_threshold": 0.95,
                    },
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
                    await session.send({"prompt": user_prompt})
                    await asyncio.wait_for(done.wait(), timeout=600)

                    messages      = await session.get_messages()
                    history_text  = extract_assistant_message_text(messages)
                    streamed_text = "".join(response_chunks).strip()
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

    Path(RESPONSE_FILE).write_text(text)
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
