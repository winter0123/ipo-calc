import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { writeFile } from 'node:fs/promises'
import AxeBuilder from '@axe-core/playwright'

const root = resolve(import.meta.dirname, '../..')
const baseUrl = process.env.BASE_URL ?? 'http://127.0.0.1:5173'
const outputRoot = resolve(root, 'docs/qa')
const apiPrefix = process.env.E2E_API_PREFIX ?? '/api-proxy'
const dbName = process.env.E2E_DB_NAME ?? process.env.DB_NAME
const dbContainer = process.env.E2E_DB_CONTAINER ?? 'ipo-calc-postgres-dev'
const dbUser = process.env.E2E_DB_USER ?? 'vibe'
let scanEmail = null

const pages = [
  { name: 'login', path: '/login', needsAuth: false },
  { name: 'signup', path: '/signup', needsAuth: false },
  { name: 'investor-register', path: '/investors/register', needsAuth: true },
  { name: 'securities-account-register', path: '/securities-accounts/register/1', needsAuth: true },
  { name: 'securities', path: '/securities', needsAuth: true },
  { name: 'subscription-register', path: '/subscriptions/register', needsAuth: true },
  { name: 'subscription-list', path: '/subscriptions', needsAuth: true },
  { name: 'allocation-register', path: '/allocations/register', needsAuth: true },
  { name: 'sale-register', path: '/sales/register', needsAuth: true },
  { name: 'profit-view', path: '/profits/view', needsAuth: true },
  { name: 'investments', path: '/investments', needsAuth: true },
  { name: 'dashboard', path: '/dashboard', needsAuth: true },
  { name: 'broker-sync', path: '/broker-sync', needsAuth: true },
  { name: 'notifications', path: '/notifications', needsAuth: true },
]

const viewports = [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'mobile', width: 390, height: 844 },
]

async function login(page, context) {
  console.log('Logging in...')
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.waitForSelector('form')
  
  // Try to login with a test account
  await page.fill('#email', 'test@example.com')
  await page.fill('#password', 'testpassword123!')
  await page.click('button[type="submit"]')
  
  try {
    await page.waitForURL('**/dashboard', { timeout: 10000 })
    console.log('Login successful')
    scanEmail = 'test@example.com'
    return true
  } catch {
    console.log('Login failed, trying signup...')
    // Try signup - form has email, password, name
    await page.goto(`${baseUrl}/signup`, { waitUntil: 'networkidle' })
    await page.waitForSelector('form')
    const testEmail = `testscan${Date.now()}@example.com`
    await page.fill('#email', testEmail)
    await page.fill('#password', 'testpassword123!')
    await page.fill('#name', 'Test Scanner')
    await page.click('button[type="submit"]')
    
    try {
      // After signup, it redirects to /investors/register with accountId in state
      await page.waitForURL('**/investors/register*', { timeout: 15000 })
      scanEmail = testEmail
      console.log('Signup successful, redirected to investor register')
      // Now we're logged in, navigate to dashboard
      await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
      await page.waitForTimeout(1000)
      console.log('Navigated to dashboard')
      return true
    } catch (e) {
      console.log('Signup also failed:', e.message)
      // Check for error message on page
      const errorText = await page.textContent('body').catch(() => '')
      console.log('Page content:', errorText.substring(0, 500))
      return false
    }
  }
}

