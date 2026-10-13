import { useMutation, useQueryClient } from '@tanstack/react-query'
import { importIposCsv, ipoKeys } from '@/entities/ipo/index.ts'

export function useIpoImport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: importIposCsv,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ipoKeys.all }),
  })
}
