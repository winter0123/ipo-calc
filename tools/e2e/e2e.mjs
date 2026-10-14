import { chromium } from 'playwright'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const BASE_URL = (
  process.env.BASE_URL ?? readFileSync(resolve(import.meta.dirname, '.base-url'), 'utf8')
).trim()
const PASSWORD = 'testpassword123!'
const WRONG_PASSWORD = 'wrongpassword987!'
const ACCOUNT_NUMBER = '1234567890123456'
const LAST4 = ACCOUNT_NUMBER.slice(-4)
const MISSING_INVESTOR_ID = '999999'
const API_PREFIX = process.env.E2E_API_PREFIX ?? '/api-proxy'
const DB_NAME = process.env.E2E_DB_NAME
const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? 'vibe-ai-team-postgres-dev'
const DB_USER = process.env.E2E_DB_USER ?? 'vibe'

const results = []
const shared = { account: null, seq: 0, trade: null }

function uniqueEmail() {
  shared.seq += 1
  return `e2e-${Date.now()}-${shared.seq}@example.com`
}

function charWidth(ch) {
  return ch.codePointAt(0) > 0xff ? 2 : 1
}

function textWidth(text) {
  let total = 0
  for (const ch of String(text)) total += charWidth(ch)
  return total
}

function pad(text, width) {
  const value = String(text)
  return value + ' '.repeat(Math.max(0, width - textWidth(value)))
}

const diag = []
let shots = 0

async function openContext(browser) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'ko-KR',
    colorScheme: 'light',
  })
  const page = await context.newPage()
  // 실패 진단: 4xx/5xx 응답·콘솔 에러를 버퍼에 쌓고(FAIL 시 출력), E2E_ARTIFACTS 가 있으면 닫기 직전 스크린샷+URL 저장.
  page.on('response', (res) => {
    if (res.status() >= 400) diag.push(`${res.status()} ${res.request().method()} ${res.url()}`)
  })
  page.on('framenavigated', (f) => f === page.mainFrame() && diag.push(`nav: ${f.url()}`))
  page.on('console', (m) => m.type() === 'error' && diag.push(`console: ${m.text()}`))
  const close = context.close.bind(context)
  context.close = async () => {
    if (process.env.E2E_ARTIFACTS) {
      diag.push(`url: ${page.url()}`)
      await page.screenshot({ path: `${process.env.E2E_ARTIFACTS}/ctx-${++shots}.png`, fullPage: true }).catch(() => {})
    }
    return close()
  }
  page.setDefaultTimeout(10000)
  page.setDefaultNavigationTimeout(20000)
  return { context, page }
}

async function goto(page, path) {
  await page.goto(`${BASE_URL}${path}`, { waitUntil: 'domcontentloaded' })
}

async function waitForUrl(page, pattern, timeout = 15000) {
  try {
    await page.waitForURL(pattern, { timeout })
    return true
  } catch {
    return false
  }
}

async function waitFor(predicate, timeout = 15000) {
  const started = Date.now()
  while (Date.now() - started < timeout) {
    if (await predicate()) return true
    await new Promise((r) => setTimeout(r, 200))
  }
  return false
}

async function errorTexts(page) {
  const locator = page.locator('[aria-live="polite"], [role="alert"]')
  const count = await withTimeout(locator.count(), 10000, 'errorTexts count 타임아웃')
  const texts = []
  for (let i = 0; i < count; i += 1) {
    const item = locator.nth(i)
    if (await item.isVisible().catch(() => false)) {
      const text = (await item.innerText().catch(() => '')).trim()
      if (text) texts.push(text)
    }
  }
  return texts
}

function hasText(texts, expected) {
  return texts.some((t) => t.includes(expected))
}

function pathname(page) {
  try {
    return new URL(page.url()).pathname
  } catch {
    return page.url()
  }
}

async function fillSignup(page, email, name = 'E2E User') {
  await goto(page, '/signup')
  await page.waitForSelector('form')
  await page.fill('#email', email)
  await page.fill('#password', PASSWORD)
  await page.fill('#name', name)
}

async function submit(page) {
  await page.click('button[type="submit"]')
}

async function login(page, email, password) {
  const responsePromise = page
    .waitForResponse(
      (res) =>
        res.request().method() === 'POST' && new URL(res.url()).pathname.endsWith('/auth/login'),
      { timeout: 15000 },
    )
    .catch(() => null)

  await goto(page, '/login')
  await page.waitForSelector('form')
  await page.fill('#email', email)
  await page.fill('#password', password)
  await submit(page)

  const ok = await waitForUrl(page, '**/dashboard', 15000)
  // 라우터는 lazy 청크가 로드될 때까지 옛 경로를 유지하다 한꺼번에 전환한다. 그 전에 헤더 메뉴를 열면 전환 순간 메뉴가 닫혀
  // 클릭이 타임아웃(E2E-12·13 간헐 실패, 콜드 vite 캐시인 CI에서 재현) → 본문 제목이 뜰 때까지 기다린다.
  if (ok) await page.locator('#main-content h1').first().waitFor().catch(() => {})
  const response = await responsePromise
  const bodyText = response
    ? await withTimeout(response.text(), 10000, 'response.text 타임아웃').catch(() => '<본문 읽기 실패>')
    : null
  const texts = await errorTexts(page)
  return {
    ok,
    status: response ? response.status() : null,
    bodyText,
    texts,
    url: page.url(),
  }
}

async function withTimeout(promise, ms, message) {
  let timer
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => reject(new Error(message)), ms)
  })
  try {
    return await Promise.race([promise, timeout])
  } finally {
    clearTimeout(timer)
  }
}

async function authenticate(page, account, notes) {
  const attempt = await login(page, account.email, account.password)
  if (attempt.ok) {
    notes.push('인증=로그인 UI 성공')
    return
  }
  notes.push(
    `인증=로그인 UI 실패(POST /auth/login 상태=${attempt.status === null ? '응답 없음' : attempt.status}, 본문=${attempt.bodyText === null ? '없음' : `"${attempt.bodyText}"`}, 화면=${attempt.texts.join(' | ') || '(없음)'}) → 회원가입 세션으로 대체`,
  )
  const email = uniqueEmail()
  await fillSignup(page, email)
  await submit(page)
  if (!(await waitForUrl(page, '**/investors/register', 15000))) {
    const texts = await errorTexts(page)
    throw new Error(
      `대체 인증(회원가입) 실패: URL=${page.url()} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
    )
  }
  shared.account = { email, password: PASSWORD }
  notes.push('인증=회원가입으로 생성한 세션')
}

async function ensureAccount(browser) {
  if (shared.account) return shared.account
  const { context, page } = await openContext(browser)
  try {
    const email = uniqueEmail()
    await fillSignup(page, email)
    await submit(page)
    const ok = await waitForUrl(page, '**/investors/register', 15000)
    if (!ok) {
      const texts = await errorTexts(page)
      throw new Error(
        `계정 생성(회원가입) 실패: URL=${page.url()} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }
    shared.account = { email, password: PASSWORD }
    return shared.account
  } finally {
    await context.close()
  }
}

async function waitForFirmsLoaded(page) {
  return waitFor(async () => {
    const count = await page.locator('#securitiesFirmId option').count()
    return count > 1
  }, 15000)
}

