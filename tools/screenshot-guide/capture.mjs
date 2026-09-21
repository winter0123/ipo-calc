import { chromium } from 'playwright'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '../..')
const baseUrl = process.env.BASE_URL ?? 'http://127.0.0.1:5174'
const outputRoot = resolve(root, 'docs/user-guides/web-account-setup/screenshots')

const pages = [
  { name: 'login', path: '/login' },
  { name: 'signup', path: '/signup' },
]

const viewports = [
  { name: 'desktop', width: 1280, height: 720 },
  { name: 'mobile', width: 390, height: 844 },
]

await mkdir(outputRoot, { recursive: true })

const browser = await chromium.launch({ headless: true })
try {
  for (const viewport of viewports) {
    const context = await browser.newContext({
      viewport: { width: viewport.width, height: viewport.height },
      deviceScaleFactor: 1,
      locale: 'ko-KR',
      colorScheme: 'light',
    })

    for (const page of pages) {
      const response = await context.newPage()
      await response.goto(`${baseUrl}${page.path}`, { waitUntil: 'networkidle' })
      await response.getByRole('heading', { level: 1 }).waitFor()
      await response.screenshot({
        path: resolve(outputRoot, `${page.name}-${viewport.name}.png`),
        fullPage: true,
      })
      await response.close()
    }

    await context.close()
  }
} finally {
  await browser.close()
}

console.log(`Screenshots written to ${outputRoot}`)
