import { useWatch } from 'react-hook-form'
import styles, { StyleScope } from './SaleRegisterForm.styles.ts'
import type { CapturedField, SaleForm } from './useSaleForm.ts'

/** 캡처에서 채운 값을 사용자가 고치지 않았을 때만 보이는 표시. */
export default function CapturedBadge({ f, name }: { f: SaleForm; name: CapturedField }) {
  'use no memo'
  const current = useWatch({ control: f.control, name })
  const captured = f.captured[name]
  if (captured === undefined || captured !== current) return null
  return <StyleScope>{<span className={styles.capturedBadge}>캡처에서 채움</span>}</StyleScope>
}
