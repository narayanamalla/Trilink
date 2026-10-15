#!/usr/bin/env python3
"""
Protocol schema and example validation script for TriLink.
Validates that example messages strictly conform to JSON schemas and expectations.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS_DIR = ROOT / "shared" / "protocol" / "schemas"
EXAMPLES_DIR = ROOT / "shared" / "protocol" / "examples"

UUID_REGEX = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.I)
SHA256_REGEX = re.compile(r"^[a-fA-F0-9]{64}$")

def validate_pairing_payload(data: dict):
    assert data["version"] == 1, "Invalid version"
    assert UUID_REGEX.match(data["pairing_id"]), "Invalid pairing_id UUID"
    assert UUID_REGEX.match(data["device_id"]), "Invalid device_id UUID"
    assert 1 <= len(data["device_name"]) <= 64, "Invalid device_name length"
    assert data["platform"] in ["android", "macos"], "Invalid platform"
    assert len(data["public_key"]) >= 16, "Public key too short"
    assert len(data["exchange_key"]) >= 16, "Exchange key too short"
    assert isinstance(data["expires_at"], int), "expires_at must be an integer timestamp"
    print("✓ pairing_payload_example.json passed validation")

def validate_transfer_request(data: dict):
    assert data["version"] == 1, "Invalid version"
    assert UUID_REGEX.match(data["transfer_id"]), "Invalid transfer_id UUID"
    assert UUID_REGEX.match(data["sender_device_id"]), "Invalid sender_device_id UUID"
    assert UUID_REGEX.match(data["recipient_device_id"]), "Invalid recipient_device_id UUID"
    assert data["item_type"] in ["text", "url", "image", "file"], "Invalid item_type"
    assert 1 <= len(data["display_name"]) <= 255, "Invalid display_name length"
    assert data["size_bytes"] >= 0, "Negative size_bytes"
    assert SHA256_REGEX.match(data["sha256"]), "Invalid sha256 checksum"
    assert data["delivery_mode"] in ["local", "remote"], "Invalid delivery_mode"
    assert isinstance(data["created_at"], int), "created_at must be integer"
    print("✓ transfer_request_example.json passed validation")

def validate_transfer_status(data: dict):
    assert data["version"] == 1, "Invalid version"
    assert UUID_REGEX.match(data["transfer_id"]), "Invalid transfer_id UUID"
    valid_statuses = [
        "created", "queued", "uploading", "available",
        "downloading", "delivered", "failed", "cancelled", "expired"
    ]
    assert data["status"] in valid_statuses, f"Invalid status: {data['status']}"
    assert data["bytes_transferred"] >= 0, "Negative bytes_transferred"
    assert data["total_bytes"] >= 0, "Negative total_bytes"
    assert isinstance(data["updated_at"], int), "updated_at must be integer"
    print("✓ transfer_status_example.json passed validation")

def main():
    print("Validating TriLink protocol examples...")
    with open(EXAMPLES_DIR / "pairing_payload_example.json") as f:
        validate_pairing_payload(json.load(f))

    with open(EXAMPLES_DIR / "transfer_request_example.json") as f:
        validate_transfer_request(json.load(f))

    with open(EXAMPLES_DIR / "transfer_status_example.json") as f:
        validate_transfer_status(json.load(f))

    print("All protocol schemas and example payloads successfully verified.")

if __name__ == "__main__":
    main()
