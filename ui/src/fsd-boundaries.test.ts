import { describe, it, expect } from 'vitest'

const LAYERS = ['app', 'pages', 'widgets', 'features', 'entities', 'shared']
// 공개 API(index.ts) 강제 대상 레이어. app→pages는 라우트 진입점이라 페이지 컴포넌트를 직접 import한다.
const PUBLIC_API_LAYERS = ['widgets', 'features', 'entities']
// app·shared는 슬라이스 없이 세그먼트로만 구성된다.
const SLICELESS_LAYERS = ['app', 'shared']

const sources = import.meta.glob<string>('/src/**/*.{ts,tsx}', {
  query: '?raw',
  import: 'default',
  eager: true,
})

function resolvePath(fromFile: string, spec: string): string {
  if (spec.startsWith('@/')) return `/src/${spec.slice(2)}`
  const parts = fromFile.split('/').slice(0, -1)
  for (const seg of spec.split('/')) {
    if (seg === '..') parts.pop()
    else if (seg !== '.') parts.push(seg)
  }
  return parts.join('/')
}

function locate(path: string) {
  const [, , layer, second] = path.split('/')
  if (!LAYERS.includes(layer)) return null
  return { layer, slice: SLICELESS_LAYERS.includes(layer) ? '' : second }
}

function violations(): string[] {
  const found: string[] = []
  const pattern = /(?:from|import)\s*\(?\s*['"](\.{1,2}\/[^'"]+|@\/[^'"]+)['"]/g

  for (const [file, source] of Object.entries(sources)) {
    const from = locate(file)
    if (!from || file.endsWith('fsd-boundaries.test.ts')) continue
    const isTest = /\.test\.tsx?$/.test(file)
    const where = file.replace('/src/', '')

    for (const match of source.matchAll(pattern)) {
      const spec = match[1]
      const target = resolvePath(file, spec)
      const to = locate(target)
      if (!to) continue

      if (LAYERS.indexOf(to.layer) < LAYERS.indexOf(from.layer)) {
        found.push(`${where}: 상위 레이어 ${to.layer} 를 import (${spec})`)
        continue
      }
      if (from.layer === to.layer && from.slice === to.slice) continue
      if (from.layer === to.layer && !SLICELESS_LAYERS.includes(from.layer)) {
        found.push(`${where}: 같은 레이어의 다른 슬라이스 ${to.slice} 를 import (${spec})`)
        continue
      }
      // 테스트는 목(mock) 핸들을 얻으려고 내부 api-client 모듈만 직접 import할 수 있다.
      if (isTest && /\/api-client\.ts$/.test(target)) continue
      if (!PUBLIC_API_LAYERS.includes(to.layer)) continue
      if (!/^\/src\/[\w-]+\/[\w-]+\/index(\.ts)?$/.test(target)) {
        found.push(
          `${where}: ${to.layer}/${to.slice} 의 index.ts 를 거치지 않고 내부 모듈을 import (${spec})`,
        )
      }
    }
  }
  return found
}

describe('FSD 경계', () => {
  it('레이어 방향, 슬라이스 격리, 공개 API 규칙을 지킨다', () => {
    expect(violations()).toEqual([])
  })

  it('위반 감지기가 동작한다 (샘플 소스 검증)', () => {
    expect(resolvePath('/src/features/a/b/C.tsx', '../../../entities/x/index.ts')).toBe(
      '/src/entities/x/index.ts',
    )
    expect(locate('/src/shared/api/client.ts')).toEqual({ layer: 'shared', slice: '' })
    expect(locate('/src/main.tsx')).toBeNull()
    expect(resolvePath('/src/features/a/B.tsx', '@/entities/x/index.ts')).toBe(
      '/src/entities/x/index.ts',
    )
  })
})
