// 로그인 없이 볼 수 있는 화면. 백엔드의 공개 GET(/ipos, /market-holidays)과 짝이다.
export function isPublicPath(pathname: string): boolean {
  return (
    pathname === '/login' ||
    pathname === '/signup' ||
    pathname === '/ipo-calendar' ||
    pathname.startsWith('/ipos/')
  )
}