async function scenario1(browser) {
  const { context, page } = await openContext(browser)
  try {
    const email = uniqueEmail()
    const notes = []

    await fillSignup(page, email, 'E2E User')
    await submit(page)
    if (!(await waitForUrl(page, '**/investors/register', 15000))) {
      const texts = await errorTexts(page)
      throw new Error(
        `1단계 회원가입 실패: 기대=/investors/register 이동 vs 실제 URL=${page.url()} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }
    shared.account = { email, password: PASSWORD }
    notes.push('회원가입 201 → /investors/register 이동 확인')

    const investorResponse = page
      .waitForResponse(
        (res) =>
          res.request().method() === 'POST' && new URL(res.url()).pathname.endsWith('/investors'),
        { timeout: 15000 },
      )
      .catch(() => null)

    await page.fill('#displayName', 'E2E Investor')
    await submit(page)
    const investorOk = await waitForUrl(page, '**/dashboard', 15000)
    const response = await investorResponse
    const body = response ? await response.json().catch(() => null) : null
    const investorId = body && typeof body.id === 'number' ? body.id : null

    if (!investorOk) {
      const texts = await errorTexts(page)
      throw new Error(
        `2단계 투자자 등록 실패: 기대=/dashboard 이동 vs 실제 URL=${page.url()} / POST /investors 상태=${response ? response.status() : '응답 없음'} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }
    if (investorId === null) {
      throw new Error(
        `2단계 투자자 등록 응답에서 investorId 확인 불가: POST /investors 상태=${response ? response.status() : '응답 없음'}`,
      )
    }
    notes.push(`투자자 등록 201 → /dashboard 이동, investorId=${investorId}`)

    await goto(page, `/securities-accounts/register/${investorId}`)
    if (!(await waitForFirmsLoaded(page))) {
      const texts = await errorTexts(page)
      throw new Error(
        `3단계 증권사 목록 로드 실패: 드롭다운 옵션 1개 이하(시드/인증 확인 필요) / 화면 텍스트=${texts.join(' | ') || '(없음)'}`,
      )
    }
    await page.selectOption('#securitiesFirmId', { index: 1 })
    await page.fill('#accountNumber', ACCOUNT_NUMBER)

    let dialogText = null
    page.on('dialog', (dialog) => {
      dialogText = dialog.message()
      dialog.accept().catch(() => {})
    })

    await submit(page)
    if (!(await waitFor(() => dialogText !== null, 15000))) {
      const texts = await errorTexts(page)
      throw new Error(
        `3단계 증권계좌 등록 실패: 마스킹 안내 미발생 / URL=${page.url()} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }

    const maskedOk =
      dialogText.includes(LAST4) &&
      dialogText.includes('*') &&
      !dialogText.includes(ACCOUNT_NUMBER)
    if (!maskedOk) {
      throw new Error(
        `마스킹 계좌번호 아님: 기대(spec)=뒷 4자리(${LAST4})만 노출된 마스킹 문자열 / 실제="${dialogText}"`,
      )
    }

    if (!(await waitForUrl(page, '**/dashboard', 15000))) {
      throw new Error(
        `최종 확인 실패: 기대=/dashboard 이동 vs 실제 URL=${page.url()}`,
      )
    }

    notes.push(`마스킹 계좌번호 표시="${dialogText}"`)
    notes.push(`최종 URL=${pathname(page)}`)
    return notes.join(' / ')
  } finally {
    await context.close()
  }
}

async function scenario2(browser) {
  const account = await ensureAccount(browser)
  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, account.email, account.password)
    if (!attempt.ok) {
      throw new Error(
        `로그인 실패: 기대(spec)=200 응답 → /dashboard 이동 vs 실제 URL=${attempt.url} / POST /auth/login 상태=${attempt.status === null ? '응답 없음' : attempt.status} 본문=${attempt.bodyText === null ? '없음' : `"${attempt.bodyText}"`} / 화면 에러=${attempt.texts.join(' | ') || '(없음)'}`,
      )
    }
    return `새 컨텍스트 로그인 성공 → URL=${pathname(page)}`
  } finally {
    await context.close()
  }
}

async function scenario3(browser) {
  const account = await ensureAccount(browser)
  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, account.email, WRONG_PASSWORD)

    const expected = '이메일 또는 비밀번호가 올바르지 않습니다.'
    const shown = hasText(attempt.texts, expected)

    if (!shown) {
      throw new Error(
        `인라인 에러 미표시: 기대(spec)="${expected}" vs 실제 화면=${attempt.texts.join(' | ') || '(없음)'} / POST /auth/login 상태=${attempt.status === null ? '응답 없음' : attempt.status} / URL=${attempt.url}`,
      )
    }
    if (attempt.ok) {
      throw new Error(`잘못된 비밀번호인데 /dashboard로 이동: URL=${attempt.url}`)
    }
    return `POST /auth/login 상태=${attempt.status === null ? '응답 없음' : attempt.status}, 인라인 에러="${attempt.texts.find((t) => t.includes('올바르지'))}", URL=${pathname(page)}(이동 없음)`
  } finally {
    await context.close()
  }
}

async function scenario4(browser) {
  const account = await ensureAccount(browser)
  const { context, page } = await openContext(browser)
  try {
    const responsePromise = page
      .waitForResponse(
        (res) =>
          res.request().method() === 'POST' && new URL(res.url()).pathname.endsWith('/accounts'),
        { timeout: 15000 },
      )
      .catch(() => null)

    await fillSignup(page, account.email)
    await submit(page)

    const expected = '이미 사용 중인 이메일입니다.'
    const shown = await waitFor(async () => hasText(await errorTexts(page), expected), 15000)
    const texts = await errorTexts(page)
    const response = await responsePromise

    if (!shown) {
      throw new Error(
        `인라인 에러 미표시: 기대(spec)="${expected}" vs 실제 화면=${texts.join(' | ') || '(없음)'} / POST /accounts 상태=${response ? response.status() : '응답 없음'} / URL=${page.url()}`,
      )
    }
    if (pathname(page) !== '/signup') {
      throw new Error(`중복 이메일인데 회원가입 화면을 벗어남: URL=${page.url()}`)
    }
    return `POST /accounts 상태=${response ? response.status() : '응답 없음'}, 인라인 에러="${texts.find((t) => t.includes('이미 사용'))}", URL=${pathname(page)}(이동 없음)`
  } finally {
    await context.close()
  }
}

async function scenario5(browser) {
  const account = await ensureAccount(browser)
  const { context, page } = await openContext(browser)
  try {
    const notes = []
    await authenticate(page, account, notes)

    let investorPosts = 0
    page.on('request', (request) => {
      if (request.method() === 'POST' && new URL(request.url()).pathname.endsWith('/investors')) {
        investorPosts += 1
      }
    })

    await goto(page, '/investors/register')
    await page.waitForSelector('form')
    await page.fill('#displayName', '')
    await submit(page)

    const expected = '표시 이름은 필수입니다.'
    const shown = await waitFor(async () => hasText(await errorTexts(page), expected), 10000)
    const texts = await errorTexts(page)

    if (!shown) {
      throw new Error(
        `인라인 에러 미표시: 기대(spec)="${expected}" vs 실제 화면=${texts.join(' | ') || '(없음)'} / URL=${page.url()}`,
      )
    }
    if (pathname(page) !== '/investors/register') {
      throw new Error(`등록 실패인데 화면을 벗어남: URL=${page.url()}`)
    }
    if (investorPosts > 0) {
      throw new Error(`POST /investors가 ${investorPosts}건 발생(등록 시도됨): URL=${page.url()}`)
    }
    return [...notes, `인라인 에러="${texts.find((t) => t.includes('표시 이름'))}", POST /investors ${investorPosts}건, URL=${pathname(page)}`].join(' / ')
  } finally {
    await context.close()
  }
}

async function scenario6(browser) {
  const account = await ensureAccount(browser)
  const { context, page } = await openContext(browser)
  try {
    const notes = []
    await authenticate(page, account, notes)

    await goto(page, `/securities-accounts/register/${MISSING_INVESTOR_ID}`)
    if (!(await waitForFirmsLoaded(page))) {
      const texts = await errorTexts(page)
      throw new Error(
        `증권사 목록 로드 실패: 드롭다운 옵션 1개 이하 / 화면 텍스트=${texts.join(' | ') || '(없음)'}`,
      )
    }
    await page.selectOption('#securitiesFirmId', { index: 1 })
    await page.fill('#accountNumber', ACCOUNT_NUMBER)

    const responsePromise = page
      .waitForResponse(
        (res) =>
          res.request().method() === 'POST' &&
          new URL(res.url()).pathname.endsWith('/securities-accounts'),
        { timeout: 15000 },
      )
      .catch(() => null)

    await submit(page)

    const expected = '투자자 정보를 찾을 수 없습니다.'
    const shown = await waitFor(async () => hasText(await errorTexts(page), expected), 15000)
    const texts = await errorTexts(page)
    const response = await responsePromise

    if (!shown) {
      throw new Error(
        `인라인 에러 미표시: 기대(spec)="${expected}" vs 실제 화면=${texts.join(' | ') || '(없음)'} / POST /securities-accounts 상태=${response ? response.status() : '응답 없음'} / URL=${page.url()}`,
      )
    }
    return [
      ...notes,
      `인라인 에러="${texts.find((t) => t.includes('찾을 수'))}", POST 상태=${response ? response.status() : '응답 없음'}, URL=${pathname(page)}`,
    ].join(' / ')
  } finally {
    await context.close()
  }
}

const TRADE = {
  allocatedQuantity: 100,
  allocationPrice: 10000,
  sales: [
    { quantity: 30, price: 15000, amount: 450000, fee: 100, tax: 50 },
    { quantity: 50, price: 12000, amount: 600000, fee: 200, tax: 100 },
  ],
  // 안분 규칙(#243): 총투자금 = 배정단가 × 매도수량 합, 참여 건수 = 서로 다른 배정 수
  // 세전 = 매도금액 − 배정단가×매도수량 − 수수료, 세후 = 세전 − 세금 (비용 없음)
  expected: {
    investment: 800000,
    preTax: 249700,
    afterTax: 249550,
    rate: '31.2%',
    participation: 1,
  },
}

function localDateTime(hour = 9) {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(hour)}:00`
}

function localDate() {
  return localDateTime().slice(0, 10)
}

async function api(context, method, path, data) {
  const response = await context.request.fetch(`${BASE_URL}${API_PREFIX}${path}`, {
    method,
    data,
    headers: { 'Content-Type': 'application/json' },
  })
  const text = await response.text()
  if (!response.ok()) {
    throw new Error(`API ${method} ${path} 실패: 상태=${response.status()} 본문=${text}`)
  }
  return text ? JSON.parse(text) : null
}

async function apiLogin(context, email) {
  await api(context, 'POST', '/auth/login', { email, password: PASSWORD })
}

function runSql(statement) {
  if (!DB_NAME) {
    throw new Error(
      'DB 직접 수정이 필요한 시나리오라 E2E_DB_NAME(예: vibe_slot_2) 환경변수를 지정해야 한다',
    )
  }
  execFileSync('docker', ['exec', DB_CONTAINER, 'psql', '-U', DB_USER, '-d', DB_NAME, '-c', statement])
}

function promoteToAdmin(email) {
  runSql(`UPDATE accounts SET role='ADMIN' WHERE email='${email}'`)
}

// 거래 흐름 시나리오용 픽스처: 계정·투자자·증권계좌는 API로, 공모주는 ADMIN 승격 후 API로 만든다(UI에 공모주 등록 화면 없음).
async function ensureTrade(browser) {
  if (shared.trade) return shared.trade
  const email = uniqueEmail()
  const ipoName = `E2E공모주-${Date.now()}`
  const investorName = 'E2E Trader'
  const setup = await browser.newContext()
  try {
    await api(setup, 'POST', '/accounts', { email, password: PASSWORD, name: 'E2E Trader' })
    await apiLogin(setup, email)
    const firms = await api(setup, 'GET', '/securities-firms')
    if (!firms || firms.length === 0) throw new Error('증권사 목록이 비어 있음(시드 확인 필요)')
    const investor = await api(setup, 'POST', '/investors', { displayName: investorName })
    await api(setup, 'POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: firms[0].id,
      accountNumber: ACCOUNT_NUMBER,
      isDefault: true,
    })
    promoteToAdmin(email)
  } finally {
    await setup.close()
  }
  const admin = await browser.newContext()
  try {
    await apiLogin(admin, email)
    await api(admin, 'POST', '/ipos', {
      companyName: ipoName,
      ipoName,
      subscriptionStartDate: localDate(),
      subscriptionEndDate: localDate(),
      fixedPrice: TRADE.allocationPrice,
      offeringShareCount: 1000000,
    })
  } finally {
    await admin.close()
  }
  shared.trade = { email, password: PASSWORD, ipoName, investorName, subscriptionId: null }
  return shared.trade
}

async function loginTrader(browser, trade) {
  const { context, page } = await openContext(browser)
  const attempt = await login(page, trade.email, trade.password)
  if (!attempt.ok) {
    await context.close()
    throw new Error(
      `거래 계정 로그인 실패: 상태=${attempt.status} / 화면 에러=${attempt.texts.join(' | ') || '(없음)'}`,
    )
  }
  return { context, page }
}

function requirePrevious(condition, id) {
  if (!condition) throw new Error(`선행 시나리오 ${id}가 통과하지 않아 진행할 수 없다`)
}

async function scenario7(browser) {
  const trade = await ensureTrade(browser)
  const { context, page } = await loginTrader(browser, trade)
  try {
    const responsePromise = page
      .waitForResponse(
        (res) =>
          res.request().method() === 'POST' && new URL(res.url()).pathname.endsWith('/subscriptions'),
        { timeout: 15000 },
      )
      .catch(() => null)

    const failedApis = []
    page.on('response', (res) => {
      if (res.status() >= 400) failedApis.push(`${res.request().method()} ${new URL(res.url()).pathname} → ${res.status()}`)
    })

    await goto(page, '/subscriptions/register')
    const formShown = await waitFor(async () => {
      if ((await page.locator('form[aria-label="청약 등록 폼"]').count()) > 0) return true
      return (await page.locator('body').innerText()).includes('목록을 불러올 수 없습니다')
    }, 30000)
    if (!formShown || (await page.locator('form[aria-label="청약 등록 폼"]').count()) === 0) {
      const body = (await page.locator('body').innerText()).replace(/\s+/g, ' ')
      throw new Error(
        `청약 등록 폼 미표시: 기대=투자자·증권계좌·공모주 드롭다운이 채워진 폼 vs 실제 화면="${body.slice(0, 120)}" / 실패한 API=${[...new Set(failedApis)].join(', ') || '(없음)'}`,
      )
    }
    await page.selectOption('#securitiesAccountId', { index: 1 })
    await page.selectOption('#ipoId', { label: trade.ipoName })
    await page.fill('#quantity', String(TRADE.allocatedQuantity))
    await page.fill('#depositAmount', '500000')
    await page.fill('#subscribedAt', localDateTime().slice(0, 10))
    await submit(page)

    const response = await responsePromise
    const body = response ? await response.json().catch(() => null) : null
    if (!(await waitForUrl(page, '**/subscriptions', 15000))) {
      const texts = await errorTexts(page)
      throw new Error(
        `청약 등록 실패: 기대=/subscriptions 이동 vs 실제 URL=${page.url()} / POST 상태=${response ? response.status() : '응답 없음'} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }
    trade.subscriptionId = body && typeof body.id === 'number' ? body.id : null

    const card = page.locator('li', { hasText: trade.ipoName })
    await card.first().waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
    const cardText = (await card.count()) > 0 ? await card.first().innerText() : ''
    if (!cardText.includes(`${TRADE.allocatedQuantity}주`) || !cardText.includes('미배정')) {
      throw new Error(
        `목록 표시 불일치: 기대=${trade.ipoName}·${TRADE.allocatedQuantity}주·미배정 vs 실제 카드="${cardText.replace(/\s+/g, ' ') || '(카드 없음)'}"`,
      )
    }
    trade.subscribed = true
    return `POST /subscriptions 상태=${response.status()}, subscriptionId=${trade.subscriptionId}, 카드="${cardText.replace(/\s+/g, ' ')}"`
  } finally {
    await context.close()
  }
}

// E2E-7(UI)이 실패해도 후속 시나리오를 검증할 수 있도록 API로 청약을 대신 만든다.
async function ensureSubscription(browser, trade, notes) {
  if (trade.subscribed) return
  const context = await browser.newContext()
  try {
    await apiLogin(context, trade.email)
    const investors = await api(context, 'GET', '/investors')
    const accounts = await api(context, 'GET', `/securities-accounts?investorId=${investors[0].id}`)
    const ipos = await api(context, 'GET', '/ipos')
    const ipo = ipos.find((i) => i.ipoName === trade.ipoName)
    const created = await api(context, 'POST', '/subscriptions', {
      investorId: investors[0].id,
      securitiesAccountId: accounts[0].id,
      ipoId: ipo.id,
      quantity: TRADE.allocatedQuantity,
      depositAmount: 500000,
      subscribedAt: localDateTime(),
    })
    trade.subscriptionId = created.id
    trade.subscribed = true
    notes.push(`E2E-7 미통과로 청약을 API로 대체 생성(subscriptionId=${created.id})`)
  } finally {
    await context.close()
  }
}

async function scenario8(browser) {
  const trade = await ensureTrade(browser)
  const preNotes = []
  await ensureSubscription(browser, trade, preNotes)
  const { context, page } = await loginTrader(browser, trade)
  try {
    await goto(page, '/subscriptions')
    const card = page.locator('li', { hasText: trade.ipoName }).first()
    await card.waitFor({ state: 'visible', timeout: 15000 })
    await card.getByRole('link', { name: '배정 입력' }).click()
    await page.waitForSelector('form[aria-label="배정 등록 폼"]')

    const responsePromise = page
      .waitForResponse(
        (res) =>
          res.request().method() === 'POST' && new URL(res.url()).pathname.endsWith('/allocations'),
        { timeout: 15000 },
      )
      .catch(() => null)

    await page.fill('#allocatedQuantity', String(TRADE.allocatedQuantity))
    await page.fill('#allocatedAt', localDateTime(10).slice(0, 10))
    await page.fill('#allocationPrice', String(TRADE.allocationPrice))
    await submit(page)

    const response = await responsePromise
    if (!(await waitForUrl(page, '**/subscriptions', 15000))) {
      const texts = await errorTexts(page)
      throw new Error(
        `배정 등록 실패: 기대=/subscriptions 이동 vs 실제 URL=${page.url()} / POST 상태=${response ? response.status() : '응답 없음'} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
      )
    }
    const after = page.locator('li', { hasText: trade.ipoName }).first()
    await after.waitFor({ state: 'visible', timeout: 15000 })
    const shown = await waitFor(async () => (await after.innerText()).includes('배정완료'), 10000)
    const cardText = (await after.innerText()).replace(/\s+/g, ' ')
    if (!shown) {
      throw new Error(`배정 표시 불일치: 기대=배정완료 배지 vs 실제 카드="${cardText}"`)
    }
    trade.allocated = true
    return [...preNotes, `POST /allocations 상태=${response ? response.status() : '응답 없음'}, 카드="${cardText}"`].join(' / ')
  } finally {
    await context.close()
  }
}

async function registerSale(page, trade, sale) {
  await goto(page, '/sales/register')
  await page.waitForSelector('form[aria-label="매도 등록 폼"]')
  await waitFor(async () => (await page.locator('#subscriptionId option').count()) > 1)
  await page.selectOption('#subscriptionId', { index: 1 })
  // 청약당 배정은 하나라 선택 칸 없이 자동 연결된다(숨은 입력에 배정 ID가 채워질 때까지 기다린다).
  await waitFor(async () => (await page.locator('#allocationId').inputValue().catch(() => '')) !== '')
  // 매도수량·매도단가·매도일는 공모주 정보로 미리 채워지지만 사용자가 고칠 수 있다(여기서는 직접 입력).
  // 공모주수수료(#ipoFee)는 저장 시 fee, 세금 및 기타 수수료(#otherFees)는 tax로 간다. 실현손익은 공식으로 자동 계산된다.
  await page.fill('#quantity', String(sale.quantity))
  await page.fill('#price', String(sale.price))
  await page.fill('#saleDate', localDateTime(14).slice(0, 10))
  await page.fill('#ipoFee', String(sale.fee))
  await page.fill('#otherFees', String(sale.tax))
  await submit(page)
  if (!(await waitForUrl(page, '**/investments', 15000))) {
    const texts = await errorTexts(page)
    throw new Error(
      `매도 등록 실패(${sale.quantity}주): 기대=/investments 이동 vs 실제 URL=${page.url()} / 화면 에러=${texts.join(' | ') || '(없음)'}`,
    )
  }
}

async function cardValue(page, label) {
  const locator = page.locator(`[aria-label^="${label}: "]`).first()
  await locator.waitFor({ state: 'visible', timeout: 15000 }).catch(() => {})
  return (await locator.count()) > 0 ? (await locator.innerText()).trim() : '(없음)'
}

async function scenario9(browser) {
  const trade = shared.trade
  requirePrevious(trade?.allocated, 'E2E-8')
  const { context, page } = await loginTrader(browser, trade)
  try {
    for (const sale of TRADE.sales) await registerSale(page, trade, sale)

    await goto(page, '/dashboard')
    await page.waitForSelector('h1')
    const e = TRADE.expected
    const expected = {
      '총 투자금': `${e.investment.toLocaleString('en-US')}원`,
      최종수익금: `${e.afterTax.toLocaleString('en-US')}원`,
      수익률: e.rate,
      '참여 건수': `${e.participation}건`,
    }
    const actual = {}
    for (const label of Object.keys(expected)) actual[label] = await cardValue(page, label)
    const mismatches = Object.keys(expected)
      .filter((label) => actual[label] !== expected[label])
      .map((label) => `${label}: 기대=${expected[label]} vs 실제=${actual[label]}`)
    if (mismatches.length > 0) {
      throw new Error(`대시보드 종합 불일치(안분 규칙 #243): ${mismatches.join(' / ')}`)
    }

    // 종합 아래 투자자 섹션의 총 투자금이 합계와 같아야 한다(투자자가 1명이므로)
    const section = page.getByRole('region', { name: trade.investorName })
    await section.waitFor({ state: 'visible', timeout: 10000 }).catch(() => {})
    const investorText =
      (await section.count()) > 0
        ? (await section.locator('[aria-label^="총 투자금: "]').first().innerText()).trim()
        : '(없음)'
    const line = `${e.investment.toLocaleString('en-US')}원`
    if (investorText !== line) {
      throw new Error(`투자자 섹션 불일치: 기대="${line}" vs 실제="${investorText}"`)
    }
    trade.sold = true
    return `매도 ${TRADE.sales.length}건(${TRADE.sales.map((s) => s.quantity).join('+')}주/배정 ${TRADE.allocatedQuantity}주) → ${Object.entries(actual).map(([k, v]) => `${k}=${v}`).join(', ')}, 투자자 섹션 총 투자금="${investorText}"`
  } finally {
    await context.close()
  }
}

async function scenario10(browser) {
  const trade = shared.trade
  requirePrevious(trade?.allocated, 'E2E-8')
  const other = await ensureAccount(browser)
  requirePrevious(other.email !== trade.email, 'E2E-10 (다른 계정 확보)')
  const { context, page } = await openContext(browser)
  try {
    const notes = []
    await authenticate(page, other, notes)

    await goto(page, '/dashboard')
    const emptyShown = await waitFor(async () => {
      const text = await page.locator('body').innerText()
      return text.includes('해당 조건에 해당하는 기록이 없습니다.')
    }, 15000)
    const dashboardText = await page.locator('body').innerText()
    if (!emptyShown || dashboardText.includes(trade.investorName)) {
      throw new Error(
        `다른 계정 대시보드에 데이터 노출: 기대=빈 상태 vs 실제="${dashboardText.replace(/\s+/g, ' ').slice(0, 200)}"`,
      )
    }

    await goto(page, '/subscriptions')
    await page.getByRole('heading', { name: '청약 목록' }).waitFor()
    const listed = await waitFor(async () => {
      const text = await page.locator('body').innerText()
      return text.includes('표시할 청약이 없습니다.') || text.includes(trade.ipoName)
    }, 15000)
    const listText = await page.locator('body').innerText()
    if (!listed || listText.includes(trade.ipoName)) {
      throw new Error(
        `다른 계정 청약 목록에 타인 데이터 노출: 기대=표시할 청약이 없습니다. vs 실제="${listText.replace(/\s+/g, ' ').slice(0, 200)}"`,
      )
    }

    if (trade.subscriptionId !== null) {
      const status = await page.evaluate(
        async ({ url }) => (await fetch(url, { credentials: 'include' })).status,
        { url: `${BASE_URL}${API_PREFIX}/subscriptions/${trade.subscriptionId}` },
      )
      if (status !== 403) {
        throw new Error(`타인 청약 직접 조회: 기대=403 vs 실제=${status}`)
      }
      notes.push(`GET /subscriptions/${trade.subscriptionId}=403`)
    } else {
      notes.push('subscriptionId 미확보로 직접 조회 확인 생략')
    }
    return [...notes, '대시보드=빈 상태, 청약 목록=표시할 청약 없음'].join(' / ')
  } finally {
    await context.close()
  }
}

async function periodLines(page) {
  await page.waitForSelector('ul li, [role="status"]', { timeout: 10000 })
  return page.locator('ul li').allInnerTexts()
}

async function searchPeriod(page, year, month) {
  await page.fill('#period-year', String(year))
  if (month !== undefined) await page.selectOption('#period-month', String(month))
  await page.getByRole('button', { name: '조회' }).click()
}

// D-9: 월별/연도별 탭이 선택한 기간만 집계한다(기간 밖은 빈 상태).
async function scenario11(browser) {
  const trade = shared.trade
  requirePrevious(trade?.sold, 'E2E-9')
  const { context, page } = await loginTrader(browser, trade)
  try {
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth() + 1
    const total = `${TRADE.expected.investment.toLocaleString('en-US')}원`
    const emptyText = '해당 조건에 해당하는 기록이 없습니다.'
    const notes = []

    await goto(page, '/dashboard')
    await page.getByRole('tab', { name: '월별' }).click()
    const key = `${year}-${String(month).padStart(2, '0')}`
    const inMonth = await waitFor(async () => (await periodLines(page)).includes(`${key}: ${total}`))
    if (!inMonth) throw new Error(`현재 월(${key}) 집계 불일치: 기대="${key}: ${total}" vs 실제=${JSON.stringify(await periodLines(page))}`)
    notes.push(`월별 ${key}=${total}`)

    await searchPeriod(page, year - 1, month)
    const monthEmpty = await waitFor(async () => (await page.locator('body').innerText()).includes(emptyText))
    if (!monthEmpty) throw new Error(`전년 동월(${year - 1}-${month}) 조회가 빈 상태가 아님: ${JSON.stringify(await periodLines(page))}`)
    notes.push(`${year - 1}년 ${month}월=빈 상태`)

    await page.getByRole('tab', { name: '연도별' }).click()
    await searchPeriod(page, year)
    const inYear = await waitFor(async () => (await periodLines(page)).includes(`${year}: ${total}`))
    if (!inYear) throw new Error(`현재 연도(${year}) 집계 불일치: 기대="${year}: ${total}" vs 실제=${JSON.stringify(await periodLines(page))}`)
    notes.push(`연도별 ${year}=${total}`)

    await searchPeriod(page, year - 1)
    const yearEmpty = await waitFor(async () => (await page.locator('body').innerText()).includes(emptyText))
    if (!yearEmpty) throw new Error(`전년(${year - 1}) 조회가 빈 상태가 아님: ${JSON.stringify(await periodLines(page))}`)
    notes.push(`${year - 1}년=빈 상태`)
    return notes.join(', ')
  } finally {
    await context.close()
  }
}

// 증권사 연동 화면: 외부 증권사를 호출하지 않는다(토큰은 동기화 조회 때만 발급). 연결·키 교체·해제와 진입 링크만 검증한다.
async function scenario12(browser) {
  const email = uniqueEmail()
  const setup = await browser.newContext()
  try {
    await api(setup, 'POST', '/accounts', { email, password: PASSWORD, name: 'E2E Broker' })
    await apiLogin(setup, email)
    const firms = await api(setup, 'GET', '/securities-firms')
    const kis = (firms ?? []).find((f) => f.name.includes('한국투자'))
    if (!kis) throw new Error('한국투자증권 시드가 없음(V15 확인 필요)')
    const investor = await api(setup, 'POST', '/investors', { displayName: 'E2E Broker' })
    await api(setup, 'POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: kis.id,
      accountNumber: ACCOUNT_NUMBER,
      isDefault: true,
    })
  } finally {
    await setup.close()
  }

  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, email, PASSWORD)
    if (!attempt.ok) throw new Error(`로그인 실패: 상태=${attempt.status}`)
    const notes = []

    await page
      .getByRole('navigation', { name: '주요 메뉴' })
      .getByRole('button', { name: '설정' })
      .click()
    await page
      .getByRole('navigation', { name: '주요 메뉴' })
      .getByRole('link', { name: '증권사 연동' })
      .click()
    await page.getByRole('heading', { name: '증권사 연동' }).waitFor()
    notes.push('헤더 링크로 진입')

    await waitFor(async () => (await page.getByLabel('증권계좌').locator('option').count()) > 1)
    await page.getByLabel('증권계좌').selectOption({ index: 1 })
    await page.getByRole('heading', { name: '증권사 API 연결' }).waitFor()
    await page.getByText('연결 안 됨', { exact: true }).waitFor()

    await page.getByLabel('앱키').fill('e2e-dummy-key')
    await page.getByLabel('앱시크릿').fill('e2e-dummy-secret')
    await page.getByRole('button', { name: '연결', exact: true }).click()
    await page.getByText('연결됨', { exact: true }).waitFor()
    const expiry = await page.getByText(/사용기간 만료일 \d{4}-\d{2}-\d{2}/).count()
    if (expiry === 0) throw new Error('연결 뒤 사용기간 만료일이 표시되지 않음')
    const leaked = (await page.locator('body').innerText()).includes('e2e-dummy-secret')
    if (leaked) throw new Error('입력한 앱시크릿이 화면에 다시 노출됨')
    notes.push('연결됨·만료일 표시·키 비노출')

    await page.getByRole('button', { name: '키 교체' }).click()
    await page.getByLabel('앱키').fill('e2e-new-key')
    await page.getByLabel('앱시크릿').fill('e2e-new-secret')
    await page.getByRole('button', { name: '새 키로 교체' }).click()
    await page.getByRole('button', { name: '키 교체' }).waitFor()
    notes.push('키 교체')

    await page.getByRole('button', { name: '연결 해제' }).click()
    await page.getByRole('button', { name: '해제', exact: true }).click()
    await page.getByText('연결 안 됨', { exact: true }).waitFor()
    notes.push('연결 해제')
    return notes.join(', ')
  } finally {
    await context.close()
  }
}

