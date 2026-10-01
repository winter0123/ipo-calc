import { Suspense } from 'react'
import { Landmark, UserCheck, Waves } from 'lucide-react'
import { Link, Outlet } from 'react-router-dom'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import styles from './MainLayout.module.css'

export default function MainLayout() {
  const { data: currentAccount } = useCurrentAccount()
  return (
    <div className={styles.layout}>
      <a href="#main-content" className={styles.skipLink}>
        본문으로 건너뛰기
      </a>
      <header className={styles.header}>
        <div className={styles.siteName}>
          <Waves aria-hidden="true" size={26} />
          투자 관리 시스템
        </div>
        {currentAccount && (
          <nav aria-label="주요 메뉴" className={styles.nav}>
            <Link to="/broker-sync">
              <Landmark aria-hidden="true" size={18} />
              증권사 연동
            </Link>
            {currentAccount?.role === 'ADMIN' && (
              <Link to="/admin/accounts">
                <UserCheck aria-hidden="true" size={18} />
                가입 승인
              </Link>
            )}
          </nav>
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
