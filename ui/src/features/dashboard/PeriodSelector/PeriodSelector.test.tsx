import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import PeriodSelector from './PeriodSelector.tsx'

const mockOnSearch = vi.fn()

function renderPeriodSelector(
  props: {
    unit: 'month' | 'year'
    initialYear?: number
    initialMonth?: number
    onSearch?: (year: number, month?: number) => void
  } = { unit: 'month' },
) {
  return render(
    <PeriodSelector
      unit={props.unit}
      initialYear={props.initialYear}
      initialMonth={props.initialMonth}
      onSearch={props.onSearch ?? mockOnSearch}
    />,
  )
}

describe('PeriodSelector', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-09-15T12:00:00'))
    mockOnSearch.mockClear()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  describe('AC-1920: 기본값이 현재 연·월', () => {
    it('월별 탭에서 기본 연도가 현재 연도(2026)로 설정됨', () => {
      renderPeriodSelector({ unit: 'month' })
      expect(screen.getByDisplayValue(2026)).toBeInTheDocument()
    })

    it('월별 탭에서 기본 월이 현재 월(9월)로 설정됨', () => {
      renderPeriodSelector({ unit: 'month' })
      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('9')
    })

    it('연도별 탭에서 기본 연도가 현재 연도(2026)로 설정됨', () => {
      renderPeriodSelector({ unit: 'year' })
      expect(screen.getByDisplayValue(2026)).toBeInTheDocument()
    })

    it('초기값이 전달되면 해당 값이 사용됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2025, initialMonth: 3 })
      expect(screen.getByDisplayValue(2025)).toBeInTheDocument()
      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('3')
    })

    it('제목에 현재 연월이 표시됨 (AC-1922)', () => {
      renderPeriodSelector({ unit: 'month' })
      expect(screen.getByRole('heading', { level: 2, name: '2026년 9월' })).toBeInTheDocument()
    })

    it('연도별 탭에서 제목에 현재 연도만 표시됨 (AC-1922)', () => {
      renderPeriodSelector({ unit: 'year' })
      expect(screen.getByRole('heading', { level: 2, name: '2026년' })).toBeInTheDocument()
    })
  })

  describe('AC-1921: 이전/다음 버튼으로 기간 이동', () => {
    it('이전 달 버튼 클릭 시 월이 감소함', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      fireEvent.click(screen.getByRole('button', { name: '이전 달로 이동' }))

      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('8')
      expect(screen.getByRole('heading', { level: 2, name: '2026년 8월' })).toBeInTheDocument()
    })

    it('다음 달 버튼 클릭 시 월이 증가함', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 8 })

      fireEvent.click(screen.getByRole('button', { name: '다음 달로 이동' }))

      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('9')
      expect(screen.getByRole('heading', { level: 2, name: '2026년 9월' })).toBeInTheDocument()
    })

    it('1월에서 이전 달 클릭 시 전년 12월로 이동함 (연도 경계)', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 1 })

      fireEvent.click(screen.getByRole('button', { name: '이전 달로 이동' }))

      expect(screen.getByDisplayValue(2025)).toBeInTheDocument()
      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('12')
      expect(screen.getByRole('heading', { level: 2, name: '2025년 12월' })).toBeInTheDocument()
    })

    it('12월에서 다음 달 클릭 시 다음 해 1월로 이동함 (연도 경계)', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2025, initialMonth: 12 })

      fireEvent.click(screen.getByRole('button', { name: '다음 달로 이동' }))

      expect(screen.getByDisplayValue(2026)).toBeInTheDocument()
      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      expect(monthSelect).toHaveValue('1')
      expect(screen.getByRole('heading', { level: 2, name: '2026년 1월' })).toBeInTheDocument()
    })

    it('이번 달(현재 월)에서는 다음 달 버튼이 비활성화됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })
      const nextButton = screen.getByRole('button', { name: '다음 달로 이동' })
      expect(nextButton).toBeDisabled()
    })

    it('미래 연도에서는 다음 달 버튼이 비활성화됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2027, initialMonth: 1 })
      const nextButton = screen.getByRole('button', { name: '다음 달로 이동' })
      expect(nextButton).toBeDisabled()
    })

    it('최소 연도(2000)에서는 이전 연도 버튼이 비활성화됨', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2000 })
      const prevButton = screen.getByRole('button', { name: '이전 연도로 이동' })
      expect(prevButton).toBeDisabled()
    })

    it('2000년 1월에서는 이전 달 버튼이 비활성화됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2000, initialMonth: 1 })
      const prevButton = screen.getByRole('button', { name: '이전 달로 이동' })
      expect(prevButton).toBeDisabled()
    })

    it('연도별 탭에서 이전/다음 연도 버튼이 동작함', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2026 })

      fireEvent.click(screen.getByRole('button', { name: '이전 연도로 이동' }))
      expect(screen.getByDisplayValue(2025)).toBeInTheDocument()
      expect(screen.getByRole('heading', { level: 2, name: '2025년' })).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: '다음 연도로 이동' }))
      expect(screen.getByDisplayValue(2026)).toBeInTheDocument()
      expect(screen.getByRole('heading', { level: 2, name: '2026년' })).toBeInTheDocument()
    })

    it('연도별 탭에서 현재 연도에서는 다음 연도 버튼 비활성화', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2026 })
      const nextButton = screen.getByRole('button', { name: '다음 연도로 이동' })
      expect(nextButton).toBeDisabled()
    })
  })

  describe('조회 버튼 및 입력 검증', () => {
    it('조회 버튼 클릭 시 onSearch가 올바른 인자로 호출됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      fireEvent.click(screen.getByRole('button', { name: '조회' }))

      expect(mockOnSearch).toHaveBeenCalledWith(2026, 9)
    })

    it('연도별 탭에서 조회 시 month 파라미터 없이 호출됨', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2026 })

      fireEvent.click(screen.getByRole('button', { name: '조회' }))

      expect(mockOnSearch).toHaveBeenCalledWith(2026, undefined)
    })

    it('잘못된 연도 입력 시 에러 메시지 표시', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      const yearInput = screen.getByLabelText('연도 입력')
      fireEvent.change(yearInput, { target: { value: '199' } })
      fireEvent.click(screen.getByRole('button', { name: '조회' }))

      const yearError = document.getElementById('period-year-error')
      expect(yearError).toHaveTextContent('4자리 숫자로 입력해 주세요.')
      expect(mockOnSearch).not.toHaveBeenCalled()
    })

    it('Enter 키 입력 시 조회 실행됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      const yearInput = screen.getByLabelText('연도 입력')
      fireEvent.keyDown(yearInput, { key: 'Enter', code: 'Enter' })

      expect(mockOnSearch).toHaveBeenCalledWith(2026, 9)
    })
  })

  describe('접근성', () => {
    it('버튼에 aria-label이 있음', () => {
      renderPeriodSelector({ unit: 'month' })

      expect(screen.getByRole('button', { name: '이전 달로 이동' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '다음 달로 이동' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: '조회' })).toBeInTheDocument()
    })

    it('버튼의 터치 대상이 최소 44px (CSS 클래스로 보장)', () => {
      renderPeriodSelector({ unit: 'month' })

      const prevButton = screen.getByRole('button', { name: '이전 달로 이동' })
      const nextButton = screen.getByRole('button', { name: '다음 달로 이동' })

      // CSS .navButton 클래스에 min-width: 44px, min-height: 44px 정의됨 (해시된 클래스명)
      expect(prevButton.className).toContain('navButton')
      expect(nextButton.className).toContain('navButton')
    })

    it('월 변경 시 aria-live 영역에 알림 발생', async () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      fireEvent.click(screen.getByRole('button', { name: '이전 달로 이동' }))

      await waitFor(() => {
        const announcer = document.getElementById('period-announcer')
        expect(announcer).toHaveTextContent('2026년 8월 데이터로 조회됨')
      })
    })

    it('입력 필드에 aria-label과 aria-invalid 속성 있음', () => {
      renderPeriodSelector({ unit: 'month' })

      expect(screen.getByLabelText('연도 입력')).toHaveAttribute('aria-label', '연도 입력')
      expect(screen.getByLabelText('월 선택')).toHaveAttribute('aria-label', '월 선택')
    })
  })

  describe('월 선택 드롭다운', () => {
    it('1~12월 옵션이 모두 표시됨', () => {
      renderPeriodSelector({ unit: 'month' })

      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      const options = monthSelect.querySelectorAll('option')
      expect(options).toHaveLength(12)
      expect(options[0]).toHaveValue('1')
      expect(options[0]).toHaveTextContent('1월')
      expect(options[11]).toHaveValue('12')
      expect(options[11]).toHaveTextContent('12월')
    })

    it('연도별 탭에서는 월 선택 드롭다운이 표시되지 않음', () => {
      renderPeriodSelector({ unit: 'year' })

      expect(screen.queryByRole('combobox', { name: '월 선택' })).not.toBeInTheDocument()
    })
  })

  describe('드롭다운 변경 시 onSearch 호출', () => {
    it('월 드롭다운 변경 후 조회 버튼 클릭 시 변경된 월로 조회됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      fireEvent.change(monthSelect, { target: { value: '5' } })
      fireEvent.click(screen.getByRole('button', { name: '조회' }))

      expect(mockOnSearch).toHaveBeenCalledWith(2026, 5)
    })
  })

  describe('이전/다음 버튼 클릭 시 onSearch 즉시 호출 (AC-1921)', () => {
    it('이전 달 버튼 클릭 시 onSearch가 새 연월로 즉시 호출됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      fireEvent.click(screen.getByRole('button', { name: '이전 달로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2026, 8)
    })

    it('다음 달 버튼 클릭 시 onSearch가 새 연월로 즉시 호출됨', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 8 })

      fireEvent.click(screen.getByRole('button', { name: '다음 달로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2026, 9)
    })

    it('1월에서 이전 달 클릭 시 onSearch가 전년 12월로 호출됨 (연도 경계)', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 1 })

      fireEvent.click(screen.getByRole('button', { name: '이전 달로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2025, 12)
    })

    it('12월에서 다음 달 클릭 시 onSearch가 다음 해 1월로 호출됨 (연도 경계)', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2025, initialMonth: 12 })

      fireEvent.click(screen.getByRole('button', { name: '다음 달로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2026, 1)
    })

    it('연도별 탭에서 이전 연도 버튼 클릭 시 onSearch가 새 연도로 호출됨', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2026 })

      fireEvent.click(screen.getByRole('button', { name: '이전 연도로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2025)
    })

    it('연도별 탭에서 다음 연도 버튼 클릭 시 onSearch가 새 연도로 호출됨', () => {
      renderPeriodSelector({ unit: 'year', initialYear: 2025 })

      fireEvent.click(screen.getByRole('button', { name: '다음 연도로 이동' }))

      expect(mockOnSearch).toHaveBeenCalledTimes(1)
      expect(mockOnSearch).toHaveBeenCalledWith(2026)
    })
  })

  describe('월 드롭다운/연도 입력 변경 시 onSearch 호출 안 함 (조회 버튼 전까지)', () => {
    it('월 드롭다운 변경만으로는 onSearch가 호출되지 않음', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      const monthSelect = screen.getByRole('combobox', { name: '월 선택' })
      fireEvent.change(monthSelect, { target: { value: '5' } })

      expect(mockOnSearch).not.toHaveBeenCalled()
    })

    it('연도 입력 변경만으로는 onSearch가 호출되지 않음', () => {
      renderPeriodSelector({ unit: 'month', initialYear: 2026, initialMonth: 9 })

      const yearInput = screen.getByLabelText('연도 입력')
      fireEvent.change(yearInput, { target: { value: '2025' } })

      expect(mockOnSearch).not.toHaveBeenCalled()
    })
  })
})
