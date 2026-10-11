-- V5 made local_path globally unique, which was right when there was one implicit user.
-- With ownership (V6) it is wrong: two users cloning the same public repository would
-- collide, and the second registration would silently return the first user's row -
-- handing them someone else's repository.
--
-- Uniqueness now belongs to the PAIR. The same path may appear once per user, and never
-- twice for one user (which would create two rows competing over the same code_chunks).
DROP INDEX IF EXISTS idx_repositories_local_path;

CREATE UNIQUE INDEX idx_repositories_user_path ON repositories (user_id, local_path);