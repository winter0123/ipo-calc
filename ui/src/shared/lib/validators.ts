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
