-- Seed data for local Supabase testing

-- Mock Authenticated User UUID: 00000000-0000-0000-0000-000000000001
-- Note: In production, users are created via Supabase Auth sign-in.

-- 1. Devices Seed
INSERT INTO public.devices (id, owner_id, device_name, platform, public_key, exchange_key, is_revoked)
VALUES
    ('11111111-1111-1111-1111-111111111111', '00000000-0000-0000-0000-000000000001', 'Realme Phone', 'android', 'MCowBQYDK2VwAyEAfv1Qz80J98GzM7Vj1GfFmN77yEw4VlF/xXhKz0eGq+A=', 'MCowBQYDK2VuAyEAO2e4h5q0+jY7T4mF2/wV7lK8z1nK4tM1/n3h5Q5F+qA=', FALSE),
    ('22222222-2222-2222-2222-222222222222', '00000000-0000-0000-0000-000000000001', 'Xiaomi Pad 8', 'android', 'MCowBQYDK2VwAyEAU1xZ90J98GzM7Vj1GfFmN77yEw4VlF/xXhKz0eGq+B=', 'MCowBQYDK2VuAyEA9kL4h5q0+jY7T4mF2/wV7lK8z1nK4tM1/n3h5Q5F+qB=', FALSE),
    ('33333333-3333-3333-3333-333333333333', '00000000-0000-0000-0000-000000000001', 'MacBook Air', 'macos', 'MCowBQYDK2VwAyEAY8qZ90J98GzM7Vj1GfFmN77yEw4VlF/xXhKz0eGq+C=', 'MCowBQYDK2VuAyEA8xM4h5q0+jY7T4mF2/wV7lK8z1nK4tM1/n3h5Q5F+qC=', FALSE)
ON CONFLICT (id) DO NOTHING;