// 비공개 운영: 승인 대기 계정은 로그인되지 않고, 관리자가 `/admin/accounts`에서 승인하면 로그인된다.
// 백엔드는 APP_SIGNUP_MODE=OPEN으로 띄우므로 대기 상태는 DB로 만든다.
async function scenario13(browser) {
  const adminEmail = uniqueEmail()
  const memberEmail = uniqueEmail()
  const setup = await browser.newContext()
  try {
    await api(setup, 'POST', '/accounts', { email: adminEmail, password: PASSWORD, name: 'E2E Admin' })
    await api(setup, 'POST', '/accounts', { email: memberEmail, password: PASSWORD, name: 'E2E Waiting' })
  } finally {
    await setup.close()
  }
  promoteToAdmin(adminEmail)
  runSql(`UPDATE accounts SET approved=false WHERE email='${memberEmail}'`)

  const memberLogin = async () => {
    const ctx = await browser.newContext()
    try {
      const response = await ctx.request.fetch(`${BASE_URL}${API_PREFIX}/auth/login`, {
        method: 'POST',
        data: { email: memberEmail, password: PASSWORD },
        headers: { 'Content-Type': 'application/json' },
      })
      return { status: response.status(), body: await response.text() }
    } finally {
      await ctx.close()
    }
  }
  const before = await memberLogin()
  if (before.status !== 403 || !before.body.includes('ACCOUNT_PENDING_APPROVAL')) {
    throw new Error(`승인 전 로그인이 403 ACCOUNT_PENDING_APPROVAL이 아님: ${before.status} ${before.body}`)
  }

  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, adminEmail, PASSWORD)
    if (!attempt.ok) throw new Error(`관리자 로그인 실패: 상태=${attempt.status}`)
    // 관리자에게만 헤더에 `가입 승인` 링크가 보인다.
    await page
      .getByRole('navigation', { name: '주요 메뉴' })
      .getByRole('button', { name: '관리자' })
      .click()
    await page
      .getByRole('navigation', { name: '주요 메뉴' })
      .getByRole('link', { name: '가입 승인' })
      .click()
    await page.getByRole('heading', { name: '가입 승인 관리' }).waitFor()
    await page.getByText(memberEmail, { exact: true }).waitFor()
    await page.getByRole('button', { name: `${memberEmail} 승인`, exact: true }).click()
    await page.getByText(memberEmail, { exact: true }).waitFor({ state: 'detached' })
  } finally {
    await context.close()
  }

  const after = await memberLogin()
  if (after.status !== 200) throw new Error(`승인 뒤 로그인 실패: ${after.status} ${after.body}`)
  return '승인 전 403, 관리자 헤더 링크로 진입해 승인, 승인 뒤 로그인 200'
}

