#!/usr/bin/env bash
set -euo pipefail

WORK_DIR="${WORK_DIR:-/root/gzly_scraper}"
SQL_FILE="${SQL_FILE:-$WORK_DIR/data/export/official_links.sql}"
STAMP_FILE="$WORK_DIR/data/official_links/.last_import_mtime"
BACKUP_DIR="${BACKUP_DIR:-/opt/gzly/backup}"
DB_NAME="${DB_NAME:-gzly}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
SLEEP_SECONDS="${IMPORT_INTERVAL_SECONDS:-30}"
MAX_BACKUPS="${MAX_BACKUPS:-10}"

if [[ -z "$DB_PASS" ]]; then
  echo "[$(date '+%F %T')] ERROR: DB_PASS is required" >&2
  exit 1
fi

metric_query() {
  mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "
    SELECT
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE admission_site IS NULL OR admission_site = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE admission_brochure_url IS NULL OR admission_brochure_url = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE major_catalog_url IS NULL OR major_catalog_url = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE tuition_info_url IS NULL OR tuition_info_url = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE tuition_summary IS NULL OR tuition_summary = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE parse_status <> 1
          OR adjustment_rule IS NULL OR adjustment_rule = ''
          OR foreign_language_rule IS NULL OR foreign_language_rule = ''
          OR physical_exam_rule IS NULL OR physical_exam_rule = ''
          OR single_subject_rule IS NULL OR single_subject_rule = ''
      ) +
      (
        SELECT COUNT(*) FROM uni_official_link
        WHERE (school_site <> '' AND school_site NOT REGEXP '^https?://')
           OR (admission_site <> '' AND admission_site NOT REGEXP '^https?://')
           OR (admission_brochure_url <> '' AND admission_brochure_url NOT REGEXP '^https?://')
           OR (major_catalog_url <> '' AND major_catalog_url NOT REGEXP '^https?://')
           OR (tuition_info_url <> '' AND tuition_info_url NOT REGEXP '^https?://')
      );
  "
}

cleanup_invalid_urls() {
  mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "
    UPDATE uni_official_link
    SET
      school_site = CASE WHEN school_site <> '' AND school_site NOT REGEXP '^https?://' THEN '' ELSE school_site END,
      admission_site = CASE WHEN admission_site <> '' AND admission_site NOT REGEXP '^https?://' THEN '' ELSE admission_site END,
      admission_brochure_url = CASE WHEN admission_brochure_url <> '' AND admission_brochure_url NOT REGEXP '^https?://' THEN '' ELSE admission_brochure_url END,
      major_catalog_url = CASE WHEN major_catalog_url <> '' AND major_catalog_url NOT REGEXP '^https?://' THEN '' ELSE major_catalog_url END,
      tuition_info_url = CASE WHEN tuition_info_url <> '' AND tuition_info_url NOT REGEXP '^https?://' THEN '' ELSE tuition_info_url END
    WHERE (school_site <> '' AND school_site NOT REGEXP '^https?://')
       OR (admission_site <> '' AND admission_site NOT REGEXP '^https?://')
       OR (admission_brochure_url <> '' AND admission_brochure_url NOT REGEXP '^https?://')
       OR (major_catalog_url <> '' AND major_catalog_url NOT REGEXP '^https?://')
       OR (tuition_info_url <> '' AND tuition_info_url NOT REGEXP '^https?://');
  "
}

echo "Starting official links importer loop..."
echo "SQL file: $SQL_FILE"

while true; do
  if [[ -f "$SQL_FILE" ]]; then
    MTIME=$(stat -c %Y "$SQL_FILE" 2>/dev/null || echo 0)
    LAST=$(cat "$STAMP_FILE" 2>/dev/null || echo 0)
    if [[ "$MTIME" -gt "$LAST" ]]; then
      echo "[$(date '+%F %T')] Importing official_links.sql ..."
      mkdir -p "$BACKUP_DIR"
      BACKUP_FILE="$BACKUP_DIR/uni_official_link_auto_$(date '+%Y%m%d%H%M%S').sql"
      BEFORE_MISSING=$(metric_query)
      mysqldump -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" uni_official_link > "$BACKUP_FILE"
      mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$SQL_FILE"
      cleanup_invalid_urls
      ls -1t "$BACKUP_DIR"/uni_official_link_auto_*.sql 2>/dev/null | tail -n +"$((MAX_BACKUPS + 1))" | xargs -r rm -f
      AFTER_MISSING=$(metric_query)
      if [[ "$AFTER_MISSING" -gt "$BEFORE_MISSING" ]]; then
        echo "[$(date '+%F %T')] Import worsened missing metric: before=$BEFORE_MISSING after=$AFTER_MISSING. Rolling back."
        mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$BACKUP_FILE"
        echo "$MTIME" > "$STAMP_FILE"
        continue
      fi
      echo "$MTIME" > "$STAMP_FILE"
      echo "[$(date '+%F %T')] Import done. missing_metric: before=$BEFORE_MISSING after=$AFTER_MISSING"
    fi
  fi
  sleep "$SLEEP_SECONDS"
done
