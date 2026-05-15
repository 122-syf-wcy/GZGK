CREATE TABLE IF NOT EXISTS admin_import_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  province_code VARCHAR(16) NOT NULL,
  year INT NOT NULL,
  batch_code VARCHAR(80) NOT NULL DEFAULT '',
  subject_type VARCHAR(40) NOT NULL DEFAULT '',
  import_type VARCHAR(80) NOT NULL DEFAULT '',
  source_type VARCHAR(80) NOT NULL DEFAULT '',
  status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
  source_dir VARCHAR(500) NOT NULL DEFAULT '',
  output_dir VARCHAR(500) NOT NULL DEFAULT '',
  created_by VARCHAR(80) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_admin_import_job_province_year (province_code, year),
  KEY idx_admin_import_job_status (status),
  KEY idx_admin_import_job_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_import_job_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  file_name VARCHAR(255) NOT NULL DEFAULT '',
  file_path VARCHAR(500) NOT NULL DEFAULT '',
  sha256 CHAR(64) NOT NULL DEFAULT '',
  file_size BIGINT NOT NULL DEFAULT 0,
  file_type VARCHAR(80) NOT NULL DEFAULT '',
  source_url VARCHAR(1000) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_admin_import_job_file_path (job_id, file_path),
  KEY idx_admin_import_job_file_job (job_id),
  KEY idx_admin_import_job_file_type (file_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_import_job_gate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  gate_name VARCHAR(128) NOT NULL DEFAULT '',
  gate_status VARCHAR(32) NOT NULL DEFAULT '',
  expected_value VARCHAR(500) NOT NULL DEFAULT '',
  actual_value VARCHAR(500) NOT NULL DEFAULT '',
  sample_path VARCHAR(500) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_import_job_gate_job (job_id),
  KEY idx_admin_import_job_gate_status (gate_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS admin_import_job_artifact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  artifact_type VARCHAR(80) NOT NULL DEFAULT '',
  artifact_path VARCHAR(500) NOT NULL DEFAULT '',
  sha256 CHAR(64) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_admin_import_job_artifact_job (job_id),
  KEY idx_admin_import_job_artifact_type (artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
