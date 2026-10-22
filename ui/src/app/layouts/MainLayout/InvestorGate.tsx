import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useSecuritiesAccounts } from '@/entities/securities-account/index.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import styles from './MainLayout.module.css'

// 투자자별로 작업하는 화면. 대시보드·공모주 일정·투자자 등록·관리자 화면은 계정 단위라 제외한다.
const GATED_PREFIXES = [
  '/investments',
  '/subscriptions',
  '/allocations',
  '/sales',
  '/profits',
  '/broker-sync',
  '/securities-accounts',
  '/securities',
]

function isInvestorScoped(pathname: string): boolean {
  return GATED_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`))
}

// 투자자가 2명 이상인데 아직 고르지 않았을 때 작업 화면 대신 보여 준다.
export default function InvestorGate() {
  const { investors, select } = useCurrentInvestor()
  return (
    <section className={styles.gate} aria-labelledby="gate-title">
      <h1 id="gate-title">어느 투자자로 작업할까요?</h1>
      <p>
        투자자를 선택하면 청약·배정·매도·수익 화면이 그 투자자 기준으로 열립니다. 상단 메뉴에서
        언제든 바꿀 수 있습니다.
      </p>
      <div className={styles.gateButtons}>
        {investors.map((investor) => (
          <button key={investor.id} type="button" onClick={() => select(investor.id)}>
            {investor.displayName}
          </button>
        ))}
      </div>
    </section>
  )
}

// 투자자가 아직 한 명도 없을 때 작업 화면 대신 보여 준다(투자 내역·증권사 관리는 자체 안내가 있다).
function NoInvestorGate() {
  return (
    <section className={styles.gate} aria-labelledby="no-investor-title">
      <h1 id="no-investor-title">먼저 투자자를 등록해 주세요</h1>
      <p>
        청약·배정·매도·수익은 투자자별로 기록합니다. 본인 이름이나 가족 이름으로 먼저 만들어 두세요.
      </p>
      <div className={styles.gateButtons}>
        <Link to="/investors/register" className={styles.gateLink}>
          투자자 등록하기
        </Link>
      </div>
    </section>
  )
}

// 로그인한 사용자의 본문. 선택이 필요하면 작업 화면 대신 선택 화면을 보여 준다.
export function GatedOutlet() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const { needsSelection, investors, current, isLoading } = useCurrentInvestor()
  const accountsQuery = useSecuritiesAccounts(current?.id)
  if (!isInvestorScoped(pathname)) return <Outlet />
  if (needsSelection) return <InvestorGate />
  const selfGuided = pathname.startsWith('/investments') || pathname === '/securities'
  if (!isLoading && investors.length === 0 && !selfGuided) return <NoInvestorGate />
  const needsSecuritiesAccount =
    pathname !== '/securities' &&
    !pathname.startsWith('/securities-accounts/register/') &&
    current !== null &&
    !accountsQuery.isLoading &&
    !accountsQuery.isError &&
    accountsQuery.data?.length === 0
  if (needsSecuritiesAccount) {
    return (
      <ConfirmDialog
        isOpen
        title="증권계좌를 먼저 등록해 주세요"
        message="청약·배정·매도 등의 기록을 시작하려면 현재 투자자의 증권계좌가 필요합니다. 증권계좌 등록 화면으로 이동할까요?"
        confirmLabel="증권계좌 등록"
        cancelLabel="나중에"
        onConfirm={() => navigate(`/securities-accounts/register/${current.id}`)}
        onCancel={() => navigate('/dashboard', { replace: true })}
      />
    )
  }
  return <Outlet />
}
