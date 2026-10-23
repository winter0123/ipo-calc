import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createAnnouncement,
  deleteAnnouncement,
  fetchAdminAnnouncements,
  updateAnnouncement,
  type Announcement,
  type AnnouncementInput,
} from '@/entities/announcement/index.ts'
import ConfirmDialog from '@/shared/components/ConfirmDialog/ConfirmDialog.tsx'
import styles from './AdminAnnouncementsPage.module.css'

const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const EMPTY: AnnouncementInput = {
  title: '',
  content: '',
  startAt: today(),
  endAt: today(),
  status: 'ACTIVE',
}

export default function AdminAnnouncementsPage() {
  const client = useQueryClient()
  const [editing, setEditing] = useState<Announcement | null>(null)
  const [deleting, setDeleting] = useState<Announcement | null>(null)
  const [page, setPage] = useState(0)
  const [statusFilter, setStatusFilter] = useState<'' | 'ACTIVE' | 'CANCELLED'>('')
  const [form, setForm] = useState<AnnouncementInput>(EMPTY)
  const query = useQuery({
    queryKey: ['admin-announcements', page, statusFilter],
    queryFn: ({ signal }) => fetchAdminAnnouncements(page, statusFilter || undefined, signal),
  })
  const save = useMutation({
    mutationFn: () => (editing ? updateAnnouncement(editing.id, form) : createAnnouncement(form)),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['admin-announcements'] })
      setEditing(null)
      setForm(EMPTY)
    },
  })
  const remove = useMutation({
    mutationFn: deleteAnnouncement,
    onSuccess: () => client.invalidateQueries({ queryKey: ['admin-announcements'] }),
  })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!form.title.trim() || !form.content.trim() || form.startAt > form.endAt) return
    save.mutate()
  }
  const edit = (item: Announcement) => {
    setEditing(item)
    setForm({
      title: item.title,
      content: item.content,
      startAt: item.startAt,
      endAt: item.endAt,
      status: item.status,
    })
  }
  return (
    <section className={styles.page}>
      <h1>공지사항 관리</h1>
      <form className={styles.form} onSubmit={submit}>
        <h2>{editing ? '공지 수정' : '공지 등록'}</h2>
        <label>
          제목
          <input
            maxLength={200}
            required
            value={form.title}
            onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
        </label>
        <label>
          내용
          <textarea
            maxLength={10000}
            required
            rows={8}
            value={form.content}
            onChange={(e) => setForm({ ...form, content: e.target.value })}
          />
        </label>
        <div className={styles.dates}>
          <label>
            게시 시작일
            <input
              type="date"
              required
              value={form.startAt}
              onChange={(e) => setForm({ ...form, startAt: e.target.value })}
            />
          </label>
          <label>
            게시 종료일
            <input
              type="date"
              required
              value={form.endAt}
              onChange={(e) => setForm({ ...form, endAt: e.target.value })}
            />
          </label>
        </div>
        {editing ? (
          <label>
            상태
            <select
              value={form.status}
              onChange={(e) =>
                setForm({ ...form, status: e.target.value as AnnouncementInput['status'] })
              }
            >
              <option value="ACTIVE">게시</option>
              <option value="CANCELLED">비활성</option>
            </select>
          </label>
        ) : null}
        {form.startAt > form.endAt ? (
          <p role="alert">종료일은 시작일보다 빠를 수 없습니다.</p>
        ) : null}
        {save.isError ? <p role="alert">저장하지 못했습니다. 입력 내용을 확인해 주세요.</p> : null}
        <div className={styles.actions}>
          {editing ? (
            <button
              type="button"
              onClick={() => {
                setEditing(null)
                setForm(EMPTY)
              }}
            >
              취소
            </button>
          ) : null}
          <button type="submit" disabled={save.isPending || form.startAt > form.endAt}>
            {save.isPending ? '저장 중...' : '저장'}
          </button>
        </div>
      </form>
      <label className={styles.filter}>
        상태 필터
        <select
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value as typeof statusFilter)
            setPage(0)
          }}
        >
          <option value="">전체</option>
          <option value="ACTIVE">게시</option>
          <option value="CANCELLED">비활성</option>
        </select>
      </label>
      {query.isPending ? <p role="status">불러오는 중...</p> : null}
      {query.isError ? <p role="alert">목록을 불러오지 못했습니다.</p> : null}
      <ul className={styles.list}>
        {query.data?.content.map((item) => (
          <li key={item.id}>
            <div>
              <strong>{item.title}</strong>
              <span>
                {item.startAt} ~ {item.endAt} · {item.status}
              </span>
            </div>
            <div className={styles.actions}>
              <button type="button" onClick={() => edit(item)}>
                수정
              </button>
              <button type="button" onClick={() => setDeleting(item)}>
                삭제
              </button>
            </div>
          </li>
        ))}
      </ul>
      {query.data && query.data.totalPages > 1 ? (
        <nav className={styles.paging} aria-label="관리자 공지사항 페이지">
          <button type="button" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>
            이전
          </button>
          <span>
            {page + 1} / {query.data.totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= query.data.totalPages}
            onClick={() => setPage((value) => value + 1)}
          >
            다음
          </button>
        </nav>
      ) : null}
      {remove.isError ? <p role="alert">삭제하지 못했습니다.</p> : null}
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
  )
}
