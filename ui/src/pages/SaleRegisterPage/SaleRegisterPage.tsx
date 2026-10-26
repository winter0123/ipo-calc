import styles, { StyleScope } from './SaleRegisterPage.styles.ts'
import { useCurrentInvestor } from '@/entities/investor/index.ts'
import { SaleRegisterForm } from '@/features/sale-register/index.ts'

export default function SaleRegisterPage() {
  const { current } = useCurrentInvestor()
  return (
    <StyleScope>
      {
        <div className={styles.container}>
          <h1 className={styles.title}>매도 등록</h1>
          {/* 투자자를 바꾸면 선택했던 청약·입력값을 비우려고 폼을 새로 만든다 */}
          <SaleRegisterForm key={current?.id} />
        </div>
      }
    </StyleScope>
  )
}
