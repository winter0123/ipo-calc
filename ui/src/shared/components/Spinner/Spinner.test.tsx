import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import Spinner from './Spinner.tsx'

describe('Spinner', () => {
  it('role="status"와 기본 라벨로 렌더링됨', () => {
    render(<Spinner />)

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('로딩 중...')
  })

  it('label을 전달하면 해당 텍스트로 렌더링됨', () => {
    render(<Spinner label="투자 내역을 불러오는 중..." />)

    expect(screen.getByRole('status')).toHaveTextContent('투자 내역을 불러오는 중...')
  })

  it('스크린리더용 텍스트가 role="status" 영역에 포함됨', () => {
    render(<Spinner label="대시보드 데이터를 불러오는 중..." />)

    expect(screen.getByRole('status')).toBeInTheDocument()
    expect(screen.getByText('대시보드 데이터를 불러오는 중...')).toBeInTheDocument()
  })
})
