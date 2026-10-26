import { InvestorRegisterForm } from '@/features/investor-register/index.ts'
import { useInvestors, type InvestorResponse } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import styles, { StyleScope } from './InvestorRegisterPage.styles.ts'

const RELATIONSHIPS: Record<string, string> = {
  SELF: '본인',
  PARTNER: '연인',
  SPOUSE: '연인',
  FRIEND: '친구',
  PARENT: '부모',
  CHILD: '자녀',
  OTHER: '기타',
}

function InvestorRow({ investor }: { investor: InvestorResponse }) {
  const { data: accounts, isLoading, isError } = useSecuritiesAccounts(investor.id)

  return (
    <StyleScope>
      {
        <li className={styles.investorRow}>
          <span className={styles.investorName}>{investor.displayName}</span>
          <span>
            {investor.relationship
              ? (RELATIONSHIPS[investor.relationship] ?? investor.relationship)
              : '관계 미입력'}
          </span>
          <span>
            {isLoading
              ? '계좌 확인 중...'
              : isError
                ? '계좌 조회 실패'
                : `증권계좌 ${accounts?.length ?? 0}개`}
          </span>
        </li>
      }
    </StyleScope>
  )
}

export default function InvestorRegisterPage() {
  const { data: investors, isLoading, isError } = useInvestors()

  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>투자자 관리</h1>
          <section aria-labelledby="investor-list-title">
            <h2 id="investor-list-title">등록된 투자자</h2>
            {isLoading ? (
              <p role="status">투자자 목록을 불러오는 중...</p>
            ) : isError ? (
              <p role="alert">투자자 목록을 불러오지 못했습니다.</p>
            ) : (
              <>
                <p className={styles.count}>
                  현재 {investors?.length ?? 0}명의 투자자가 등록되어 있습니다.
                </p>
                {investors && investors.length > 0 ? (
                  <ul className={styles.investorList} aria-label="투자자별 증권계좌 현황">
                    {investors.map((investor) => (
                      <InvestorRow key={investor.id} investor={investor} />
                    ))}
                  </ul>
                ) : (
                  <p>등록된 투자자가 없습니다.</p>
                )}
              </>
            )}
          </section>
          <section aria-labelledby="investor-register-title">
            <h2 id="investor-register-title">투자자 추가</h2>
            <InvestorRegisterForm />
          </section>
        </div>
      }
    </StyleScope>
  )
}
