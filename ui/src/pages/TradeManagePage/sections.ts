/** TradeManagePage 내부에서만 쓰는 타입. features는 자기 props 타입을 직접 export하고,
 *  page가 features의 공개 API에서 조합한다(features → pages 역방향 import 금지). */

/** TradeManagePage가 관리하는 섹션 id. */
export type TradeManageSectionId = 'subscription' | 'allocation' | 'sale'

/**
 * 섹션 조회 상태. 'loading'/'error'는 '기록 없음'이 아니다 —
 * 섹션은 오류를 신규 입력 가능 상태로 바꿔치기하지 않는다.
 */
export type SectionStatus = 'loading' | 'error' | 'ready'
