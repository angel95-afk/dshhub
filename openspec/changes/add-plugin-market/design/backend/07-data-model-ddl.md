# §6.1–§6.2　Flyway 文件命名与顺序、可直接执行的完整 DDL

> 所属：后端　｜　覆盖章节：§6.1–§6.2
> 章节号全局唯一、不随文件移动；跨文件引用写 `§N.M`，不要写文件名。索引见 [`../README.md`](../README.md)。

## 6. 数据模型详细设计

### 6.1 Flyway 文件命名与顺序

编号选择（**必须写清理由，不得凭猜**）：

- 低段：`db/migration/` 现有 127 个文件，低段实际到 **V126**（`V126__github_repository_snapshots.sql`），不是 V103——`V104__github_sync_worker_presence.sql` 已存在。低段被 `FlywayMigrationGuardrailTest.versionedMigrations_mustRemainContiguous` 要求从 V1 连续，没有任何空号可用。
- 高位段：守卫的 `FORK_VERSION_FLOOR = 10000`，高位段只要求"无重复"（`highBand.distinct().count() == size`），**允许跳号**。已占用 `V10000`（`remove-github-sync`）。
- 因此本变更只占一个号：**`V10001`**。不预留号段：仓库内没有任何号段预留约定，预留只在多个 fork 变更并行改迁移时才成立，本变更独占推进，空 100 个号只增加解释成本。若将来出现并行变更，各自取下一个可用高位号即可（高位段不要求连续）。

文件粒度：**单文件**。9 张新表 + 循环外键 + seed + `review_task` 索引全部在一个文件内完成。理由是 Flyway 按文件提交事务：拆成多个文件时第 N 个失败会留下前 N-1 个已提交的半套 schema，必须 `flyway repair` 手工收敛；单文件让全新域的空库迁移要么全有、要么全无。循环外键在单文件内同样可解——先建两张表、再用 `ALTER TABLE ... ADD CONSTRAINT` 补约束。

| 顺序 | 文件 | 内容 | 依赖 |
|---|---|---|---|
| 1 | `V10001__plugin_market.sql` | 9 张表 + 循环 FK（建表后 ALTER 补）+ `plugin_index_state` seed + `review_task` 部分唯一索引与注释 | `namespace`、`review_task` |

文件内顺序不可调换：`plugin` 先于 `plugin_version`；`plugin_scan_report` 先建表（`plugin_submission_id` 暂不加内联 FK），等 `plugin_submission` 建表后用 `ALTER` 补双向 FK；`plugin_scan_waiver` 依赖 `plugin_submission`；`plugin_report` 只依赖 `plugin`，放在 `review_task` 索引之前；`review_task` 的索引放在最后。空库从 V1 全量执行到 `V10001` 必须成功；`dirty` seed 值取 `TRUE`，让首次启动就生成一份合法的空索引。

### 6.2 DDL（可直接执行）