// 증권사 연동 화면의 상태별 스캔을 위해 API로 투자자·증권계좌(KIS)를 만들고 한 계좌만 연결해 둔다.
// 외부 증권사는 호출하지 않는다(더미 키 등록만, 토큰은 동기화 조회 때만 발급).
async function setupBrokerStates(context) {
  const call = async (method, path, data) => {
    const response = await context.request.fetch(`${baseUrl}${apiPrefix}${path}`, {
      method,
      data,
      headers: { 'Content-Type': 'application/json' },
    })
    if (!response.ok()) throw new Error(`${method} ${path} → ${response.status()}`)
    const text = await response.text()
    return text ? JSON.parse(text) : null
  }
  try {
    const firms = await call('GET', '/securities-firms')
    const kis = firms.find((f) => f.name.includes('한국투자'))
    if (!kis) throw new Error('한국투자증권 시드 없음')
    const investor = await call('POST', '/investors', { displayName: 'Scan Broker' })
    const connected = await call('POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: kis.id,
      accountNumber: '1111111111',
      isDefault: true,
    })
    const open = await call('POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: kis.id,
      accountNumber: '2222222222',
      isDefault: false,
    })
    await call('POST', `/securities-accounts/${connected.id}/api-credential`, {
      apiProvider: 'KIS',
      appKey: 'scan-dummy-key',
      appSecret: 'scan-dummy-secret',
    })
    const query = (accountId) =>
      `/broker-sync?investorId=${investor.id}&securitiesAccountId=${accountId}`
    // 현재 투자자는 localStorage 기준이라, 이 투자자로 바꾸고 새로고침한 뒤 화면을 스캔한다.
    const asBroker = (extra) => async (page) => {
      await page.evaluate((id) => localStorage.setItem('currentInvestorId', String(id)), investor.id)
      await page.reload({ waitUntil: 'networkidle' })
      if (extra) await extra(page)
    }
    return [
      { name: 'broker-sync-connected', path: query(connected.id), needsAuth: true, prepare: asBroker() },
      {
        name: 'broker-sync-replace-form',
        path: query(connected.id),
        needsAuth: true,
        prepare: asBroker(async (page) => {
          await page.getByRole('button', { name: '키 교체' }).click()
          await page.getByLabel('앱키').waitFor()
        }),
      },
      { name: 'broker-sync-register-form', path: query(open.id), needsAuth: true, prepare: asBroker() },
    ]
  } catch (error) {
    console.log(`증권사 연동 상태별 스캔 준비 실패(건너뜀): ${error.message}`)
    return []
  }
}

// 가입 승인 화면 스캔: 스캔 계정을 DB에서 ADMIN으로 승격하고 승인 대기 계정을 하나 만든 뒤 다시 로그인한다.
// DB 접근(E2E_DB_NAME 또는 DB_NAME)이 없으면 건너뛴다. 백엔드는 APP_SIGNUP_MODE=OPEN이어야 한다.
async function setupAdminStates(browser, page) {
  if (!dbName || !scanEmail) {
    console.log('관리자 화면 스캔 건너뜀: DB 이름(E2E_DB_NAME/DB_NAME) 또는 스캔 계정이 없음')
    return []
  }
  try {
    const memberEmail = `testpending${Date.now()}@example.com`
    const memberContext = await browser.newContext()
    const created = await memberContext.request.fetch(`${baseUrl}${apiPrefix}/accounts`, {
      method: 'POST',
      data: { email: memberEmail, password: 'testpassword123!', name: 'Scan Pending' },
      headers: { 'Content-Type': 'application/json' },
    })
    await memberContext.close()
    if (!created.ok()) throw new Error(`대기 계정 가입 실패: ${created.status()}`)
    const sql = (statement) =>
      execFileSync('docker', ['exec', dbContainer, 'psql', '-U', dbUser, '-d', dbName, '-c', statement])
    // 잠긴 계정 표시('잠김'·'잠금 해제')도 스캔되도록 대기 계정을 30분 잠금 상태로 둔다(앱이 로컬 시각 KST로 저장하므로 Asia/Seoul 기준으로 넣는다).
    sql(`UPDATE accounts SET approved=false, failed_login_attempts=5, locked_until=(now() at time zone 'Asia/Seoul') + interval '30 minutes' WHERE email='${memberEmail}'`)
    sql(`UPDATE accounts SET role='ADMIN' WHERE email='${scanEmail}'`)

    // 역할은 로그인 시점의 세션에 담기므로 다시 로그인한다(로그인 상태면 /login이 대시보드로 보내므로 먼저 로그아웃).
    await page.context().request.post(`${baseUrl}${apiPrefix}/auth/logout`)
    await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
    await page.fill('#email', scanEmail)
    await page.fill('#password', 'testpassword123!')
    await page.click('button[type="submit"]')
    await page.waitForURL('**/dashboard', { timeout: 15000 })
    return [
      { name: 'admin-accounts-pending', path: '/admin/accounts', needsAuth: true },
      {
        name: 'admin-accounts-all',
        path: '/admin/accounts',
        needsAuth: true,
        prepare: async (p) => {
          await p.getByLabel('승인 대기만 보기').uncheck()
          await p.getByRole('table').waitFor()
        },
      },
      { name: 'admin-ipo-register', path: '/admin/ipos', needsAuth: true },
    ]
  } catch (error) {
    console.log(`관리자 화면 스캔 준비 실패(건너뜀): ${error.message}`)
    return []
  }
}


