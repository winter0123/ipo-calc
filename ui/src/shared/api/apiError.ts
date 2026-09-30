export class ApiError extends Error {
  readonly status: number
  readonly serverMessage?: string

  constructor(status: number, message: string, serverMessage?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.serverMessage = serverMessage
  }
}

export const NETWORK_ERROR_MESSAGE = '네트워크 연결을 확인해 주세요.'
const INVALID_INPUT_MESSAGE = '입력값을 확인해 주세요.'

type StatusRule = string | ((error: ApiError) => string | undefined)

interface ErrorMessageOptions {
  byStatus?: Partial<Record<number, StatusRule>>
  fallback?: string
}

export function toErrorMessage(
  error: unknown,
  { byStatus, fallback }: ErrorMessageOptions = {},
): string | null {
  if (!error) return null
  if (!(error instanceof ApiError)) return NETWORK_ERROR_MESSAGE

  const rule = byStatus?.[error.status]
  const matched = typeof rule === 'function' ? rule(error) : rule
  if (matched !== undefined) return matched

  if (fallback !== undefined) return fallback
  if (error.status === 400) return error.serverMessage ?? INVALID_INPUT_MESSAGE
  return error.message
}
