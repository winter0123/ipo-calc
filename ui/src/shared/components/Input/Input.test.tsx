import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import Input from './Input.tsx'

describe('Input', () => {
  it('label과 input이 htmlFor/id로 연결되어 getByLabelText로 조회됨', () => {
    render(<Input label="비용종류" />)

    const input = screen.getByLabelText('비용종류')
    expect(input).toBeInTheDocument()
    expect(input.tagName).toBe('INPUT')
  })

  it('id를 전달하면 해당 id가 사용됨', () => {
    render(<Input label="금액" id="amount" />)

    expect(screen.getByLabelText('금액')).toHaveAttribute('id', 'amount')
  })

  it('error가 없으면 aria-invalid와 에러 메시지가 렌더링되지 않음', () => {
    render(<Input label="메모" />)

    const input = screen.getByLabelText('메모')
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('error가 있으면 aria-invalid="true"와 에러 메시지가 연결됨', () => {
    render(<Input label="금액" id="amount" error="금액을 입력해 주세요." />)

    const input = screen.getByLabelText('금액')
    expect(input).toHaveAttribute('aria-invalid', 'true')

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('금액을 입력해 주세요.')
    expect(input.getAttribute('aria-describedby')).toBe(alert.getAttribute('id'))
  })

  it('표준 input props(type, value, onChange)를 그대로 전달함', () => {
    const onChange = vi.fn()
    render(
      <Input label="발생일시" type="datetime-local" value="2026-09-10T10:00" onChange={onChange} />,
    )

    const input = screen.getByLabelText('발생일시')
    expect(input).toHaveAttribute('type', 'datetime-local')

    fireEvent.change(input, { target: { value: '2026-09-11T10:00' } })
    expect(onChange).toHaveBeenCalled()
  })

  it('disabled를 전달하면 비활성화됨', () => {
    render(<Input label="비용종류" disabled />)

    expect(screen.getByLabelText('비용종류')).toBeDisabled()
  })

  it('두 개를 렌더링해도 label 연결이 서로 독립적임', () => {
    render(
      <>
        <Input label="비용종류" error="필수입니다." />
        <Input label="메모" />
      </>,
    )

    const costType = screen.getByLabelText('비용종류')
    const memo = screen.getByLabelText('메모')
    expect(costType).toHaveAttribute('aria-invalid', 'true')
    expect(memo).not.toHaveAttribute('aria-invalid')
  })
})
