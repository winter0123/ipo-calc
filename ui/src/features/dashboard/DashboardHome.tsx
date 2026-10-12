import DashboardTodo from './DashboardTodo.tsx'
import DashboardUpcoming from './DashboardUpcoming.tsx'
import styles from './DashboardHome.module.css'

export default function DashboardHome() {
  return (
    <div className={styles.grid}>
      <DashboardTodo />
      <DashboardUpcoming />
    </div>
  )
}
