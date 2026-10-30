import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { adminUserKeys } from './queryKeys.ts'
import { fetchAdminUsers } from './api-client.ts'
import { defaultSearchParams, type AdminUserSearchParams } from './types.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import Button from '@/shared/components/Button/Button.tsx'
import styles, { StyleScope } from './AdminUserList.styles.ts'

const ROLE_OPTIONS = [
  { value: null, label: '전체' },
  { value: 'USER', label: '일반' },
  { value: 'ADMIN', label: '관리자' },
] as const

const BOOL_OPTIONS = [
  { value: null, label: '전체' },
  { value: true, label: '예' },
  { value: false, label: '아니오' },
] as const

const SIZE_OPTIONS = [10, 20, 50, 100] as const

const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: '가입일 최신순' },
  { value: 'createdAt,asc', label: '가입일 오래된순' },
  { value: 'email,asc', label: '이메일 순' },
] as const

function emptyParams(): AdminUserSearchParams {
  return { ...defaultSearchParams, search: '', role: null, approved: null, locked: null }
}

export default function AdminUserList() {
  const navigate = useNavigate()
  const [draft, setDraft] = useState<AdminUserSearchParams>(emptyParams())
  const [submitted, setSubmitted] = useState<AdminUserSearchParams>(emptyParams())
  const [size, setSize] = useState(defaultSearchParams.size)

  const query = useQuery({
    queryKey: adminUserKeys.list({
      page: submitted.page,
      size: submitted.size,
      search: submitted.search || undefined,
    }),
    queryFn: ({ signal }) => fetchAdminUsers(submitted, signal),
    enabled: Boolean(submitted),
  })

  const setDraftField = useCallback(
    (field: keyof AdminUserSearchParams, value: string | number | boolean | null) => {
      setDraft((prev) => ({ ...prev, [field]: value }))
    },
    [],
  )

  const handleSubmit = () => {
    setSubmitted({ ...draft, page: 0, size })
  }

  const handleReset = () => {
    setDraft(emptyParams())
    setSubmitted(emptyParams())
    setSize(defaultSearchParams.size)
  }

  const isEmpty = Boolean(query.data) && query.data?.content.length === 0
  const loadError = toErrorMessage(query.error, {
    byStatus: { 403: '관리자만 볼 수 있는 화면입니다.' },
  })

  return (
    <StyleScope>
      <section className={styles.panel} aria-label="회원 목록">
        <h2 className={styles.title}>회원 관리</h2>

        <form className={styles.searchForm} onSubmit={(e) => e.preventDefault()}>
          <div className={styles.searchRow}>
            <label className={styles.searchLabel}>
              검색어
              <input
                className={styles.searchInput}
                value={draft.search ?? ''}
                onChange={(e) => setDraftField('search', e.target.value)}
                placeholder="이메일·닉네임·이름"
              />
            </label>
            <label className={styles.selectLabel}>
              역할
              <select
                className={styles.select}
                value={draft.role ?? ''}
                onChange={(e) => setDraftField('role', e.target.value || null)}
              >
                {ROLE_OPTIONS.map((opt) => (
                  <option key={String(opt.value)} value={opt.value ?? ''}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.selectLabel}>
              승인
              <select
                className={styles.select}
                value={String(draft.approved ?? '')}
                onChange={(e) =>
                  setDraftField(
                    'approved',
                    e.target.value === 'true' ? true : e.target.value === 'false' ? false : null,
                  )
                }
              >
                {BOOL_OPTIONS.map((opt) => (
                  <option key={String(opt.value)} value={String(opt.value ?? '')}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.selectLabel}>
              잠금
              <select
                className={styles.select}
                value={String(draft.locked ?? '')}
                onChange={(e) =>
                  setDraftField(
                    'locked',
                    e.target.value === 'true' ? true : e.target.value === 'false' ? false : null,
                  )
                }
              >
                {BOOL_OPTIONS.map((opt) => (
                  <option key={String(opt.value)} value={String(opt.value ?? '')}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
            <Button type="submit" onClick={handleSubmit} disabled={query.isPending}>
              검색
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={handleReset}
              disabled={query.isPending}
            >
              초기화
            </Button>
          </div>
        </form>

        {query.isPending && (
          <p className={styles.status} role="status">
            불러오는 중...
          </p>
        )}
        {loadError && (
          <p className={styles.error} role="alert">
            {loadError}
          </p>
        )}

        {query.isSuccess && query.data && !isEmpty && (
          <>
            <div className={styles.toolbar}>
              <label className={styles.sizeLabel}>
                페이지 크기
                <select
                  className={styles.sizeSelect}
                  value={String(size)}
                  onChange={(e) => setSize(Number(e.target.value))}
                >
                  {SIZE_OPTIONS.map((s) => (
                    <option key={s} value={s}>
                      {s}명
                    </option>
                  ))}
                </select>
              </label>
              <label className={styles.sortLabel}>
                정렬
                <select
                  className={styles.sortSelect}
                  value={submitted.sort ?? ''}
                  onChange={(e) => {
                    setDraftField('sort', e.target.value || null)
                    setSubmitted((prev) => ({ ...prev, sort: e.target.value || null, page: 0 }))
                  }}
                >
                  {SORT_OPTIONS.map((opt) => (
                    <option key={opt.value} value={opt.value}>
                      {opt.label}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <table className={styles.table} aria-label="회원 목록">
              <caption>{query.data.content.length}건</caption>
              <thead>
                <tr>
                  <th scope="col">이메일</th>
                  <th scope="col">닉네임</th>
                  <th scope="col">이름</th>
                  <th scope="col">역할</th>
                  <th scope="col">상태</th>
                  <th scope="col">투자자</th>
                  <th scope="col">청약</th>
                  <th scope="col">가입일</th>
                  <th scope="col">상세</th>
                </tr>
              </thead>
              <tbody>
                {query.data.content.map((user) => (
                  <tr key={user.id}>
                    <td>{user.email}</td>
                    <td>{user.nickname}</td>
                    <td>{user.name ?? '-'}</td>
                    <td>{user.role}</td>
                    <td>
                      {user.approved ? '승인' : '승인 대기'}
                      {user.locked ? ' · 잠김' : ''}
                    </td>
                    <td>{user.investorCount}</td>
                    <td>{user.subscriptionCount}</td>
                    <td>{user.createdAt.slice(0, 10)}</td>
                    <td>
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => navigate(`/admin/users/${user.id}`)}
                      >
                        상세
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            <nav className={styles.paging} aria-label="회원 목록 페이지">
              <Button
                type="button"
                variant="secondary"
                disabled={submitted.page === 0 || query.isPending}
                onClick={() => setSubmitted((prev) => ({ ...prev, page: prev.page - 1 }))}
              >
                이전
              </Button>
              <span className={styles.pagingInfo}>
                {submitted.page + 1} / {Math.max(query.data.totalPages, 1)}
              </span>
              <Button
                type="button"
                variant="secondary"
                disabled={submitted.page + 1 >= query.data.totalPages || query.isPending}
                onClick={() => setSubmitted((prev) => ({ ...prev, page: prev.page + 1 }))}
              >
                다음
              </Button>
            </nav>
          </>
        )}

        {query.isSuccess && query.data && isEmpty && (
          <p className={styles.empty}>조회된 회원이 없습니다.</p>
        )}
      </section>
    </StyleScope>
  )
}