async function scenario14(browser) {
  const email = uniqueEmail()
  const setup = await browser.newContext()
  let firstId
  let secondId
  try {
    await api(setup, 'POST', '/accounts', { email, password: PASSWORD, name: 'E2E Multi' })
    await apiLogin(setup, email)
    firstId = (await api(setup, 'POST', '/investors', { displayName: 'E2E 갑' })).id
    secondId = (await api(setup, 'POST', '/investors', { displayName: 'E2E 을' })).id
  } finally {
    await setup.close()
  }

  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, email, PASSWORD)
    if (!attempt.ok) throw new Error(`로그인 실패: 상태=${attempt.status}`)

    // 투자자가 2명이면 직접 고를 때까지 작업 화면 대신 선택 화면이 나온다.
    await goto(page, '/subscriptions')
    await page.getByRole('heading', { name: '어느 투자자로 작업할까요?' }).waitFor({ timeout: 15000 })
    if ((await page.getByRole('heading', { name: '청약 목록' }).count()) > 0) {
      throw new Error('투자자를 고르기 전인데 청약 목록이 보임')
    }

    await page.getByRole('button', { name: 'E2E 을' }).click()
    await page.getByRole('heading', { name: '청약 목록' }).waitFor({ timeout: 15000 })
    const switcher = page.getByLabel('현재 투자자')
    if ((await switcher.inputValue()) !== String(secondId)) {
      throw new Error(`전환기 값 불일치: 기대=${secondId} 실제=${await switcher.inputValue()}`)
    }

    // 전환기로 바꾸면 값이 바뀌고, 새로고침해도 선택이 기억된다.
    await switcher.selectOption(String(firstId))
    await page.reload()
    await page.getByRole('heading', { name: '청약 목록' }).waitFor({ timeout: 15000 })
    const after = await page.getByLabel('현재 투자자').inputValue()
    if (after !== String(firstId)) {
      throw new Error(`새로고침 뒤 선택이 유지되지 않음: 기대=${firstId} 실제=${after}`)
    }

    // 대시보드는 계정 단위라 선택 없이도 열린다.
    await goto(page, '/dashboard')
    await page.getByRole('heading', { name: '대시보드' }).waitFor({ timeout: 15000 })
  } finally {
    await context.close()
  }
  return '투자자 2명: 선택 화면 → 선택 → 전환기 값·새로고침 유지, 대시보드 접근'
}

