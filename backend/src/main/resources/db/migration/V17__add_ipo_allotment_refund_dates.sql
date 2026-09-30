-- 배정일·환불일 컬럼 추가 (Epic 17 SF-3)
-- allotment_date: 배정일 (공모주 청약 결과 배정 발표일)
-- refund_date: 환불일 (청약 증거금 환불일)
ALTER TABLE ipos ADD COLUMN allotment_date DATE NULL;
ALTER TABLE ipos ADD COLUMN refund_date DATE NULL;

-- 기존 행은 allotment_date=NULL, refund_date=NULL 유지 (기존 데이터 영향 없음)