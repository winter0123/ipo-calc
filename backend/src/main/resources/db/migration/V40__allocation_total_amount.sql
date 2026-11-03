-- V40: allocations 테이블 총액 기반 전환
-- 1) 새 컬럼 추가 (총 배정금액, 원)
ALTER TABLE allocations
    ADD COLUMN allocated_amount BIGINT NULL;

-- 2) 백필: 기존 allocation_price × allocated_quantity
UPDATE allocations
SET allocated_amount = allocation_price * allocated_quantity
WHERE allocation_price IS NOT NULL
  AND allocated_quantity IS NOT NULL;

-- 3) 백필: allocation_price가 null이면 IPO 확정 공모가(fixed_price) 사용
UPDATE allocations a
SET allocated_amount = sub.total_amount
FROM (
    SELECT a.id AS allocation_id,
           a.subscription_id,
           a.allocated_quantity,
           i.fixed_price,
           (i.fixed_price * a.allocated_quantity) AS total_amount
    FROM allocations a
    JOIN subscriptions sub ON sub.id = a.subscription_id
    JOIN ipos i ON i.id = sub.ipo_id AND i.deleted_at IS NULL
    WHERE a.allocation_price IS NULL
      AND a.allocated_amount IS NULL
      AND a.allocated_quantity IS NOT NULL
      AND i.fixed_price IS NOT NULL
) AS sub
WHERE a.id = sub.allocation_id
  AND a.allocation_price IS NULL
  AND a.allocated_amount IS NULL;

-- 4) 기존 단가 컬럼 제거 (코디네이터 정정: deprecated → DROP)
ALTER TABLE allocations DROP COLUMN allocation_price;

COMMENT ON COLUMN allocations.allocated_amount IS '배정 총금액(원) = 확정공모가 × 배정수량';