// ---- 최근 기능 시나리오(15~18): 각자 계정을 만들어 서로 독립 ----

// 계정·투자자·증권계좌·공모주(ADMIN 승격)를 만들고 stage까지(sub/alloc/sale) 거래를 API로 채운다.
async function buildFixture(browser, { stage, listingDate, ipoCount = 1 }) {
  const email = uniqueEmail()
  const tag = `${Date.now()}-${Math.floor(Math.random() * 1000)}`
  const setup = await browser.newContext()
  const fx = { email, ipoNames: [], ipoIds: [] }
  try {
    await api(setup, 'POST', '/accounts', { email, password: PASSWORD, name: 'E2E New' })
    await apiLogin(setup, email)
    const firms = await api(setup, 'GET', '/securities-firms')
    const investor = await api(setup, 'POST', '/investors', { displayName: 'E2E 신규기능' })
    const account = await api(setup, 'POST', '/securities-accounts', {
      investorId: investor.id,
      securitiesFirmId: firms[0].id,
      accountNumber: ACCOUNT_NUMBER,
      isDefault: true,
    })
    fx.firms = firms
    fx.investor = investor
    fx.account = account
    promoteToAdmin(email)
    await apiLogin(setup, email)
    for (let i = 0; i < ipoCount; i++) {
      const name = `E2E신규-${tag}-${i}`
      const ipo = await api(setup, 'POST', '/ipos', {
        companyName: name,
        ipoName: name,
        subscriptionStartDate: localDate(),
        subscriptionEndDate: localDate(),
        fixedPrice: 10000,
        offeringShareCount: 1000000,
        ...(listingDate ? { listingDate } : {}),
      })
      fx.ipoNames.push(name)
      fx.ipoIds.push(ipo.id)
    }
    if (stage) {
      fx.sub = await api(setup, 'POST', '/subscriptions', {
        investorId: investor.id,
        securitiesAccountId: account.id,
        ipoId: fx.ipoIds[0],
        quantity: 100,
        depositAmount: 500000,
        subscribedAt: localDateTime(),
      })
      if (stage !== 'sub') {
        fx.alloc = await api(setup, 'POST', '/allocations', {
          subscriptionId: fx.sub.id,
          allocatedQuantity: 100,
          allocatedAt: localDateTime(10),
          allocationPrice: 10000,
        })
      }
      if (stage === 'sale') {
        fx.sale = await api(setup, 'POST', '/sales', {
          allocationId: fx.alloc.id,
          quantity: 30,
          price: 15000,
          amount: 450000,
          saleDate: localDateTime(14),
          fee: 100,
          tax: 50,
        })
      }
    }
  } finally {
    await setup.close()
  }
  return fx
}

