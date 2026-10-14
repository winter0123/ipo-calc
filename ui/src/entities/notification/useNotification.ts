import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchPreferences, fetchVapidKey, savePreferences } from './api-client.ts'
import { notificationKeys } from './queryKeys.ts'

export function useVapidKey() {
  return useQuery({
    queryKey: notificationKeys.vapidKey(),
    queryFn: ({ signal }) => fetchVapidKey(signal),
    staleTime: 60 * 60 * 1000,
  })
}

export function usePreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences(),
    queryFn: ({ signal }) => fetchPreferences(signal),
  })
}

export function useSavePreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: savePreferences,
    onSuccess: (data) => queryClient.setQueryData(notificationKeys.preferences(), data),
  })
}
