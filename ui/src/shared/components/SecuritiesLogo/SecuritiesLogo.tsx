import { getSecuritiesLogoUrl, getSecuritiesLogoAlt } from '@/shared/lib/securitiesLogo.ts'
import styles, { StyleScope } from './SecuritiesLogo.styles.ts'

interface SecuritiesLogoProps {
  firmName: string
  size?: number
  className?: string
}

export default function SecuritiesLogo({ firmName, size = 32, className = '' }: SecuritiesLogoProps) {
  const src = getSecuritiesLogoUrl(firmName)
  const alt = getSecuritiesLogoAlt(firmName)

  return (
    <StyleScope>
      <img
        src={src}
        alt={alt}
        width={size}
        height={size}
        style={{ width: size, height: size, display: 'inline-block' }}
        className={`${styles.logo} ${className}`}
        loading="lazy"
      />
    </StyleScope>
  )
}