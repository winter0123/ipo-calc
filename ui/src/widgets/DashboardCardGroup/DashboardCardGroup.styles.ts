import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-859845d-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-859845d-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--space-2);
    width: 100%;
  }

  /* 태블릿 */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-859845d-grid {
      grid-template-columns: repeat(2, 1fr);
      gap: var(--space-4);
    }
  }

  /* 데스크톱 */
  @media (min-width: 1024px) {
    .s-859845d-grid {
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: var(--space-4);
    }
  }

  /* 한눈에 읽히는 지표 카드: 핵심 합계에만 브랜드 면을 적용한다. */
  .s-859845d-grid > * {
    min-height: 132px;
    padding: 22px;
    border-color: var(--border);
    border-radius: 20px;
    box-shadow: 0 8px 20px rgba(25, 54, 91, 0.045);
    transition:
      transform 160ms ease,
      box-shadow 160ms ease;
  }

  .s-859845d-grid > *:hover {
    transform: translateY(-2px);
    box-shadow: 0 14px 28px rgba(25, 54, 91, 0.09);
  }

  .s-859845d-grid > *:nth-child(1) {
    color: var(--on-primary);
    background: linear-gradient(145deg, var(--primary-active), var(--primary));
    border-color: transparent;
  }

  .s-859845d-grid > *:nth-child(1) span:first-child {
    color: rgba(255, 255, 255, 0.82);
  }
  .s-859845d-grid > *:nth-child(1) span:last-child {
    color: var(--on-primary);
  }

  .s-859845d-grid > *:nth-child(2) {
    background: linear-gradient(145deg, var(--success-bg), var(--surface));
    border-color: var(--success-border);
  }

  .s-859845d-grid > *:nth-child(3) {
    background: linear-gradient(145deg, var(--surface-sea), var(--surface));
    border-color: var(--info-border);
  }

  @media (max-width: 640px) {
    .s-859845d-grid > * {
      min-height: 104px;
      padding: 16px;
      border-radius: 16px;
    }
  }
`

export default styles
