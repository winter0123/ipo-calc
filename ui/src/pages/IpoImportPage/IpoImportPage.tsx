import { IpoImportForm } from '@/features/ipo-import/index.ts'

export default function IpoImportPage() {
  return (
    <div>
      <h1>공모주 CSV 가져오기</h1>
      <p>CSV 파일로 공모주를 한 번에 등록합니다. 관리자만 사용할 수 있습니다.</p>
      <IpoImportForm />
    </div>
  )
}
