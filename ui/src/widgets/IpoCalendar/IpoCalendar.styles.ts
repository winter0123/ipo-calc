import styled from 'styled-components'

export const styles: Record<string, string> = new Proxy(
  {},
  {
    get: (_target, key) => (typeof key === 'string' ? 's-e1b278a-' + key : undefined),
  },
)

export const StyleScope = styled.div`
  display: contents;
  /* 달력(왼쪽) + 선택한 날짜 상세(오른쪽). 1px 선 격자와 막대로 구분한다. */
  .s-e1b278a-calendar {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'header'
      'filter'
      'weekdays'
      'grid'
      'detail'
      'emptyMessage';
    row-gap: 0;
    column-gap: var(--space-6);
    width: 100%;
    min-width: 0;
  }

  .s-e1b278a-header {
    grid-area: header;
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-bottom: var(--space-4);
  }

  .s-e1b278a-dateSelectors {
    margin: 0 auto 0 0;
    display: flex;
    align-items: center;
    gap: var(--space-1);
  }

  .s-e1b278a-dateSelect {
    min-height: 40px;
    padding: 0 var(--space-2);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    font-size: 20px;
    font-weight: 700;
  }

  .s-e1b278a-dateSelect:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
  }

  .s-e1b278a-srOnly {
    position: absolute;
    width: 1px;
    height: 1px;
    padding: 0;
    margin: -1px;
    overflow: hidden;
    clip: rect(0, 0, 0, 0);
    white-space: nowrap;
    border: 0;
  }

  .s-e1b278a-navButton,
  .s-e1b278a-todayButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 40px;
    min-height: 40px;
    padding: 0 var(--space-3);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
  }

  .s-e1b278a-navButton:hover,
  .s-e1b278a-todayButton:hover {
    background: var(--surface-soft);
  }

  .s-e1b278a-navButton:focus-visible,
  .s-e1b278a-todayButton:focus-visible,
  .s-e1b278a-dateCell:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
    z-index: 1;
  }

  .s-e1b278a-filterGroup {
    grid-area: filter;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-3);
    padding-bottom: var(--space-3);
    border-bottom: 1px solid var(--border);
  }

  .s-e1b278a-filterButton {
    display: inline-flex;
    align-items: center;
    gap: var(--space-2);
    min-height: 44px;
    padding: 0 var(--space-3);
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    font: inherit;
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
    transition:
      background-color 0.15s,
      color 0.15s,
      border-color 0.15s;
  }

  .s-e1b278a-filterButton:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
    z-index: 1;
  }

  .s-e1b278a-filterDot {
    display: inline-block;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    flex-shrink: 0;
  }

  .s-e1b278a-filterOn {
    background: var(--surface-sea);
    color: var(--ink);
    border-color: var(--border-strong);
  }

  .s-e1b278a-filterOn .s-e1b278a-filterDot {
    background: currentColor;
  }

  .s-e1b278a-filterOn[class*='barListing'] .s-e1b278a-filterDot,
  .s-e1b278a-filterOn.s-e1b278a-barListing .s-e1b278a-filterDot {
    background: var(--success-bg);
  }

  .s-e1b278a-filterOff {
    background: var(--surface);
    color: var(--ink-muted);
    border-color: var(--border-strong);
  }

  .s-e1b278a-filterOff .s-e1b278a-filterDot {
    background: var(--border-strong);
  }

  .s-e1b278a-showAllButton {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 44px;
    padding: 0 var(--space-3);
    margin-left: auto;
    border: 1px solid var(--border-strong);
    border-radius: var(--radius-sm);
    background: var(--surface);
    color: var(--ink);
    font: inherit;
    font-size: 14px;
    font-weight: 650;
    cursor: pointer;
  }

  .s-e1b278a-showAllButton:hover:not(:disabled) {
    background: var(--surface-soft);
  }

  .s-e1b278a-showAllButton:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .s-e1b278a-showAllButton:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
    z-index: 1;
  }

  .s-e1b278a-weekdays {
    grid-area: weekdays;
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    min-width: 0;
    border: 1px solid var(--border);
    border-bottom: none;
    border-radius: var(--radius-sm) var(--radius-sm) 0 0;
    background: var(--surface-soft);
  }

  .s-e1b278a-weekday {
    padding: var(--space-3) 0;
    text-align: center;
    font-size: 13px;
    font-weight: 650;
    color: var(--ink-muted);
  }

  .s-e1b278a-weekday:first-child {
    color: var(--danger);
  }

  .s-e1b278a-weekday:last-child {
    color: var(--link);
  }

  .s-e1b278a-grid {
    grid-area: grid;
    display: flex;
    flex-direction: column;
    min-width: 0;
    overflow: hidden;
    border-top: 1px solid var(--border);
    border-left: 1px solid var(--border);
    border-radius: 0 0 var(--radius-sm) var(--radius-sm);
  }

  .s-e1b278a-week {
    display: grid;
    grid-template-columns: repeat(7, minmax(0, 1fr));
    grid-template-rows: 38px repeat(var(--calendar-lanes, 0), 24px) minmax(8px, 1fr);
    min-height: 96px;
    min-width: 0;
    position: relative;
  }

  .s-e1b278a-emptyCell,
  .s-e1b278a-dateCell {
    grid-column: var(--calendar-column);
    grid-row: 1 / -1;
    border: 0;
    border-right: 1px solid var(--border);
    border-bottom: 1px solid var(--border);
  }

  .s-e1b278a-emptyCell {
    background: var(--surface-soft);
  }

  .s-e1b278a-dateCell {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-1);
    padding: var(--space-2);
    background: var(--surface);
    color: var(--ink);
    text-align: left;
    font: inherit;
    cursor: pointer;
  }

  .s-e1b278a-dateCell:hover {
    background: var(--surface-soft);
  }

  .s-e1b278a-dateCell.s-e1b278a-selected {
    background: var(--surface-sea);
    box-shadow: inset 0 0 0 2px var(--primary);
  }

  /* 날짜 숫자와 휴장일 이름을 같은 윗줄에 둔다(이름은 왼쪽, 숫자는 오른쪽) */
  .s-e1b278a-dateHead {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--space-1);
    min-width: 0;
  }

  .s-e1b278a-dateNumber {
    flex: none;
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 24px;
    height: 24px;
    border-radius: 12px;
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-e1b278a-dateCell.s-e1b278a-today .s-e1b278a-dateNumber {
    background: var(--primary);
    /* 진한 primary 배경 위라 흰 글자(검은 글자는 대비 3.1:1로 접근성 미달) */
    color: var(--white);
    font-weight: 700;
  }

  /* 공휴일 이름: 날짜 숫자 위(왼쪽 상단)에 작은 글자로 표시 */
  .s-e1b278a-holidayName {
    font-size: 11px;
    font-weight: 600;
    color: var(--danger);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    flex: 1 1 auto;
    min-width: 0;
    padding-top: 5px;
    line-height: 1.3;
  }

  /* 공휴일인 날짜의 숫자 색상을 danger로 변경 */
  .s-e1b278a-dateCell.s-e1b278a-hasHoliday .s-e1b278a-dateNumber {
    color: var(--danger);
  }

  .s-e1b278a-dateCell.s-e1b278a-today.s-e1b278a-hasHoliday .s-e1b278a-dateNumber {
    background: var(--primary);
    color: var(--white);
  }

  /* 막대 */
  .s-e1b278a-bar {
    grid-column: var(--calendar-bar-column);
    grid-row: var(--calendar-bar-row);
    text-decoration: none;
    display: flex;
    align-items: center;
    justify-content: flex-start;
    height: 20px;
    padding: 0 6px;
    border-top-left-radius: var(--calendar-bar-radius-left);
    border-bottom-left-radius: var(--calendar-bar-radius-left);
    border-top-right-radius: var(--calendar-bar-radius-right);
    border-bottom-right-radius: var(--calendar-bar-radius-right);
    margin-left: var(--calendar-bar-margin-left);
    margin-right: var(--calendar-bar-margin-right);
    font-size: 12px;
    font-weight: 650;
    line-height: 1.4;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    z-index: 1;
    transition: filter 0.15s;
  }

  .s-e1b278a-bar:hover {
    filter: brightness(0.96);
  }

  .s-e1b278a-bar:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 1px;
    z-index: 2;
  }

  .s-e1b278a-barSubscription {
    background: var(--surface-sea);
    color: var(--ink);
  }

  .s-e1b278a-barListing {
    background: var(--success-bg);
    color: var(--success-ink);
  }

  .s-e1b278a-barRefund {
    background: var(--warning-bg);
    color: var(--warning-ink);
  }

  /* 증권사 데이터가 아니라 청약 종료일 + 2영업일로 계산한 환불일은 점선 테두리로 구분한다 */
  .s-e1b278a-barEstimated {
    border: 1px dashed var(--warning-border);
  }

  /* 레인별 행 위치는 grid-row로 제어 (인라인 스타일) */

  /* 선택한 날짜 상세 */
  .s-e1b278a-detail {
    grid-area: detail;
    margin-top: var(--space-5);
    padding: var(--space-5);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--surface);
  }

  .s-e1b278a-detailTitle {
    margin: 0 0 var(--space-4);
    font-size: 16px;
    font-weight: 700;
    color: var(--ink);
  }

  /* 휴장일 안내: 제목 바로 아래에 표시 */
  .s-e1b278a-holidayNotice {
    margin: 0 0 var(--space-3);
    font-size: 14px;
    font-weight: 600;
    color: var(--danger);
  }

  .s-e1b278a-noEvents {
    margin: 0;
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-e1b278a-noTypesSelected {
    grid-area: emptyMessage;
    margin: var(--space-4) 0 0;
    padding: var(--space-3);
    color: var(--ink-muted);
    font-size: 14px;
    text-align: center;
    background: var(--surface-soft);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
  }

  .s-e1b278a-eventList {
    margin: 0;
    padding: 0;
    list-style: none;
    display: flex;
    flex-direction: column;
    gap: var(--space-3);
  }

  .s-e1b278a-eventItem {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    flex-wrap: wrap;
  }

  .s-e1b278a-eventType {
    display: inline-block;
  }

  .s-e1b278a-eventCompany {
    font-weight: 650;
    color: var(--ink);
  }

  .s-e1b278a-eventIpo {
    color: var(--ink-muted);
    font-size: 14px;
  }

  .s-e1b278a-eventPeriod {
    font-size: 13px;
    color: var(--ink-muted);
  }

  .s-e1b278a-eventPrice {
    font-size: 14px;
    color: var(--ink);
    background: var(--surface-soft);
    padding: 2px var(--space-2);
    border-radius: 6px;
  }

  /* 이벤트 링크 */
  .s-e1b278a-eventLink {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    flex-wrap: wrap;
    width: 100%;
    padding: var(--space-2);
    text-decoration: none;
    color: var(--ink);
    border-radius: var(--radius-sm);
    transition: background-color 0.15s;
  }

  .s-e1b278a-eventLink:hover {
    background: var(--surface-soft);
  }

  .s-e1b278a-eventLink:focus-visible {
    outline: 2px solid var(--link);
    outline-offset: 2px;
    z-index: 1;
  }

  .s-e1b278a-eventArrow {
    margin-left: auto;
    color: var(--ink-muted);
    font-size: 14px;
    font-weight: 700;
  }

  @media (min-width: 1024px) {
    .s-e1b278a-calendar {
      grid-template-columns: minmax(0, 1fr) 320px;
      grid-template-areas:
        'header detail'
        'filter detail'
        'weekdays detail'
        'grid detail'
        'emptyMessage detail';
      align-items: start;
    }

    .s-e1b278a-detail {
      margin-top: 0;
      align-self: start;
    }

    .s-e1b278a-filterGroup {
      margin-bottom: var(--space-2);
      padding-bottom: var(--space-2);
    }
  }

  @media (max-width: 640px) {
    .s-e1b278a-header {
      flex-wrap: wrap;
      gap: var(--space-1);
    }

    .s-e1b278a-dateSelectors {
      flex: 1;
      justify-content: center;
      margin: 0;
    }

    .s-e1b278a-dateSelect {
      padding: 0 var(--space-1);
      font-size: 16px;
    }

    .s-e1b278a-navButton,
    .s-e1b278a-todayButton {
      min-width: 36px;
      padding: 0 var(--space-2);
    }

    /* 칸이 좁아 잘린 이름 조각만 보이므로 숨긴다(붉은 날짜·접근성 이름·선택일 안내는 유지) */
    .s-e1b278a-holidayName {
      display: none;
    }

    .s-e1b278a-emptyCell,
    .s-e1b278a-dateCell {
      min-height: 72px;
    }

    .s-e1b278a-bar {
      font-size: 11px;
      height: 18px;
      padding: 0 4px;
    }

    .s-e1b278a-filterButton {
      min-height: 44px;
      padding: 0 var(--space-2);
      font-size: 13px;
    }

    .s-e1b278a-showAllButton {
      min-height: 44px;
      padding: 0 var(--space-2);
      font-size: 13px;
    }
  }

  .s-e1b278a-registerLink {
    margin-left: var(--space-2);
    padding: var(--space-1) var(--space-3);
    background-color: var(--primary);
    color: var(--on-primary);
    border-radius: var(--radius-sm);
    font-size: 13px;
    font-weight: 650;
    text-decoration: none;
    white-space: nowrap;
  }

  .s-e1b278a-registerLink:hover {
    background-color: var(--primary-hover);
  }
`

export default styles
