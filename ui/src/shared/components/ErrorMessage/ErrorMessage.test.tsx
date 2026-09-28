import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import ErrorMessage from './ErrorMessage.tsx'

describe('ErrorMessage', () => {
  it('message가 있으면 role="alert"과 함께 에러 문구를 렌더링함', () => {
    render(<ErrorMessage message="청약을 찾을 수 없습니다." />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('청약을 찾을 수 없습니다.')
  })

  it('message가 없으면 아무것도 렌더링하지 않음', () => {
    const { container } = render(<ErrorMessage message={undefined} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('message가 빈 문자열이면 아무것도 렌더링하지 않음', () => {
    const { container } = render(<ErrorMessage message="" />)

    expect(container).toBeEmptyDOMElement()
  })

  it('id를 전달하면 해당 id가 적용되어 aria-describedby로 참조할 수 있음', () => {
    render(<ErrorMessage id="amount-error" message="금액을 입력해 주세요." />)

    expect(screen.getByRole('alert')).toHaveAttribute('id', 'amount-error')
  })
})
