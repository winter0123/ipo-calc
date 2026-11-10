import { Navigate } from 'react-router-dom'

// 홈은 로그인 여부와 관계없이 공모주 일정으로 연결한다.
export default function RootRedirect() {
  return <Navigate to="/ipo-calendar" replace />
}
