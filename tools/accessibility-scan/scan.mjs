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
const dbContainer = process.env.E2E_DB_CONTAINER ?? 'vibe-ai-team-postgres'
const dbUser = process.env.E2E_DB_USER ?? 'vibe'
let scanEmail = null

const pages = [
  { name: 'login', path: '/login', needsAuth: false },
  { name: 'signup', path: '/signup', needsAuth: false },
  { name: 'investor-register', path: '/investors/register', needsAuth: true },
  { name: 'securities-account-register', path: '/securities-accounts/register/1', needsAuth: true },
  { name: 'subscription-register', path: '/subscriptions/register', needsAuth: true },
  { name: 'subscription-list', path: '/subscriptions', needsAuth: true },
  { name: 'allocation-register', path: '/allocations/register', needsAuth: true },
  { name: 'sale-register', path: '/sales/register', needsAuth: true },
  { name: 'cost-register', path: '/costs/register', needsAuth: true },
  { name: 'profit-view', path: '/profits/view', needsAuth: true },
  { name: 'investments', path: '/investments', needsAuth: true },
  { name: 'dashboard', path: '/dashboard', needsAuth: true },
  { name: 'broker-sync', path: '/broker-sync', needsAuth: true },
]

const viewports = [
  { name: 'desktop', width: 1280, height: 720 },
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
    return [
      { name: 'broker-sync-connected', path: query(connected.id), needsAuth: true },
      {
        name: 'broker-sync-replace-form',
        path: query(connected.id),
        needsAuth: true,
        prepare: async (page) => {
          await page.getByRole('button', { name: '키 교체' }).click()
          await page.getByLabel('앱키').waitFor()
        },
      },
      { name: 'broker-sync-register-form', path: query(open.id), needsAuth: true },
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
    sql(`UPDATE accounts SET approved=false WHERE email='${memberEmail}'`)
    sql(`UPDATE accounts SET role='ADMIN' WHERE email='${scanEmail}'`)

    // 역할은 로그인 시점의 세션에 담기므로 다시 로그인한다.
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
    ]
  } catch (error) {
    console.log(`관리자 화면 스캔 준비 실패(건너뜀): ${error.message}`)
    return []
  }
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
  if (loggedIn) pages.push(...(await setupBrokerStates(context)))
  if (loggedIn) pages.push(...(await setupAdminStates(browser, page)))

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
    
    const result = await scanPage(page, pageInfo)
    results.push(result)
    
    // Print summary
    if (result.violations) {
      const bySeverity = {}
      for (const v of result.violations) {
        bySeverity[v.impact] = (bySeverity[v.impact] || 0) + v.nodes.length
      }
      console.log(`  ${pageInfo.name}: ${result.violations.length} violations (${Object.entries(bySeverity).map(([k,v])=>`${k}:${v}`).join(', ')})`)
    } else if (result.error) {
      console.log(`  ${pageInfo.name}: ERROR - ${result.error}`)
    } else if (result.skipped) {
      console.log(`  ${pageInfo.name}: SKIPPED - ${result.reason}`)
    }
  }
  
  await context.close()
  await browser.close()
  
  // Write detailed results
  await writeFile(resolve(outputRoot, 'accessibility-raw-results.json'), JSON.stringify(results, null, 2))
  
  // Generate markdown report
  const report = generateReport(results)
  await writeFile(resolve(outputRoot, 'accessibility-report.md'), report)
  
  console.log(`\nResults written to ${outputRoot}/accessibility-report.md`)
  console.log(`Raw results written to ${outputRoot}/accessibility-raw-results.json`)
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

main().catch(console.error)