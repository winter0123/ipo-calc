# Query key factory 누락 구조

기존 key 값을 factory에 옮겨 캐시 동작을 보존한다.

```text
announcementKeys.list(page)      = ['announcements', page]
announcementKeys.detail(id)      = ['announcement', id]
announcementKeys.popup(account)  = ['announcement-popup', account]
announcementKeys.adminList(...)  = ['admin-announcements', page, status]
ipoKeys.syncStatus()              = ['ipoSyncStatus']
investmentHistoryKeys.allPages(f) = [...investmentHistoryKeys.list(f), 'all']
```

각 루트는 기존 값 그대로여서 관리자 prefix invalidation과 다른 공지 cache namespace가 서로 간섭하지 않는다.
