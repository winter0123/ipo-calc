import { ChevronDown } from 'lucide-react'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import styles from './MainLayout.module.css'

// 현재 투자자 전환기. 투자자가 없으면 보이지 않는다.
// 이니셜 아바타 + 이름 한 덩어리의 알약 모양이고, 2명 이상이면 눌러서 바꾼다(실제 요소는 select).
export default function InvestorSwitcher() {
  const { investors, current, select } = useCurrentInvestor()
  if (investors.length === 0) return null

  const name = current?.displayName ?? '투자자 선택'
  const avatar = (
    <span className={styles.avatar} aria-hidden="true">
      {current ? current.displayName.slice(0, 1) : '?'}
    </span>
  )

  if (investors.length === 1) {
    return (
      <span className={styles.investorChip}>
        {avatar}
        <span className={styles.srOnly}>현재 투자자: </span>
        <span className={styles.chipName}>{name}</span>
      </span>
    )
  }

  return (
    <label className={`${styles.investorChip} ${styles.investorChipButton}`}>
      {avatar}
      <select
        aria-label="현재 투자자"
        className={styles.chipSelect}
        value={current?.id ?? ''}
        onChange={(e) => select(Number(e.target.value))}
      >
        {current === null && <option value="">선택해 주세요</option>}
        {investors.map((investor) => (
          <option key={investor.id} value={investor.id}>
            {investor.displayName}
          </option>
        ))}
      </select>
      <ChevronDown aria-hidden="true" size={16} className={styles.chevron} />
    </label>
  )
}
