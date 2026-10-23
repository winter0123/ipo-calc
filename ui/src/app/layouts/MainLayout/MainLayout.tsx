import { Suspense, useEffect, useRef, useState } from 'react'
import { Waves, X, LogOut } from 'lucide-react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useLogout } from '@/features/login/index.ts'
import { resetAnnouncementVisit } from '@/features/announcements/index.ts'
import { GatedOutlet } from './InvestorGate.tsx'
import NavGroup from './NavGroup.tsx'
import InvestorSwitcher from './InvestorSwitcher.tsx'
import styles from './MainLayout.module.css'

const NAV_ITEMS = [
  { to: '/dashboard', label: '대시보드' },
  { to: '/ipo-calendar', label: '공모주 일정' },
  { to: '/subscriptions', label: '청약' },
  { to: '/investments', label: '투자 내역' },
  { to: '/profits/view', label: '수익 확인' },
  { to: '/announcements', label: '공지사항' },
  { to: '/about', label: '서비스 소개' },
]

const RECORD_ITEMS = [
  { to: '/subscriptions/register', label: '청약 등록' },
  { to: '/allocations/register', label: '배정 입력' },
  { to: '/sales/register', label: '매도 등록' },
]

const SETTINGS_ITEMS = [
  { to: '/my-page', label: '마이페이지' },
  { to: '/investors/register', label: '투자자 추가' },
  { to: '/securities', label: '증권사 계좌 등록' },
  { to: '/broker-sync', label: '증권사 연동' },
  { to: '/notifications', label: '알림 설정' },
]

const ADMIN_ITEMS = [
  { to: '/admin/accounts', label: '가입 승인' },
  { to: '/admin/ipos', label: '공모주 등록' },
  { to: '/admin/announcements', label: '공지사항 관리' },
]

const GUEST_ITEMS = [
  { to: '/about', label: '서비스 소개' },
  { to: '/ipo-calendar', label: '공모주 일정' },
]

const linkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? `${styles.link} ${styles.active}` : styles.link

export default function MainLayout() {
  const { data: currentAccount, isPending } = useCurrentAccount()
  const previousAccountId = useRef<number | null | undefined>(undefined)
  useEffect(() => {
    if (isPending) return
    const accountId = currentAccount?.accountId ?? null
    if (previousAccountId.current !== undefined && previousAccountId.current !== accountId) {
      resetAnnouncementVisit()
    }
    previousAccountId.current = accountId
  }, [currentAccount?.accountId, isPending])
  const isGuest = !isPending && !currentAccount
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const isWide = pathname === '/' || pathname === '/about'
  const logoutMutation = useLogout()
  // 메뉴를 연 경로를 기억해 두면 페이지가 바뀔 때 자동으로 닫힌다
  const [openedAt, setOpenedAt] = useState<string | null>(null)
  const menuOpen = openedAt === pathname
  return (
    <div className={styles.layout}>
      <a href="#main-content" className={styles.skipLink}>
        본문으로 건너뛰기
      </a>
      <header className={styles.header}>
        <div className={styles.brand}>
          {/* 데스크톱: 브랜드 마크. 모바일(≤900px): 같은 자리의 물결이 메뉴 버튼 */}
          <span className={styles.logo} aria-hidden="true">
            <Waves size={20} />
          </span>
          {(currentAccount || isGuest) && (
            <button
              type="button"
              className={styles.menuButton}
              aria-expanded={menuOpen}
              aria-controls="primary-nav"
              onClick={() => setOpenedAt(menuOpen ? null : pathname)}
            >
              {menuOpen ? (
                <X aria-hidden="true" size={22} />
              ) : (
                <Waves aria-hidden="true" size={22} />
              )}
              <span className={styles.menuLabel}>메뉴</span>
            </button>
          )}
          <span className={styles.siteName}>공모주계산기</span>
        </div>
        {(currentAccount || isGuest) && (
          <nav
            id="primary-nav"
            aria-label="주요 메뉴"
            className={menuOpen ? `${styles.nav} ${styles.navOpen}` : styles.nav}
          >
            {(currentAccount ? NAV_ITEMS : GUEST_ITEMS).map((item) => (
              <NavLink key={item.to} to={item.to} className={linkClass}>
                {item.label}
              </NavLink>
            ))}
            {currentAccount && <NavGroup label="새로 기록" items={RECORD_ITEMS} />}
            {currentAccount?.role === 'ADMIN' && <NavGroup label="관리자" items={ADMIN_ITEMS} />}
            {currentAccount && <NavGroup label="설정" items={SETTINGS_ITEMS} />}
            {currentAccount && (
              <button
                type="button"
                className={styles.logout}
                onClick={() => {
                  logoutMutation.mutate(undefined, {
                    onSettled: () => navigate('/login', { replace: true }),
                  })
                }}
                disabled={logoutMutation.isPending}
              >
                <LogOut aria-hidden="true" size={16} />
                로그아웃
              </button>
            )}
          </nav>
        )}
        {currentAccount && <InvestorSwitcher />}
        {isGuest && (
          <div className={styles.authActions}>
            <Link to="/login" className={styles.loginButton}>
              로그인
            </Link>
            <Link to="/signup" className={styles.signupButton}>
              가입하기
            </Link>
          </div>
        )}
      </header>
      <main
        id="main-content"
        className={isWide ? `${styles.main} ${styles.mainWide}` : styles.main}
      >
        <Suspense fallback={<p role="status">불러오는 중...</p>}>
          {currentAccount ? <GatedOutlet /> : <Outlet />}
        </Suspense>
      </main>
    </div>
  )
}
