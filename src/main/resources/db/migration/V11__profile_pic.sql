-- ============================================================================
-- Harsh-Boss — profile pic column (V11)
-- Stores the user's avatar as a base64 data URL (no file system needed).
-- ============================================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_pic TEXT;
