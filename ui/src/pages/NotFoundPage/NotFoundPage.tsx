import { Link } from 'react-router-dom'
import styles from './NotFoundPage.module.css'

export default function NotFoundPage() {
  return (
    <div className={styles.page}>
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>주소를 확인하거나 대시보드로 이동해 주세요.</p>
      <Link to="/dashboard">대시보드로 이동</Link>
    </div>
  )
}
