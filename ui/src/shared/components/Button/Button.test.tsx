import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import Button from './Button.tsx'

describe('Button', () => {
  it('label을 버튼 텍스트로 렌더링하고 button 시맨틱을 유지함', async () => {
    render(<Button>등록</Button>)

    expect(screen.getByRole('button', { name: '등록' })).toBeInTheDocument()
  })

  it('type을 지정하지 않으면 기본값은 type="button"', async () => {
    render(<Button>취소</Button>)

    expect(screen.getByRole('button', { name: '취소' })).toHaveAttribute('type', 'button')
  })

  it('type을 지정하면 그대로 적용됨', async () => {
    render(<Button type="submit">등록</Button>)

    expect(screen.getByRole('button', { name: '등록' })).toHaveAttribute('type', 'submit')
  })

  it('disabled면 비활성화되고 클릭 이벤트도 발생하지 않음', async () => {
    const onClick = vi.fn()
    render(
      <Button disabled onClick={onClick}>
        등록 중...
      </Button>,
    )

    const button = screen.getByRole('button', { name: '등록 중...' })
    expect(button).toBeDisabled()

    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('variant가 primary/secondary일 때 서로 다른 클래스 적용됨', async () => {
    const { rerender } = render(<Button variant="secondary">취소</Button>)
    const secondary = screen.getByRole('button', { name: '취소' })
    const secondaryClass = secondary.className

    rerender(<Button variant="primary">등록</Button>)
    const primary = screen.getByRole('button', { name: '등록' })

    expect(primary.className).not.toBe(secondaryClass)
  })

  it('variant를 지정하지 않으면 primary가 적용됨', async () => {
    const { rerender } = render(<Button variant="secondary">취소</Button>)
    const secondaryClass = screen.getByRole('button', { name: '취소' }).className

    rerender(<Button>등록</Button>)

    expect(screen.getByRole('button', { name: '등록' }).className).not.toBe(secondaryClass)
  })

  it('클릭 시 onClick 호출', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>등록</Button>)

    await userEvent.click(screen.getByRole('button', { name: '등록' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('추가 className을 함께 적용함', async () => {
    render(<Button className="custom-class">등록</Button>)

    expect(screen.getByRole('button', { name: '등록' }).className).toContain('custom-class')
  })
})
