import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchInvestors } from '../../entities/investor/api-client.ts'
import ProfitQueryToggle from '../../features/profit-view/ProfitQueryToggle.tsx'
import { useSaleProfit } from '../../features/profit-view/useSaleProfit.ts'
import { useInvestorProfits } from '../../features/profit-view/useInvestorProfits.ts'
import ProfitTable from '../../features/profit-view/ProfitTable/ProfitTable.tsx'
import styles from './ProfitViewPage.module.css'

type QueryMode = 'sale' | 'investor'

export default function ProfitViewPage() {
  const [mode, setMode] = useState<QueryMode>('sale')
  const [saleId, setSaleId] = useState<string>('')
  const [investorId, setInvestorId] = useState<string>('')

  const { data: investors, isLoading: investorsLoading } = useQuery({
    queryKey: ['investors'],
    queryFn: fetchInvestors,
  })

  const saleProfitQuery = useSaleProfit({
    saleId: saleId ? Number(saleId) : undefined,
    enabled: mode === 'sale' && saleId !== '',
  })

  const investorProfitsQuery = useInvestorProfits({
    investorId: investorId ? Number(investorId) : undefined,
    enabled: mode === 'investor' && investorId !== '',
  })

  const activeQuery = mode === 'sale' ? saleProfitQuery : investorProfitsQuery

  const handleSaleIdChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSaleId(e.target.value)
  }

  const handleInvestorIdChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setInvestorId(e.target.value)
  }

  const getErrorMessage = () => {
    if (!activeQuery.error) return null
    const msg = activeQuery.error.message

    if (mode === 'sale') {
      if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
        return '매도건을 찾을 수 없습니다.'
      }
    } else {
      if (msg.includes('Request failed: 404') || msg.includes('Not Found: 404')) {
        return '투자자를 찾을 수 없습니다.'
      }
    }

    if (
      msg.includes('Failed to fetch') ||
      msg.includes('Network error') ||
      msg.includes('Network request failed')
    ) {
      return '네트워크 연결을 확인해 주세요.'
    }

    return '수익 조회에 실패했습니다.'
  }

  const handleModeChange = (newMode: QueryMode) => {
    setMode(newMode)
    setSaleId('')
    setInvestorId('')
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>수익 확인</h1>

      <ProfitQueryToggle mode={mode} onModeChange={handleModeChange} />

      <div className={styles.querySection}>
        {mode === 'sale' && (
          <div className={styles.inputGroup}>
            <label htmlFor="saleId" className={styles.label}>
              매도건 ID
            </label>
            <input
              id="saleId"
              type="number"
              className={styles.input}
              value={saleId}
              onChange={handleSaleIdChange}
              placeholder="매도건 ID를 입력하세요"
              min="1"
              disabled={activeQuery.isLoading}
              aria-describedby="saleId-error"
            />
            <button
              type="button"
              className={styles.searchButton}
              onClick={() => saleProfitQuery.refetch()}
              disabled={activeQuery.isLoading || !saleId}
            >
              {activeQuery.isLoading ? '조회 중...' : '조회'}
            </button>
          </div>
        )}

        {mode === 'investor' && (
          <div className={styles.inputGroup}>
            <label htmlFor="investorId" className={styles.label}>
              투자자
            </label>
            {investorsLoading ? (
              <div className={styles.loading}>투자자 목록을 불러오는 중...</div>
            ) : (
              <select
                id="investorId"
                className={styles.select}
                value={investorId}
                onChange={handleInvestorIdChange}
                disabled={activeQuery.isLoading}
                aria-describedby="investorId-error"
              >
                <option value="">투자자 선택</option>
                {investors?.map((investor) => (
                  <option key={investor.id} value={investor.id}>
                    {investor.displayName}
                  </option>
                ))}
              </select>
            )}
            <button
              type="button"
              className={styles.searchButton}
              onClick={() => investorProfitsQuery.refetch()}
              disabled={activeQuery.isLoading || !investorId}
            >
              {activeQuery.isLoading ? '조회 중...' : '조회'}
            </button>
          </div>
        )}

        {activeQuery.error && (
          <div id={`${mode === 'sale' ? 'saleId' : 'investorId'}-error`} className={styles.error} role="alert">
            {getErrorMessage()}
          </div>
        )}
      </div>

      {activeQuery.isLoading && <div className={styles.loading}>수익 정보를 불러오는 중...</div>}

      {!activeQuery.isLoading && !activeQuery.error && (
        <ProfitTable data={activeQuery.data ?? null} mode={mode} />
      )}

      {!activeQuery.isLoading && !activeQuery.error && !activeQuery.data && mode === 'investor' && (
        <div className={styles.empty}>매도 내역이 없습니다.</div>
      )}
    </div>
  )
}