import { Suspense, useState } from 'react'
import { Menu, Waves, X, LogOut } from 'lucide-react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useLogout } from '@/features/login/index.ts'
import styles from './MainLayout.module.css'

const NAV_ITEMS = [
  { to: '/dashboard', label: '대시보드' },
  { to: '/investments', label: '투자 내역' },
  { to: '/subscriptions', label: '청약' },
  { to: '/ipo-calendar', label: '공모주 일정' },
  { to: '/profits/view', label: '수익 확인' },
  { to: '/broker-sync', label: '증권사 연동' },
]

const REGISTER_ITEMS = [
  { to: '/investors/register', label: '투자자' },
  { to: '/subscriptions/register', label: '청약' },
  { to: '/allocations/register', label: '배정' },
  { to: '/sales/register', label: '매도' },
  { to: '/costs/register', label: '기타 비용' },
]

const linkClass = ({ isActive }: { isActive: boolean }) =>
  isActive ? `${styles.link} ${styles.active}` : styles.link

export default function MainLayout() {
  const { data: currentAccount } = useCurrentAccount()
  const { pathname } = useLocation()
  const navigate = useNavigate()
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
          <span className={styles.logo} aria-hidden="true">
            <Waves size={20} />
          </span>
          <span className={styles.siteName}>투자 관리 시스템</span>
        </div>
        {currentAccount && (
          <>
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
                <Menu aria-hidden="true" size={22} />
              )}
              <span className={styles.menuLabel}>메뉴</span>
            </button>
            <nav
              id="primary-nav"
              aria-label="주요 메뉴"
              className={menuOpen ? `${styles.nav} ${styles.navOpen}` : styles.nav}
            >
              {NAV_ITEMS.map((item) => (
                <NavLink key={item.to} to={item.to} className={linkClass}>
                  {item.label}
                </NavLink>
              ))}
              <details key={pathname} className={styles.more}>
                <summary className={styles.link}>등록</summary>
                <div className={styles.morePanel}>
                  {REGISTER_ITEMS.map((item) => (
                    <NavLink key={item.to} to={item.to} className={styles.moreLink}>
                      {item.label}
                    </NavLink>
                  ))}
                </div>
              </details>
              {currentAccount.role === 'ADMIN' && (
                <>
                  <NavLink to="/admin/accounts" className={linkClass}>
                    가입 승인
                  </NavLink>
                  <NavLink to="/admin/ipos" className={linkClass}>
                    공모주 등록
                  </NavLink>
                </>
              )}
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
            </nav>
          </>
        )}
      </header>
      <main id="main-content" className={styles.main}>
        <Suspense fallback={<p role="status">불러오는 중...</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
