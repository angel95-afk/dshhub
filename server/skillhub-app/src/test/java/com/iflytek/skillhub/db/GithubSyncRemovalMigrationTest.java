package com.iflytek.skillhub.db;

import com.iflytek.skillhub.support.PostgresTestImage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves V10000 leaves no GitHub sync object behind and does not damage the schema it shares.
 *
 * <p>Runs a full V1 to latest migration, which is the new-deployment path, and also writes to
 * the core tables, which is what would break if a dropped column were still referenced.
 */
@Testcontainers
class GithubSyncRemovalMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(PostgresTestImage.name());

    /** All 37 tables V76-V126 created for the sync engine. */
    private static final List<String> GITHUB_TABLES = List.of(
            "github_deep_discovery_config",
            "github_deep_discovery_query",
            "github_discovery_batch",
            "github_discovery_candidate",
            "github_discovery_candidate_origin",
            "github_discovery_entry",
            "github_discovery_legacy_unknown_validation",
            "github_discovery_metrics_epoch",
            "github_discovery_outcome_event",
            "github_discovery_task_hit",
            "github_official_discovery_budget",
            "github_official_discovery_reservation",
            "github_official_discovery_shard",
            "github_official_quota_observation",
            "github_official_resource_cooldown",
            "github_official_secondary_cooldown",
            "github_official_validated_seed",
            "github_platform_collection_control",
            "github_repository_snapshot",
            "github_repository_snapshot_member",
            "github_source_binding",
            "github_source_origin",
            "github_source_skill_binding",
            "github_sync_admission_policy",
            "github_sync_claim_cursor",
            "github_sync_namespace_admission_state",
            "github_sync_package_admission",
            "github_sync_package_ingestion",
            "github_sync_path_result",
            "github_sync_run",
            "github_sync_schedule_policy",
            "github_sync_staged_upload",
            "github_sync_task",
            "github_sync_worker_instance",
            "github_sync_worker_presence",
            "github_system_credential",
            "github_system_credential_quota");

    @Test
    void githubTablesAreGone() {
        JdbcTemplate jdbc = migratedJdbc("removal_tables");

        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name LIKE 'github#_%' ESCAPE '#'
                ORDER BY table_name
                """, String.class)).isEmpty();
        assertThat(GITHUB_TABLES).hasSize(37);
    }

    @Test
    void githubViewsAndFunctionAreGone() {
        JdbcTemplate jdbc = migratedJdbc("removal_views");

        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.views
                WHERE table_schema = current_schema()
                  AND table_name IN ('github_candidate_discovery_eligibility', 'github_official_owner_eligibility')
                """, String.class)).isEmpty();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = current_schema() AND p.proname = 'refresh_github_sync_run_status'
                """, Integer.class)).isZero();
    }

    @Test
    void githubColumnsOnCoreTablesAreGone() {
        JdbcTemplate jdbc = migratedJdbc("removal_columns");

        assertThat(coreTableColumns(jdbc, "review_task"))
                .doesNotContain("origin_type", "github_sync_ingestion_id");
        assertThat(coreTableColumns(jdbc, "skill"))
                .doesNotContain("github_sync_managed");
        assertThat(coreTableColumns(jdbc, "skill_version"))
                .doesNotContain("github_sync_ingestion_id", "github_sync_revision");
    }

    @Test
    void githubNamespaceAndPublisherAccountAreGone() {
        JdbcTemplate jdbc = migratedJdbc("removal_identity");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM namespace WHERE slug = 'github'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM user_account WHERE id = 'github-sync-publisher'", Integer.class)).isZero();
    }

    /** Positive control: the surrounding core structure must survive the column drops. */
    @Test
    void coreTablesKeepTheirRemainingColumns() {
        JdbcTemplate jdbc = migratedJdbc("removal_control");

        assertThat(coreTableColumns(jdbc, "review_task"))
                .contains("id", "skill_id", "namespace_id", "status", "subject_type", "subject_id",
                        "submitted_by", "previous_visibility", "requested_visibility", "visibility_version_id");
        assertThat(coreTableColumns(jdbc, "skill"))
                .contains("id", "namespace_id", "slug", "owner_id", "display_name", "summary",
                        "visibility", "latest_version_id", "active_avatar_id");
        assertThat(coreTableColumns(jdbc, "skill_version"))
                .contains("id", "skill_id", "version", "status", "yanked_at");

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND column_name LIKE 'github%'
                """, Integer.class)).isZero();
    }

    /** The reported upgrade failure mode: a leftover reference breaks ordinary writes. */
    @Test
    void coreTablesStillAcceptWrites() {
        JdbcTemplate jdbc = migratedJdbc("removal_writes");

        jdbc.update("INSERT INTO user_account(id,display_name) VALUES ('removal-owner','Removal owner')");
        long namespace = jdbc.queryForObject("""
                INSERT INTO namespace(slug,display_name,type,created_by)
                VALUES ('removal-team','Removal team','TEAM','removal-owner') RETURNING id
                """, Long.class);
        long skill = jdbc.queryForObject("""
                INSERT INTO skill(namespace_id,slug,owner_id,display_name,summary,visibility)
                VALUES (?,'removed','removal-owner','Title','Summary','PUBLIC') RETURNING id
                """, Long.class, namespace);
        long version = jdbc.queryForObject("""
                INSERT INTO skill_version(skill_id,version,status) VALUES (?,'1.0.0','PUBLISHED') RETURNING id
                """, Long.class, skill);
        jdbc.update("UPDATE skill SET latest_version_id=? WHERE id=?", version, skill);
        jdbc.update("""
                INSERT INTO review_task(skill_id,namespace_id,subject_type,subject_id,subject_version,submitted_by)
                VALUES (?,?,'SKILL_METADATA',?,'metadata:removal','removal-owner')
                """, skill, namespace, skill);

        assertThat(jdbc.queryForObject(
                "SELECT version FROM skill_version WHERE id=?", String.class, version)).isEqualTo("1.0.0");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM review_task WHERE skill_id=?", Integer.class, skill)).isEqualTo(1);
    }

    private List<String> coreTableColumns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, String.class, table);
    }

    private JdbcTemplate migratedJdbc(String schema) {
        var admin = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        String url = POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema;
        DataSource source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        return new JdbcTemplate(source);
    }
}
