export function formatHyphen(value: string): string {
  const digits = value.replace(/\D/g, '')
  return digits.replace(/(\d{4})(?=\d)/g, '$1-')
}
