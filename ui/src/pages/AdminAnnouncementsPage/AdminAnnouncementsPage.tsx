import { useState, type FormEvent, type ChangeEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAnnouncement,
  deleteAnnouncement,
  announcementKeys,
  fetchAdminAnnouncements,
  updateAnnouncement,
  type Announcement,
  type AnnouncementInput,
} from '@/entities/announcement/index.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import styles, { StyleScope } from './AdminAnnouncementsPage.styles.ts'

const STATUS_OPTIONS = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '게시' },
  { value: 'CANCELLED', label: '비활성' },
] as const

type StatusFilter = typeof STATUS_OPTIONS[number]['value']

const getToday = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const createEmptyForm = (): AnnouncementInput => ({
  title: '',
  content: '',
  startAt: getToday(),
  endAt: getToday(),
  status: 'ACTIVE',
})

function toFormStatus(value: string): AnnouncementInput['status'] {
  return (value === 'CANCELLED' ? 'CANCELLED' : 'ACTIVE') as AnnouncementInput['status']
}

function toFilterStatus(value: string): StatusFilter {
  return (value === 'ACTIVE' || value === 'CANCELLED' ? value : '') as StatusFilter
}

function getStatusBadgeClass(status: string): 'active' | 'cancelled' | 'inactive' {
  switch (status) {
    case 'ACTIVE':
      return 'active'
    case 'CANCELLED':
      return 'cancelled'
    default:
      return 'inactive'
  }
}

function getStatusLabel(status: string): string {
  switch (status) {
    case 'ACTIVE':
      return '게시'
    case 'CANCELLED':
      return '비활성'
    case 'INACTIVE':
      return '비공개'
    default:
      return status
  }
}