const day = (offset) => {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 달력·공모주 상세·수익·투자 내역 화면을 데이터가 있는 상태로 스캔하기 위한 시드. 관리자 승격(공모주 등록 API) 뒤에 부른다.
// 상세의 공시 구역은 ipo_details 예시 행을 SQL로 넣어 보이게 한다(실제 DART 호출 없음).
let tradeInvestorId = null // 거래 시드 투자자(증권사 화면 스캔이 현재 투자자를 바꾼 뒤 키보드 점검이 되돌린다)
async function setupTradeStates(page, context) {
  const call = async (method, path, data) => {
    const response = await context.request.fetch(`${baseUrl}${apiPrefix}${path}`, {
      method,
      data,
      headers: { 'Content-Type': 'application/json' },
    })
    if (!response.ok()) throw new Error(`${method} ${path} → ${response.status()} ${await response.text()}`)
    const text = await response.text()
    return text ? JSON.parse(text) : null
  }
  try {
    const kis = (await call('GET', '/securities-firms')).find((f) => f.name.includes('한국투자'))
    const investor = await call('POST', '/investors', { displayName: 'Scan Trader' })
    const account = await call('POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: kis.id,
      accountNumber: '3333333333',
      isDefault: true,
    })
    const stamp = Date.now() % 80
    const defs = [
      ['바다전자', -6, -4, -1, 10000],
      ['파도소프트', -1, 1, 5, 25000],
      ['산호테크', 3, 5, 10, 32000],
    ]
    const ipos = []
    for (const [i, [name, s, e, l, price]] of defs.entries()) {
      ipos.push(
        await call('POST', '/ipos', {
          companyName: name,
          ipoName: name,
          stockCode: String(200000 + i * 1111 + stamp * 10000),
          roundLabel: null,
          fixedPrice: price,
          subscriptionStartDate: day(s),
          subscriptionEndDate: day(e),
          listingDate: day(l),
          offeringShareCount: 1000000,
        }),
      )
    }
    const sub = await call('POST', '/subscriptions', {
      investorId: investor.id,
      securitiesAccountId: account.id,
      ipoId: ipos[0].id,
      quantity: 100,
      depositAmount: 500000,
      subscribedAt: `${day(-6)}T09:00:00`,
    })
    const alloc = await call('POST', '/allocations', {
      subscriptionId: sub.id,
      allocatedQuantity: 40,
      allocatedAt: `${day(-3)}T09:00:00`,
      allocationPrice: 10000,
    })
    await call('POST', '/sales', {
      allocationId: alloc.id,
      quantity: 25,
      price: 16000,
      amount: 400000,
      saleDate: `${day(-1)}T09:00:00`,
      fee: 600,
      tax: 920,
    })
    // 휴대폰 수익 카드형을 매도 2건으로 확인하기 위한 두 번째 매도
    await call('POST', '/sales', {
      allocationId: alloc.id,
      quantity: 10,
      price: 15000,
      amount: 150000,
      saleDate: `${day(-1)}T10:00:00`,
      fee: 200,
      tax: 345,
    })
    execFileSync('docker', ['exec', dbContainer, 'psql', '-U', dbUser, '-d', dbName, '-c', `INSERT INTO ipo_details (ipo_id, source_rcept_no, source_report_name, synced_at, ceo_name, established_on, address, par_value, capital_stock,
  largest_shareholder_name, largest_shareholder_ratio, institutional_competition_rate, lock_up_commitment_ratio, financials, float_schedule, underwriters, created_at, updated_at)
  SELECT id, '20260101000001', '투자설명서', now(), '가나다', DATE '2015-03-02', '서울특별시 예시구 예시로 100', 500, 3500000000,
  '예시홀딩스', 52.30, 850.40, 31.20,
  '[{"period":"제11기(2025년12월말)","revenue":24800000000,"operatingProfit":3300000000,"netIncome":2700000000},{"period":"제9기(2023년12월말)","revenue":8200000000,"operatingProfit":-900000000,"netIncome":-1200000000}]',
  '[{"label":"상장일 유통가능","shares":4200000,"ratio":38.50},{"label":"상장 후 1개월 유통가능","shares":5100000,"ratio":46.80}]',
  '[{"name":"예시증권","role":"대표","shares":700000},{"name":"샘플투자증권","role":"인수","shares":300000}]', now(), now()
  FROM ipos WHERE id=${ipos[0].id}`])
    // 투자자가 여럿이면 현재 투자자를 직접 골라야 하므로 시드한 투자자로 고정한다.
    await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
    await page.evaluate((id) => localStorage.setItem('currentInvestorId', String(id)), investor.id)
    tradeInvestorId = investor.id
    const openRow = async (p) => {
      await p.locator('tbody tr').first().click()
      await p.getByRole('button', { name: '청약 수정' }).waitFor()
    }
    return [
      { name: 'ipo-calendar', path: '/ipo-calendar', needsAuth: true },
      { name: 'ipo-detail', path: `/ipos/${ipos[0].id}`, needsAuth: true },
      { name: 'profits-graph', path: '/profits/view', needsAuth: true },
      { name: 'investments-detail-panel', path: '/investments', needsAuth: true, prepare: openRow },
      {
        name: 'investments-edit-dialog',
        path: '/investments',
        needsAuth: true,
        prepare: async (p) => {
          await openRow(p)
          await p.getByRole('button', { name: '청약 수정' }).click()
          await p.getByRole('dialog').waitFor()
        },
      },
      {
        name: 'investments-delete-dialog',
        path: '/investments',
        needsAuth: true,
        prepare: async (p) => {
          await openRow(p)
          await p.getByRole('button', { name: '청약 삭제' }).click()
          await p.getByRole('alertdialog').waitFor()
        },
      },
    ]
  } catch (error) {
    console.log(`거래 데이터 시드 실패(건너뜀): ${error.message}`)
    return []
  }
}

