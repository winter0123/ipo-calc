import { Link } from 'react-router-dom'
import { ASSURANCES, EXTRAS, SHOWCASES } from './content.ts'
import styles, { StyleScope } from './LandingPage.styles.ts'

// 로그인 여부는 호출하는 쪽이 넘긴다. 여기서 계정 조회를 구독하면 RootRedirect의 로딩 분기와 맞물려 무한 재조회가 된다.
export default function LandingPage({ loggedIn = false }: { loggedIn?: boolean }) {
  const primaryCta = loggedIn
    ? { to: '/dashboard', label: '대시보드로' }
    : { to: '/signup', label: '무료로 시작하기' }

  const actions = (
    <div className={styles.actions}>
      <Link to={primaryCta.to} className={styles.primary}>
        {primaryCta.label}
      </Link>
      <Link to="/ipo-calendar" className={styles.secondary}>
        공모주 일정 보기
      </Link>
    </div>
  )

  return (
    <StyleScope>
      {
        <div className={styles.page}>
          <section className={styles.hero} aria-labelledby="landing-title">
            <div className={`${styles.inner} ${styles.heroGrid}`}>
              <div className={styles.heroText}>
                <h1 id="landing-title">공모주 수익, 수수료와 세금까지 빼고 계산합니다</h1>
                <p>
                  청약·배정·매도를 기록하면 건별, 기간별 수익과 수익률이 나옵니다. 일정과 수요예측,
                  락업 정보는 로그인 없이 볼 수 있습니다.
                </p>
                {actions}
              </div>
              <img
                className={styles.shot}
                src="/landing/hero-upcoming.png"
                alt="대시보드의 다가오는 일정: 청약 시작·마감, 환불 예정일이 날짜순으로 나열되고 종목 링크가 걸려 있다"
                width={580}
                height={370}
              />
            </div>
          </section>

          {SHOWCASES.map(({ title, description, example, image }, index) => (
            <section
              key={title}
              className={index % 2 === 0 ? styles.band : `${styles.band} ${styles.bandGray}`}
              aria-labelledby={`landing-feature-${index}`}
            >
              <div
                className={
                  index % 2 === 0
                    ? `${styles.inner} ${styles.showcase}`
                    : `${styles.inner} ${styles.showcase} ${styles.flip}`
                }
              >
                <div className={styles.showcaseText}>
                  <h2 id={`landing-feature-${index}`}>{title}</h2>
                  <p>{description}</p>
                  <p className={styles.example}>{example}</p>
                </div>
                <img
                  className={styles.shot}
                  src={image.src}
                  alt={image.alt}
                  width={image.width}
                  height={image.height}
                  loading="lazy"
                />
              </div>
            </section>
          ))}

          <section className={styles.band} aria-labelledby="landing-extras">
            <div className={`${styles.inner} ${styles.twoCol}`}>
              <h2 id="landing-extras">그 밖에 쓰는 기능</h2>
              <dl className={styles.list}>
                {EXTRAS.map(({ term, text }) => (
                  <div key={term}>
                    <dt>{term}</dt>
                    <dd>{text}</dd>
                  </div>
                ))}
              </dl>
            </div>
          </section>

          <section
            className={`${styles.band} ${styles.bandGray}`}
            aria-labelledby="landing-assurance"
          >
            <div className={`${styles.inner} ${styles.twoCol}`}>
              <h2 id="landing-assurance">알아 두실 점</h2>
              <ul className={styles.notes}>
                {ASSURANCES.map((text) => (
                  <li key={text}>{text}</li>
                ))}
              </ul>
            </div>
          </section>

          <section className={styles.band} aria-labelledby="landing-cta">
            <div className={`${styles.inner} ${styles.closing}`}>
              <h2 id="landing-cta">둘러보는 데 로그인은 필요 없습니다.</h2>
              {actions}
            </div>
          </section>
        </div>
      }
    </StyleScope>
  )
}
