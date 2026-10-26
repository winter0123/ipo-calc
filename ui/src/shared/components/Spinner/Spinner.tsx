import styled, { keyframes } from 'styled-components'

const rotate = keyframes`
  to { transform: rotate(360deg); }
`

const Status = styled.div`
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-muted);
  font-size: 0.92rem;
`

const Indicator = styled.span`
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  border: 2px solid var(--info-border);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: ${rotate} 700ms linear infinite;

  @media (prefers-reduced-motion: reduce) {
    animation-duration: 1800ms;
  }
`

export interface SpinnerProps {
  label?: string
  className?: string
}

export default function Spinner({ label = '로딩 중...', className }: SpinnerProps) {
  return (
    <Status className={className} role="status" aria-live="polite">
      <Indicator aria-hidden="true" />
      <span>{label}</span>
    </Status>
  )
}
