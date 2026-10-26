import { useRef } from 'react'
import { DASHBOARD_TABS, type DashboardTab } from '../tabs.ts'
import styles, { StyleScope } from './DashboardTabs.styles.ts'

interface DashboardTabsProps {
  currentTab: DashboardTab
  onTabChange: (tab: DashboardTab) => void
}

export default function DashboardTabs({ currentTab, onTabChange }: DashboardTabsProps) {
  const tabRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = (e: React.KeyboardEvent, tab: DashboardTab) => {
    const tabs = DASHBOARD_TABS.map((t) => t.key)
    const currentIndex = tabs.indexOf(tab)
    let nextIndex = currentIndex

    switch (e.key) {
      case 'ArrowRight':
      case 'ArrowDown':
        e.preventDefault()
        nextIndex = (currentIndex + 1) % tabs.length
        break
      case 'ArrowLeft':
      case 'ArrowUp':
        e.preventDefault()
        nextIndex = (currentIndex - 1 + tabs.length) % tabs.length
        break
      case 'Home':
        e.preventDefault()
        nextIndex = 0
        break
      case 'End':
        e.preventDefault()
        nextIndex = tabs.length - 1
        break
      case 'Enter':
      case ' ':
        e.preventDefault()
        onTabChange(tab)
        return
      default:
        return
    }

    onTabChange(tabs[nextIndex])
    const tabButtons = tabRef.current?.querySelectorAll<HTMLElement>('[role="tab"]')
    tabButtons?.[nextIndex]?.focus()
  }

  return (
    <StyleScope>
      {
        <div className={styles.tabList} role="tablist" aria-label="대시보드 탭" ref={tabRef}>
          {DASHBOARD_TABS.map(({ key: tab, label }) => (
            <button
              key={tab}
              type="button"
              role="tab"
              aria-selected={currentTab === tab}
              id={`tab-${tab}`}
              tabIndex={currentTab === tab ? 0 : -1}
              className={`${styles.tab} ${currentTab === tab ? styles.activeTab : ''}`}
              onClick={() => onTabChange(tab)}
              onKeyDown={(e) => handleKeyDown(e, tab)}
            >
              {label}
            </button>
          ))}
        </div>
      }
    </StyleScope>
  )
}
