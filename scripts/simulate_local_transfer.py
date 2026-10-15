#!/usr/bin/env python3
"""
TriLink End-to-End Local Network Transfer Contract Simulation.
Tests:
1. Server protocol dispatcher for /api/v1/health.
2. Authenticated Pairing Exchange and Revocation enforcement.
3. Untrusted Peer Rejection (403 Forbidden).
4. Authenticated Transfer Initiation (200 OK with session token).
5. Binary Payload Streaming (64 KB chunks) with SHA-256 integrity verification.
6. Temporary .part file creation and atomic rename to downloads folder.
7. Corrupted/Tampered Payload Integrity Check (422 INTEGRITY_MISMATCH rejection).
"""
import hashlib
import io
import json
import os
import tempfile
import time
from pathlib import Path

TRUSTED_DEVICE_ID = "device-realme-11"
REVOKED_DEVICE_ID = "device-revoked-pad"
LOCAL_SERVER_DEVICE_ID = "device-macbook-air"

class LocalTransferProtocolEngine:
    def __init__(self, download_dir: str):
        self.download_dir = Path(download_dir)
        self.active_transfers = {}
        self.trusted_devices = {
            TRUSTED_DEVICE_ID: {
                "id": TRUSTED_DEVICE_ID,
                "name": "Realme 11 Pro+",
                "platform": "android",
                "is_revoked": False
            },
            REVOKED_DEVICE_ID: {
                "id": REVOKED_DEVICE_ID,
                "name": "Old Revoked Pad",
                "platform": "android",
                "is_revoked": True
            }
        }

    def handle_request(self, method: str, path: str, headers: dict, body: bytes) -> tuple[int, dict, bytes]:
        # Health Check
        if method == "GET" and path == "/api/v1/health":
            return (200, {"Content-Type": "application/json"}, b'{"status":"ok"}')

        # Pairing
        if method == "POST" and path == "/api/v1/pair":
            payload = json.loads(body.decode("utf-8"))
            dev_id = payload.get("device_id")
            if dev_id in self.trusted_devices and self.trusted_devices[dev_id]["is_revoked"]:
                return (403, {"Content-Type": "application/json"}, b'{"error":"DEVICE_REVOKED"}')
            return (200, {"Content-Type": "application/json"}, b'{"status":"approved"}')

        # Authenticate Sender
        sender_id = headers.get("X-TriLink-Sender-ID")
        sig = headers.get("X-TriLink-Signature")
        ts = headers.get("X-TriLink-Timestamp")

        if not sender_id or sender_id not in self.trusted_devices:
            return (403, {"Content-Type": "application/json"}, b'{"error":"DEVICE_UNTRUSTED"}')

        if self.trusted_devices[sender_id]["is_revoked"]:
            return (403, {"Content-Type": "application/json"}, b'{"error":"DEVICE_REVOKED"}')

        if not sig:
            return (401, {"Content-Type": "application/json"}, b'{"error":"MISSING_SIGNATURE"}')

        # Transfer Initiate
        if method == "POST" and path == "/api/v1/transfers/initiate":
            req = json.loads(body.decode("utf-8"))
            tid = req["transfer_id"]
            temp_file = self.download_dir / f"{tid}.part"
            temp_file.write_bytes(b"") # create empty part file
            self.active_transfers[tid] = {
                "request": req,
                "temp_file": temp_file,
                "received_bytes": 0
            }
            resp = json.dumps({"status": "accepted", "upload_token": tid}).encode("utf-8")
            return (200, {"Content-Type": "application/json"}, resp)

        # Payload Streaming Chunk
        if method == "POST" and "/payload" in path:
            comps = path.split("/")
            tid = comps[4]
            if tid not in self.active_transfers:
                return (404, {"Content-Type": "application/json"}, b'{"error":"TRANSFER_NOT_FOUND"}')

            transfer = self.active_transfers[tid]
            temp_file = transfer["temp_file"]

            with open(temp_file, "ab") as f:
                f.write(body)

            transfer["received_bytes"] += len(body)

            # Check if all bytes received
            if transfer["received_bytes"] >= transfer["request"]["size_bytes"]:
                received_data = temp_file.read_bytes()
                computed_sha = hashlib.sha256(received_data).hexdigest()
                declared_sha = transfer["request"]["sha256"]

                if computed_sha.lower() == declared_sha.lower():
                    # Commit to final destination
                    dest_file = self.download_dir / transfer["request"]["display_name"]
                    temp_file.rename(dest_file)
                    del self.active_transfers[tid]

                    resp = json.dumps({
                        "status": "delivered",
                        "transfer_id": tid,
                        "verified_sha256": computed_sha
                    }).encode("utf-8")
                    return (200, {"Content-Type": "application/json"}, resp)
                else:
                    temp_file.unlink(missing_ok=True)
                    del self.active_transfers[tid]
                    return (422, {"Content-Type": "application/json"}, b'{"error":"INTEGRITY_MISMATCH"}')
            else:
                resp = json.dumps({
                    "status": "uploading",
                    "bytes_received": transfer["received_bytes"]
                }).encode("utf-8")
                return (206, {"Content-Type": "application/json"}, resp)

        return (404, {}, b'{"error":"NOT_FOUND"}')

