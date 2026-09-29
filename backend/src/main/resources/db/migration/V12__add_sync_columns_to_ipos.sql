-- 자동 동기화 메타데이터 컬럼 추가 (SF-3)
-- sync_source: 동기화 출처 (OPENDART, OPENDART+USTOCKPLUS, NULL=수동 등록)
-- last_synced_at: 마지막 동기화 시각
ALTER TABLE ipos ADD COLUMN sync_source VARCHAR(20) NULL;
ALTER TABLE ipos ADD COLUMN last_synced_at TIMESTAMP NULL;

-- 기존 행은 sync_source=NULL, last_synced_at=NULL 유지 (수동 등록 데이터는 영향 없음)