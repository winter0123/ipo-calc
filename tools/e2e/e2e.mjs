import { chromium } from 'playwright'
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

const results = []
const shared = { account: null, seq: 0 }

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
  const locator = page.locator('[aria-live="polite"]')
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

const SCENARIOS = [
  ['E2E-1', '시나리오1: 회원가입 → 투자자 등록 → 증권계좌 등록', scenario1],
  ['E2E-2', '시나리오2: 로그인 → 대시보드 접속', scenario2],
  ['E2E-3', '시나리오3: 잘못된 비밀번호 로그인 실패', scenario3],
  ['E2E-4', '시나리오4: 중복 이메일 회원가입', scenario4],
  ['E2E-5', '시나리오5: 투자자 등록 빈 표시 이름', scenario5],
  ['E2E-6', '시나리오6: 존재하지 않는 investorId 증권계좌 등록', scenario6],
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
