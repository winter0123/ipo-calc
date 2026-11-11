-- allocations.allocation_method 컬럼 제거 (사용자 승인: DB 컬럼·기존 값까지 제거).
-- 배정방식은 자유 텍스트 메모로만 쓰였고 도메인·API·화면에서 더 이상 사용하지 않는다.
-- id/subscription_id/allocated_quantity/allocated_amount/allocated_at/note/status/deleted_at/
-- created_at/updated_at과 외래키·유니크 인덱스·이력은 그대로 보존된다.
ALTER TABLE allocations DROP COLUMN allocation_method;
