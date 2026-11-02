import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { useAllocationRegister } from '@/features/allocation-register/index.ts'
import { SubscriptionRegisterForm } from '@/features/subscription-register/index.ts'
import styles, { StyleScope } from './SubscriptionRegisterPage.styles.ts'

export default function SubscriptionRegisterPage() {
  const { current } = useCurrentInvestor()
  const allocationRegistration = useAllocationRegister()
  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>청약 등록</h1>
          {/* 투자자를 바꾸면 입력값을 비우려고 폼을 새로 만든다 */}
          <SubscriptionRegisterForm
            key={current?.id}
            registerAllocation={allocationRegistration.mutateAsync}
            allocationError={allocationRegistration.error}
            allocationPending={allocationRegistration.isPending}
          />
        </div>
      }
    </StyleScope>
  )
}
