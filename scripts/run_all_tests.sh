#!/usr/bin/env bash
set -e

echo "=================================================="
echo "          TriLink Automated Test Suite            "
echo "=================================================="

# 1. Validate Protocol Schemas and Examples
echo ""
echo "--- [1/5] Protocol Schemas & Examples Validation ---"
python3 scripts/validate_protocol.py

# 2. State Machine Transitions & Idempotency
echo ""
echo "--- [2/5] Transfer State Machine Tests ---"
python3 scripts/test_state_machine.py

# 3. Security, Canonicalization, Path Sanitizer
echo ""
echo "--- [3/5] Security & Path Sanitizer Tests ---"
python3 scripts/test_security_and_path.py

# 4. Supabase Schema & Row-Level Security
echo ""
echo "--- [4/5] Supabase DDL & RLS Policy Verification ---"
python3 scripts/test_supabase_schema.py

# 5. Local Transfer Protocol Contract Simulation
echo ""
echo "--- [5/5] Local Network Streaming Transfer Simulation ---"
python3 scripts/simulate_local_transfer.py

echo ""
echo "=================================================="
echo "✓ ALL TRILINK TEST SUITES PASSED SUCCESSFULLY!"
echo "=================================================="
