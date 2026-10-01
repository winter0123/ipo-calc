import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  brokerKeys,
  disconnectBrokerCredential,
  registerBrokerCredential,
  type BrokerCredentialRegisterRequest,
} from '@/entities/broker/index.ts'

export function useRegisterBrokerCredential(securitiesAccountId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: BrokerCredentialRegisterRequest) =>
      registerBrokerCredential(securitiesAccountId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: brokerKeys.all }),
  })
}

export function useDisconnectBrokerCredential(securitiesAccountId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => disconnectBrokerCredential(securitiesAccountId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: brokerKeys.all }),
  })
}
