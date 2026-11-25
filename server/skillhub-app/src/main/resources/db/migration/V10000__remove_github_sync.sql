-- Retires the GitHub sync capability (Java worker + Go collector).
--
-- This fork never ran a sync worker against a deployed environment, so no stored row is
-- expected to carry GitHub sync semantics. The assertions below turn that precondition into
-- an enforced migration guard: if someone did run a sync, this migration aborts instead of
-- silently deleting business data. Cleaning that data is a manual, deliberate operation.
--
-- Deliberately retained:
--   * V76-V126 stay untouched, including their checksums, as the historical record.
--   * GitHub OAuth login (external identity provider) has no sync coupling.
--
-- Irreversible: recovery requires restoring a backup.

DO $$
DECLARE
    managed_skills BIGINT;
    catalog_skills BIGINT;
    sync_reviews BIGINT;
BEGIN
    SELECT count(*) INTO managed_skills FROM skill WHERE github_sync_managed = TRUE;
    IF managed_skills <> 0 THEN
        RAISE EXCEPTION
            'Cannot retire GitHub sync: % skill row(s) still have github_sync_managed = TRUE',
            managed_skills;
    END IF;

    SELECT count(*) INTO catalog_skills
    FROM skill s
    JOIN namespace n ON n.id = s.namespace_id
    WHERE n.slug = 'github';
    IF catalog_skills <> 0 THEN
        RAISE EXCEPTION
            'Cannot retire GitHub sync: % skill row(s) still live in the @github catalog namespace',
            catalog_skills;
    END IF;

    SELECT count(*) INTO sync_reviews FROM review_task WHERE origin_type = 'GITHUB_SYNC';
    IF sync_reviews <> 0 THEN
        RAISE EXCEPTION
            'Cannot retire GitHub sync: % review_task row(s) still have origin_type = GITHUB_SYNC',
            sync_reviews;
    END IF;
END $$;

-- Step 1: views depend on the tables and on core columns, so drop them first.
DROP VIEW IF EXISTS github_candidate_discovery_eligibility;
DROP VIEW IF EXISTS github_official_owner_eligibility;

-- Step 2: break the mutual reference V109 created between tasks and discovery queries.
ALTER TABLE github_sync_task
    DROP CONSTRAINT IF EXISTS fk_github_sync_task_official_query;
ALTER TABLE github_deep_discovery_query
    DROP CONSTRAINT IF EXISTS github_deep_discovery_query_active_task_id_fkey;

-- Step 3: drop GitHub constraints and indexes on the three core tables before their columns.
ALTER TABLE review_task
    DROP CONSTRAINT IF EXISTS ck_review_task_github_sync_origin,
    DROP CONSTRAINT IF EXISTS ck_review_task_origin_type,
    DROP CONSTRAINT IF EXISTS review_task_github_sync_ingestion_id_fkey;
DROP INDEX IF EXISTS uq_review_task_github_sync_ingestion;
DROP INDEX IF EXISTS idx_review_task_origin_status;

-- ck_skill_version_github_sync_revision spans two columns, so it must go before either one.
ALTER TABLE skill_version
    DROP CONSTRAINT IF EXISTS ck_skill_version_github_sync_revision,
    DROP CONSTRAINT IF EXISTS skill_version_github_sync_ingestion_id_fkey,
    DROP CONSTRAINT IF EXISTS skill_version_github_sync_ingestion_id_key;

-- Step 4: drop GitHub tables from leaf to root. CASCADE absorbs inline constraints; it must
-- not replace the ordering, because doing so would hide a real dependency mistake.
DROP TABLE IF EXISTS github_sync_worker_presence CASCADE;
DROP TABLE IF EXISTS github_sync_worker_instance CASCADE;
DROP TABLE IF EXISTS github_sync_schedule_policy CASCADE;
DROP TABLE IF EXISTS github_official_resource_cooldown CASCADE;
DROP TABLE IF EXISTS github_official_secondary_cooldown CASCADE;
DROP TABLE IF EXISTS github_official_discovery_budget CASCADE;
DROP TABLE IF EXISTS github_discovery_metrics_epoch CASCADE;
DROP TABLE IF EXISTS github_discovery_outcome_event CASCADE;
DROP TABLE IF EXISTS github_discovery_legacy_unknown_validation CASCADE;
DROP TABLE IF EXISTS github_official_validated_seed CASCADE;
DROP TABLE IF EXISTS github_platform_collection_control CASCADE;
DROP TABLE IF EXISTS github_sync_claim_cursor CASCADE;
DROP TABLE IF EXISTS github_sync_namespace_admission_state CASCADE;
DROP TABLE IF EXISTS github_sync_admission_policy CASCADE;
DROP TABLE IF EXISTS github_discovery_entry CASCADE;
DROP TABLE IF EXISTS github_source_origin CASCADE;

DROP TABLE IF EXISTS github_official_quota_observation CASCADE;
DROP TABLE IF EXISTS github_system_credential_quota CASCADE;
DROP TABLE IF EXISTS github_repository_snapshot_member CASCADE;

DROP TABLE IF EXISTS github_official_discovery_reservation CASCADE;
DROP TABLE IF EXISTS github_discovery_task_hit CASCADE;
DROP TABLE IF EXISTS github_discovery_batch CASCADE;
DROP TABLE IF EXISTS github_sync_path_result CASCADE;
DROP TABLE IF EXISTS github_discovery_candidate_origin CASCADE;

-- Self-referencing shard tree: leaves are removed with the table in one statement.
DROP TABLE IF EXISTS github_official_discovery_shard CASCADE;

DROP TABLE IF EXISTS github_repository_snapshot CASCADE;
DROP TABLE IF EXISTS github_system_credential CASCADE;

DROP TABLE IF EXISTS github_discovery_candidate CASCADE;
DROP TABLE IF EXISTS github_source_skill_binding CASCADE;
DROP TABLE IF EXISTS github_sync_package_ingestion CASCADE;
DROP TABLE IF EXISTS github_sync_staged_upload CASCADE;
DROP TABLE IF EXISTS github_sync_package_admission CASCADE;
DROP TABLE IF EXISTS github_sync_task CASCADE;
DROP TABLE IF EXISTS github_deep_discovery_query CASCADE;
DROP TABLE IF EXISTS github_deep_discovery_config CASCADE;
DROP TABLE IF EXISTS github_sync_run CASCADE;
DROP TABLE IF EXISTS github_source_binding CASCADE;

-- Step 5: drop the GitHub-only columns from the core tables.
ALTER TABLE review_task
    DROP COLUMN IF EXISTS origin_type,
    DROP COLUMN IF EXISTS github_sync_ingestion_id;

ALTER TABLE skill
    DROP COLUMN IF EXISTS github_sync_managed;

ALTER TABLE skill_version
    DROP COLUMN IF EXISTS github_sync_ingestion_id,
    DROP COLUMN IF EXISTS github_sync_revision;

-- Step 6: remove the reserved catalog namespace and its system actor. The foreign keys that
-- reference them carry no ON DELETE action, so the assertions above are what guarantee no
-- remaining reference exists.
DELETE FROM namespace_member
WHERE namespace_id IN (SELECT id FROM namespace WHERE slug = 'github');

DELETE FROM namespace WHERE slug = 'github';
DELETE FROM user_account WHERE id = 'github-sync-publisher';

-- Step 7: the run-status trigger and function survive DROP TABLE, so remove them explicitly.
DROP FUNCTION IF EXISTS refresh_github_sync_run_status();
