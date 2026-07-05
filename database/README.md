# Database

## Overview

MySQL 8.4 database for the Gaokao Admission Platform. Uses Flyway for schema migrations.

## Requirements

- MySQL 8.4+
- Flyway 10.x (community or teams edition)

## Migration Naming Convention

```
V{version}__{description}.sql
```

- `V` - Versioned migration (required prefix)
- `{version}` - Sequential version number with leading zeros (V1, V2, ...)
- `__` - Double underscore separator
- `{description}` - Snake_case description, use underscores to separate words

## Migration Files

| File | Description |
|------|-------------|
| `V1__base_tables.sql` | Core tables: user_account, candidate_profile, admin_user, admin_role, admin_permission, admin_user_role, admin_role_permission, audit_log |
| `V2__catalog_tables.sql` | Catalog tables: school, standard_major, enrollment_plan, admission_history, plan_series, school_link, major_link, score_rank_segment |
| `V3__volunteer_tables.sql` | Volunteer tables: prediction_result, volunteer_form, volunteer_item, volunteer_check_run, volunteer_check_issue, export_record |
| `V4__data_version_tables.sql` | Data versioning: data_version, active_data_version, import_batch, import_row_error, import_file, year_config. Also adds remaining foreign keys from V2 tables |
| `V5__seed_data.sql` | Seed data: year_config (2023-2026), admin roles, permissions, role-permission mappings, seed admin user |

## Running Flyway Migrations

### Using Docker (recommended for local dev)

```bash
flyway -url=jdbc:mysql://localhost:3306/admission_platform \
       -user=root \
       -password=root123 \
       -locations=filesystem:./database/migrations \
       migrate
```

### Using Flyway CLI

```bash
flyway -configFiles=./database/flyway.conf migrate
```

### Using Gradle (if configured)

```bash
./gradlew flywayMigrate
```

## Key Design Decisions

1. **Charset**: All tables use `utf8mb4` with `utf8mb4_unicode_ci` collation for full Unicode support (including emoji).
2. **Engine**: All tables use InnoDB for transaction support and referential integrity.
3. **Timestamps**: `DATETIME(3)` with millisecond precision, consistent across all tables.
4. **Foreign Keys**: Cascading deletes where appropriate (user data, links), RESTRICT for reference data.
5. **JSON Columns**: Used for flexible/extensible data (subject rules, intentions, exclusions, etc.) where schema is not fixed.
6. **Soft Deletes**: Not used—status fields control visibility (TINYINT) to avoid complexity. Audit log captures deletions.
7. **Data Versioning**: `data_version` + `active_data_version` pattern allows multi-version data management with atomic publish.
8. **Encryption**: `mobile_ciphertext` stores AES-256-GCM encrypted phone numbers; `mobile_hash` (SHA-256) enables unique constraint and lookup without decryption.

## ER Diagram (Conceptual)

```
user_account ──< candidate_profile
admin_user   ──< admin_user_role >── admin_role ──< admin_role_permission >── admin_permission
admin_user   ──< audit_log
school       ──< enrollment_plan ──< plan_series >── admission_history
standard_major ──< enrollment_plan
standard_major ──< plan_series
school       ──< school_link
plan_series  ──< major_link
user_account ──< volunteer_form ──< volunteer_item >── enrollment_plan
volunteer_form ──< volunteer_check_run ──< volunteer_check_issue
user_account ──< export_record
data_version ──< active_data_version
data_version ──< enrollment_plan
data_version ──< admission_history
data_version ──< score_rank_segment
import_batch ──< import_row_error
import_batch ──< import_file
import_batch ──< data_version
```