export default function AdminAnnouncementsPage() {
  const client = useQueryClient()
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [deleting, setDeleting] = useState<Announcement | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')
  const [form, setForm] = useState<AnnouncementInput>(createEmptyForm)

  const query = useQuery({
    queryKey: announcementKeys.adminList(page, statusFilter),
    queryFn: ({ signal }) => fetchAdminAnnouncements(page, statusFilter || undefined, signal),
  })

  const save = useMutation({
    mutationFn: () => (editing ? updateAnnouncement(editing.id, form) : createAnnouncement(form)),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: announcementKeys.adminAll })
      setEditing(null)
      setForm(createEmptyForm())
    },
  })

  const remove = useMutation({
    mutationFn: deleteAnnouncement,
    onSuccess: () => client.invalidateQueries({ queryKey: announcementKeys.adminAll }),
  })

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!form.title.trim() || !form.content.trim() || form.startAt > form.endAt) return
    save.mutate()
  }

  const handleInputChange = <K extends keyof AnnouncementInput>(
    field: K,
    value: AnnouncementInput[K],
  ) => {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  const handleDateChange = (field: 'startAt' | 'endAt', event: ChangeEvent<HTMLInputElement>) => {
    handleInputChange(field, event.target.value)
  }

  const handleSelectChange = (field: 'status', event: ChangeEvent<HTMLSelectElement>) => {
    handleInputChange(field, toFormStatus(event.target.value))
  }

  const handleFilterChange = (event: ChangeEvent<HTMLSelectElement>) => {
    setStatusFilter(toFilterStatus(event.target.value))
    setPage(0)
  }

  const edit = (item: Announcement) => {
    setEditing(item)
    setForm({
      title: item.title,
      content: item.content,
      startAt: item.startAt,
      endAt: item.endAt,
      status: item.status === 'INACTIVE' ? 'ACTIVE' : item.status,
    })
  }

  const resetForm = () => {
    setEditing(null)
    setForm(createEmptyForm())
  }

  return (
    <StyleScope>
      <section className={styles.pageWrapper}>
        <h1>공지사항 관리</h1>

        {/* 필터 섹션 */}
        <div className={`${styles.section} ${styles.filterSection}`}>
          <label className={styles.filterLabel} htmlFor="status-filter">
            상태 필터
          </label>
          <select
            id="status-filter"
            className={styles.filterSelect}
            value={statusFilter}
            onChange={handleFilterChange}
          >
            {STATUS_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>
                {opt.label}
              </option>
            ))}
          </select>
        </div>

        {/* 폼 섹션 */}
        <div className={`${styles.section} ${styles.formSection}`}>
          <header className={styles.formHeader}>
            <h2 className={styles.formTitle}>
              {editing ? '공지 수정' : '공지 등록'}
            </h2>
            {editing && (
              <span className={styles.formBadge}>
                {getStatusLabel(form.status)}
              </span>
            )}
          </header>

          <form className={styles.form} onSubmit={submit}>
            <div className={`${styles.formRow} ${styles.twoCols}`}>
              <label>
                게시 시작일
                <input
                  type="date"
                  required
                  value={form.startAt}
                  onChange={(e) => handleDateChange('startAt', e)}
                />
              </label>
              <label>
                게시 종료일
                <input
                  type="date"
                  required
                  value={form.endAt}
                  onChange={(e) => handleDateChange('endAt', e)}
                />
              </label>
            </div>

            {editing && (
              <label>
                상태
                <select
                  value={form.status}
                  onChange={(e) => handleSelectChange('status', e)}
                >
                  <option value="ACTIVE">게시</option>
                  <option value="CANCELLED">비활성</option>
                </select>
              </label>
            )}

            {form.startAt > form.endAt && (
              <p className={styles.error} role="alert">
                종료일은 시작일보다 빠를 수 없습니다.
              </p>
            )}

            {save.isError && (
              <p className={styles.error} role="alert">
                저장하지 못했습니다. 입력 내용을 확인해 주세요.
              </p>
            )}

            <label>
              제목
              <input
                maxLength={200}
                required
                value={form.title}
                onChange={(e) => handleInputChange('title', e.target.value)}
                placeholder="공지사항 제목을 입력하세요"
              />
            </label>

            <label>
              내용
              <textarea
                maxLength={10000}
                required
                rows={6}
                value={form.content}
                onChange={(e) => handleInputChange('content', e.target.value)}
                placeholder="공지사항 내용을 입력하세요"
              />
            </label>

            <div className={styles.formActions}>
              {editing ? (
                <button
                  type="button"
                  className={`${styles.btn} ${styles.btnSecondary}`}
                  onClick={resetForm}
                >
                  취소
                </button>
              ) : null}
              <button
                type="submit"
                className={`${styles.btn} ${styles.btnPrimary}`}
                disabled={save.isPending || form.startAt > form.endAt}
              >
                {save.isPending ? '저장 중...' : '저장'}
              </button>
            </div>
          </form>
        </div>

        {/* 목록 섹션 */}
        <div className={`${styles.section} ${styles.listSection}`}>
          {query.isPending && (
            <div className={styles.status} role="status">불러오는 중...</div>
          )}

          {query.isError && (
            <div className={`${styles.status} ${styles.error}`} role="alert">
              목록을 불러오지 못했습니다.
            </div>
          )}

          {!query.isPending && !query.isError && (!query.data?.content || query.data.content.length === 0) && (
            <div className={styles.empty}>
              <span className={styles.emptyIcon}>📭</span>
              <p className={styles.emptyText}>등록된 공지사항이 없습니다.</p>
              <p className={styles.emptySubtext}>
                위 폼에서 첫 공지사항을 등록해 보세요.
              </p>
            </div>
          )}

          <ul className={styles.list}>
            {query.data?.content.map((item) => (
              <li key={item.id} className={styles.listItem}>
                <div className={styles.listContent}>
                  <strong className={styles.listTitle}>{item.title}</strong>
                  <div className={styles.listMeta}>
                    <span>{item.startAt} ~ {item.endAt}</span>
                    <span className={`${styles.statusBadge} ${styles[`statusBadge${getStatusBadgeClass(item.status)}`] as string}`}>
                      {getStatusLabel(item.status)}
                    </span>
                  </div>
                </div>
                <div className={styles.listActions}>
                  <button
                    type="button"
                    className={`${styles.btn} ${styles.btnGhost} ${styles.listBtn}`}
                    onClick={() => edit(item)}
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    className={`${styles.btn} ${styles.btnDanger} ${styles.listBtn}`}
                    onClick={() => setDeleting(item)}
                  >
                    삭제
                  </button>
                </div>
              </li>
            ))}
          </ul>

          {query.data && query.data.totalPages > 1 && (
            <nav className={styles.paging} aria-label="관리자 공지사항 페이지">
              <button
                type="button"
                className={styles.pagingBtn}
                disabled={page === 0}
                onClick={() => setPage((value) => value - 1)}
              >
                이전
              </button>
              <span className={styles.pagingInfo}>
                {page + 1} / {query.data.totalPages}
              </span>
              <button
                type="button"
                className={styles.pagingBtn}
                disabled={page + 1 >= query.data.totalPages}
                onClick={() => setPage((value) => value + 1)}
              >
                다음
              </button>
            </nav>
          )}

          {remove.isError && (
            <div className={`${styles.status} ${styles.error}`} role="alert">
              삭제하지 못했습니다.
            </div>
          )}
        </div>

        <ConfirmDialog
          isOpen={Boolean(deleting)}
          title="공지사항 삭제"
          message={`“${deleting?.title ?? ''}” 공지를 삭제할까요?`}
          confirmLabel="삭제"
          cancelLabel="취소"
          pending={remove.isPending}
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            if (deleting) remove.mutate(deleting.id, { onSuccess: () => setDeleting(null) })
          }}
        />
      </section>
    </StyleScope>
  )
}