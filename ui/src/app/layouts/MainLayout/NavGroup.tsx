import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import { ChevronDown } from 'lucide-react'
import styles, { StyleScope } from './MainLayout.styles.ts'

export interface NavGroupItem {
  to?: string
  label: string
  onClick?: () => void
  disabled?: boolean
  static?: boolean
}

// 데스크톱: 버튼을 누르면 펼쳐지는 메뉴. 모바일: CSS가 소제목 + 항상 펼친 목록으로 바꾼다.
export default function NavGroup({ label, items }: { label: string; items: NavGroupItem[] }) {
  const { pathname } = useLocation()
  // 연 경로를 기억해 두면 페이지가 바뀔 때 자동으로 닫힌다
  const [openedAt, setOpenedAt] = useState<string | null>(null)
  const open = openedAt === pathname
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const active = items.some((item) => pathname === item.to || pathname.startsWith(`${item.to}/`))

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpenedAt(null)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  const links = () =>
    Array.from(rootRef.current?.querySelectorAll<HTMLElement>('a, button[data-menu-item]') ?? [])
  const focusAt = (i: number) => {
    const list = links()
    list[(i + list.length) % list.length]?.focus()
  }

  const onKeyDown = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && open) {
      setOpenedAt(null)
      buttonRef.current?.focus()
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      if (!open) {
        setOpenedAt(pathname)
        // 패널이 hidden 해제된 뒤에 포커스를 옮긴다
        requestAnimationFrame(() => focusAt(e.key === 'ArrowDown' ? 0 : -1))
        return
      }
      const i = links().indexOf(document.activeElement as HTMLElement)
      focusAt(e.key === 'ArrowDown' ? i + 1 : i - 1)
    }
  }

  return (
    <StyleScope>
      {
        // eslint-disable-next-line jsx-a11y/no-static-element-interactions -- 키 입력은 자식 버튼·링크에서 올라온 것만 처리
        <div
          ref={rootRef}
          className={styles.group}
          onKeyDown={onKeyDown}
          onBlur={(e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setOpenedAt(null)
          }}
        >
          <button
            ref={buttonRef}
            type="button"
            className={active ? `${styles.link} ${styles.active}` : styles.link}
            aria-expanded={open}
            aria-haspopup="true"
            onClick={() => setOpenedAt(open ? null : pathname)}
          >
            {label}
            <ChevronDown aria-hidden="true" size={14} />
          </button>
          <span className={styles.groupTitle} aria-hidden="true">
            {label}
          </span>
          <div className={styles.morePanel} hidden={!open} role="group" aria-label={label}>
            {items.map((item) =>
              item.onClick ? (
                <button
                  key={item.label}
                  type="button"
                  data-menu-item
                  className={styles.moreLink}
                  onClick={item.onClick}
                  disabled={item.disabled}
                >
                  {item.label}
                </button>
              ) : item.to && item.static ? (
                <a
                  key={item.to}
                  href={item.to}
                  className={styles.moreLink}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {item.label}
                </a>
              ) : item.to ? (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    isActive ? `${styles.moreLink} ${styles.moreLinkActive}` : styles.moreLink
                  }
                >
                  {item.label}
                </NavLink>
              ) : null,
            )}
          </div>
        </div>
      }
    </StyleScope>
  )
}