// 키보드만으로 수정 대화상자를 열고 닫고 저장할 수 있는지 확인한다(마우스 사용 없음). 어긋나면 error로 보고서에 남는다.
async function keyboardCheck(page) {
  const name = 'keyboard-edit-dialog'
  try {
    await page.setViewportSize({ width: 1280, height: 720 })
    if (tradeInvestorId) {
      await page.evaluate((id) => localStorage.setItem('currentInvestorId', String(id)), tradeInvestorId)
    }
    await page.goto(`${baseUrl}/investments`, { waitUntil: 'networkidle' })
    await page.locator('tbody tr').first().focus()
    await page.keyboard.press('Enter')
    const edit = page.getByRole('button', { name: '청약 수정' })
    await edit.waitFor()
    await edit.focus()
    const inDialog = () => page.evaluate(() => !!document.activeElement?.closest('[role="dialog"]'))
    const assert = (ok, msg) => { if (!ok) throw new Error(msg) }

    await page.keyboard.press('Enter')
    await page.getByRole('dialog').waitFor()
    assert(await inDialog(), '열린 직후 포커스가 대화상자 안에 없음')
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press('Tab')
      assert(await inDialog(), `Tab ${i + 1}번째에 포커스가 대화상자 밖으로 나감`)
    }
    await page.keyboard.press('Escape')
    await page.getByRole('dialog').waitFor({ state: 'detached' })
    assert(await edit.evaluate((el) => el === document.activeElement), 'Esc 뒤 포커스가 수정 버튼으로 돌아오지 않음')

    await page.keyboard.press('Enter')
    await page.getByRole('dialog').waitFor()
    await page.getByRole('button', { name: '저장' }).waitFor()
    await page.keyboard.press('Shift+Tab') // 대화상자 처음에서 거꾸로 가도 안에 머무는지
    assert(await inDialog(), 'Shift+Tab에서 포커스가 대화상자 밖으로 나감')
    await page.getByRole('button', { name: '저장' }).focus()
    await page.keyboard.press('Enter')
    await page.getByRole('dialog').waitFor({ state: 'detached' })
    console.log(`  ${name}: OK (열기·Tab 가둠·Esc 복귀·키보드 저장)`)
    return { page: name, path: '/investments', violations: [], passes: 0, incomplete: 0, inapplicable: 0 }
  } catch (error) {
    console.log(`  ${name}: FAIL - ${error.message}`)
    return { page: name, path: '/investments', error: error.message, violations: [] }
  }
}