```sql
-- 文件：V10001__plugin_market.sql（单文件；PostgreSQL DDL 事务性，整体成功或整体回滚）
-- ============ 1. plugin（核心实体） ============
-- DSH 插件目录实体。与 SkillHub 技能（skill/skill_version）零共享，禁止跨表复用状态机。
CREATE TABLE plugin (
    id                  BIGSERIAL      PRIMARY KEY,
    namespace_id        BIGINT         NOT NULL REFERENCES namespace(id),
    slug                VARCHAR(64)    NOT NULL,
    package_name        VARCHAR(214)   NOT NULL,
    category            VARCHAR(32)    NOT NULL,
    title_zh            VARCHAR(200),
    title_en            VARCHAR(200),
    summary_zh          TEXT,
    summary_en          TEXT,
    icon_key            VARCHAR(512),
    repository_url      VARCHAR(1000),
    author_display      VARCHAR(120),
    status              VARCHAR(16)    NOT NULL DEFAULT 'DRAFT',
    visibility          VARCHAR(16)    NOT NULL,
    latest_version_id   BIGINT,
    latest_scan_verdict VARCHAR(16),
    blocked_at          TIMESTAMPTZ,
    blocked_by          VARCHAR(255),
    block_reason        VARCHAR(1000),
    removed_at          TIMESTAMPTZ,
    removed_by          VARCHAR(255),
    remove_reason       VARCHAR(1000),
    name_released_at    TIMESTAMPTZ,
    name_released_by    VARCHAR(255),
    prompt_copy_count   BIGINT         NOT NULL DEFAULT 0,
    search_vector       tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(package_name, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(title_zh, '') || ' ' || coalesce(title_en, '')), 'B') ||
        setweight(to_tsvector('simple', coalesce(summary_zh, '') || ' ' || coalesce(summary_en, '')), 'C')
    ) STORED,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255)   NOT NULL,
    updated_by          VARCHAR(255),
    CONSTRAINT ck_plugin_status CHECK (status IN ('DRAFT', 'PENDING', 'LISTED', 'HIDDEN', 'BLOCKED', 'REMOVED')),
    CONSTRAINT ck_plugin_visibility CHECK (visibility IN ('PUBLIC', 'NAMESPACE_ONLY', 'PRIVATE')),
    CONSTRAINT ck_plugin_category CHECK (category IN (
        'agent', 'ui', 'memory', 'communication', 'devtools',
        'search-knowledge', 'data', 'security', 'other')),
    CONSTRAINT ck_plugin_scan_verdict CHECK (latest_scan_verdict IS NULL
        OR latest_scan_verdict IN ('clear', 'review', 'red')),
    CONSTRAINT ck_plugin_block CHECK (
        (blocked_at IS NULL AND blocked_by IS NULL AND block_reason IS NULL)
        OR (blocked_at IS NOT NULL AND blocked_by IS NOT NULL AND block_reason IS NOT NULL)),
    CONSTRAINT ck_plugin_blocked_status CHECK (status <> 'BLOCKED' OR blocked_at IS NOT NULL),
    CONSTRAINT ck_plugin_removed_status CHECK (status <> 'REMOVED' OR removed_at IS NOT NULL),
    CONSTRAINT ck_plugin_removed_fields CHECK (
        (removed_at IS NULL AND removed_by IS NULL AND remove_reason IS NULL)
        OR (removed_at IS NOT NULL AND removed_by IS NOT NULL AND remove_reason IS NOT NULL)),
    CONSTRAINT ck_plugin_released_status CHECK (name_released_at IS NULL OR status = 'REMOVED'),
    CONSTRAINT ck_plugin_released_fields CHECK (
        (name_released_at IS NULL AND name_released_by IS NULL)
        OR (name_released_at IS NOT NULL AND name_released_by IS NOT NULL)),
    CONSTRAINT ck_plugin_prompt_copy_count CHECK (prompt_copy_count >= 0)
);

COMMENT ON TABLE plugin IS 'DSH 插件目录实体（npm 包形态，声明 dsh.bundle）；与 skill 表无任何共享语义';
COMMENT ON COLUMN plugin.package_name IS 'npm 包名；唯一性由部分唯一索引 uq_plugin_package_name_active 保证（name_released_at IS NULL 时唯一），释放后旧行保留为墓碑';
COMMENT ON COLUMN plugin.latest_scan_verdict IS '冗余列：最新版本的扫描裁决，随 latest_version_id 一起维护，供卡片徽标与列表筛选';
COMMENT ON COLUMN plugin.prompt_copy_count IS '「复制提示词次数」冗余计数；刻意不叫安装量（站内无法确认 DSH 内是否安装成功）';
COMMENT ON COLUMN plugin.block_reason IS '黑名单理由；status=BLOCKED 时必填。黑名单是"全站禁止上架"，同时拦同 package_name 的后续提交（旧行仍占位，除非已被释放）';
COMMENT ON COLUMN plugin.remove_reason IS '软删理由；status=REMOVED 时必填；仅 SUPER_ADMIN 与空间 ADMIN 可触发';
COMMENT ON COLUMN plugin.name_released_at IS '包名释放时间；非空表示这一行的 package_name 与 (namespace_id, slug) 都不再占位。释放不改 status，软删后仍可被再次释放调用（幂等，不改首次值）';
COMMENT ON COLUMN plugin.visibility IS '可见范围三档（`PRD §6.3` v2.16）：PUBLIC 公开 / NAMESPACE_ONLY 仅命名空间 / PRIVATE 私有。与"空间是否公开"是两个正交维度，共同决定可见性与状态文案（`PRD §8.4` 四行判定表）。**刻意不设 DEFAULT**：产品口径是"提交时必选、无留空"，库层默认值会让漏传静默落成 PUBLIC（等于"没选就公开"），默认值本身仍是待拍板项（§14.2 Q10）';

-- 唯一性：两条都必须是部分唯一索引，否则"释放包名"无法实现（墓碑行会一直占位）
CREATE UNIQUE INDEX uq_plugin_package_name_active ON plugin (package_name) WHERE name_released_at IS NULL;
CREATE UNIQUE INDEX uq_plugin_namespace_slug_active ON plugin (namespace_id, slug) WHERE name_released_at IS NULL;

CREATE INDEX idx_plugin_listed ON plugin (category, updated_at DESC) WHERE status = 'LISTED';
CREATE INDEX idx_plugin_namespace_status ON plugin (namespace_id, status);
CREATE INDEX idx_plugin_search_vector ON plugin USING GIN (search_vector);

-- ============ 2. plugin_version ============
CREATE TABLE plugin_version (
    id                  BIGSERIAL      PRIMARY KEY,
    plugin_id           BIGINT         NOT NULL REFERENCES plugin(id) ON DELETE CASCADE,
    version             VARCHAR(64)    NOT NULL,
    source_type         VARCHAR(8)     NOT NULL,
    npm_registry        VARCHAR(500),
    git_url             VARCHAR(1000),
    git_commit          CHAR(40),
    git_branch          VARCHAR(200),
    git_resolved_at     TIMESTAMPTZ,
    engines_dsh         VARCHAR(200),
    tarball_key         VARCHAR(512),
    tarball_size_bytes  BIGINT,
    sha512              VARCHAR(128),
    readme_snapshot     TEXT,
    metadata_snapshot   JSONB          NOT NULL DEFAULT '{}'::jsonb,
    publish_timestamp   BIGINT,
    status              VARCHAR(16)    NOT NULL DEFAULT 'SCAN_PASSED',
    submission_source   VARCHAR(8)     NOT NULL,
    scan_verdict        VARCHAR(16),
    scan_report_id      BIGINT,
    scan_waived         BOOLEAN        NOT NULL DEFAULT FALSE,
    publish_attempt     INT            NOT NULL DEFAULT 0,
    publish_error       VARCHAR(1000),
    next_attempt_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    created_by          VARCHAR(255)   NOT NULL,
    CONSTRAINT uq_plugin_version UNIQUE (plugin_id, version),
    CONSTRAINT ck_plugin_version_status CHECK (status IN (
        'SCAN_PASSED', 'REVIEW_PENDING', 'APPROVED', 'PUBLISHING', 'LISTED', 'REJECTED', 'YANKED')),
    CONSTRAINT ck_plugin_version_publish_attempt CHECK (publish_attempt >= 0),
    -- 待代发态的一致性：只有 APPROVED / SCAN_PASSED 允许带 publish_error 与 next_attempt_at（重试线索），
    -- 已上架或已撤下的版本不允许残留"待重试"字段，避免运维误判还有活儿在排队。
    -- 反过来也要兜住：进了 APPROVED 就必须有 next_attempt_at（否则队列谓词扫不到它，版本会静默卡死）。
    CONSTRAINT ck_plugin_version_publish_retry_coherence CHECK (
        (next_attempt_at IS NULL OR status IN ('SCAN_PASSED', 'APPROVED'))
        AND (status <> 'APPROVED' OR next_attempt_at IS NOT NULL)),
    CONSTRAINT ck_plugin_version_source CHECK (source_type IN ('npm', 'git')),
    CONSTRAINT ck_plugin_version_submission_source CHECK (submission_source IN ('npm', 'git', 'zip', 'folder')),
    CONSTRAINT ck_plugin_version_verdict CHECK (scan_verdict IS NULL
        OR scan_verdict IN ('clear', 'review', 'red')),
    CONSTRAINT ck_plugin_version_source_coherence CHECK (
        (source_type = 'npm' AND npm_registry IS NOT NULL AND git_url IS NULL AND git_commit IS NULL)
        OR (source_type = 'git' AND git_url IS NOT NULL AND git_commit IS NOT NULL AND npm_registry IS NULL)),
    CONSTRAINT ck_plugin_version_commit_hex CHECK (git_commit IS NULL OR git_commit ~ '^[0-9a-f]{40}$'),
    -- git 来源版本指纹三件套：commit + branch + resolved_at（溯源用，不参与唯一性判定）。
    -- 一插件一仓库、包在根目录，没有用户可控 path，故不设 path 列与 path 安全约束。
    CONSTRAINT ck_plugin_version_git_fingerprint CHECK (
        (source_type = 'git' AND git_commit IS NOT NULL AND git_branch IS NOT NULL
         AND git_resolved_at IS NOT NULL)
        OR (source_type = 'npm' AND git_commit IS NULL AND git_branch IS NULL
            AND git_resolved_at IS NULL)),
    CONSTRAINT ck_plugin_version_waived CHECK (scan_waived = FALSE OR scan_verdict = 'red')
);

COMMENT ON COLUMN plugin_version.scan_verdict IS '最近一次「成功」扫描的裁决；扫描失败不覆盖该列（失败写入 plugin_scan_report.status=FAILED）';
COMMENT ON COLUMN plugin_version.scan_waived IS '冗余派生列：该版本当前的裁决指纹是否命中有效放行（真源是 plugin_scan_waiver，按 package_name+version+fingerprint 判定）；仅 red 可能为真';
COMMENT ON COLUMN plugin_version.status IS 'SCAN_PASSED=扫描通过（待审或免审待代发）；REVIEW_PENDING=等待审核（制品只在暂存区）；APPROVED=审核通过待代发（免审空间不出现）；PUBLISHING=代发中可重试；LISTED=已上架；REJECTED=审核拒绝终态（制品从未推送）；YANKED=人工撤下';
COMMENT ON COLUMN plugin_version.publish_attempt IS '代发尝试次数；PUBLISHING 自环重试的上限依据，耗尽后停在 APPROVED（需审核空间）或退回 SCAN_PASSED（免审空间），两者都可重试且不重走扫描与审核';
COMMENT ON COLUMN plugin_version.publish_error IS '最后一次代发失败的脱敏原因（稳定错误码，不含 URL userinfo / token）；代发成功或重试成功后清空';
COMMENT ON COLUMN plugin_version.next_attempt_at IS '下一次代发尝试时间；退避重试与手动重试共用，NULL 表示不在队列中';
COMMENT ON COLUMN plugin_version.submission_source IS '内部记录提交来源（zip/folder 会代发为 npm），不写进索引';

CREATE INDEX idx_plugin_version_listed ON plugin_version (plugin_id, publish_timestamp DESC)
    WHERE status = 'LISTED';
CREATE INDEX idx_plugin_version_status ON plugin_version (status, updated_at DESC);
CREATE INDEX idx_plugin_version_report ON plugin_version (scan_report_id);
-- 代发待办：只扫"待审已通过/免审待发"且到期的行，不随历史行变大（审核结论已定的才进队列）
CREATE INDEX idx_plugin_version_publish_queue ON plugin_version (next_attempt_at, id)
    WHERE status IN ('APPROVED', 'SCAN_PASSED') AND next_attempt_at IS NOT NULL;

-- ============ 3. plugin_scan_report ============
-- 归属以「提交」为准：红线终止时不建版本，报告必须仍有挂载点，否则放行与追溯都无对象可绑。
-- plugin_submission_id 的内联 FK 不写在这里（表还没建），在本文件第 4 段建完 plugin_submission 后用 ALTER 追加。
CREATE TABLE plugin_scan_report (
    id                  BIGSERIAL      PRIMARY KEY,
    plugin_submission_id BIGINT        NOT NULL,
    plugin_version_id   BIGINT,
    attempt_no          INT            NOT NULL,
    status              VARCHAR(16)    NOT NULL,
    verdict             VARCHAR(16),
    capabilities        JSONB          NOT NULL DEFAULT '[]'::jsonb,
    red_line_codes      JSONB          NOT NULL DEFAULT '[]'::jsonb,
    summary             JSONB          NOT NULL DEFAULT '{}'::jsonb,
    report              JSONB,
    report_truncated    BOOLEAN        NOT NULL DEFAULT FALSE,
    tool_version        VARCHAR(64)    NOT NULL,
    schema_version      INT            NOT NULL,
    fingerprint         VARCHAR(128),
    tarball_sha512      VARCHAR(128),
    duration_ms         INT,
    error_message       VARCHAR(2000),
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_plugin_scan_report_status CHECK (status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_plugin_scan_report_verdict CHECK (verdict IS NULL
        OR verdict IN ('clear', 'review', 'red')),
    CONSTRAINT ck_plugin_scan_report_attempt CHECK (attempt_no >= 1),
    -- 成功必有裁决；但「成功必有版本」不成立：红线是 SUCCESS+RED 且没有版本。
    CONSTRAINT ck_plugin_scan_report_success_has_verdict CHECK (
        (status = 'SUCCESS' AND verdict IS NOT NULL) OR (status = 'FAILED')),
    CONSTRAINT ck_plugin_scan_report_schema CHECK (schema_version > 0),
    CONSTRAINT uq_plugin_scan_report_attempt UNIQUE (plugin_submission_id, attempt_no)
);

COMMENT ON TABLE plugin_scan_report IS '一次扫描一行，不覆盖历史。归属以提交为单位（plugin_submission_id NOT NULL）：成功发布后才顺带挂上版本；红线/失败只有提交没有版本';
COMMENT ON COLUMN plugin_scan_report.attempt_no IS '该提交内第几次扫描尝试（从 1 起）；与 submission_id 组成唯一键，重扫不覆盖旧行';
COMMENT ON COLUMN plugin_scan_report.plugin_version_id IS '可空：只有真正建出版本（review/clear 且代发+落库成功）后才回填，红线与失败恒为 NULL';
COMMENT ON COLUMN plugin_scan_report.report IS '完整脱敏报告；超过 1 MiB 只存稳定字段 + summary，并置 report_truncated';
COMMENT ON COLUMN plugin_scan_report.tarball_sha512 IS '与被扫产物对应，证明"扫的就是发的"；与 plugin_version.sha512 不等即视为供应链异常';

CREATE INDEX idx_plugin_scan_report_submission ON plugin_scan_report (plugin_submission_id, attempt_no DESC);
CREATE INDEX idx_plugin_scan_report_version ON plugin_scan_report (plugin_version_id, created_at DESC)
    WHERE plugin_version_id IS NOT NULL;
CREATE INDEX idx_plugin_scan_report_verdict ON plugin_scan_report (verdict, created_at DESC)
    WHERE status = 'SUCCESS';
CREATE INDEX idx_plugin_scan_report_tool ON plugin_scan_report (tool_version, created_at DESC);
CREATE INDEX idx_plugin_scan_report_fingerprint ON plugin_scan_report (fingerprint)
    WHERE fingerprint IS NOT NULL;

ALTER TABLE plugin_version
    ADD CONSTRAINT fk_plugin_version_scan_report
    FOREIGN KEY (scan_report_id) REFERENCES plugin_scan_report(id);

-- ============ 4. plugin_submission ============
CREATE TABLE plugin_submission (
    id                  BIGSERIAL      PRIMARY KEY,
    namespace_id        BIGINT         NOT NULL REFERENCES namespace(id),
    submitted_by        VARCHAR(255)   NOT NULL,
    source              VARCHAR(8)     NOT NULL,
    status              VARCHAR(24)    NOT NULL,
    package_name        VARCHAR(214)   NOT NULL,
    version             VARCHAR(64)    NOT NULL,
    category            VARCHAR(32)    NOT NULL,
    plugin_id           BIGINT         REFERENCES plugin(id),
    plugin_version_id   BIGINT         REFERENCES plugin_version(id),
    source_payload      JSONB          NOT NULL DEFAULT '{}'::jsonb,
    staged_tarball_key  VARCHAR(512),
    tarball_sha512      VARCHAR(128),
    scan_report_id      BIGINT         REFERENCES plugin_scan_report(id),
    verdict             VARCHAR(16),
    attempt_count       INT            NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    lease_until         TIMESTAMPTZ,
    lease_owner         VARCHAR(64),
    error_code          VARCHAR(64),
    error_detail        VARCHAR(2000),
    finished_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_plugin_submission_source CHECK (source IN ('npm', 'git', 'zip', 'folder')),
    CONSTRAINT ck_plugin_submission_status CHECK (status IN (
        'RECEIVED', 'MATERIALIZING', 'SCANNING', 'SCAN_RED',
        'REVIEW_PENDING', 'PUBLISHING', 'LISTED', 'SCAN_FAILED',
        'REVIEW_REJECTED', 'PUBLISH_FAILED', 'VALIDATION_FAILED', 'CANCELED')),
    CONSTRAINT ck_plugin_submission_category CHECK (category IN (
        'agent', 'ui', 'memory', 'communication', 'devtools',
        'search-knowledge', 'data', 'security', 'other')),
    CONSTRAINT ck_plugin_submission_verdict CHECK (verdict IS NULL
        OR verdict IN ('clear', 'review', 'red')),
    CONSTRAINT ck_plugin_submission_attempts CHECK (attempt_count >= 0),
    CONSTRAINT ck_plugin_submission_payload CHECK (jsonb_typeof(source_payload) = 'object')
);

COMMENT ON TABLE plugin_submission IS '提交生命周期状态机。HTTP 请求内只做毫秒级同步校验，扫描/代发/落库/索引重建全部异步';
COMMENT ON COLUMN plugin_submission.source_payload IS '来源参数快照：npm{packageName, resolvedVersion, resolvedAt} / git{url} / zip{filedName} / folder{filedName}；不含任何凭据。npm 的 resolvedVersion 是服务端 resolveLatest(packageName) 在提交那一刻解析出的最新非预发布版本（registry latest dist-tag，跳 prerelease），不是用户原话——用户只填包名，请求里不允许出现 version。该版本提交即冻结：materialize 下载、scan、publish、upsertVersion、索引组装全程复用这一个 resolvedVersion，任何阶段都不重新解析 latest；上游后续发版不影响已上架版本，要上新版必须重新提交';
COMMENT ON COLUMN plugin_submission.lease_owner IS '认领者标识（host:pid:uuid），用于排障；与 scan_task_outbox 的 lease 模式同构';

-- 同一 name@version 同时只允许一条在途提交：同步校验与 worker 落库之间的竞态在这一层兜底。
-- SCAN_RED 不在集合内：红线是"待裁决"而非在途，作者改包后应能立即重提，不被旧提交挡住。
-- REVIEW_PENDING 与 PUBLISHING 在集合内：等待审核、代发中都属于在途（同版本再提也只会撞 npm 同版本冲突）。
-- APPROVED 刻意不进集合：审核已通过、只差代发，属于可重试的"卡住"而不是在途（见 §4.6 附表）；
--   把它挡在唯一索引外，作者可以在不改包的前提下继续等 worker，也可以放弃后重提一个新版本。
CREATE UNIQUE INDEX uq_plugin_submission_active ON plugin_submission (package_name, version)
    WHERE status IN ('RECEIVED', 'MATERIALIZING', 'SCANNING', 'REVIEW_PENDING', 'PUBLISHING');

CREATE INDEX idx_plugin_submission_dispatch ON plugin_submission (next_attempt_at, id)
    WHERE status = 'RECEIVED';
CREATE INDEX idx_plugin_submission_lease ON plugin_submission (lease_until)
    WHERE status IN ('MATERIALIZING', 'SCANNING', 'PUBLISHING');
CREATE INDEX idx_plugin_submission_submitter ON plugin_submission (submitted_by, created_at DESC);
CREATE INDEX idx_plugin_submission_namespace ON plugin_submission (namespace_id, created_at DESC);
CREATE INDEX idx_plugin_submission_review ON plugin_submission (status, updated_at DESC)
    WHERE status IN ('REVIEW_PENDING', 'SCAN_RED');

-- 报告归属的循环外键：两张表都已存在，此处补。
ALTER TABLE plugin_scan_report
    ADD CONSTRAINT fk_plugin_scan_report_submission
    FOREIGN KEY (plugin_submission_id) REFERENCES plugin_submission(id) ON DELETE CASCADE;

-- ============ 5. plugin_scan_waiver ============
-- 放行对象是「包名@版本 + 报告指纹」而不是某一行报告：重扫得到相同指纹仍然有效，
-- 内容一变指纹即变，白名单自动失效。撤销放行不删行，写 revoked_*。
CREATE TABLE plugin_scan_waiver (
    id                  BIGSERIAL      PRIMARY KEY,
    package_name        VARCHAR(214)   NOT NULL,
    version             VARCHAR(64)    NOT NULL,
    fingerprint         VARCHAR(128)   NOT NULL,
    first_submission_id BIGINT         NOT NULL REFERENCES plugin_submission(id) ON DELETE CASCADE,
    red_line_codes      JSONB          NOT NULL DEFAULT '[]'::jsonb,
    waived_by           VARCHAR(255)   NOT NULL,
    waived_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    waive_reason        VARCHAR(1000)  NOT NULL,
    revoked_by          VARCHAR(255),
    revoked_at          TIMESTAMPTZ,
    CONSTRAINT uq_plugin_scan_waiver UNIQUE (package_name, version, fingerprint),
    CONSTRAINT ck_plugin_scan_waiver_reason CHECK (length(btrim(waive_reason)) >= 4),
    CONSTRAINT ck_plugin_scan_waiver_revoked CHECK (
        (revoked_by IS NULL AND revoked_at IS NULL)
        OR (revoked_by IS NOT NULL AND revoked_at IS NOT NULL))
);

COMMENT ON TABLE plugin_scan_waiver IS '红线放行白名单（按指纹）。放行/撤销都写审计；内容变化导致指纹变化时旧放行自动不生效';
COMMENT ON COLUMN plugin_scan_waiver.red_line_codes IS '放行时命中的红线 code 快照，供详情页与审计回溯（红线条数不等于主机数）';
COMMENT ON COLUMN plugin_scan_waiver.first_submission_id IS '首次触发放行的提交，便于从提交视角回溯';

CREATE INDEX idx_plugin_scan_waiver_submission ON plugin_scan_waiver (first_submission_id);

-- ============ 6. plugin_stat_event / plugin_stat_daily ============
CREATE TABLE plugin_stat_event (
    id                  BIGSERIAL      PRIMARY KEY,
    plugin_id           BIGINT         NOT NULL REFERENCES plugin(id) ON DELETE CASCADE,
    plugin_version_id   BIGINT         REFERENCES plugin_version(id),
    event               VARCHAR(16)    NOT NULL,
    dedup_key           VARCHAR(128)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_plugin_stat_event_event CHECK (event IN ('view', 'copy_prompt')),
    CONSTRAINT uq_plugin_stat_event UNIQUE (plugin_id, event, dedup_key)
);

COMMENT ON TABLE plugin_stat_event IS '只保留 view / copy_prompt；download 与「仅收录」概念已删除（不提供站内 tarball 下载接口）';
COMMENT ON COLUMN plugin_stat_event.dedup_key IS 'HMAC(ip_hash + 事件 + UTC 日期) 的十六进制摘要，服务端密钥由配置提供；不含原始 IP';

CREATE INDEX idx_plugin_stat_event_agg ON plugin_stat_event (plugin_id, event, created_at);

CREATE TABLE plugin_stat_daily (
    plugin_id           BIGINT         NOT NULL REFERENCES plugin(id) ON DELETE CASCADE,
    stat_date           DATE           NOT NULL,
    event               VARCHAR(16)    NOT NULL,
    event_count         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_plugin_stat_daily PRIMARY KEY (plugin_id, stat_date, event),
    CONSTRAINT ck_plugin_stat_daily_event CHECK (event IN ('view', 'copy_prompt')),
    CONSTRAINT ck_plugin_stat_daily_count CHECK (event_count >= 0)
);

-- ============ 7. plugin_index_state ============
CREATE TABLE plugin_index_state (
    id                  SMALLINT       PRIMARY KEY DEFAULT 1,
    dirty               BOOLEAN        NOT NULL DEFAULT TRUE,
    gen                 BIGINT         NOT NULL DEFAULT 0,
    built_gen           BIGINT         NOT NULL DEFAULT 0,
    dirty_marked_at     TIMESTAMPTZ,
    lease_until         TIMESTAMPTZ,
    lease_owner         VARCHAR(64),
    current_key         VARCHAR(512),
    current_etag        VARCHAR(128),
    current_size_bytes  BIGINT,
    last_built_at       TIMESTAMPTZ,
    last_error          VARCHAR(2000),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_plugin_index_state_single_row CHECK (id = 1),
    CONSTRAINT ck_plugin_index_state_gen CHECK (gen >= built_gen AND built_gen >= 0)
);

COMMENT ON TABLE plugin_index_state IS '单行脏标记 + 世代号。多副本安全：claim 用 UPDATE ... WHERE dirty AND lease 过期 RETURNING，不引入分布式锁';
COMMENT ON COLUMN plugin_index_state.gen IS '每次认领 +1；构建完成时若 gen > 认领值，说明期间又标脏，立即再跑一轮（天然合并抖动）';

INSERT INTO plugin_index_state (id, dirty) VALUES (1, TRUE);

-- ============ 8. plugin_report（举报：独立表，不复用 skill_report；**只记录，不做处置流转**） ============
-- 为什么不复用 skill_report：该表 skill_id NOT NULL，且 SkillReportService.submit 会断言 skill 存在并触发 skill 领域事件，
-- 挂插件需要改表 + 改服务 + 改既有举报查询，风险高于新建一张窄表。
-- 为什么不建处置状态机：本期只承诺"入口"（PRD §1.2/§5.1），处置流程与责任方不在范围内。
-- 表里刻意没有状态列、也没有处理人/处理时间/处理备注列：没有状态列，就不可能出现"半套处置流程"。
CREATE TABLE plugin_report (
    id              BIGSERIAL     PRIMARY KEY,
    plugin_id       BIGINT        NOT NULL REFERENCES plugin(id) ON DELETE CASCADE,
    plugin_version  VARCHAR(64),
    reporter_id     VARCHAR(255)  NOT NULL,
    reason_code     VARCHAR(32)   NOT NULL,
    details         VARCHAR(2000),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_plugin_report_reason CHECK (reason_code IN (
        'misleading', 'malicious', 'license', 'spam', 'other'))
);

COMMENT ON TABLE plugin_report IS '插件举报记录（只读可见，不做处置流转）；与 skill_report 零共享，只复用"理由码 → 文案"';
COMMENT ON COLUMN plugin_report.plugin_version IS '可空：举报可以只针对插件整体（如简介误导），不必绑定版本';

-- 后台只读列表按时间倒序看，按插件聚合看
CREATE INDEX idx_plugin_report_created ON plugin_report (created_at DESC);
CREATE INDEX idx_plugin_report_plugin ON plugin_report (plugin_id, created_at DESC);

-- ============ 9. review_task（既有表：部分唯一索引 + 列注释） ============
-- subject_type 是 VARCHAR(32) 且无 CHECK 约束（见 V1/V50），新增枚举值不需要改表结构。
COMMENT ON COLUMN review_task.subject_type IS
    '审核主题类型：SKILL_VERSION / SKILL_METADATA / SUITE_VERSION / PLUGIN_VERSION（插件市场 v1.0 新增）';

CREATE UNIQUE INDEX uq_review_task_plugin_version_pending
    ON review_task (subject_type, subject_version_id)
    WHERE subject_type = 'PLUGIN_VERSION' AND status = 'PENDING';
```

