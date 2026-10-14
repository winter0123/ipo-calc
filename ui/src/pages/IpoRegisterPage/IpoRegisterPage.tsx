import { IpoSyncNowPanel } from '@/features/ipo-sync-now/index.ts'
import { IpoRegisterForm } from '@/features/ipo-register/index.ts'
import styles from './IpoRegisterPage.module.css'

export default function IpoRegisterPage() {
  return (
    <div className={styles.container}>
      <h1>공모주 등록</h1>
      <p className={styles.description}>
        자동 동기화에 없는 공모주를 직접 등록합니다. 관리자만 사용할 수 있습니다.
      </p>
      <IpoSyncNowPanel />
      <IpoRegisterForm />
    </div>
  )
}
