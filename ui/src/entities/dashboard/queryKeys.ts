type PeriodUnit = 'month' | 'year'

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: () => [...dashboardKeys.all, 'summary'] as const,
  byInvestor: () => [...dashboardKeys.all, 'by-investor'] as const,
  bySecuritiesFirm: () => [...dashboardKeys.all, 'by-securities-firm'] as const,
  byPeriod: (unit: PeriodUnit, year?: number, month?: number) =>
    [
      ...dashboardKeys.all,
      'by-period',
      unit,
      ...(year !== undefined ? [year] : []),
      ...(month !== undefined ? [month] : []),
    ] as const,
}
