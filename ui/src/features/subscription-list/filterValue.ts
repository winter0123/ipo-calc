export type SubscriptionFilterValue = 'all' | 'unallocated' | 'allocated'

export const FILTERS: { value: SubscriptionFilterValue; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'unallocated', label: '미배정' },
  { value: 'allocated', label: '배정완료' },
]

export function parseSubscriptionFilter(raw: string | null): SubscriptionFilterValue {
  return FILTERS.find((filter) => filter.value === raw)?.value ?? 'all'
}
