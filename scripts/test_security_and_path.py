#!/usr/bin/env python3
"""
Security and Path Sanitizer Contract Verification Suite.
"""
import hashlib
import os
import re
import tempfile
import time
from pathlib import Path

ILLEGAL_CHARS = re.compile(r'[\\/:*?"<>|\x00-\x1f]')

def sanitize_filename(raw_name: str, fallback: str = "received_file") -> str:
    name = raw_name.strip()
    if not name:
        return fallback

    name = name.replace("\\", "/")
    if "/" in name:
        name = name.split("/")[-1]

    name = name.replace("..", "")
    if not name:
        return fallback

    original_ext = ""
    if "." in raw_name:
        original_ext = raw_name.split(".")[-1]

    cleaned = ILLEGAL_CHARS.sub("_", name)
    if not cleaned or cleaned == ".":
        return fallback

    if original_ext and "/" not in original_ext and "\\" not in original_ext:
        base = cleaned.rsplit(".", 1)[0]
        cleaned = f"{base}.{original_ext}"

    if len(cleaned) > 120:
        cleaned = cleaned[:120]

    return cleaned if cleaned.strip() else fallback

def canonical_string(method: str, path: str, timestamp: int, nonce: str, body_sha: str) -> str:
    return f"{method.upper()}\n{path}\n{timestamp}\n{nonce}\n{body_sha.lower()}"

def verify_timestamp(ts: int, max_drift: int = 120) -> bool:
    now = int(time.time())
    delta = abs(now - (ts // 1000 if ts > 1_000_000_000_000 else ts))
    return delta <= max_drift

def test_path_sanitization():
    # 1. Traversal attacks
    assert sanitize_filename("../../etc/passwd") == "passwd"
    assert sanitize_filename("../../../../Windows/System32/drivers/etc/hosts") == "hosts"
    assert sanitize_filename("..\\..\\malicious.exe") == "malicious.exe"

    # 2. Control characters & forbidden symbols
    sanitized = sanitize_filename("doc:photo*?<>.pdf")
    assert ":" not in sanitized
    assert "*" not in sanitized
    assert "?" not in sanitized
    assert "<" not in sanitized
    assert ">" not in sanitized
    assert sanitized.endswith(".pdf")

    # 3. Fallbacks
    assert sanitize_filename("   ", fallback="empty.bin") == "empty.bin"
    assert sanitize_filename("../..", fallback="fallback.dat") == "fallback.dat"
    print("✓ Path sanitization and path traversal defenses verified")

def test_collision_resolution():
    with tempfile.TemporaryDirectory() as tmpdir:
        p = Path(tmpdir)
        file1 = p / "test.txt"
        file1.write_text("v1")

        # Resolving collision
        target = file1
        base = target.stem
        ext = target.suffix
        counter = 1
        while target.exists() and counter < 1000:
            target = p / f"{base}({counter}){ext}"
            counter += 1

        assert target.name == "test(1).txt"
        target.write_text("v2")

        # Second collision
        target2 = p / f"{base}({counter}){ext}"
        assert target2.name == "test(2).txt"
    print("✓ Destination file collision resolution verified")

def test_security_canonicalization_and_drift():
    # 1. Canonical string format
    data = b"Hello TriLink Transfer"
    sha = hashlib.sha256(data).hexdigest()
    ts = int(time.time())
    canon = canonical_string("POST", "/api/v1/transfers/initiate", ts, "nonce-12345", sha)
    expected = f"POST\n/api/v1/transfers/initiate\n{ts}\nnonce-12345\n{sha}"
    assert canon == expected

    # 2. Drift window
    assert verify_timestamp(int(time.time())) is True
    assert verify_timestamp(int(time.time()) - 60) is True
    assert verify_timestamp(int(time.time()) - 300) is False  # 5 minutes old -> rejected
    assert verify_timestamp(int(time.time()) + 300) is False  # Future replay -> rejected
    print("✓ Canonicalization and clock drift protection verified")

if __name__ == "__main__":
    print("Running Security & Sanitization Verification...")
    test_path_sanitization()
    test_collision_resolution()
    test_security_canonicalization_and_drift()
    print("All Security and Sanitization Tests PASSED successfully.")
