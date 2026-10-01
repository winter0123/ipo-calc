import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import ConfirmDialog from './ConfirmDialog.tsx'

describe('ConfirmDialog', () => {
  it('isOpen이 false이면 아무것도 렌더링하지 않음', async () => {
    render(
      <ConfirmDialog
        isOpen={false}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('isOpen이 true이면 제목/메시지와 함께 렌더링되고 aria-modal이 적용됨', async () => {
    render(
      <ConfirmDialog
        isOpen={true}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    const dialog = screen.getByRole('alertdialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(screen.getByText('삭제 확인')).toBeInTheDocument()
    expect(screen.getByText('정말로 삭제하시겠습니까?')).toBeInTheDocument()
  })

  it('확인 버튼 클릭 시 onConfirm 호출', async () => {
    const onConfirm = vi.fn()
    render(
      <ConfirmDialog
        isOpen={true}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: '확인' }))
    expect(onConfirm).toHaveBeenCalled()
  })

  it('취소 버튼 클릭 시 onCancel 호출', async () => {
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        isOpen={true}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: '취소' }))
    expect(onCancel).toHaveBeenCalled()
  })

  it('Escape 키 입력 시 onCancel 호출', async () => {
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        isOpen={true}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      />,
    )

    await userEvent.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalled()
  })

  it('열리면 확인 버튼에 포커스가 이동함', async () => {
    render(
      <ConfirmDialog
        isOpen={true}
        title="삭제 확인"
        message="정말로 삭제하시겠습니까?"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '확인' })).toHaveFocus()
  })
})
