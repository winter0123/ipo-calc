export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

export function isMinLength(value: string, min: number): boolean {
  return value.length >= min
}

export function isValidDisplayName(value: string): boolean {
  // 1-50자, 한글/영문/특수문자 허용
  if (value.length < 1 || value.length > 50) return false
  return true // 모든 문자(한글, 영문, 특수문자) 허용
}

// 서버 PasswordPolicy와 같은 규칙: 8자 이상, UTF-8 72바이트 이하, 영문·숫자·특수문자 각 1개 이상
export function passwordProblem(value: string): string | null {
  if (new TextEncoder().encode(value).length > 72) {
    return '비밀번호는 72바이트(영문 기준 72자)를 넘을 수 없습니다.'
  }
  const missing: string[] = []
  if (value.length < 8) missing.push('8자 이상')
  if (!/[A-Za-z]/.test(value)) missing.push('영문')
  if (!/\d/.test(value)) missing.push('숫자')
  if (!/[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(value)) {
    missing.push('특수문자(!@#$%^&*()_+-=[]{};\':"\\|,.<>/?)')
  }
  return missing.length === 0 ? null : `비밀번호에 ${missing.join(', ')}이(가) 필요합니다.`
}
