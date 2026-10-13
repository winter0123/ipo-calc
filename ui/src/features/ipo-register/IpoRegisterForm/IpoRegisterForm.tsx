import { useForm } from 'react-hook-form'
import { useIpoRegister } from '../useIpoRegister.ts'
import { type IpoRegisterRequest } from '@/entities/ipo/index.ts'
import styles from './IpoRegisterForm.module.css'
import { toErrorMessage } from '@/shared/api/apiError.ts'

export default function IpoRegisterForm() {
  'use no memo'
  const { mutate, isPending, error } = useIpoRegister()

  const {
    register,
    handleSubmit,
    formState: { errors },
    getValues,
  } = useForm<IpoRegisterRequest>({ mode: 'onSubmit' })

  const cleanNumber = (v: number | undefined) => (Number.isNaN(v) ? undefined : v)

  const onSubmit = (data: IpoRegisterRequest) => {
    // 빈 선택 입력은 undefined로 변환
    const submitData = {
      companyName: data.companyName.trim(),
      ipoName: data.ipoName.trim(),
      stockCode: data.stockCode?.trim().toUpperCase() || undefined,
      roundLabel: data.roundLabel || undefined,
      lowerBandPrice: cleanNumber(data.lowerBandPrice),
      upperBandPrice: cleanNumber(data.upperBandPrice),
      fixedPrice: cleanNumber(data.fixedPrice),
      subscriptionStartDate: data.subscriptionStartDate,
      subscriptionEndDate: data.subscriptionEndDate,
      listingDate: data.listingDate || undefined,
      offeringShareCount: cleanNumber(data.offeringShareCount),
    }
    mutate(submitData)
  }

  const formErrorMessage = toErrorMessage(error, {
    byStatus: {
      400: (e) => e.serverMessage,
      403: '관리자만 공모주를 등록할 수 있습니다.',
      409: (e) => e.serverMessage,
    },
    fallback: '공모주를 등록하지 못했습니다. 잠시 후 다시 시도해 주세요.',
  })

  // 가격·수량은 비워 두거나 0보다 큰 정수여야 한다. (확정가는 밴드 밖일 수 있어 밴드와 비교하지 않는다)
  const validatePositiveInt = (v: unknown) =>
    v === undefined ||
    v === null ||
    v === '' ||
    Number.isNaN(Number(v)) ||
    (Number.isInteger(Number(v)) && Number(v) > 0)
      ? undefined
      : '0보다 큰 정수여야 합니다.'

  const validateRequiredTrim = (message: string) => (v: unknown) =>
    (typeof v === 'string' && v.trim() !== '') || message

  const validateStockCode = (v: unknown) =>
    !v || /^[A-Za-z0-9]{6}$/.test(String(v).trim())
      ? undefined
      : '종목코드는 6자리 영문·숫자입니다.'

  const validateListingAfterEnd = (v: unknown) => {
    const end = getValues('subscriptionEndDate')
    if (!end || !v) return undefined
    return String(v) >= end ? undefined : '상장일은 청약 종료일보다 빠를 수 없습니다.'
  }

  const validateEndDateAfterStart = (v: unknown) => {
    const start = getValues('subscriptionStartDate')
    if (!start || !v) return undefined
    return v >= start ? undefined : '청약 종료일은 시작일보다 빠를 수 없습니다.'
  }

  const validateLowerBandNotGreater = (v: unknown) => {
    const lower = getValues('lowerBandPrice')
    if (
      lower === undefined ||
      lower === null ||
      Number.isNaN(lower) ||
      v === undefined ||
      v === null ||
      v === '' ||
      Number.isNaN(v)
    )
      return undefined
    return Number(lower) <= Number(v) ? undefined : '희망 공모가 하단은 상단보다 클 수 없습니다.'
  }

  return (
    <form
      className={styles.form}
      onSubmit={handleSubmit(onSubmit)}
      aria-label="공모주 등록 폼"
      noValidate
    >
      <div className={styles.field}>
        <label htmlFor="companyName">
          회사명 <span className={styles.required}>(필수)</span>
        </label>
        <input
          id="companyName"
          type="text"
          aria-invalid={!!errors.companyName}
          aria-describedby={errors.companyName ? 'companyName-error' : undefined}
          {...register('companyName', {
            required: '회사명을 입력해 주세요.',
            validate: validateRequiredTrim('회사명을 입력해 주세요.'),
            maxLength: { value: 255, message: '회사명은 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.companyName && (
          <span id="companyName-error" className={styles.error} aria-live="polite">
            {String(errors.companyName.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="ipoName">
          공모주명 <span className={styles.required}>(필수)</span>
        </label>
        <input
          id="ipoName"
          type="text"
          aria-invalid={!!errors.ipoName}
          aria-describedby={errors.ipoName ? 'ipoName-error' : undefined}
          {...register('ipoName', {
            required: '공모주명을 입력해 주세요.',
            validate: validateRequiredTrim('공모주명을 입력해 주세요.'),
            maxLength: { value: 255, message: '공모주명은 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.ipoName && (
          <span id="ipoName-error" className={styles.error} aria-live="polite">
            {String(errors.ipoName.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="stockCode">종목코드</label>
        <input
          id="stockCode"
          type="text"
          aria-invalid={!!errors.stockCode}
          aria-describedby={errors.stockCode ? 'stockCode-error' : undefined}
          {...register('stockCode', {
            validate: validateStockCode,
            maxLength: { value: 255, message: '종목코드는 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.stockCode && (
          <span id="stockCode-error" className={styles.error} aria-live="polite">
            {String(errors.stockCode.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="roundLabel">회차</label>
        <input
          id="roundLabel"
          type="text"
          aria-invalid={!!errors.roundLabel}
          aria-describedby={errors.roundLabel ? 'roundLabel-error' : undefined}
          {...register('roundLabel', {
            maxLength: { value: 255, message: '회차는 255자 이하로 입력해 주세요.' },
          })}
          disabled={isPending}
        />
        {errors.roundLabel && (
          <span id="roundLabel-error" className={styles.error} aria-live="polite">
            {String(errors.roundLabel.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="lowerBandPrice">희망 공모가 하단</label>
        <input
          id="lowerBandPrice"
          type="number"
          min="1"
          step="1"
          aria-invalid={!!errors.lowerBandPrice}
          aria-describedby={errors.lowerBandPrice ? 'lowerBandPrice-error' : undefined}
          {...register('lowerBandPrice', {
            valueAsNumber: true,
            validate: validatePositiveInt,
          })}
          disabled={isPending}
        />
        {errors.lowerBandPrice && (
          <span id="lowerBandPrice-error" className={styles.error} aria-live="polite">
            {String(errors.lowerBandPrice.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="upperBandPrice">희망 공모가 상단</label>
        <input
          id="upperBandPrice"
          type="number"
          min="1"
          step="1"
          aria-invalid={!!errors.upperBandPrice}
          aria-describedby={errors.upperBandPrice ? 'upperBandPrice-error' : undefined}
          {...register('upperBandPrice', {
            valueAsNumber: true,
            validate: (v) => validatePositiveInt(v) ?? validateLowerBandNotGreater(v),
          })}
          disabled={isPending}
        />
        {errors.upperBandPrice && (
          <span id="upperBandPrice-error" className={styles.error} aria-live="polite">
            {String(errors.upperBandPrice.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="fixedPrice">확정 공모가</label>
        <input
          id="fixedPrice"
          type="number"
          min="1"
          step="1"
          aria-invalid={!!errors.fixedPrice}
          aria-describedby={errors.fixedPrice ? 'fixedPrice-error' : undefined}
          {...register('fixedPrice', {
            valueAsNumber: true,
            validate: validatePositiveInt,
          })}
          disabled={isPending}
        />
        {errors.fixedPrice && (
          <span id="fixedPrice-error" className={styles.error} aria-live="polite">
            {String(errors.fixedPrice.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="subscriptionStartDate">
          청약 시작일 <span className={styles.required}>(필수)</span>
        </label>
        <input
          id="subscriptionStartDate"
          type="date"
          aria-invalid={!!errors.subscriptionStartDate}
          aria-describedby={
            errors.subscriptionStartDate ? 'subscriptionStartDate-error' : undefined
          }
          {...register('subscriptionStartDate', {
            required: '청약 시작일을 선택해 주세요.',
          })}
          disabled={isPending}
        />
        {errors.subscriptionStartDate && (
          <span id="subscriptionStartDate-error" className={styles.error} aria-live="polite">
            {String(errors.subscriptionStartDate.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="subscriptionEndDate">
          청약 종료일 <span className={styles.required}>(필수)</span>
        </label>
        <input
          id="subscriptionEndDate"
          type="date"
          aria-invalid={!!errors.subscriptionEndDate}
          aria-describedby={errors.subscriptionEndDate ? 'subscriptionEndDate-error' : undefined}
          {...register('subscriptionEndDate', {
            required: '청약 종료일을 선택해 주세요.',
            validate: validateEndDateAfterStart,
          })}
          disabled={isPending}
        />
        {errors.subscriptionEndDate && (
          <span id="subscriptionEndDate-error" className={styles.error} aria-live="polite">
            {String(errors.subscriptionEndDate.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="listingDate">상장일</label>
        <input
          id="listingDate"
          type="date"
          aria-invalid={!!errors.listingDate}
          aria-describedby={errors.listingDate ? 'listingDate-error' : undefined}
          {...register('listingDate', { validate: validateListingAfterEnd })}
          disabled={isPending}
        />
        {errors.listingDate && (
          <span id="listingDate-error" className={styles.error} aria-live="polite">
            {String(errors.listingDate.message)}
          </span>
        )}
      </div>

      <div className={styles.field}>
        <label htmlFor="offeringShareCount">공모 주식 수</label>
        <input
          id="offeringShareCount"
          type="number"
          min="1"
          step="1"
          aria-invalid={!!errors.offeringShareCount}
          aria-describedby={errors.offeringShareCount ? 'offeringShareCount-error' : undefined}
          {...register('offeringShareCount', {
            valueAsNumber: true,
            validate: validatePositiveInt,
          })}
          disabled={isPending}
        />
        {errors.offeringShareCount && (
          <span id="offeringShareCount-error" className={styles.error} aria-live="polite">
            {String(errors.offeringShareCount.message)}
          </span>
        )}
      </div>

      {formErrorMessage && (
        <div className={styles.formError} role="alert">
          {formErrorMessage}
        </div>
      )}

      <button type="submit" disabled={isPending} className={styles.submitButton}>
        {isPending ? '등록 중...' : '공모주 등록'}
      </button>
    </form>
  )
}
