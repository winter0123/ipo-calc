/** `2026-10-12(T...)` → `2026. 10. 12.` (다른 화면과 같은 ko-KR 형식) */
export function formatDay(iso: string): string {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('ko-KR')
}
