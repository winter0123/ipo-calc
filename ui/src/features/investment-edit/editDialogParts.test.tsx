import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { UseQueryResult } from '@tanstack/react-query'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/apiError.ts'
import { EditForm, Field, Shell } from './editDialogParts.tsx'

const q = (over: Record<string, unknown>) => over as unknown as UseQueryResult<string>

describe('Shell', () => {
  it('불러오는 중에는 안내와 닫기 버튼을 보인다', async () => {
    const onClose = vi.fn()
    render(
      <Shell title="수정" query={q({ data: undefined, isError: false })} onClose={onClose}>
        {() => <p>폼</p>}
      </Shell>,
    )
    expect(screen.getByText('불러오는 중…')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '닫기' }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('조회 오류면 오류 메시지를 alert로 보이고 폼은 그리지 않는다', () => {
    render(
      <Shell
        title="수정"
        query={q({ data: undefined, isError: true, error: new ApiError(404, 'x') })}
        onClose={() => {}}
      >
        {() => <p>폼</p>}
      </Shell>,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.queryByText('폼')).not.toBeInTheDocument()
  })

  it('데이터가 있으면 children에 넘기고 닫기 버튼은 숨긴다', () => {
    render(
      <Shell title="수정" query={q({ data: '레코드' })} onClose={() => {}}>
        {(r) => <p>{r}</p>}
      </Shell>,
    )
    expect(screen.getByText('레코드')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '닫기' })).not.toBeInTheDocument()
  })

  it('Esc로 닫고, 열릴 때 대화상자로 포커스가 가며, 닫히면 열기 전 요소로 돌아간다', () => {
    const opener = document.createElement('button')
    document.body.append(opener)
    opener.focus()
    const onClose = vi.fn()
    const { unmount } = render(
      <Shell title="수정" query={q({ data: 'a' })} onClose={onClose}>
        {() => <button>내용</button>}
      </Shell>,
    )
    expect(screen.getByRole('dialog')).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalledOnce()
    unmount()
    expect(opener).toHaveFocus()
    opener.remove()
  })

  it('Tab은 마지막에서 처음으로, Shift+Tab은 처음/대화상자에서 마지막으로 순환한다', () => {
    render(
      <Shell title="수정" query={q({ data: 'a' })} onClose={() => {}}>
        {() => (
          <>
            <button>처음</button>
            <button>끝</button>
          </>
        )}
      </Shell>,
    )
    const [first, last] = [screen.getByText('처음'), screen.getByText('끝')]
    last.focus()
    fireEvent.keyDown(document, { key: 'Tab' })
    expect(first).toHaveFocus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(last).toHaveFocus()
    screen.getByRole('dialog').focus()
    fireEvent.keyDown(document, { key: 'Tab', shiftKey: true })
    expect(last).toHaveFocus()
  })

  it('포커스 가능한 요소가 없고 Tab 이외 키면 아무 일도 하지 않는다', () => {
    const onClose = vi.fn()
    render(
      <Shell title="수정" query={q({ data: 'a' })} onClose={onClose}>
        {() => <p>텍스트만</p>}
      </Shell>,
    )
    fireEvent.keyDown(document, { key: 'Tab' })
    fireEvent.keyDown(document, { key: 'a' })
    expect(onClose).not.toHaveBeenCalled()
  })
})

describe('Field', () => {
  it('error가 있을 때만 오류 문구를 보인다', () => {
    const { rerender } = render(
      <Field id="f" label="수량">
        <input id="f" />
      </Field>,
    )
    expect(screen.getByLabelText('수량')).toBeInTheDocument()
    expect(screen.queryByText('필수')).not.toBeInTheDocument()
    rerender(
      <Field id="f" label="수량" error="필수">
        <input id="f" />
      </Field>,
    )
    expect(screen.getByText('필수')).toBeInTheDocument()
  })
})

describe('EditForm', () => {
  it('저장 중이면 저장 버튼이 비활성화되고 라벨이 바뀐다', () => {
    render(
      <EditForm onSubmit={(e) => e.preventDefault()} pending error={null} onClose={() => {}}>
        <span />
      </EditForm>,
    )
    expect(screen.getByRole('button', { name: '저장 중…' })).toBeDisabled()
  })

  it('error가 있으면 alert로 보이고 취소/제출이 동작한다', async () => {
    const onSubmit = vi.fn((e) => e.preventDefault())
    const onClose = vi.fn()
    render(
      <EditForm onSubmit={onSubmit} pending={false} error={new Error('x')} onClose={onClose}>
        <span />
      </EditForm>,
    )
    expect(screen.getByRole('alert')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '저장' }))
    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(onSubmit).toHaveBeenCalledOnce()
    expect(onClose).toHaveBeenCalledOnce()
  })
})
