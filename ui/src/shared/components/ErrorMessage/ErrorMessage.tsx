import styled from 'styled-components'

const Message = styled.p`
  margin: 0;
  padding: 12px 16px;
  color: var(--danger);
  background: var(--danger-bg);
  border: 1px solid var(--danger-border);
  border-radius: 12px;
  font-size: 0.9rem;
  line-height: 1.5;
`

export interface ErrorMessageProps {
  message?: string | null
  id?: string
  className?: string
}

export default function ErrorMessage({ message, id, className }: ErrorMessageProps) {
  if (!message) return null
  return (
    <Message id={id} role="alert" className={className}>
      {message}
    </Message>
  )
}
