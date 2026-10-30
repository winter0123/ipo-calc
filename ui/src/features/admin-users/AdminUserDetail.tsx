import { useCallback, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { fetchAdminUserDetail, fetchAdminUserInvestors } from './api-client.ts'
import { adminUserKeys } from './queryKeys.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import { useImpersonation } from '@/entities/impersonation/index.ts'
import styles, { StyleScope } from './AdminUserDetail.styles.ts'
import type { AdminUserDetail } from './types.ts'

const TABS = [
  { key: 'info', label: '기본정보' },
  { key: 'stats', label: '실제통계' },
  { key: 'history', label: '내역' },
] as const

export default function AdminUserDetail() {
  const { accountId } = useParams<{ accountId: string }>()
  const id = accountId ? Number(accountId) : NaN
  const navigate = useNavigate()
  const { data: account } = useCurrentAccount()
  const isAdmin = account?.role === 'ADMIN'
  const [tab, setTab] = useState<'info' | 'stats' | 'history'>('info')
  const [confirming, setConfirming] = useState(false)

  const detail = useQuery({
    queryKey: adminUserKeys.detail(id),
    queryFn: ({ signal }) => fetchAdminUserDetail(id, signal),
    enabled: Number.isFinite(id),
    retry: false,
  })

  const investors = useQuery({
    queryKey: adminUserKeys.investors(id),
    queryFn: ({ signal }) => fetchAdminUserInvestors(id, signal),
    enabled: Number.isFinite(id) && tab === 'history',
    retry: false,
  })

  const { start, isImpersonating, isPending, startError } = useImpersonation({ isAdmin })

  const loadError = toErrorMessage(detail.error ?? investors.error, {
    byStatus: { 403: '관리자만 볼 수 있는 화면입니다.', 404: '회원을 찾을 수 없습니다.' },
  })

  const goBack = useCallback(() => navigate('/admin/users', { replace: true }), [navigate])

  const handleStart = useCallback(() => {
    if (!isAdmin || !Number.isFinite(id) || isImpersonating) return
    setConfirming(true)
  }, [isAdmin, id, isImpersonating])

  const handleConfirmStart = useCallback(async () => {
    setConfirming(false)
    try {
      await start(id)
      navigate('/dashboard', { replace: true })
    } catch {
      // 시작 오류는 startError로 노출
    }
  }, [start, id, navigate])

  if (!Number.isFinite(id)) {
    return (
      <StyleScope>
        <p className={styles.error} role="alert">
          잘못된 회원 식별자입니다.
        </p>
        <Button type="button" onClick={goBack}>
          목록으로
        </Button>
      </StyleScope>
    )
  }

  if (detail.isPending) {
    return (
      <StyleScope>
        <p className={styles.status} role="status">
          불러오는 중...
        </p>
      </StyleScope>
    )
  }

  if (loadError || !detail.data) {
    return (
      <StyleScope>
        <p className={styles.error} role="alert">
          {loadError ?? '회원을 불러오지 못했습니다.'}
        </p>
        <Button type="button" onClick={goBack}>
          목록으로
        </Button>
      </StyleScope>
    )
  }

  const user = detail.data
  return (
    <StyleScope>
      <section className={styles.panel} aria-label="회원 상세">
        <div className={styles.header}>
          <Button type="button" variant="secondary" onClick={goBack}>
            목록으로
          </Button>
          <h2 className={styles.title}>{user.nickname ?? user.email}</h2>
          {isAdmin && !isImpersonating && (
            <Button
              type="button"
              disabled={isPending}
              onClick={handleStart}
              aria-label={`${user.email} 임퍼소네이션 시작`}
            >
              {isPending ? '시작 중...' : '임퍼소네이션 시작'}
            </Button>
          )}
        </div>

        <div className={styles.tabs}>
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              className={`${styles.tab} ${tab === t.key ? styles.tabActive : ''}`}
              onClick={() => setTab(t.key as 'info' | 'stats' | 'history')}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'info' && (
          <table className={styles.infoTable} aria-label="회원 기본정보">
            <tbody>
              <tr>
                <th scope="row">이메일</th>
                <td>{user.email}</td>
              </tr>
              <tr>
                <th scope="row">닉네임</th>
                <td>{user.nickname ?? '-'}</td>
              </tr>
              <tr>
                <th scope="row">이름</th>
                <td>{user.name ?? '-'}</td>
              </tr>
              <tr>
                <th scope="row">역할</th>
                <td>{user.role}</td>
              </tr>
              <tr>
                <th scope="row">승인 여부</th>
                <td>{user.approved ? '승인됨' : '승인 대기'}</td>
              </tr>
              <tr>
                <th scope="row">잠금 여부</th>
                <td>{user.locked ? '잠김' : '정상'}</td>
              </tr>
              {user.lockedUntil && (
                <tr>
                  <th scope="row">잠금 해제 시각</th>
                  <td>{user.lockedUntil.slice(0, 16)}</td>
                </tr>
              )}
              <tr>
                <th scope="row">마지막 로그인</th>
                <td>{user.lastLoginAt ? user.lastLoginAt.slice(0, 16) : '-'}</td>
              </tr>
              <tr>
                <th scope="row">가입일</th>
                <td>{user.createdAt.slice(0, 10)}</td>
              </tr>
            </tbody>
          </table>
        )}

        {tab === 'stats' && (
          <div className={styles.statsGrid}>
            <div className={styles.statCard}>
              <span className={styles.statLabel}>청약 건수</span>
              <span className={styles.statValue}>{user.stats.subscriptionCount}</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statLabel}>배정 건수</span>
              <span className={styles.statValue}>{user.stats.allocationCount}</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statLabel}>매도 건수</span>
              <span className={styles.statValue}>{user.stats.saleCount}</span>
            </div>
            <div className={styles.statCard}>
              <span className={styles.statLabel}>총 실현손익</span>
              <span className={styles.statValue}>{user.stats.totalProfit.toLocaleString()}원</span>
            </div>
          </div>
        )}

        {tab === 'history' && (
          <>
            <h3 className={styles.sectionTitle}>투자자</h3>
            {investors.isPending && (
              <p className={styles.status} role="status">
                불러오는 중...
              </p>
            )}
            {investors.error && (
              <p className={styles.error} role="alert">
                {toErrorMessage(investors.error)}
              </p>
            )}
            {investors.data && investors.data.length > 0 && (
              <table className={styles.historyTable} aria-label="투자자 목록">
                <thead>
                  <tr>
                    <th scope="col">표시명</th>
                    <th scope="col">관계</th>
                    <th scope="col">상태</th>
                  </tr>
                </thead>
                <tbody>
                  {investors.data.map((inv) => (
                    <tr key={inv.id}>
                      <td>{inv.displayName}</td>
                      <td>{inv.relationship}</td>
                      <td>{inv.status}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {investors.data && investors.data.length === 0 && (
              <p className={styles.empty}>등록된 투자자가 없습니다.</p>
            )}
          </>
        )}

        {startError && (
          <p className={styles.error} role="alert">
            임퍼소네이션을 시작하지 못했습니다.
          </p>
        )}

        <ConfirmDialog
          isOpen={confirming}
          title="임퍼소네이션 시작"
          message={`“${user.nickname ?? user.email}” 계정으로 읽기 전용 모드로 전환할까요?`}
          confirmLabel="시작"
          cancelLabel="취소"
          pending={isPending}
          onConfirm={handleConfirmStart}
          onCancel={() => setConfirming(false)}
        />
      </section>
    </StyleScope>
  )
}
