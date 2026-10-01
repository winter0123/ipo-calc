import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

export default class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error(error, info.componentStack)
  }

  render() {
    if (!this.state.hasError) return this.props.children
    return (
      <div role="alert">
        <h1>문제가 발생했습니다</h1>
        <p>잠시 후 다시 시도해 주세요.</p>
        <button type="button" onClick={() => window.location.reload()}>
          새로고침
        </button>{' '}
        {/* Router 바깥이라 Link 대신 <a>를 쓴다. 전체 이동이라 오류 상태도 초기화된다. */}
        <a href="/dashboard">대시보드로 이동</a>
      </div>
    )
  }
}
