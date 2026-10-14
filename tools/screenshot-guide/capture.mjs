// 사용자 가이드(docs/user-guides/manual, pc.html·mobile.html)용 스크린샷을 더미 데이터로 만든다. run.sh가 백엔드·프론트를 띄운 뒤 호출한다.
// 백엔드는 APP_SIGNUP_MODE=APPROVAL, DB는 슬롯 4(vibe_slot_4)를 쓴다. 증권사 조회 미리보기는 더미 응답으로 대체한다(실제 호출 없음).
import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE_URL
const OUT = process.env.OUT
mkdirSync(OUT, { recursive: true })
const PW = 'testpassword123!'
const stamp = Date.now()
const admin = `guide-admin-${stamp}@example.com`
const member = `guide-member-${stamp}@example.com`
const sql = (q) =>
  execFileSync('docker', ['exec', process.env.E2E_DB_CONTAINER ?? 'ipo-calc-postgres-dev', 'psql', '-U', 'vibe', '-d', process.env.SLOT_DB ?? 'vibe_slot_4', '-c', q])

const browser = await chromium.launch({ headless: true })
const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'ko-KR' })
const page = await ctx.newPage()
page.setDefaultTimeout(15000)

async function shot(name, { full = true } = {}) {
  await page.setViewportSize({ width: 1280, height: 800 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${OUT}/${name}-desktop.png`, fullPage: full })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForTimeout(400)
  await page.screenshot({ path: `${OUT}/${name}-mobile.png`, fullPage: full })
  await page.setViewportSize({ width: 1280, height: 800 })
}
async function api(method, path, data) {
  const r = await ctx.request.fetch(`${BASE}/api-proxy${path}`, { method, data, headers: { 'Content-Type': 'application/json' } })
  if (!r.ok()) throw new Error(`${method} ${path} → ${r.status()} ${await r.text()}`)
  const t = await r.text()
  return t ? JSON.parse(t) : null
}
const go = async (path, ready) => {
  await page.goto(`${BASE}${path}`)
  if (ready) await ready()
  await page.waitForLoadState('networkidle')
}
const day = (offset, time = '09:00:00') => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}${time ? 'T' + time : ''}`
}

// 1) 랜딩 페이지(비로그인 상태, '/' 경로) → 가입 화면 → 승인 대기 안내 → 로그인 화면(로그아웃 상태)
await go('/')
await shot('landing')
await go('/signup')
await shot('signup')
await page.fill('#email', admin)
await page.fill('#password', PW)
await page.fill('#name', '관리자')
await page.click('button[type="submit"]')
await page.getByRole('status').waitFor()
await shot('signup-pending')
await go('/login')
await shot('login')

// 2) 관리자 지정 + 승인 대기 계정 하나 + 로그인
sql(`UPDATE accounts SET role='ADMIN', approved=TRUE WHERE email='${admin}'`)
const other = await browser.newContext()
await other.request.fetch(`${BASE}/api-proxy/accounts`, {
  method: 'POST', data: { email: member, password: PW, name: '김가입' }, headers: { 'Content-Type': 'application/json' },
})
await other.close()
// 가입 승인 화면에 '잠김'·'잠금 해제'가 보이도록 승인 대기 계정을 30분 잠금 상태로 둔다(앱이 로컬 시각 KST로 저장하므로 Asia/Seoul 기준으로 넣는다).
sql(`UPDATE accounts SET failed_login_attempts=5, locked_until=(now() at time zone 'Asia/Seoul') + interval '30 minutes' WHERE email='${member}'`)
await page.fill('#email', admin)
await page.fill('#password', PW)
await page.click('button[type="submit"]')
await page.waitForURL('**/dashboard')

// 3) 기본 정보: 투자자·증권계좌 (등록 화면은 빈 상태로 먼저 찍는다)
await go('/investors/register')
await shot('investor-register')
const kis = (await api('GET', '/securities-firms')).find((f) => f.name.includes('한국투자'))
const inv1 = await api('POST', '/investors', { displayName: '홍길동' })
const inv2 = await api('POST', '/investors', { displayName: '김가족' })
// 투자자가 2명이면 직접 골라야 한다. 상단 전환기에서 고르는 것과 같은 효과로 홍길동을 현재 투자자로 둔다.
await page.evaluate((id) => localStorage.setItem('currentInvestorId', String(id)), inv1.id)
await go(`/securities-accounts/register/${inv1.id}`)
await shot('securities-account-register')
const acc1 = await api('POST', '/securities-accounts', { investorId: inv1.id, securitiesFirmId: kis.id, accountNumber: '1234567890', isDefault: true })
const acc2 = await api('POST', '/securities-accounts', { investorId: inv2.id, securitiesFirmId: kis.id, accountNumber: '9876543210', isDefault: true })

// 4) 공모주(관리자 API로 시드) → 달력
const ipoDefs = [
  ['바다전자', 'BADA', -6, -4, -1, 10000],
  ['파도소프트', 'PADO', -1, 1, 5, 25000],
  ['물결바이오', 'MULG', 0, 2, 7, 18000],
  ['산호테크', 'SANH', 5, 7, 10, 32000],
  ['해초푸드', 'HAEC', 6, 8, 13, 12000],
]
const ipos = []
for (const [i, [name, code, s, e, l, price]] of ipoDefs.entries()) {
  ipos.push(await api('POST', '/ipos', {
    companyName: name, ipoName: name, stockCode: String(100000 + i * 1111 + (stamp % 80) * 10000), roundLabel: null,
    fixedPrice: price, subscriptionStartDate: day(s, ''), subscriptionEndDate: day(e, ''), listingDate: day(l, ''), offeringShareCount: 1000000,
  }))
}
// 공시 정보 구역(수요예측·유통가능 일정·기업정보·손익계산서·인수단)이 보이도록 설명용 예시 값을 직접 넣는다(실제 DART 호출 없음).
sql(`INSERT INTO ipo_details (ipo_id, source_rcept_no, source_report_name, synced_at, ceo_name, established_on, address, par_value, capital_stock,
  largest_shareholder_name, largest_shareholder_ratio, institutional_competition_rate, lock_up_commitment_ratio, financials, float_schedule, underwriters, created_at, updated_at)
  SELECT id, '20260101000001', '투자설명서', now(), '가나다', DATE '2015-03-02', '서울특별시 예시구 예시로 100', 500, 3500000000,
  '예시홀딩스', 52.30, 850.40, 31.20,
  '[{"period":"제11기(2025년12월말)","revenue":24800000000,"operatingProfit":3300000000,"netIncome":2700000000},{"period":"제10기(2024년12월말)","revenue":15400000000,"operatingProfit":1100000000,"netIncome":800000000},{"period":"제9기(2023년12월말)","revenue":8200000000,"operatingProfit":-900000000,"netIncome":-1200000000}]',
  '[{"label":"상장일 유통가능","shares":4200000,"ratio":38.50},{"label":"상장 후 1개월 유통가능","shares":5100000,"ratio":46.80},{"label":"상장 후 3개월 유통가능","shares":6300000,"ratio":57.80},{"label":"상장 후 6개월 유통가능","shares":8400000,"ratio":77.10},{"label":"상장 후 1년 유통가능","shares":10900000,"ratio":100.00}]',
  '[{"name":"예시증권","role":"대표","shares":700000},{"name":"샘플투자증권","role":"인수","shares":300000}]', now(), now()
  FROM ipos`)
await go('/ipo-calendar')
await page.getByRole('heading', { name: /공모주 일정/ }).waitFor()
const pick = new Date()
pick.setDate(pick.getDate() + 5)
await page.locator(`button[aria-label^="${pick.getMonth() + 1}월 ${pick.getDate()}일,"]`).click({ position: { x: 6, y: 6 } }) // 막대가 칸 가운데를 덮으므로 날짜 숫자 줄(맨 위)을 누른다
await shot('ipo-calendar')
await page.locator('a[href^="/ipos/"]').first().click()
await page.getByRole('heading', { level: 1 }).waitFor()
await page.waitForLoadState('networkidle')
await shot('ipo-detail')

// 5) 거래 데이터 시드(청약→배정→매도·비용)
const sub1 = await api('POST', '/subscriptions', { investorId: inv1.id, securitiesAccountId: acc1.id, ipoId: ipos[0].id, quantity: 100, depositAmount: 500000, subscribedAt: day(-6) })
const sub2 = await api('POST', '/subscriptions', { investorId: inv2.id, securitiesAccountId: acc2.id, ipoId: ipos[0].id, quantity: 50, depositAmount: 250000, subscribedAt: day(-6) })
const al1 = await api('POST', '/allocations', { subscriptionId: sub1.id, allocatedQuantity: 40, allocatedAt: day(-3), allocationPrice: 10000 })
await api('POST', '/allocations', { subscriptionId: sub2.id, allocatedQuantity: 10, allocatedAt: day(-3), allocationPrice: 10000 })
await api('POST', '/subscriptions', { investorId: inv1.id, securitiesAccountId: acc1.id, ipoId: ipos[1].id, quantity: 30, depositAmount: 375000, subscribedAt: day(0) })
await api('POST', '/sales', { allocationId: al1.id, quantity: 25, price: 16000, amount: 400000, saleDate: day(-1), fee: 600, tax: 920 })

await go('/subscriptions/register', () => page.getByRole('heading', { name: /청약/ }).first().waitFor())
await shot('subscription-register')
await go('/subscriptions')
await shot('subscription-list')
await go('/allocations/register')
await shot('allocation-register')
await go('/sales/register')
// 공모가·상장일·수량이 미리 채워진 모습을 보여 주려고 청약을 고른 뒤 찍는다
await page.waitForFunction(() => document.querySelectorAll('#subscriptionId option').length > 1)
const subValue = await page.locator('#subscriptionId option', { hasText: '바다전자' }).first().getAttribute('value')
await page.selectOption('#subscriptionId', subValue)
await page.waitForFunction(() => document.querySelector('#price')?.value !== '')
await page.waitForTimeout(500)
await shot('sale-register')
await go('/profits/view')
await shot('profit-view')
await go('/dashboard')
await shot('dashboard')
await go('/investments')
const row = page.getByRole('row').nth(1)
if (await row.count()) await row.click()
await shot('investments')
await go('/securities', () => page.getByRole('heading', { name: '증권사 관리' }).waitFor())
await shot('securities')

// 6) 증권사 연동: 키 등록 전 → 연결됨 → 매도 내역 조회(미리보기는 더미 응답으로 대체)
await go(`/broker-sync?investorId=${inv1.id}&securitiesAccountId=${acc1.id}`)
await page.getByRole('heading', { name: '증권사 API 연결' }).waitFor()
await shot('broker-register')
await page.getByLabel('앱키').fill('dummy-app-key')
await page.getByLabel('앱시크릿').fill('dummy-app-secret')
await page.getByRole('button', { name: '연결', exact: true }).click()
await page.getByText('연결됨', { exact: true }).waitFor()
await shot('broker-connected')
await page.getByRole('button', { name: '키 교체' }).click()
await page.getByLabel('앱키').waitFor()
await shot('broker-replace')
await go(`/broker-sync?investorId=${inv1.id}&securitiesAccountId=${acc1.id}`)
await page.getByText('연결됨', { exact: true }).waitFor()
await page.route('**/broker-sync/preview*', (route) => route.fulfill({
  status: 200, contentType: 'application/json',
  body: JSON.stringify({
    matched: [{ tradeDate: day(-1, ''), stockCode: '100000', quantity: 15, amount: 240000, price: 16000, fee: 360, tax: 552, allocationId: al1.id, reason: null }],
    unmatched: [{ tradeDate: day(-2, ''), stockCode: '999999', quantity: 5, amount: 50000, price: 10000, fee: 75, tax: 115, allocationId: null, reason: 'NO_ALLOCATION_FOR_STOCK' }],
    allocationsWithoutStockCode: [],
  }),
}))
await page.getByRole('button', { name: '증권사에서 매도 내역 조회' }).click()
await page.waitForLoadState('networkidle')
await page.waitForTimeout(600)
await shot('broker-sync-preview')
await page.unroute('**/broker-sync/preview*')

// 7) 관리자 화면
await go('/admin/accounts')
await page.getByText(member, { exact: true }).waitFor()
await shot('admin-accounts')
if (process.env.ADMIN_IPO_PATH) {
  await go(process.env.ADMIN_IPO_PATH)
  await shot('admin-ipo-register')
}

// 8) 헤더·모바일 메뉴
await go('/dashboard')
await page.setViewportSize({ width: 1280, height: 800 })
await page.screenshot({ path: `${OUT}/nav-desktop.png`, clip: { x: 0, y: 0, width: 1280, height: 72 } })
await page.setViewportSize({ width: 390, height: 844 })
await page.getByRole('button', { name: '메뉴' }).click()
await page.waitForTimeout(300)
await page.screenshot({ path: `${OUT}/nav-mobile-menu.png`, fullPage: false })

await browser.close()
console.log('manual screenshots done')
