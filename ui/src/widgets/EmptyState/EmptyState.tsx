import { Link } from 'react-router-dom'
import Button from '@/shared/components/Button/Button.tsx'
import { SurfaceCard } from '@/shared/components/Primitives.tsx'
import styled from 'styled-components'

const EmptyPanel = styled(SurfaceCard)`
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: clamp(28px, 5vw, 56px) 24px;
  text-align: center;
  background: linear-gradient(145deg, #ffffff 45%, #f1f8ff 100%);
`

const EmptyMark = styled.div`
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  color: var(--primary);
  background: var(--surface-sea);
  border-radius: 18px;
  font-size: 1.4rem;
  font-weight: 800;
`

const Message = styled.p`
  color: var(--ink);
  font-size: 1rem;
  font-weight: 750;
`

const Hint = styled.p`
  max-width: 42ch;
  color: var(--ink-muted);
  font-size: 0.9rem;
  line-height: 1.65;

  a {
    font-weight: 700;
    text-decoration: none;
  }
`

interface EmptyStateProps {
  onResetFilters: () => void
}

export default function EmptyState({ onResetFilters }: EmptyStateProps) {
  return (
    <EmptyPanel role="status" aria-live="polite">
      <EmptyMark aria-hidden="true">—</EmptyMark>
      <Message>해당 조건에 해당하는 기록이 없습니다.</Message>
      <Button type="button" variant="secondary" onClick={onResetFilters}>
        전체 필터 초기화
      </Button>
      <Hint>
        아직 기록한 적이 없다면 <Link to="/subscriptions/register">청약 등록</Link>부터 시작해
        보세요.
      </Hint>
    </EmptyPanel>
  )
}