async function loginFixture(browser, fx) {
  const { context, page } = await openContext(browser)
  const attempt = await login(page, fx.email, PASSWORD)
  if (!attempt.ok) {
    await context.close()
    throw new Error(`로그인 실패: 상태=${attempt.status}`)
  }
  return { context, page }
}

async function expectText(page, text, what, timeout = 10000) {
  const ok = await waitFor(async () => (await page.locator('body').innerText()).includes(text), timeout)
  if (!ok) throw new Error(`${what}: "${text}" 가 보이지 않음`)
}

async function scenario15(browser) {
  const fx = await buildFixture(browser, { stage: 'sale' })
  const { context, page } = await loginFixture(browser, fx)
  try {
    await goto(page, '/investments')
    await page.getByRole('row', { name: new RegExp(fx.ipoNames[0]) }).click()
    await page.getByRole('heading', { name: '청약 상세' }).waitFor()

    // 매도 수량 수정(30 → 20)
    await page.getByRole('button', { name: '매도 수정' }).click()
    const dialog = page.getByRole('dialog', { name: '매도 수정' })
    await dialog.waitFor()
    await dialog.locator('#edit-sale-quantity').fill('20')
    await dialog.getByRole('button', { name: '저장' }).click()
    await dialog.waitFor({ state: 'detached' })
    const salesCell = page.locator('td[data-label="수량"]').first()
    if (!(await waitFor(async () => (await salesCell.innerText()).trim() === '20'))) {
      throw new Error(`매도 수량 수정 미반영: 실제=${await salesCell.innerText()}`)
    }

    // 청약 수량을 배정 수량(100)보다 작게 수정 → 거부 안내
    await page.getByRole('button', { name: '청약 수정' }).click()
    const subDialog = page.getByRole('dialog', { name: '청약 수정' })
    await subDialog.waitFor()
    await subDialog.locator('#edit-quantity').fill('50')
    await subDialog.getByRole('button', { name: '저장' }).click()
    await subDialog.getByRole('alert').filter({ hasText: '이미 배정받은 수량' }).waitFor()
    await subDialog.getByRole('button', { name: '취소' }).click()
    await subDialog.waitFor({ state: 'detached' })

    const confirm = async () => {
      const d = page.getByRole('alertdialog')
      await d.waitFor()
      await d.getByRole('button', { name: '삭제' }).click()
      await d.waitFor({ state: 'detached' })
    }

    // 매도가 남은 배정은 삭제 불가
    await page.getByRole('button', { name: '배정 삭제' }).click()
    await confirm()
    await page.getByRole('alert').filter({ hasText: '매도 기록이 있는 배정은 취소할 수 없습니다' }).waitFor()

    // 매도 → 배정 → 청약 순서로 삭제
    await page.getByRole('button', { name: '매도 삭제' }).click()
    await confirm()
    await page.getByRole('button', { name: '매도 삭제' }).waitFor({ state: 'detached' })
    await page.getByRole('button', { name: '배정 삭제' }).click()
    await confirm()
    await page.getByRole('button', { name: '배정 삭제' }).waitFor({ state: 'detached' })
    await page.getByRole('button', { name: '청약 삭제' }).click()
    await confirm()
    await page.getByRole('heading', { name: '청약 상세' }).waitFor({ state: 'detached' })
    if (!(await waitFor(async () => (await page.getByRole('row', { name: new RegExp(fx.ipoNames[0]) }).count()) === 0))) {
      throw new Error('청약 삭제 뒤에도 목록에 행이 남음')
    }
    return '매도 수량 30→20 반영, 청약<배정 수정 거부, 매도 있는 배정 삭제 거부, 매도→배정→청약 삭제'
  } finally {
    await context.close()
  }
}

