import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAnnouncements } from '@/entities/announcement/index.ts'
import styles from './AnnouncementsPage.module.css'

export default function AnnouncementsPage() {
  const [page, setPage] = useState(0)
  const query = useQuery({
    queryKey: ['announcements', page],
    queryFn: ({ signal }) => fetchAnnouncements(page, signal),
  })
  return (
    <section className={styles.page}>
      <h1>공지사항</h1>
      {query.isPending ? <p role="status">불러오는 중...</p> : null}
      {query.isError ? <p role="alert">공지사항을 불러오지 못했습니다.</p> : null}
      {query.data?.content.length === 0 ? <p>등록된 공지사항이 없습니다.</p> : null}
      <ul className={styles.list}>
        {query.data?.content.map((item) => (
          <li key={item.id}>
            <Link to={`/announcements/${item.id}`}>
              <strong>{item.title}</strong>
              <span>
                {item.startAt} ~ {item.endAt}
              </span>
            </Link>
          </li>
        ))}
      </ul>
      {query.data && query.data.totalPages > 1 ? (
        <nav className={styles.paging} aria-label="공지사항 페이지">
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
    </section>
  )
}
