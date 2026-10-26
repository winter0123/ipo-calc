// 저장소의 사용자 가이드를 UI 정적 자원으로 복사해 개발·배포 화면에서 제공한다.
import { cpSync, rmSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const repoRoot = resolve(scriptDir, '../..')
const source = resolve(repoRoot, 'docs/user-guides/manual')
const destination = resolve(scriptDir, '../public/user-guides/manual')

rmSync(destination, { recursive: true, force: true })
cpSync(source, destination, { recursive: true })
console.log(`사용자 가이드 복사 완료 -> ${destination}`)
