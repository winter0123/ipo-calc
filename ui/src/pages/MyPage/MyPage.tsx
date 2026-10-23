import { useCurrentAccount } from '@/entities/current-account/index.ts'
import { useInvestors } from '@/entities/investor/index.ts'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import styles from './MyPage.module.css'

const RELATIONSHIP_LABELS: Record<string, string> = {
  SELF: '본인',
  PARTNER: '연인',
  SPOUSE: '연인',
  FRIEND: '친구',
  PARENT: '부모',
  CHILD: '자녀',
  OTHER: '기타',
}

export default function MyPage() {
  const accountQuery = useCurrentAccount()
  const investorsQuery = useInvestors()

  if (accountQuery.isLoading || investorsQuery.isLoading) {
    return <p role="status">회원 정보를 불러오는 중...</p>
  }

  if (accountQuery.error || investorsQuery.error || !accountQuery.data) {
    return (
      <div className={styles.container}>
        <h1>마이페이지</h1>
        <p role="alert">
          {toErrorMessage(accountQuery.error ?? investorsQuery.error, {
            fallback: '회원 정보를 불러오지 못했습니다. 다시 시도해 주세요.',
          })}
        </p>
      </div>
    )
  }

  const selfInvestor = investorsQuery.data?.find(
    (investor) =>
      investor.accountId === accountQuery.data.accountId && investor.relationship === 'SELF',
  )

  return (
    <div className={styles.container}>
      <h1>마이페이지</h1>
      <p className={styles.description}>회원가입 때 입력한 정보를 확인할 수 있습니다.</p>
      <dl className={styles.profile}>
        <div>
          <dt>이메일</dt>
          <dd>{accountQuery.data.email}</dd>
        </div>
        <div>
          <dt>닉네임</dt>
          <dd>{accountQuery.data.nickname}</dd>
        </div>
        <div>
          <dt>이름</dt>
          <dd>{accountQuery.data.name || '등록된 이름이 없습니다.'}</dd>
        </div>
        <div>
          <dt>투자자 표시 이름</dt>
          <dd>{selfInvestor?.displayName ?? '가입 당시 본인 투자자 정보가 없습니다.'}</dd>
        </div>
        <div>
          <dt>관계</dt>
          <dd>
            {selfInvestor?.relationship
              ? (RELATIONSHIP_LABELS[selfInvestor.relationship] ?? selfInvestor.relationship)
              : '가입 당시 본인 투자자 정보가 없습니다.'}
          </dd>
        </div>
      </dl>
      <p className={styles.note}>비밀번호는 보안을 위해 표시하지 않습니다.</p>
    </div>
  )
}