// 비로그인 소개 페이지(/)는 로그인 세션과 분리된 컨텍스트로 스캔한다(로그인 상태면 대시보드로 가 버린다).
async function scanLanding(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 }, locale: 'ko-KR', colorScheme: 'light' })
  const p = await ctx.newPage()
  const out = []
  for (const vp of viewports) {
    await p.setViewportSize({ width: vp.width, height: vp.height })
    out.push(await scanPage(p, { name: `landing@${vp.name}`, path: '/' }))
    console.log(`  ${out.at(-1).page}: ${out.at(-1).violations.length} violations`)
  }
  await ctx.close()
  return out
}

// 가입 직후 계정(투자자 0명)의 안내 화면을 별도 컨텍스트로 스캔한다.
async function scanEmptyAccount(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 720 }, locale: 'ko-KR', colorScheme: 'light' })
  const p = await ctx.newPage()
  const out = []
  try {
    await p.goto(`${baseUrl}/signup`, { waitUntil: 'networkidle' })
    await p.fill('#email', `testempty${Date.now()}@example.com`)
    await p.fill('#password', 'testpassword123!')
    await p.fill('#name', 'Empty Scanner')
    await p.click('button[type="submit"]')
    await p.waitForURL('**/investors/register*', { timeout: 15000 })
    for (const path of ['/dashboard', '/investments', '/profits/view', '/subscriptions/register']) {
      for (const vp of viewports) {
        await p.setViewportSize({ width: vp.width, height: vp.height })
        out.push(await scanPage(p, { name: `empty-account${path.replaceAll('/', '-')}@${vp.name}`, path }))
        console.log(`  ${out.at(-1).page}: ${out.at(-1).violations.length} violations`)
      }
    }
  } catch (error) {
    console.log(`투자자 0명 스캔 실패: ${error.message}`)
    out.push({ page: 'empty-account', path: '/signup', error: error.message, violations: [] })
  }
  await ctx.close()
  return out
}

async function scanPage(page, pageInfo) {
  console.log(`Scanning ${pageInfo.name} at ${pageInfo.path}...`)
  
  try {
    await page.goto(`${baseUrl}${pageInfo.path}`, { waitUntil: 'networkidle', timeout: 30000 })
    
    // Wait for page to be ready
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(1000) // Give time for dynamic content
    if (pageInfo.prepare) await pageInfo.prepare(page)
    
    // Run axe-core scan
    const results = await new AxeBuilder({ page }).analyze()
    
    return {
      page: pageInfo.name,
      path: pageInfo.path,
      violations: results.violations,
      passes: results.passes.length,
      incomplete: results.incomplete.length,
      inapplicable: results.inapplicable.length,
      timestamp: new Date().toISOString()
    }
  } catch (error) {
    console.error(`Error scanning ${pageInfo.name}:`, error.message)
    return {
      page: pageInfo.name,
      path: pageInfo.path,
      error: error.message,
      violations: [],
      timestamp: new Date().toISOString()
    }
  }
}

