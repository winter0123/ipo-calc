import type { ReactNode } from 'react'
import { useIpoInsight } from '@/entities/ipo/index.ts'
import type { IpoInsight } from '@/entities/ipo/index.ts'
import styles from './IpoDetailPage.module.css'

const num = (v: number) => v.toLocaleString('ko-KR', { maximumFractionDigits: 2 })
const ratio = (v: number | null) => (v === null ? null : `${num(v)}:1`)
const percent = (v: number | null) => (v === null ? null : `${num(v)}%`)
const shares = (v: number | null) => (v === null ? null : `${num(v)}주`)
const eok = (v: number | null) =>
  v === null
    ? null
    : `${(v / 100_000_000).toLocaleString('ko-KR', { maximumFractionDigits: 1 })}억원`
const range = (a: string | null, b: string | null) => (a && b ? `${a} ~ ${b}` : (a ?? b))

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

function Brokers({ brokers }: { brokers: IpoInsight['brokers'] }) {
  if (brokers.length === 0) return null
  return (
    <section className={styles.insightSection} aria-label="증권사별 청약">
      <h2 className={styles.insightTitle}>증권사별 청약</h2>
      <div className={styles.tableWrap} role="region" aria-label="증권사별 청약 표" tabIndex={0}>
        <table className={styles.insightTable}>
          <thead>
            <tr>
              <th scope="col">증권사</th>
              <th scope="col">일반청약 물량</th>
              <th scope="col">균등 1인당(예상)</th>
              <th scope="col">비례 경쟁률</th>
            </tr>
          </thead>
          <tbody>
            {brokers.map((b, i) => (
              <tr key={`${b.name}-${i}`}>
                <th scope="row">{b.name ?? '-'}</th>
                <td>{shares(b.shares) ?? '-'}</td>
                <td>
                  {b.equalSharesPerPerson === null ? '-' : `${num(b.equalSharesPerPerson)}주`}
                </td>
                <td>{ratio(b.proportionalCompetitionRate) ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className={styles.insightNote}>균등·비례 값은 청약 마감 뒤에 채워집니다.</p>
    </section>
  )
}

/** 공모주 상세 아래의 부가 정보. 출처에서 못 받은 항목·구역은 보이지 않는다. */
export default function IpoInsightSections({ ipoId }: { ipoId: number }) {
  const { data, isLoading, isError } = useIpoInsight(ipoId)
  if (isLoading) return <p className={styles.insightNote}>추가 정보를 불러오는 중...</p>
  if (isError || !data) {
    return (
      <p className={styles.insightNote}>추가 정보(수요예측·기업정보)를 지금 불러올 수 없습니다.</p>
    )
  }
  const protectedFloat =
    data.protectedShareRatio !== null || data.floatShareRatio !== null
      ? [percent(data.protectedShareRatio), percent(data.floatShareRatio)]
          .map((v) => v ?? '-')
          .join(' / ')
      : null
  return (
    <>
      <Section
        title="수요예측·공모"
        rows={[
          ['수요예측 기간', range(data.demandForecastStartDate, data.demandForecastEndDate)],
          ['기관 경쟁률', ratio(data.institutionalCompetitionRate)],
          ['의무보유확약 비율', percent(data.lockUpCommitmentRatio)],
          ['보호예수 / 유통가능 물량', protectedFloat],
          ['상장 예정 주식 수', shares(data.listedShares)],
          ['공모가 기준 시가총액', eok(data.marketCapAtOfferPrice)],
        ]}
      />
      <Brokers brokers={data.brokers} />
      <Section
        title="기업정보"
        rows={[
          ['소개', data.description],
          ['업종', data.keywords.length > 0 ? data.keywords.join(', ') : null],
          ['대표이사', data.ceoName],
          ['설립일', data.establishedOn],
          ['주소', data.address],
        ]}
      />
      <Section
        title="최근 실적(최근 사업연도)"
        rows={[
          ['매출액', eok(data.recentSales)],
          ['순이익', eok(data.recentNetProfit)],
        ]}
      />
      <p className={styles.insightNote}>
        출처: 증권플러스 비상장·금융감독원 DART.{' '}
        {data.registrationStatementUrl && (
          <a href={data.registrationStatementUrl} target="_blank" rel="noopener noreferrer">
            증권신고서 보기
          </a>
        )}{' '}
        락업 해제 일정·연도별 실적은 증권신고서에서 확인하세요.
      </p>
    </>
  )
}
