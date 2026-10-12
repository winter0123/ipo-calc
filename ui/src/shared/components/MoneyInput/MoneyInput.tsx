import { useState, type InputHTMLAttributes } from 'react'
import { useController, useWatch, type Control, type FieldValues, type Path } from 'react-hook-form'

interface MoneyInputProps<T extends FieldValues> extends Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'name' | 'value' | 'defaultValue' | 'onChange' | 'type' | 'inputMode'
> {
  control: Control<T>
  name: Path<T>
  requiredMessage?: string
  validate?: (value: number) => true | string
  /** 실현손익처럼 음수가 가능한 칸 */
  allowNegative?: boolean
  /** 값이 사용자 입력으로 바뀐 직후(폼 값이 이미 갱신된 뒤) 호출된다 */
  onValueChange?: () => void
}

const format = (value: unknown) =>
  typeof value === 'number' && !Number.isNaN(value) ? value.toLocaleString('ko-KR') : ''

/** 표시에는 천 단위 쉼표를 넣고, 폼 값은 숫자(비면 NaN)로 유지하는 입력칸. */
export default function MoneyInput<T extends FieldValues>({
  control,
  name,
  requiredMessage,
  validate,
  allowNegative = false,
  onValueChange,
  ...inputProps
}: MoneyInputProps<T>) {
  'use no memo' // RHF의 field.ref를 렌더 중 읽는 구조라 컴파일러 메모이제이션과 맞지 않는다
  const { field } = useController({
    control,
    name,
    // 비어 있으면(NaN) required 메시지를 직접 돌려준다. RHF의 required는 NaN을 빈 값으로 보지 않는다.
    rules: {
      validate: (v) =>
        requiredMessage && Number.isNaN(v)
          ? requiredMessage
          : validate
            ? validate(v as number)
            : true,
    },
  })
  // 숫자가 아직 되지 않은 입력("-")을 보여 주려고 사용자가 친 글자를 따로 들고 있는다.
  const [typed, setTyped] = useState<string | null>(null)
  const numeric = useWatch({ control, name }) as number
  const shown =
    typed !== null && (typed === '' || typed === '-') && Number.isNaN(numeric)
      ? typed
      : format(numeric)

  return (
    <input
      {...inputProps}
      type="text"
      inputMode="numeric"
      autoComplete="off"
      /* eslint-disable react-hooks/refs -- field.ref는 RHF의 콜백 ref이며 렌더 중에 .current를 읽지 않는다 */
      name={field.name}
      ref={field.ref}
      onBlur={field.onBlur}
      /* eslint-enable react-hooks/refs */
      value={shown}
      onChange={(e) => {
        const negative = allowNegative && e.target.value.trim().startsWith('-')
        const digits = e.target.value.replace(/\D/g, '')
        if (digits === '') {
          setTyped(negative ? '-' : '')
          field.onChange(Number.NaN)
        } else {
          setTyped(null)
          field.onChange(Number(digits) * (negative ? -1 : 1))
        }
        onValueChange?.()
      }}
    />
  )
}
