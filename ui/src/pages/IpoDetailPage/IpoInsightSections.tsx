import type { ReactNode } from 'react'
import { useIpoInsight } from '@/entities/ipo/index.ts'
import type { IpoInsight } from '@/entities/ipo/index.ts'
import { expectedUnlockDate } from './lockUpDate.ts'
import styles from './IpoDetailPage.module.css'

const num = (v: number) => v.toLocaleString('ko-KR', { maximumFractionDigits: 2 })
const ratio = (v: number | null) => (v === null ? null : `${num(v)}:1`)
const percent = (v: number | null) => (v === null ? null : `${num(v)}%`)
const shares = (v: number | null) => (v === null ? null : `${num(v)}주`)
const eok = (v: number) => (v / 100_000_000).toLocaleString('ko-KR', { maximumFractionDigits: 1 })
const capital = (v: number | null) =>
  v === null ? null : v >= 100_000_000 ? `${eok(v)}억원` : `${num(v / 1_000_000)}백만원`

type Row = [label: string, value: ReactNode]

function Section({ title, rows }: { title: string; rows: Row[] }) {
  const shown = rows.filter(([, value]) => value !== null && value !== undefined && value !== '')
  if (shown.length === 0) return null
  return (
    <section className={styles.insightSection} aria-label={title}>
      <h2 className={styles.insightTitle}>{title}</h2>
      <dl className={styles.detailGrid}>
        {shown.map(([label, value]) => (
          <div key={label} className={styles.detailItem}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function Table({
  title,
  label,
  head,
  children,
}: {
  title: string
  label: string
  head: string[]
  children: ReactNode
}) {
  return (
    <section className={styles.insightSection} aria-label={title}>
      <h2 className={styles.insightTitle}>{title}</h2>
      <div className={styles.tableWrap} role="region" aria-label={label} tabIndex={0}>
        <table className={styles.insightTable}>
          <thead>
            <tr>
              {head.map((h, i) => (
                <th key={`${h}-${i}`} scope="col">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>{children}</tbody>
        </table>
      </div>
    </section>
  )
}

function FloatSchedule({
  rows,
  listingDate,
}: {
  rows: IpoInsight['floatSchedule']
  listingDate: string | null
}) {
  if (rows.length === 0) return null
  return (
    <Table
      title="유통가능 물량(락업 해제) 일정"
      label="유통가능 물량 표"
      head={['시점', '예상 해제일', '누적 유통가능 주식수', '비율']}
    >
      {rows.map((r, i) => (
        <tr key={`${r.label}-${i}`}>
          <th scope="row">{r.label}</th>
          <td>{expectedUnlockDate(listingDate, r.label) ?? ''}</td>
          <td>{shares(r.shares) ?? '-'}</td>
          <td>{percent(r.ratio) ?? '-'}</td>
        </tr>
      ))}
    </Table>
  )
}

function Financials({ rows }: { rows: IpoInsight['financials'] }) {
  if (rows.length === 0) return null
  const lines: [string, 'revenue' | 'operatingProfit' | 'netIncome'][] = [
    ['매출액', 'revenue'],
    ['영업이익', 'operatingProfit'],
    ['순이익', 'netIncome'],
  ]
  return (
    <Table
      title="손익계산서"
      label="손익계산서 표(억원)"
      head={['(억원)', ...rows.map((r) => r.period)]}
    >
      {lines.map(([name, key]) => (
        <tr key={key}>
          <th scope="row">{name}</th>
          {rows.map((r, i) => {
            const v = r[key]
            return (
              <td key={i} className={v !== null && v < 0 ? styles.negative : undefined}>
                {v === null ? '-' : eok(v)}
              </td>
            )
          })}
        </tr>
      ))}
    </Table>
  )
}

function Underwriters({ rows }: { rows: IpoInsight['underwriters'] }) {
  if (rows.length === 0) return null
  return (
    <Table title="인수단" label="인수단 표" head={['증권사', '역할', '인수 수량']}>
      {rows.map((u, i) => (
        <tr key={`${u.name}-${i}`}>
          <th scope="row">{u.name}</th>
          <td>{u.role ?? '-'}</td>
          <td>{shares(u.shares) ?? '-'}</td>
        </tr>
      ))}
    </Table>
  )
}

/** 공모주 상세 아래의 공시 정보(DART). 값이 없는 항목·구역은 보이지 않는다. */
export default function IpoInsightSections({
  ipoId,
  listingDate = null,
}: {
  ipoId: number
  listingDate?: string | null
}) {
  const { data, isLoading, isError } = useIpoInsight(ipoId)
  if (isLoading) return <p className={styles.insightNote}>공시 정보를 불러오는 중...</p>
  if (isError || !data) {
    return <p className={styles.insightNote}>공시 정보를 지금 불러올 수 없습니다.</p>
  }
  if (data.syncedAt === null) {
    return <p className={styles.insightNote}>아직 공시 정보를 가져오지 않았습니다.</p>
  }
  const shareholder = data.largestShareholderName
    ? `${data.largestShareholderName}${data.largestShareholderRatio === null ? '' : ` (${percent(data.largestShareholderRatio)})`}`
    : null
  return (
    <>
      <Section
        title="수요예측"
        rows={[
          ['기관 경쟁률', ratio(data.institutionalCompetitionRate)],
          ['의무보유확약 비율', percent(data.lockUpCommitmentRatio)],
        ]}
      />
      <FloatSchedule rows={data.floatSchedule} listingDate={listingDate} />
      <Section
        title="기업정보"
        rows={[
          ['대표이사', data.ceoName],
          ['설립일', data.establishedOn],
          ['주소', data.address],
          ['최대주주', shareholder],
          ['자본금', capital(data.capitalStock)],
          ['액면가', data.parValue === null ? null : `${num(data.parValue)}원`],
        ]}
      />
      <Financials rows={data.financials} />
      <Underwriters rows={data.underwriters} />
      <p className={styles.insightNote}>
        출처: 금융감독원 DART {data.sourceReportName ?? '공시'} · {data.syncedAt.slice(0, 10)} 기준{' '}
        {data.sourceUrl && (
          <a href={data.sourceUrl} target="_blank" rel="noopener noreferrer">
            공시 원문 보기
          </a>
        )}
      </p>
    </>
  )
}
