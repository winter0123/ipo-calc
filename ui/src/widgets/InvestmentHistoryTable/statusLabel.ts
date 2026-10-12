const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '진행 중',
  INACTIVE: '비활성',
  CANCELLED: '취소됨',
}

// 백엔드 RecordStatus enum을 한글로 바꾼다. 모르는 값은 그대로 보여 준다.
export function statusLabel(status: string): string {
  return STATUS_LABELS[status] ?? status
}