DDL 自检要点：
- **`plugin.visibility` 不建独立索引，也不加 DEFAULT**。不建索引的理由：索引生成只扫 `status='LISTED'` 的候选集（已被部分索引 `idx_plugin_listed` 收窄，插件总量 ≤ 数千），`visibility = 'PUBLIC'` 是候选集上的逐行等值过滤，选择性接近 1（大多数插件就是 `PUBLIC`），单独建索引反而多一次回表；`NAMESPACE_ONLY`/`PRIVATE` 是少数派，用 `idx_plugin_listed` 扫完再过滤的总代价远低于维护第三个索引。运维侧若要按可见范围统计，用 `plugin_stat_daily` 的聚合即可。**不加 DEFAULT 的理由**见列注释：产品口径"必选无留空"，漏传必须被 `NOT NULL` 顶回 400，不能静默落成 `PUBLIC`。存量无数据、迁移仍是同一个 `V10001` 单文件，加列无回填风险。
- `search_vector` 必须用 `to_tsvector('simple', ...)` 这种**显式 regconfig** 形式才是 IMMUTABLE，生成列才被接受（与 `V2__phase2_skill_tables.sql:93-99` 一致）。单参数 `to_tsvector(x)` 会被 PostgreSQL 拒绝。
- `plugin.latest_version_id` **不加**外键：会与 `plugin_version.plugin_id` 形成循环依赖并阻塞插入顺序；改为应用层不变量 + 生成期断言（`latest_version_id` 必须属于本 `plugin_id`）。
- **报告归属以提交为准**：`plugin_scan_report.plugin_submission_id` 是 `NOT NULL`，`plugin_version_id` 可空。红线路径（不建版本）因此仍然有挂载点，放行与追溯都不再依赖版本行；`plugin_version_id` 只在代发+落库成功后才回填。
- 报告的唯一性是 `(plugin_submission_id, attempt_no)`：同一提交的每次重扫新增一行，旧行不覆盖；不再有"每版本一份最新报告"的唯一约束（版本视角改为 `plugin_version.scan_report_id` 指向最新一条）。
- `plugin_version.scan_report_id` 的外键在本文件内、`plugin_scan_report` 建表之后用 `ALTER TABLE` 追加；`plugin_scan_report.plugin_submission_id` 的外键则在 `plugin_submission` 建表之后补（两处都是循环依赖，只能 ALTER）。
- `ck_plugin_scan_report_success_has_verdict` 保证 `SUCCESS` 必有裁决、`FAILED` 必无裁决，让"失败不覆盖裁决"在库层可验证；**故意不加**"成功必有版本"的约束——红线正是 `SUCCESS + RED + 无版本`。
- 放行不在报告行上记（原 `waived_by/waived_at/waive_reason` 三列已移出报告表），改由 `plugin_scan_waiver` 以 `(package_name, version, fingerprint)` 为键承载：同指纹重扫仍然有效，指纹变化自动失效，撤销放行写 `revoked_*` 而不删行。
- `ck_plugin_version_publish_retry_coherence` 把"重试线索"与"版本状态"绑在一起：只有 `SCAN_PASSED`/`APPROVED` 允许带 `next_attempt_at`，已上架或已撤下的行不允许残留待重试字段——否则运维看队列会以为还有活没干完。
- **代发是唯一的重试链**：`publish_attempt` / `next_attempt_at` / `publish_error` 三个字段就是代发的全部重试状态。v2.2 的 `revoke_*` 一整套（列、约束、索引、worker）随链路反转删除，不再有第二条重试链需要区分。
- `plugin_report` 是**独立表**，不扩 `skill_report`：后者 `skill_id NOT NULL` 且服务层会断言 skill 存在并抛 skill 领域事件，泛型化要同时改表、改服务、改既有后台查询三处，而举报是一张窄表就能承载的（§3.5 给了边界）。
- `plugin_report` **没有 `status` / `handled_*` 列**，也**没有**唯一索引：本期只做「记录 + 只读可见」，不承诺处置（`PRD §1.2`）。刻意不加 `(plugin_id, reporter_id)` 唯一索引——没有处置态就无法判定「是否已处理」，全唯一会把「同一人后来再次举报」永久挡掉；防重靠限流（5 次/小时/用户）。

