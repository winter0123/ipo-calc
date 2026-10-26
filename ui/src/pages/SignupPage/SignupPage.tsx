import { SignupForm } from '@/features/signup/index.ts'
import styles, { StyleScope } from './SignupPage.styles.ts'

export default function SignupPage() {
  return (
    <StyleScope>
      {
        <div className={styles.page}>
          <h1>회원가입</h1>
          <SignupForm />
        </div>
      }
    </StyleScope>
  )
}
