-- Previous versions stored bearer tokens directly in user_sessions.token.
-- Clear existing sessions so new logins can store only SHA-256 token hashes.
DELETE FROM `user_sessions`;
