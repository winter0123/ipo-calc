import { useEffect } from 'react'
import { useBlocker, type BlockerFunction } from 'react-router-dom'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { useUnsavedGuard } from '@/shared/navigation/unsavedGuard.ts'

/**
 * 전역 미저장/진행 중 이동 차단.
 *
 * - 대화상자는 data-router 전용 useBlocker 위에서 동작하므로 AppProviders가
 *   createBrowserRouter로 변환한 뒤 라우터 안쪽에 마운트한다.
 * - 401 로그인 강제 이동이나 로그아웃처럼 /login으로 가는 이동은 막지 않는다.
 * - pending(저장·필수 재조회 진행) 중에는 이동 자체를 막는다(확인 버튼 잠금).
 * - dirty면 "이동하기"를 누른 경우에만 진행하고, 아니면 "계속 작성"으로 머문다.
 * - 새로고침·탭 닫기는 beforeunload로 한 번 더 경고한다.
 */
export default function UnsavedChangesBlocker() {
  const { isDirty, isPending } = useUnsavedGuard()

  const shouldBlock: BlockerFunction = ({ nextLocation }) => {
    if (nextLocation.pathname === '/login') return false
    return isDirty || isPending
  }
  const blocker = useBlocker(shouldBlock)
  const blocked = blocker.state === 'blocked'

  useEffect(() => {
    if (!isDirty && !isPending) return
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty, isPending])

  return (
    <ConfirmDialog
      isOpen={blocked}
      title={isPending ? '저장이 진행 중입니다' : '저장하지 않은 입력이 있습니다'}
      message={
        isPending
          ? '저장이 끝나기 전에는 이동할 수 없습니다. 저장이 끝난 뒤 다시 이동해 주세요.'
          : '이동하면 지금 입력한 내용이 사라집니다. 그래도 이동할까요?'
      }
      confirmLabel="이동하기"
      cancelLabel="계속 작성"
      pending={isPending}
      onConfirm={() => {
        if (blocker.state === 'blocked') blocker.proceed()
      }}
      onCancel={() => {
        if (blocker.state === 'blocked') blocker.reset()
      }}
    />
  )
}
