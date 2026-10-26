import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-c26ffec-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  .s-c26ffec-container {
    display: flex;
    flex-direction: column;
    gap: var(--space-4);
  }

  .s-c26ffec-fieldGroup {
    display: flex;
    flex-direction: column;
    gap: var(--space-2);
    flex: 1;
    min-width: 0;
  }

  .s-c26ffec-label {
    font-weight: 500;
    color: var(--ink);
    font-size: 14px;
  }

  .s-c26ffec-select:disabled {
    cursor: not-allowed;
  }

  .s-c26ffec-hint {
    font-size: 12px;
    color: var(--ink-muted);
  }

  /* 반응형: 모바일(~640px) 세로 스택 */
  @media (max-width: 640px) {
    .s-c26ffec-container {
      gap: var(--space-3);
    }

    .s-c26ffec-select {
      /* iOS 줌 방지 */
    }
  }

  /* 태블릿(641px~1023px) */
  @media (min-width: 641px) and (max-width: 1023px) {
    .s-c26ffec-container {
      flex-direction: row;
      flex-wrap: wrap;
      align-items: flex-start;
      gap: var(--space-4);
    }

    .s-c26ffec-fieldGroup {
      flex: 1;
      min-width: 200px;
    }
  }

  /* 데스크톱(1024px~) 가로 배치 */
  @media (min-width: 1024px) {
    .s-c26ffec-container {
      flex-direction: row;
      flex-wrap: nowrap;
      align-items: flex-start;
      gap: var(--space-5);
    }

    .s-c26ffec-fieldGroup {
      flex: 1;
      min-width: 0;
    }
  }
`

export default styles