async function main() {
  await mkdir(outputRoot, { recursive: true })
  
  const browser = await chromium.launch({ 
    headless: true,
    args: ['--disable-web-security', '--disable-features=IsolateOrigins', '--disable-site-isolation-trials']
  })
  const context = await browser.newContext({
    viewport: { width: 1280, height: 720 },
    deviceScaleFactor: 1,
    locale: 'ko-KR',
    colorScheme: 'light',
  })
  
  // Log network requests for debugging
  context.on('request', request => {
    if (request.url().includes('localhost:8080') || request.url().includes('auth') || request.url().includes('accounts')) {
      console.log(`>> REQUEST: ${request.method()} ${request.url()}`)
    }
  })
  context.on('response', response => {
    if (response.url().includes('localhost:8080') || response.url().includes('auth') || response.url().includes('accounts')) {
      console.log(`<< RESPONSE: ${response.status()} ${response.url()}`)
    }
  })
  
  const page = await context.newPage()
  
  // Login first for authenticated pages
  const loggedIn = await login(page, context)
  
  const results = []
  if (loggedIn) pages.push(...(await setupAdminStates(browser, page)))
  if (loggedIn) pages.push(...(await setupTradeStates(page, context)))
  if (loggedIn) pages.push(...(await setupBrokerStates(context)))

  for (const pageInfo of pages) {
    if (pageInfo.needsAuth && !loggedIn) {
      console.log(`Skipping ${pageInfo.name} - requires authentication`)
      results.push({
        page: pageInfo.name,
        path: pageInfo.path,
        skipped: true,
        reason: 'Authentication required but login failed',
        timestamp: new Date().toISOString()
      })
      continue
    }
    
    for (const vp of viewports) {
    await page.setViewportSize({ width: vp.width, height: vp.height })
    const result = await scanPage(page, { ...pageInfo, name: `${pageInfo.name}@${vp.name}` })
    results.push(result)
    
    // Print summary
    if (result.violations) {
      const bySeverity = {}
      for (const v of result.violations) {
        bySeverity[v.impact] = (bySeverity[v.impact] || 0) + v.nodes.length
      }
      console.log(`  ${result.page}: ${result.violations.length} violations (${Object.entries(bySeverity).map(([k,v])=>`${k}:${v}`).join(', ')})`)
    } else if (result.error) {
      console.log(`  ${pageInfo.name}: ERROR - ${result.error}`)
    } else if (result.skipped) {
      console.log(`  ${pageInfo.name}: SKIPPED - ${result.reason}`)
    }
    }
  }
  
  if (loggedIn) results.push(await keyboardCheck(page))
  results.push(...(await scanLanding(browser)))
  results.push(...(await scanEmptyAccount(browser)))

  await context.close()
  await browser.close()
  
  // Write detailed results
  await writeFile(resolve(outputRoot, 'accessibility-raw-results.json'), JSON.stringify(results, null, 2))
  
  // Generate markdown report
  const report = generateReport(results)
  await writeFile(resolve(outputRoot, 'accessibility-report.md'), report)
  
  console.log(`\nResults written to ${outputRoot}/accessibility-report.md`)
  console.log(`Raw results written to ${outputRoot}/accessibility-raw-results.json`)

  // 화면을 조용히 건너뛰면 '위반 0'이 거짓 안심이 된다(#421 가드 이후 실제로 그랬음). 기대 수보다 적게 검사했거나
  // 건너뜀·오류·위반이 있으면 실패 코드로 끝낸다. 화면을 추가·삭제하면 MIN_SCANNED도 맞춘다.
  const MIN_SCANNED = Number(process.env.A11Y_MIN_SCANNED ?? 63)
  const scannedCount = results.filter((r) => !r.skipped && !r.error).length
  const problems = results.filter((r) => r.skipped || r.error || (r.violations?.length ?? 0) > 0)
  if (scannedCount < MIN_SCANNED || problems.length > 0) {
    console.error(
      `\nFAIL: 검사 ${scannedCount}/${MIN_SCANNED}화면, 문제 ${problems.length}건: ` +
        problems.map((r) => r.page ?? r.path).join(', '),
    )
    process.exitCode = 1
  } else {
    console.log(`\nOK: ${scannedCount}화면 검사, 위반·건너뜀·오류 0`)
  }
}

