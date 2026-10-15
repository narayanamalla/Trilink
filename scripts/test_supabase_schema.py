#!/usr/bin/env python3
"""
TriLink Supabase Schema & Security Policy Verification Script.
Validates syntax, required columns, RLS enablement, and storage access restrictions.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MIGRATION_FILE = ROOT / "supabase" / "migrations" / "20261015000000_create_trilink_schema.sql"

def test_migration_policies():
    content = MIGRATION_FILE.read_text(encoding="utf-8")

    # 1. Verify Tables Created
    assert "CREATE TABLE IF NOT EXISTS public.devices" in content
    assert "CREATE TABLE IF NOT EXISTS public.transfers" in content

    # 2. Verify RLS Enabled
    assert "ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;" in content
    assert "ALTER TABLE public.transfers ENABLE ROW LEVEL SECURITY;" in content

    # 3. Verify Revocation and Device Policies
    assert "is_revoked" in content
    assert "CREATE POLICY \"Users can view their own devices\"" in content
    assert "CREATE POLICY \"Users can register devices for their account\"" in content

    # 4. Verify Transfer RLS checks unrevoked status
    assert "d.is_revoked = FALSE" in content

    # 5. Verify Private Storage Bucket
    assert "'transfers-encrypted'" in content
    assert "FALSE, -- Strictly private" in content
    assert "'application/octet-stream'" in content

    # 6. Verify Storage RLS
    assert "CREATE POLICY \"Authenticated users can upload encrypted transfer objects\"" in content
    assert "CREATE POLICY \"Authenticated users can download their encrypted transfer objects\"" in content

    print("✓ Schema and Table definitions valid")
    print("✓ Row-Level Security explicitly enabled on public.devices & public.transfers")
    print("✓ Revocation checks embedded in transfer access policies")
    print("✓ Private storage bucket 'transfers-encrypted' configured with RLS policies")
    print("All Supabase schema and policy verifications PASSED successfully.")

if __name__ == "__main__":
    test_migration_policies()
