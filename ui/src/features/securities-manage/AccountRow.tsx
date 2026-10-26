import { Link } from 'react-router-dom'
import { useBrokerCredential } from '@/entities/broker/index.ts'
import type { SecuritiesAccount } from '@/entities/securities-account/index.ts'
import Button from '@/shared/components/Button/Button.tsx'
import { toErrorMessage } from '@/shared/api/apiError.ts'
import { useRevealAccountNumber } from './api.ts'
import styles, { StyleScope } from './SecuritiesManage.styles.ts'

interface Props {
  account: SecuritiesAccount
  busy: boolean
  onEdit: () => void
  onDelete: () => void
  onSetDefault: () => void
}

export default function AccountRow({ account, busy, onEdit, onDelete, onSetDefault }: Props) {
  const reveal = useRevealAccountNumber()
  // 자동 가져오기 지원 여부는 계좌의 연동 정보(supportedProvider)로만 알 수 있다.
  const { data: credential } = useBrokerCredential(account.id)
  const label = account.accountName ?? '계좌'
  const revealError = toErrorMessage(reveal.error)

  return (
    <StyleScope>
      {
        <li className={styles.account}>
          <div className={styles.accountMain}>
            <span className={styles.number}>{reveal.data ?? account.accountNumber}</span>
            {account.accountName && <span className={styles.alias}>{account.accountName}</span>}
            {account.isDefault && <span className={styles.badge}>기본 계좌</span>}
            {credential?.supportedProvider && (
              <Link to="/broker-sync" className={styles.badgeLink}>
                자동 가져오기
              </Link>
            )}
          </div>
          <div className={styles.accountActions}>
            {reveal.data ? (
              <Button variant="secondary" onClick={() => reveal.reset()}>
                숨기기
              </Button>
            ) : (
              <Button
                variant="secondary"
                onClick={() => reveal.mutate(account.id)}
                disabled={reveal.isPending}
                aria-label={`${label} 계좌번호 보기`}
              >
                보기
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={onEdit}
              disabled={busy}
              aria-label={`${label} 수정`}
            >
              수정
            </Button>
            {!account.isDefault && (
              <Button
                variant="secondary"
                onClick={onSetDefault}
                disabled={busy}
                aria-label={`${label} 기본으로 지정`}
              >
                기본으로
              </Button>
            )}
            <Button
              variant="secondary"
              onClick={onDelete}
              disabled={busy}
              aria-label={`${label} 삭제`}
            >
              삭제
            </Button>
          </div>
          {revealError && (
            <div className={styles.formError} role="alert">
              {revealError}
            </div>
          )}
        </li>
      }
    </StyleScope>
  )
}