async function scenario16(browser) {
  const fx = await buildFixture(browser, { listingDate: '2030-01-15', ipoCount: 2 })
  runSql(`INSERT INTO ipo_details (ipo_id, source_rcept_no, source_report_name, synced_at, ceo_name, established_on, address, par_value, capital_stock,
    largest_shareholder_name, largest_shareholder_ratio, institutional_competition_rate, lock_up_commitment_ratio, financials, float_schedule, underwriters, created_at, updated_at)
    VALUES (${fx.ipoIds[0]}, '20260101000001', '투자설명서', now(), '가나다', DATE '2015-03-02', '서울특별시 예시구 예시로 100', 500, 3500000000,
    '예시홀딩스', 52.30, 850.40, 31.20,
    '[{"period":"제11기(2025년12월말)","revenue":24800000000,"operatingProfit":3300000000,"netIncome":2700000000}]',
    '[{"label":"상장일 유통가능","shares":4200000,"ratio":38.50},{"label":"상장 후 1개월 유통가능","shares":5100000,"ratio":46.80}]',
    '[{"name":"예시증권","role":"대표","shares":700000}]', now(), now())`)
  const { context, page } = await openContext(browser) // 로그인 없이
  try {
    await goto(page, `/ipos/${fx.ipoIds[0]}`)
    for (const title of ['수요예측', '유통가능 물량(락업 해제) 일정', '손익계산서']) {
      await page.getByRole('heading', { name: title }).waitFor({ timeout: 15000 })
    }
    // 상장일 2030-01-15 + 1개월 = 2030-02-15
    await expectText(page, '2030-02-15', '예상 해제일')
    await expectText(page, '850.4:1', '기관 경쟁률')
    await goto(page, `/ipos/${fx.ipoIds[1]}`)
    await expectText(page, '아직 공시 정보를 가져오지 않았습니다', '시드 없는 공모주 안내', 15000)
    return '비로그인 상세: 수요예측·락업 일정·손익계산서 구역과 예상 해제일 2030-02-15, 시드 없으면 안내 문구'
  } finally {
    await context.close()
  }
}

async function scenario17(browser) {
  const fx = await buildFixture(browser, {})
  // 계좌 없는 상태에서 시작하도록 기본 계좌를 지운다
  const ctx = await browser.newContext()
  try {
    await apiLogin(ctx, fx.email)
    await api(ctx, 'DELETE', `/securities-accounts/${fx.account.id}`)
  } finally {
    await ctx.close()
  }
  const { context, page } = await loginFixture(browser, fx)
  try {
    const firm = fx.firms[1] ?? fx.firms[0]
    await goto(page, '/securities')
    await page.getByRole('heading', { name: '증권사 관리' }).waitFor()
    await page.getByRole('button', { name: `${firm.name} 계좌 등록` }).click()
    const form = page.getByRole('form', { name: `${firm.name} 계좌 등록` })
    await form.getByLabel('계좌번호').fill(ACCOUNT_NUMBER)
    await form.getByRole('button', { name: '등록', exact: true }).click()
    const mine = page.getByRole('region', { name: '등록한 증권사' })
    await mine.getByRole('heading', { name: firm.name }).waitFor()

    const row = mine.locator('li', { hasText: firm.name }).locator('li').first()
    const digits = async () => (await row.innerText()).replace(/\D/g, '')
    if ((await digits()).includes(ACCOUNT_NUMBER)) throw new Error('번호가 숨김 상태에서 전체 노출됨')
    await row.getByRole('button', { name: /계좌번호 보기/ }).click()
    if (!(await waitFor(async () => (await digits()).includes(ACCOUNT_NUMBER)))) {
      throw new Error(`번호 보기 실패: 실제=${await row.innerText()}`)
    }
    await row.getByRole('button', { name: '숨기기' }).click()
    if (!(await waitFor(async () => !(await digits()).includes(ACCOUNT_NUMBER)))) throw new Error('숨기기 실패')

    await row.getByRole('button', { name: '계좌 수정' }).click()
    const edit = page.getByRole('form', { name: `${firm.name} 계좌 수정` })
    await edit.getByLabel('계좌명 (선택)').fill('주력 계좌')
    await edit.getByRole('button', { name: '저장' }).click()
    await mine.getByText('주력 계좌', { exact: true }).waitFor()

    await mine.getByRole('button', { name: '주력 계좌 삭제' }).click()
    const d = page.getByRole('alertdialog')
    await d.getByRole('button', { name: '삭제' }).click()
    await mine.waitFor({ state: 'detached' })
    return `${firm.name}: 다른 증권사 그리드에서 등록 → 등록한 증권사로 이동, 번호 보기/숨기기, 이름 수정, 삭제`
  } finally {
    await context.close()
  }
}

async function scenario18(browser) {
  const fx = await buildFixture(browser, { stage: 'sale', listingDate: localDate() })
  const { context, page } = await loginFixture(browser, fx)
  try {
    await goto(page, '/profits/view')
    const section = page.getByRole('region', { name: '기간별 수익' })
    await section.waitFor({ timeout: 15000 })
    // 최종수익금 = 450,000 − 10,000×30 − 100 − 50 = 149,850
    const total = section.locator('tfoot tr')
    const notes = []
    for (const label of ['연', '월', '일']) {
      await section.getByRole('button', { name: label, exact: true }).click()
      if (!(await waitFor(async () => (await section.getByRole('button', { name: label, exact: true }).getAttribute('aria-pressed')) === 'true'))) {
        throw new Error(`${label} 버튼 전환 안 됨`)
      }
      const text = (await total.innerText()).replace(/\s+/g, ' ')
      if (!text.includes('149,850') || !text.includes('450,000')) {
        throw new Error(`${label} 합계 불일치: "${text}"`)
      }
      notes.push(label)
    }
    if ((await section.locator('svg').count()) === 0) throw new Error('그래프 SVG 없음')
    return `연·월·일 전환(${notes.join('/')}) 모두 합계 매도금액 450,000 / 최종수익금 149,850, 그래프 SVG 존재`
  } finally {
    await context.close()
  }
}

async function rawPost(context, path, options) {
  const response = await context.request.fetch(`${BASE_URL}${API_PREFIX}${path}`, { method: 'POST', ...options })
  return { status: response.status(), body: await response.text() }
}

async function signupApi(browser, email, name) {
  const setup = await browser.newContext()
  try {
    await api(setup, 'POST', '/accounts', { email, password: PASSWORD, name })
  } finally {
    await setup.close()
  }
}

async function scenario19(browser) {
  const email = uniqueEmail()
  await signupApi(browser, email, 'E2E Guard')
  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, email, PASSWORD)
    if (!attempt.ok) throw new Error(`로그인 실패: 상태=${attempt.status}`)
    await goto(page, '/login')
    if (!(await waitForUrl(page, '**/dashboard'))) throw new Error(`로그인 상태에서 /login이 /dashboard로 가지 않음: ${page.url()}`)
    await goto(page, '/admin/accounts')
    await expectText(page, '관리자만 볼 수 있는 화면입니다.', '일반 사용자 관리자 화면')
    await page.getByRole('button', { name: '로그아웃' }).click()
    if (!(await waitForUrl(page, '**/login'))) throw new Error(`로그아웃 뒤 /login 이동 실패: ${page.url()}`)
    await page.waitForSelector('form #email')
    await goto(page, '/dashboard')
    if (!(await waitForUrl(page, '**/login'))) throw new Error(`로그아웃 뒤 /dashboard가 /login으로 가지 않음: ${page.url()}`)
    return '로그인 상태 /login→/dashboard, 일반 사용자 /admin/accounts 차단, 로그아웃→/login, 이후 /dashboard→/login'
  } finally {
    await context.close()
  }
}

