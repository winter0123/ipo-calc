import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useLogout } from '@/features/login/index.ts'
import { ImpersonationBanner } from '@/widgets/banner/index.ts'
import NotFoundPage from '@/pages/NotFoundPage/NotFoundPage.tsx'
import styles, { StyleScope } from './AdminLayout.styles.ts'

const ADMIN_ITEMS = [
  { to: '/admin/users', label: '회원 관리' },
  { to: '/admin/ipos', label: '공모주 등록' },
  { to: '/admin/announcements', label: '공지사항 관리' },
]

export default function AdminLayout() {
  const { data: account, isPending } = useCurrentAccount()
  const navigate = useNavigate()
  const logoutMutation = useLogout()
  const isAdmin = account?.role === 'ADMIN'

  // 일반 사용자가 /admin 경로로 직접 접속하면 NotFound를 보여 준다.
  if (isPending) return <p role="status">불러오는 중...</p>
  if (!isAdmin) return <NotFoundPage />

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    isActive ? `${styles.link} ${styles.active}` : styles.link

  return (
    <StyleScope>
      <div className={styles.layout}>
        <header className={styles.header}>
          <div className={styles.brand}>
            <span className={styles.siteName}>관리센터</span>
          </div>
          <nav className={styles.nav} aria-label="관리 메뉴">
            {ADMIN_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={linkClass}
                end={item.to === '/admin/users'}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className={styles.actions}>
            <ImpersonationBanner />
            <button
              type="button"
              className={styles.logoutButton}
              disabled={logoutMutation.isPending}
              onClick={() =>
                logoutMutation.mutate(undefined, {
                  onSettled: () => navigate('/login', { replace: true }),
                })
              }
            >
              {logoutMutation.isPending ? '로그아웃 중...' : '로그아웃'}
            </button>
          </div>
        </header>
        <main className={styles.main}>
          <Outlet />
        </main>
      </div>
    </StyleScope>
  )
}
