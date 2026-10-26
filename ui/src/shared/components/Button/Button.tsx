import styled from 'styled-components'
import type { ButtonHTMLAttributes } from 'react'

export type ButtonVariant = 'primary' | 'secondary'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

const StyledButton = styled.button<{ $variant: ButtonVariant }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 44px;
  padding: 0 var(--space-5);
  border: 1px solid transparent;
  border-radius: 12px;
  font: inherit;
  font-size: 0.925rem;
  font-weight: 700;
  line-height: 1.2;
  cursor: pointer;
  transition:
    transform 160ms ease,
    background-color 160ms ease,
    box-shadow 160ms ease;

  &:focus-visible {
    outline: none;
    box-shadow:
      0 0 0 3px var(--surface),
      0 0 0 5px var(--link);
  }

  &:disabled {
    cursor: not-allowed;
    background: var(--surface-soft);
    color: var(--ink-muted);
    border-color: var(--border);
  }

  ${({ $variant }) =>
    $variant === 'primary'
      ? `
        color: var(--on-primary);
        background: var(--primary);
        box-shadow: 0 5px 14px rgba(20, 78, 146, 0.16);
        &:hover:not(:disabled) { background: var(--primary-hover); transform: translateY(-1px); }
        &:active:not(:disabled) { background: var(--primary-active); transform: translateY(0); }
      `
      : `
        color: var(--ink);
        background: var(--surface);
        border-color: var(--border-strong);
        &:hover:not(:disabled) { background: var(--surface-soft); }
      `}
`

export default function Button({ variant = 'primary', type = 'button', ...rest }: ButtonProps) {
  return <StyledButton type={type} $variant={variant} {...rest} />
}