def run_simulation():
    print("Executing TriLink Local Transfer Protocol Contract Simulation...")
    with tempfile.TemporaryDirectory() as tmpdir:
        engine = LocalTransferProtocolEngine(tmpdir)

        # 1. Health Endpoint
        status, _, body = engine.handle_request("GET", "/api/v1/health", {}, b"")
        assert status == 200
        assert b"ok" in body
        print("✓ GET /api/v1/health -> 200 OK")

        # 2. Revoked peer rejection during pairing
        status, _, body = engine.handle_request(
            "POST", "/api/v1/pair", {},
            json.dumps({"device_id": REVOKED_DEVICE_ID}).encode()
        )
        assert status == 403
        assert b"DEVICE_REVOKED" in body
        print("✓ Revoked peer pairing -> 403 Forbidden")

        # 3. Untrusted peer rejection
        status, _, body = engine.handle_request(
            "POST", "/api/v1/transfers/initiate",
            {"X-TriLink-Sender-ID": "random-attacker", "X-TriLink-Signature": "sig"},
            b"{}"
        )
        assert status == 403
        assert b"DEVICE_UNTRUSTED" in body
        print("✓ Untrusted peer transfer initiate -> 403 Forbidden")

        # 4. Valid Transfer Initiation: 128 KB Binary Payload
        test_data = os.urandom(128 * 1024)
        file_sha = hashlib.sha256(test_data).hexdigest()
        transfer_id = "transfer-contract-12345"

        init_req = {
            "version": 1,
            "transfer_id": transfer_id,
            "sender_device_id": TRUSTED_DEVICE_ID,
            "recipient_device_id": LOCAL_SERVER_DEVICE_ID,
            "item_type": "file",
            "display_name": "meeting_recording.mp4",
            "size_bytes": len(test_data),
            "sha256": file_sha,
            "delivery_mode": "local",
            "created_at": int(time.time())
        }

        status, _, body = engine.handle_request(
            "POST", "/api/v1/transfers/initiate",
            {
                "X-TriLink-Sender-ID": TRUSTED_DEVICE_ID,
                "X-TriLink-Signature": "valid-sig",
                "X-TriLink-Timestamp": str(int(time.time()))
            },
            json.dumps(init_req).encode()
        )
        assert status == 200
        assert b"accepted" in body
        print("✓ Valid transfer initiate -> 200 Accepted with session token")

        # 5. Stream in two 64 KB chunks
        chunk1 = test_data[:65536]
        chunk2 = test_data[65536:]

        # Send Chunk 1
        status, _, body = engine.handle_request(
            "POST", f"/api/v1/transfers/{transfer_id}/payload",
            {
                "X-TriLink-Sender-ID": TRUSTED_DEVICE_ID,
                "X-TriLink-Signature": "valid-sig"
            },
            chunk1
        )
        assert status == 206
        assert b"uploading" in body
        print("✓ Stream chunk 1 (64 KB) received -> 206 Partial Content (uploading)")

        # Send Chunk 2 (completes the stream)
        status, _, body = engine.handle_request(
            "POST", f"/api/v1/transfers/{transfer_id}/payload",
            {
                "X-TriLink-Sender-ID": TRUSTED_DEVICE_ID,
                "X-TriLink-Signature": "valid-sig"
            },
            chunk2
        )
        assert status == 200
        res = json.loads(body.decode())
        assert res["status"] == "delivered"
        assert res["verified_sha256"] == file_sha
        print("✓ Stream chunk 2 (64 KB) completes stream -> SHA-256 verified -> 200 Delivered")

        # 6. Verify final file on disk
        dest_file = Path(tmpdir) / "meeting_recording.mp4"
        assert dest_file.exists()
        assert dest_file.read_bytes() == test_data
        print(f"✓ Final destination file committed to disk ({len(test_data)} bytes verified byte-for-byte)")

        # 7. Test Corrupted / Tampered Payload (Integrity mismatch)
        corrupted_id = "transfer-corrupt-999"
        corrupted_init = dict(init_req, transfer_id=corrupted_id, display_name="corrupt.bin")
        engine.handle_request(
            "POST", "/api/v1/transfers/initiate",
            {"X-TriLink-Sender-ID": TRUSTED_DEVICE_ID, "X-TriLink-Signature": "sig"},
            json.dumps(corrupted_init).encode()
        )
        # Send bad data
        bad_data = b"X" * len(test_data)
        status, _, body = engine.handle_request(
            "POST", f"/api/v1/transfers/{corrupted_id}/payload",
            {"X-TriLink-Sender-ID": TRUSTED_DEVICE_ID, "X-TriLink-Signature": "sig"},
            bad_data
        )
        assert status == 422
        assert b"INTEGRITY_MISMATCH" in body
        corrupt_file = Path(tmpdir) / "corrupt.bin"
        assert not corrupt_file.exists(), "Corrupted payload must not be committed to disk"
        print("✓ Corrupted payload rejected with 422 INTEGRITY_MISMATCH and discarded from disk")

    print("\nAll Local Transfer Protocol Simulation tests PASSED successfully.")

if __name__ == "__main__":
    run_simulation()
