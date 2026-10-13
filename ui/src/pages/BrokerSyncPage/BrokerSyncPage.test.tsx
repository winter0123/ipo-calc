import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import BrokerSyncPage from './BrokerSyncPage.tsx'

let currentId = 1
let accounts: { id: number }[] | undefined = [{ id: 10 }]

vi.mock('@/entities/investor/index.ts', () => ({
  useCurrentInvestor: () => ({ current: { id: currentId } }),
}))
vi.mock('@/entities/securities-account/index.ts', () => ({
  useSecuritiesAccounts: () => ({ data: accounts, isFetching: false }),
}))
vi.mock('@/features/investment-history/index.ts', () => ({ AccountSelector: () => null }))
vi.mock('@/features/broker-connect/index.ts', () => ({
  BrokerConnectPanel: ({ securitiesAccountId }: { securitiesAccountId: number }) => (
    <p>connect-{securitiesAccountId}</p>
  ),
}))
vi.mock('@/features/broker-sync/index.ts', () => ({ BrokerSyncPanel: () => null }))

function Search() {
  return <span data-testid="search">{useLocation().search}</span>
}

function renderAt(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <BrokerSyncPage />
      <Search />
    </MemoryRouter>,
  )
}

describe('BrokerSyncPage', () => {
  beforeEach(() => {
    currentId = 1
    accounts = [{ id: 10 }]
  })

  it('현재 투자자의 계좌면 패널을 연다', () => {
    renderAt('/broker-sync?securitiesAccountId=10')
    expect(screen.getByText('connect-10')).toBeInTheDocument()
  })

  it('다른 투자자의 계좌 id면 패널을 그리지 않고 파라미터를 지운다', () => {
    renderAt('/broker-sync?securitiesAccountId=99')
    expect(screen.queryByText(/connect-/)).not.toBeInTheDocument()
    expect(screen.getByTestId('search')).toHaveTextContent('')
  })

  it('계좌 목록을 불러오기 전에는 패널을 열지 않는다', () => {
    accounts = undefined
    renderAt('/broker-sync?securitiesAccountId=10')
    expect(screen.queryByText(/connect-/)).not.toBeInTheDocument()
    expect(screen.getByTestId('search')).toHaveTextContent('securitiesAccountId=10')
  })
})
