import DashboardTodo from './DashboardTodo.tsx'
import DashboardUpcoming from './DashboardUpcoming.tsx'
import styles, { StyleScope } from './DashboardHome.styles.ts'

export default function DashboardHome() {
  return (
    <StyleScope>
      {
        <div className={styles.grid}>
          <DashboardUpcoming />
          <DashboardTodo />
        </div>
      }
    </StyleScope>
  )
}
