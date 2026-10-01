import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'
import styles from './MainLayout.module.css'

export default function MainLayout() {
  return (
    <div className={styles.layout}>
      <a href="#main-content" className={styles.skipLink}>
        본문으로 건너뛰기
      </a>
      <header className={styles.header}>
        <div className={styles.siteName}>투자 관리 시스템</div>
      </header>
      <main id="main-content" className={styles.main}>
        <Suspense fallback={<p role="status">불러오는 중...</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  )
}
