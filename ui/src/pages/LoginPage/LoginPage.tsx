import { Link } from 'react-router-dom'
import { LoginForm } from '@/features/login/index.ts'
import styles, { StyleScope } from './LoginPage.styles.ts'

export default function LoginPage() {
  return (
    <StyleScope>
      {
        <div className={styles.page}>
          <h1>로그인</h1>
          <LoginForm />
          <p className={styles.switch}>
            계정이 없으신가요? <Link to="/signup">가입하기</Link>
          </p>
        </div>
      }
    </StyleScope>
  )
}
