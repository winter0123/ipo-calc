import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  adminAccountKeys,
  approveAccount,
  revokeAccount,
  useAdminAccounts,
} from '@/entities/admin-account/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import styles from './AdminAccountList.module.css'

const formatDate = (iso: string) => iso.slice(0, 16).replace('T', ' ')

export default function AdminAccountList() {
  const [pendingOnly, setPendingOnly] = useState(true)
  const queryClient = useQueryClient()
  const { data, isLoading, error } = useAdminAccounts(pendingOnly)
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminAccountKeys.all })
  const approve = useMutation({ mutationFn: approveAccount, onSuccess: refresh })
  const revoke = useMutation({ mutationFn: revokeAccount, onSuccess: refresh })

  const loadError = toErrorMessage(error, { byStatus: { 403: '관리자만 볼 수 있는 화면입니다.' } })
  const actionError = toErrorMessage(approve.error ?? revoke.error, {
    byStatus: { 400: (e) => e.serverMessage, 404: '계정을 찾을 수 없습니다.' },
  })

  return (
    <section className={styles.panel} aria-label="가입 승인 목록">
      <label className={styles.filter}>
        <input
          type="checkbox"
          checked={pendingOnly}
          onChange={(e) => setPendingOnly(e.target.checked)}
        />
        승인 대기만 보기
      </label>

      {isLoading && <p role="status">불러오는 중...</p>}
      {loadError && <p role="alert">{loadError}</p>}
      {actionError && <p role="alert">{actionError}</p>}

      {data && data.length === 0 && (
        <p>{pendingOnly ? '승인 대기 중인 계정이 없습니다.' : '계정이 없습니다.'}</p>
      )}
      {data && data.length > 0 && (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <caption>계정 {data.length}건</caption>
            <thead>
              <tr>
                <th scope="col">이메일</th>
                <th scope="col">이름</th>
                <th scope="col">역할</th>
                <th scope="col">가입일</th>
                <th scope="col">상태</th>
                <th scope="col">처리</th>
              </tr>
            </thead>
            <tbody>
              {data.map((account) => (
                <tr key={account.id}>
                  <td>{account.email}</td>
                  <td>{account.name ?? '-'}</td>
                  <td>{account.role}</td>
                  <td>{formatDate(account.createdAt)}</td>
                  <td>{account.approved ? '승인됨' : '승인 대기'}</td>
                  <td>
                    {account.approved ? (
                      <Button
                        variant="secondary"
                        aria-label={`${account.email} 승인 취소`}
                        disabled={revoke.isPending}
                        onClick={() => revoke.mutate(account.id)}
                      >
                        승인 취소
                      </Button>
                    ) : (
                      <Button
                        aria-label={`${account.email} 승인`}
                        disabled={approve.isPending}
                        onClick={() => approve.mutate(account.id)}
                      >
                        승인
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
