import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { announcementKeys, fetchAnnouncement } from '@/entities/announcement/index.ts'
import styles, { StyleScope } from './AnnouncementDetailPage.styles.ts'

export default function AnnouncementDetailPage() {
  const id = Number(useParams().announcementId)
  const query = useQuery({
    queryKey: announcementKeys.detail(id),
    queryFn: ({ signal }) => fetchAnnouncement(id, signal),
    enabled: Number.isInteger(id) && id > 0,
  })
  return (
    <StyleScope>
      {
        <article className={styles.page}>
          <Link to="/announcements">← 공지사항 목록</Link>
          {query.isPending ? <p role="status">불러오는 중...</p> : null}
          {query.isError ? <p role="alert">공지사항을 찾을 수 없습니다.</p> : null}
          {query.data ? (
            <>
              <h1>{query.data.title}</h1>
              <p className={styles.period}>
                게시 기간: {query.data.startAt} ~ {query.data.endAt}
              </p>
              <div className={styles.content}>{query.data.content}</div>
            </>
          ) : null}
        </article>
      }
    </StyleScope>
  )
}