async function scenario20(browser) {
  const adminEmail = uniqueEmail()
  const memberEmail = uniqueEmail()
  await signupApi(browser, adminEmail, 'E2E Admin')
  await signupApi(browser, memberEmail, 'E2E Locked')
  promoteToAdmin(adminEmail)

  const member = await browser.newContext()
  try {
    for (let i = 1; i <= 5; i += 1) {
      const r = await rawPost(member, '/auth/login', {
        data: { email: memberEmail, password: WRONG_PASSWORD },
        headers: { 'Content-Type': 'application/json' },
      })
      if (r.status !== 401) throw new Error(`틀린 비밀번호 ${i}회차가 401이 아님: ${r.status} ${r.body}`)
    }
  } finally {
    await member.close()
  }

  const { context, page } = await openContext(browser)
  try {
    const locked = await login(page, memberEmail, PASSWORD)
    if (locked.ok) throw new Error('5회 실패 뒤 올바른 비밀번호로 로그인됨')
    await expectText(page, '계정이 잠겼습니다', '잠금 안내 문구')
  } finally {
    await context.close()
  }

  const admin = await openContext(browser)
  try {
    const attempt = await login(admin.page, adminEmail, PASSWORD)
    if (!attempt.ok) throw new Error(`관리자 로그인 실패: 상태=${attempt.status}`)
    await goto(admin.page, '/admin/accounts')
    await admin.page.getByRole('heading', { name: '가입 승인 관리' }).waitFor()
    await admin.page.getByLabel('승인 대기만 보기').uncheck()
    const row = admin.page.getByRole('row').filter({ hasText: memberEmail })
    await row.waitFor()
    if (!(await row.innerText()).includes('잠김')) throw new Error('해당 계정 행에 잠김 표시 없음')
    await admin.page.getByRole('button', { name: `${memberEmail} 잠금 해제`, exact: true }).click()
    await admin.page.getByRole('button', { name: `${memberEmail} 잠금 해제`, exact: true }).waitFor({ state: 'detached' })
    // 지금 가져오기 버튼은 보이는지만 확인(실제 DART 호출이라 누르지 않는다)
    await goto(admin.page, '/admin/ipos')
    await admin.page.getByRole('button', { name: 'DART에서 가져오기' }).waitFor()
  } finally {
    await admin.context.close()
  }

  const again = await openContext(browser)
  try {
    const attempt = await login(again.page, memberEmail, PASSWORD)
    if (!attempt.ok) throw new Error(`잠금 해제 뒤 로그인 실패: 상태=${attempt.status} 화면=${attempt.texts.join(' | ')}`)
  } finally {
    await again.context.close()
  }
  return '틀린 비밀번호 5회 → 올바른 비밀번호도 잠금 문구로 거부, 관리자 화면 잠김 표시 → 잠금 해제 → 로그인 성공'
}

async function scenario21(browser) {
  const email = uniqueEmail()
  await signupApi(browser, email, 'E2E Form')
  const context = await browser.newContext()
  try {
    const r = await rawPost(context, '/login', { form: { username: email, password: PASSWORD } })
    const me = await context.request.fetch(`${BASE_URL}${API_PREFIX}/auth/me`)
    if (me.status() !== 401) {
      throw new Error(`폼 로그인 뒤 /auth/me가 401이 아님: 폼 응답=${r.status} /auth/me=${me.status()}`)
    }
    return `POST /login 폼 응답 ${r.status}, 이후 /auth/me 401(세션 없음)`
  } finally {
    await context.close()
  }
}

async function scenario22(browser) {
  const email = uniqueEmail()
  await signupApi(browser, email, 'E2E Notify')
  const { context, page } = await openContext(browser)
  try {
    const attempt = await login(page, email, PASSWORD)
    if (!attempt.ok) throw new Error(`로그인 실패: 상태=${attempt.status}`)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('link', { name: '알림 설정' }).click()
    if (!(await waitForUrl(page, '**/notifications'))) throw new Error(`알림 설정 이동 실패: ${page.url()}`)
    await page.getByRole('heading', { name: '알림 설정', level: 1 }).waitFor()
    const deadline = page.getByRole('checkbox', { name: /청약 마감 당일 알림/ })
    await deadline.waitFor()
    if (!(await deadline.isChecked())) throw new Error('청약 마감 알림이 기본으로 켜져 있지 않음')
    await deadline.uncheck()
    await expectText(page, '저장했습니다.', '알림 종류 저장')
    await page.reload({ waitUntil: 'domcontentloaded' })
    const after = page.getByRole('checkbox', { name: /청약 마감 당일 알림/ })
    await after.waitFor()
    if (await after.isChecked()) throw new Error('새로고침 뒤에도 꺼진 상태가 유지되지 않음')
    if (!(await page.getByRole('checkbox', { name: /상장일 알림/ }).isChecked())) throw new Error('상장일 알림이 함께 꺼짐')
    return '설정 메뉴 → 알림 설정 이동, 청약 마감 알림 끄기 저장 → 새로고침 후 유지(상장일 알림은 그대로)'
  } finally {
    await context.close()
  }
}

const SCENARIOS = [
  ['E2E-1', '시나리오1: 회원가입 → 투자자 등록 → 증권계좌 등록', scenario1],
  ['E2E-2', '시나리오2: 로그인 → 대시보드 접속', scenario2],
  ['E2E-3', '시나리오3: 잘못된 비밀번호 로그인 실패', scenario3],
  ['E2E-4', '시나리오4: 중복 이메일 회원가입', scenario4],
  ['E2E-5', '시나리오5: 투자자 등록 빈 표시 이름', scenario5],
  ['E2E-6', '시나리오6: 존재하지 않는 investorId 증권계좌 등록', scenario6],
  ['E2E-7', '거래흐름1: 청약 등록 → 목록 표시', scenario7],
  ['E2E-8', '거래흐름2: 배정 등록(수량/단가) → 배정완료 표시', scenario8],
  ['E2E-9', '거래흐름3: 분할 매도 2건 → 대시보드 안분 집계', scenario9],
  ['E2E-10', '거래흐름4: 다른 계정에서 타인 데이터 비노출', scenario10],
  ['E2E-11', '거래흐름5: 월별/연도별 탭이 선택 기간만 집계(D-9)', scenario11],
  ['E2E-12', '증권사 연동: 진입 링크 → 연결 → 키 교체 → 해제', scenario12],
  ['E2E-13', '비공개 운영: 승인 대기 로그인 차단 → 관리자 승인 → 로그인', scenario13],
  ['E2E-14', '현재 투자자: 2명이면 직접 선택 → 전환 → 새로고침 유지', scenario14],
  ['E2E-15', '투자 내역: 매도 수정 → 거부 안내 → 매도·배정·청약 삭제', scenario15],
  ['E2E-16', '공모주 상세: 공시 정보(수요예측·락업 일정·손익계산서)', scenario16],
  ['E2E-17', '증권사 관리: 계좌 등록 → 보기/숨기기 → 이름 수정 → 삭제', scenario17],
  ['E2E-18', '수익 확인: 기간별 연·월·일 전환과 그래프', scenario18],
  ['E2E-19', '인증: 로그아웃과 화면 가드', scenario19],
  ['E2E-20', '인증: 로그인 잠금 → 관리자 해제', scenario20],
  ['E2E-21', '인증: 폼 로그인 경로 차단', scenario21],
  ['E2E-22', '알림 설정: 종류 끄기 저장 → 새로고침 유지', scenario22],
]

function printResults() {
  const headers = ['ID', '시나리오', '결과', '상세']
  const rows = results.map((r) => [r.id, r.name, r.status, r.detail])
  const widths = headers.map((header, i) =>
    Math.max(textWidth(header), ...rows.map((row) => textWidth(row[i]))),
  )
  const line = (cells) => cells.map((cell, i) => pad(cell, widths[i])).join(' | ')
  const divider = widths.map((w) => '-'.repeat(w)).join('-+-')

  console.log('')
  console.log(line(headers))
  console.log(divider)
  for (const row of rows) console.log(line(row))
  console.log(divider)
  const passed = results.filter((r) => r.status === 'PASS').length
  const failed = results.filter((r) => r.status === 'FAIL').length
  console.log(`총 ${results.length}개 시나리오: PASS ${passed} / FAIL ${failed}`)
}

async function launchBrowser() {
  const browser = await chromium.launch({ headless: true })
  browser.on('disconnected', () => console.error('[browser] disconnected'))
  return browser
}

async function main() {
  console.log(`BASE_URL=${BASE_URL}`)
  let browser = await launchBrowser()
  try {
    for (const [id, name, run] of SCENARIOS) {
      const startedAt = Date.now()
      diag.length = 0
      let status
      let detail
      try {
        detail = (await withTimeout(run(browser), 120000, `시나리오 타임아웃(120s): ${name}`)) ?? ''
        status = 'PASS'
      } catch (error) {
        status = 'FAIL'
        detail = error instanceof Error ? error.message : String(error)
        try {
          await browser.close()
        } catch {}
        browser = await launchBrowser()
        detail += ' / [browser] 재실행됨'
        console.log(`[${id}] 진단(마지막 30건):\n${diag.slice(-30).join('\n')}`)
      }
      results.push({ id, name, status, detail })
      console.log(`[${id}] ${status} (${Date.now() - startedAt}ms)`)
    }
  } finally {
    try {
      await browser.close()
    } catch {}
  }

  printResults()
  process.exit(results.some((r) => r.status === 'FAIL') ? 1 : 0)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})
