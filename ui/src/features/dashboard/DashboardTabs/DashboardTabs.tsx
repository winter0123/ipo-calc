import { useRef } from 'react'
import type { DashboardTab } from '../useDashboardTabs.ts'
import styles from './DashboardTabs.module.css'

interface DashboardTabsProps {
  currentTab: DashboardTab
  onTabChange: (tab: DashboardTab) => void
}

const TAB_LABELS: Partial<Record<DashboardTab, string>> = {
  all: '전체',
  investor: '투자자별',
  month: '월별',
  year: '연도별',
}

const TAB_LABELS_MAP: Record<string, string> = {
  'securities-firm': '증권사별',
}

const ALL_TAB_KEYS = ['all', 'investor', 'securities-firm', 'month', 'year'] as DashboardTab[]

function getTabLabel(tab: DashboardTab): string {
  if (tab === 'securities-firm') return TAB_LABELS_MAP['securities-firm']
  return TAB_LABELS[tab] ?? ''
}

export default function DashboardTabs({ currentTab, onTabChange }: DashboardTabsProps) {
  const tabRef = useRef<HTMLDivElement>(null)

  const handleKeyDown = (e: React.KeyboardEvent, tab: DashboardTab) => {
    const tabs = ['all', 'investor', 'securities-firm', 'month', 'year'] as DashboardTab[]
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
    <div className={styles.tabList} role="tablist" aria-label="대시보드 탭" ref={tabRef}>
      {(ALL_TAB_KEYS as DashboardTab[]).map((tab) => (
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
          {getTabLabel(tab)}
        </button>
      ))}
    </div>
  )
}
