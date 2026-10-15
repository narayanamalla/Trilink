-- TriLink Supabase Migration: Schema, RLS, and Storage Configuration
-- Version: 20261015000000

-- 1. Devices Table
CREATE TABLE IF NOT EXISTS public.devices (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL CHECK (char_length(device_name) BETWEEN 1 AND 64),
    platform TEXT NOT NULL CHECK (platform IN ('android', 'macos')),
    public_key TEXT NOT NULL CHECK (char_length(public_key) >= 16),
    exchange_key TEXT NOT NULL CHECK (char_length(exchange_key) >= 16),
    local_endpoint TEXT,
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- Indexing for fast reachability and lookup
CREATE INDEX IF NOT EXISTS idx_devices_owner ON public.devices(owner_id);
CREATE INDEX IF NOT EXISTS idx_devices_unrevoked ON public.devices(id) WHERE is_revoked = FALSE;

-- 2. Transfers Table
CREATE TABLE IF NOT EXISTS public.transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender_device_id UUID NOT NULL REFERENCES public.devices(id),
    recipient_device_id UUID NOT NULL REFERENCES public.devices(id),
    item_type TEXT NOT NULL CHECK (item_type IN ('text', 'url', 'image', 'file')),
    display_name TEXT NOT NULL CHECK (char_length(display_name) BETWEEN 1 AND 255),
    mime_type TEXT,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 TEXT NOT NULL CHECK (sha256 ~* '^[a-f0-9]{64}$'),
    delivery_mode TEXT NOT NULL CHECK (delivery_mode IN ('local', 'remote')),
    status TEXT NOT NULL CHECK (status IN (
        'created', 'queued', 'uploading', 'available',
        'downloading', 'delivered', 'failed', 'cancelled', 'expired'
    )),
    storage_object_id TEXT, -- Path in private bucket 'transfers-encrypted'
    encrypted_key_envelope TEXT, -- Base64 encoded ephemeral public key & IV
    failure_code TEXT,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (timezone('utc'::text, now()) + interval '48 hours')
);

CREATE INDEX IF NOT EXISTS idx_transfers_recipient_status ON public.transfers(recipient_device_id, status);
CREATE INDEX IF NOT EXISTS idx_transfers_owner ON public.transfers(owner_id);
CREATE INDEX IF NOT EXISTS idx_transfers_expires ON public.transfers(expires_at) WHERE status != 'delivered';

-- 3. Row-Level Security (RLS) Configuration

ALTER TABLE public.devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transfers ENABLE ROW LEVEL SECURITY;

-- Device RLS Policies:
-- Users can only select, insert, update, or delete their own devices.
CREATE POLICY "Users can view their own devices"
    ON public.devices FOR SELECT
    USING (auth.uid() = owner_id);

CREATE POLICY "Users can register devices for their account"
    ON public.devices FOR INSERT
    WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Users can update their own devices"
    ON public.devices FOR UPDATE
    USING (auth.uid() = owner_id)
    WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Users can delete their own devices"
    ON public.devices FOR DELETE
    USING (auth.uid() = owner_id);

-- Transfer RLS Policies:
-- A transfer record can only be viewed or modified by the account owner
-- and sender or recipient devices that are currently unrevoked.
CREATE POLICY "Users can view transfers for their account"
    ON public.transfers FOR SELECT
    USING (
        auth.uid() = owner_id
        AND EXISTS (
            SELECT 1 FROM public.devices d
            WHERE d.id = transfers.recipient_device_id
            AND d.is_revoked = FALSE
        )
    );

CREATE POLICY "Users can insert transfers for their own devices"
    ON public.transfers FOR INSERT
    WITH CHECK (
        auth.uid() = owner_id
        AND EXISTS (
            SELECT 1 FROM public.devices s
            WHERE s.id = transfers.sender_device_id
            AND s.owner_id = auth.uid()
            AND s.is_revoked = FALSE
        )
        AND EXISTS (
            SELECT 1 FROM public.devices r
            WHERE r.id = transfers.recipient_device_id
            AND r.owner_id = auth.uid()
            AND r.is_revoked = FALSE
        )
    );

CREATE POLICY "Users can update their transfers"
    ON public.transfers FOR UPDATE
    USING (auth.uid() = owner_id)
    WITH CHECK (auth.uid() = owner_id);

CREATE POLICY "Users can delete their transfers"
    ON public.transfers FOR DELETE
    USING (auth.uid() = owner_id);

-- 4. Storage Bucket Setup (Client-Encrypted Payloads Only)
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'transfers-encrypted',
    'transfers-encrypted',
    FALSE, -- Strictly private
    52428800, -- 50 MB limit
    ARRAY['application/octet-stream'] -- Only raw ciphertext blobs
)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS Policies:
-- Only authenticated account owners with valid transfer records can read/write objects.
CREATE POLICY "Authenticated users can upload encrypted transfer objects"
    ON storage.objects FOR INSERT
    WITH CHECK (
        bucket_id = 'transfers-encrypted'
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Authenticated users can download their encrypted transfer objects"
    ON storage.objects FOR SELECT
    USING (
        bucket_id = 'transfers-encrypted'
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

CREATE POLICY "Authenticated users can delete their transfer objects"
    ON storage.objects FOR DELETE
    USING (
        bucket_id = 'transfers-encrypted'
        AND auth.role() = 'authenticated'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
