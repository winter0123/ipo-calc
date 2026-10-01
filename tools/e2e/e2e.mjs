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
const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? 'vibe-ai-team-postgres'
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

async function openContext(browser) {
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'ko-KR',
    colorScheme: 'light',
  })
  const page = await context.newPage()
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

function promoteToAdmin(email) {
  if (!DB_NAME) {
    throw new Error(
      '공모주 등록은 ADMIN 전용(#242)이라 DB 승격이 필요하다: E2E_DB_NAME(예: vibe_slot_2) 환경변수를 지정해야 한다',
    )
  }
  execFileSync('docker', [
    'exec',
    DB_CONTAINER,
    'psql',
    '-U',
    DB_USER,
    '-d',
    DB_NAME,
    '-c',
    `UPDATE accounts SET role='ADMIN' WHERE email='${email}'`,
  ])
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
    await page.selectOption('#investorId', { label: trade.investorName })
    await page.selectOption('#securitiesAccountId', { index: 1 })
    await page.selectOption('#ipoId', { label: trade.ipoName })
    await page.fill('#quantity', String(TRADE.allocatedQuantity))
    await page.fill('#depositAmount', '500000')
    await page.fill('#subscribedAt', localDateTime())
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
    await page.fill('#allocatedAt', localDateTime(10))
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
  await page.selectOption('#investorId', { label: trade.investorName })
  await waitFor(async () => (await page.locator('#subscriptionId option').count()) > 1)
  await page.selectOption('#subscriptionId', { index: 1 })
  await waitFor(async () => (await page.locator('#allocationId option').count()) > 1)
  await page.selectOption('#allocationId', { index: 1 })
  await page.fill('#quantity', String(sale.quantity))
  await page.fill('#price', String(sale.price))
  await page.fill('#amount', String(sale.amount))
  await page.fill('#saleDate', localDateTime(14))
  await page.fill('#fee', String(sale.fee))
  await page.fill('#tax', String(sale.tax))
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
      '세전 수익': `${e.preTax.toLocaleString('en-US')}원`,
      '세후 수익': `${e.afterTax.toLocaleString('en-US')}원`,
      수익률: e.rate,
      '참여 건수': `${e.participation}건`,
    }
    const actual = {}
    for (const label of Object.keys(expected)) actual[label] = await cardValue(page, label)
    const mismatches = Object.keys(expected)
      .filter((label) => actual[label] !== expected[label])
      .map((label) => `${label}: 기대=${expected[label]} vs 실제=${actual[label]}`)
    if (mismatches.length > 0) {
      throw new Error(`대시보드 전체 탭 불일치(안분 규칙 #243): ${mismatches.join(' / ')}`)
    }

    await page.getByRole('tab', { name: '투자자별' }).click()
    const line = `${trade.investorName}: ${e.investment.toLocaleString('en-US')}원`
    const shown = await waitFor(
      async () => (await page.locator('li', { hasText: trade.investorName }).count()) > 0,
      10000,
    )
    const investorText = shown ? (await page.locator('li', { hasText: trade.investorName }).first().innerText()).trim() : '(없음)'
    if (investorText !== line) {
      throw new Error(`투자자별 탭 불일치: 기대="${line}" vs 실제="${investorText}"`)
    }
    return `매도 ${TRADE.sales.length}건(${TRADE.sales.map((s) => s.quantity).join('+')}주/배정 ${TRADE.allocatedQuantity}주) → ${Object.entries(actual).map(([k, v]) => `${k}=${v}`).join(', ')}, 투자자별="${investorText}"`
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
