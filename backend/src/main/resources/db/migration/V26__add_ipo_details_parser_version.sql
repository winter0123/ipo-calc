-- 파서를 고치면 같은 공시도 다시 파싱하도록 저장 당시 파서 버전을 남긴다(기존 행은 0 → 다음 동기화에서 재파싱).
ALTER TABLE ipo_details ADD COLUMN parser_version INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN ipo_details.parser_version IS '저장 당시 DART 공시 파서 버전(바뀌면 같은 공시도 다시 파싱)';
