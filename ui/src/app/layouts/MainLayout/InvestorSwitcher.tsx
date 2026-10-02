import { useCurrentInvestor } from '@/entities/investor/index.ts'
import styles from './MainLayout.module.css'

// 현재 투자자 전환기. 투자자가 없으면 보이지 않고, 1명이면 이름만 보여 준다.
export default function InvestorSwitcher() {
  const { investors, current, select } = useCurrentInvestor()
  if (investors.length === 0) return null
  if (investors.length === 1) {
    return <span className={styles.investorName}>투자자: {investors[0].displayName}</span>
  }
  return (
    <label className={styles.investorSwitch}>
      <span>현재 투자자</span>
      <select value={current?.id ?? ''} onChange={(e) => select(Number(e.target.value))}>
        {current === null && <option value="">선택해 주세요</option>}
        {investors.map((investor) => (
          <option key={investor.id} value={investor.id}>
            {investor.displayName}
          </option>
        ))}
      </select>
    </label>
  )
}
