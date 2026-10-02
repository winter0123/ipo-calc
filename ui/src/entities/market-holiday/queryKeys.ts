export const marketHolidayKeys = {
  all: ['market-holidays'] as const,
  year: (year: number) => [...marketHolidayKeys.all, year] as const,
}
