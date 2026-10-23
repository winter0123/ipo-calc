const closedForVisit = new Set<string>()
const visitListeners = new Set<() => void>()
let visitRevision = 0

export function subscribeToAnnouncementVisit(callback: () => void) {
  visitListeners.add(callback)
  return () => {
    visitListeners.delete(callback)
  }
}

export function getAnnouncementVisitRevision() {
  return visitRevision
}

export function isAnnouncementClosedForVisit(
  accountId: number | undefined,
  announcementId: number,
) {
  return closedForVisit.has(`${accountId}:${announcementId}`)
}

export function closeAnnouncementForVisit(accountId: number | undefined, announcementId: number) {
  const key = `${accountId}:${announcementId}`
  if (closedForVisit.has(key)) return
  closedForVisit.add(key)
  visitRevision += 1
  visitListeners.forEach((listener) => listener())
}

export function resetAnnouncementVisit() {
  if (closedForVisit.size === 0) return
  closedForVisit.clear()
  visitRevision += 1
  visitListeners.forEach((listener) => listener())
}