function generateReport(results) {
  let md = '# 접근성 검사 보고서 (WCAG 2.1 AA / axe-core)\n\n'
  md += `검사 일시: ${new Date().toLocaleString('ko-KR')}\n`
  md += `검사 도구: axe-core @axe-core/playwright\n`
  md += `대상 URL: ${baseUrl}\n\n`
  
  // Summary
  const scanned = results.filter(r => !r.skipped && !r.error)
  const skipped = results.filter(r => r.skipped)
  const errors = results.filter(r => r.error)
  
  let totalViolations = 0
  const severityCounts = { critical: 0, serious: 0, moderate: 0, minor: 0 }
  
  for (const r of scanned) {
    for (const v of r.violations) {
      totalViolations++
      severityCounts[v.impact] = (severityCounts[v.impact] || 0) + v.nodes.length
    }
  }
  
  md += '## 요약\n\n'
  md += `- 검사한 화면: ${scanned.length}개\n`
  md += `- 건너뛴 화면: ${skipped.length}개\n`
  md += `- 오류 발생 화면: ${errors.length}개\n`
  md += `- 총 위반 건수: ${totalViolations}건 (critical: ${severityCounts.critical}, serious: ${severityCounts.serious}, moderate: ${severityCounts.moderate}, minor: ${severityCounts.minor})\n\n`
  
  // Pages with critical/serious violations
  const seriousPages = scanned
    .filter(r => r.violations.some(v => v.impact === 'critical' || v.impact === 'serious'))
    .sort((a, b) => {
      const aCount = a.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').reduce((sum, v) => sum + v.nodes.length, 0)
      const bCount = b.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').reduce((sum, v) => sum + v.nodes.length, 0)
      return bCount - aCount
    })
  
  if (seriousPages.length > 0) {
    md += '## 심각도 critical/serious 위반이 있는 화면 (상위)\n\n'
    for (const r of seriousPages.slice(0, 10)) {
      const count = r.violations.filter(v => v.impact === 'critical' || v.impact === 'serious').reduce((sum, v) => sum + v.nodes.length, 0)
      md += `- **${r.page}** (${r.path}): ${count}건\n`
    }
    md += '\n'
  }
  
  // Detailed results per page
  md += '## 화면별 상세 결과\n\n'
  
  for (const r of results) {
    md += `### ${r.page} (${r.path})\n\n`
    
    if (r.skipped) {
      md += `> ⚠️ **건너뜀**: ${r.reason}\n\n`
      continue
    }
    
    if (r.error) {
      md += `> ❌ **오류**: ${r.error}\n\n`
      continue
    }
    
    if (r.violations.length === 0) {
      md += `✅ **위반 0건** (passes: ${r.passes}, incomplete: ${r.incomplete}, inapplicable: ${r.inapplicable})\n\n`
      continue
    }
    
    // Group by severity
    const bySeverity = { critical: [], serious: [], moderate: [], minor: [] }
    for (const v of r.violations) {
      bySeverity[v.impact].push(v)
    }
    
    for (const severity of ['critical', 'serious', 'moderate', 'minor']) {
      const violations = bySeverity[severity]
      if (violations.length === 0) continue
      
      const nodeCount = violations.reduce((sum, v) => sum + v.nodes.length, 0)
      md += `#### ${severity.toUpperCase()} (${violations.length}개 규칙, ${nodeCount}개 요소)\n\n`
      
      for (const v of violations) {
        md += `- **${v.id}**: ${v.help} (${v.nodes.length}개 요소)\n`
        md += `  - 설명: ${v.description}\n`
        md += `  - 도움말: ${v.helpUrl}\n`
        md += `  - 영향: ${v.impact}\n`
        if (v.nodes.length > 0) {
          md += `  - 예시 요소:\n`
          for (const node of v.nodes.slice(0, 3)) {
            md += `    - \`${node.html}\` (selector: ${node.target.join(' > ')})\n`
          }
          if (v.nodes.length > 3) {
            md += `    - ... 외 ${v.nodes.length - 3}개 더\n`
          }
        }
        md += '\n'
      }
    }
  }
  
  // Skipped pages summary
  if (skipped.length > 0) {
    md += '## 건너뛴 화면\n\n'
    for (const r of skipped) {
      md += `- ${r.page} (${r.path}): ${r.reason}\n`
    }
    md += '\n'
  }
  
  // Error pages summary
  if (errors.length > 0) {
    md += '## 오류 발생 화면\n\n'
    for (const r of errors) {
      md += `- ${r.page} (${r.path}): ${r.error}\n`
    }
    md += '\n'
  }
  
  md += '---\n\n*이 보고서는 axe-core 자동 검사 결과에 기반합니다. 실제 사용자 테스트와 전문가 검토를 병행하시기 바랍니다.*\n'
  
  return md
}

main().catch((e) => {
  console.error(e)
  process.exitCode = 1
})