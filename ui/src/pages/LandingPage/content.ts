export interface Showcase {
  title: string
  description: string
  example: string
  image: { src: string; alt: string; width: number; height: number }
}

export const SHOWCASES: Showcase[] = [
  {
    title: '청약·환불·상장일을 달력에서 확인',
    description:
      'DART 공시와 휴장일을 반영한 일정입니다. 날짜를 누르면 그날 청약하거나 환불·상장하는 종목과 공모가가 나옵니다. 로그인 없이 볼 수 있습니다.',
    example: '청약 기간은 막대로, 환불일과 상장일은 칸 안에 따로 표시됩니다.',
    image: {
      src: '/landing/feature-calendar.png',
      alt: '공모주 일정 달력: 종목별 청약 기간이 막대로, 환불일과 상장일이 색이 다른 칸으로 표시된 10월 달력',
      width: 800,
      height: 620,
    },
  },
  {
    title: '청약 전에 볼 숫자를 종목 화면에',
    description:
      '투자설명서에서 청약 판단에 쓰는 항목만 추려 보여 줍니다. 각 화면 아래에 공시 원문 링크와 기준일을 붙입니다.',
    example: '수요예측 경쟁률 850.4:1, 의무보유확약 비율 31.2%, 락업 해제일별 유통 가능 물량.',
    image: {
      src: '/landing/feature-detail.png',
      alt: '공모주 상세 화면의 수요예측 경쟁률·의무보유확약 비율과 유통가능 물량(락업 해제) 일정 표',
      width: 685,
      height: 535,
    },
  },
  {
    title: '팔고 나면 실제로 남은 돈이 나옵니다',
    description:
      '청약, 배정, 매도를 기록하면 분할 매도와 공모주 수수료, 세금을 반영해 최종수익금과 수익률을 계산합니다. 기간별로 묶어 CSV로 내려받을 수도 있습니다.',
    example: '매도금액 400,000원, 취득금액 250,000원, 수수료 600원, 세금 및 기타 920원.',
    image: {
      src: '/landing/feature-profit.png',
      alt: '수익 확인 화면: 합계 매도금액·최종수익금과 취득금액, 공모주수수료, 세금 및 기타, 최종수익금이 나뉜 표',
      width: 810,
      height: 260,
    },
  },
  {
    title: '매도 내역은 증권사에서 가져옵니다',
    description:
      '한국투자증권 API로 기간을 정해 매도 내역을 조회하고, 값을 고친 뒤 확정합니다. 한국투자증권·NH투자증권 앱의 실현손익 캡처로 입력을 채울 수도 있고, 이미지는 기기 안에서만 읽습니다.',
    example:
      '확정하기 전에는 아무것도 저장되지 않습니다. 배정 기록이 없는 내역은 따로 알려 줍니다.',
    image: {
      src: '/landing/feature-broker.png',
      alt: '증권사 연동 화면: 조회한 매도 내역이 날짜·수량·단가·금액·세금·수수료 입력칸이 있는 표로 나온다',
      width: 934,
      height: 301,
    },
  },
]

export const EXTRAS = [
  { term: '알림', text: '청약 마감일과 상장일을 웹 푸시로 알립니다. 설정에서 끌 수 있습니다.' },
  { term: '대시보드', text: '아직 입력하지 않은 배정 결과와 안 판 수량을 할 일로 모아 줍니다.' },
  { term: '여러 투자자', text: '한 계정에 본인과 가족을 등록하고 투자자별로 나눠 봅니다.' },
  { term: '수익 그래프', text: '연·월·일 단위로 상장일 기준 수익을 그래프와 표로 봅니다.' },
]

export const ASSURANCES = [
  '증권사 연동은 조회 전용입니다. 주문·정정·취소는 하지 않습니다.',
  '개인 투자 기록을 정리하는 도구입니다. 투자 권유나 수익 보장은 하지 않습니다.',
  '비공개로 운영합니다. 가입 후 관리자가 승인해야 로그인할 수 있습니다.',
  '캡처 이미지는 이 기기 안에서만 읽고 서버로 보내지 않습니다.',
]
