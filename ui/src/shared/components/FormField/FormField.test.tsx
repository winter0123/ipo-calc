import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import FormField from './FormField.tsx'

describe('FormField', () => {
  it('label과 children(input)을 감싸서 렌더링함', () => {
    render(
      <FormField label="청약" htmlFor="subscriptionId">
        <select id="subscriptionId" defaultValue="">
          <option value="">청약 선택</option>
        </select>
      </FormField>,
    )

    expect(screen.getByLabelText('청약')).toBeInTheDocument()
  })

  it('임의의 children(select)을 그대로 렌더링함', () => {
    render(
      <FormField label="배정" htmlFor="allocationId">
        <select id="allocationId" data-testid="control" defaultValue="1">
          <option value="1">배정 1</option>
        </select>
      </FormField>,
    )

    expect(screen.getByTestId('control').tagName).toBe('SELECT')
    expect(screen.getByLabelText('배정')).toHaveValue('1')
  })

  it('error가 있으면 role="alert" 에러 메시지를 htmlFor 기준으로 렌더링함', () => {
    render(
      <FormField label="금액" htmlFor="amount" error="0 초과 정수를 입력해 주세요.">
        <input id="amount" type="number" />
      </FormField>,
    )

    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('0 초과 정수를 입력해 주세요.')
    expect(alert).toHaveAttribute('id', 'amount-error')
  })

  it('error가 없으면 에러 메시지를 렌더링하지 않음', () => {
    render(
      <FormField label="금액" htmlFor="amount">
        <input id="amount" type="number" />
      </FormField>,
    )

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('htmlFor이 없어도 label과 children을 렌더링함', () => {
    render(
      <FormField label="메모">
        <input type="text" />
      </FormField>,
    )

    expect(screen.getByText('메모')).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })
})
