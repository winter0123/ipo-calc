import styled, { css } from 'styled-components'

export const SurfaceCard = styled.section`
  min-width: 0;
  padding: clamp(20px, 3vw, 28px);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
`

export const SectionHeading = styled.h2`
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--ink);
  font-size: clamp(1.05rem, 1.5vw, 1.25rem);
  font-weight: 750;
  letter-spacing: -0.035em;
  line-height: 1.3;
`

export const Eyebrow = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--primary);
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.1em;
  text-transform: uppercase;
`

const toneStyles = {
  neutral: css`
    color: var(--ink-muted);
    background: var(--surface-soft);
    border-color: var(--border);
  `,
  info: css`
    color: var(--primary-deep);
    background: var(--info-bg);
    border-color: var(--info-border);
  `,
  success: css`
    color: var(--success-ink);
    background: var(--success-bg);
    border-color: var(--success-border);
  `,
  warning: css`
    color: var(--warning-ink);
    background: var(--warning-bg);
    border-color: var(--warning-border);
  `,
  danger: css`
    color: var(--danger);
    background: var(--danger-bg);
    border-color: var(--danger-border);
  `,
}

export const StatusPill = styled.span<{ $tone?: keyof typeof toneStyles }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: 750;
  line-height: 1.2;
  white-space: nowrap;
  ${({ $tone = 'neutral' }) => toneStyles[$tone]}
`
