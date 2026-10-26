import { SecuritiesAccountRegisterForm } from '@/features/securities-account-register/index.ts'
import styles, { StyleScope } from './SecuritiesAccountRegisterPage.styles.ts'

export default function SecuritiesAccountRegisterPage() {
  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>증권계좌 등록</h1>
          <SecuritiesAccountRegisterForm />
        </div>
      }
    </StyleScope>
  )
}
