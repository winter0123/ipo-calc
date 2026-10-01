import userEvent from '@testing-library/user-event'

// 입력을 비운 뒤 사용자가 타이핑/선택한 것처럼 값을 채운다. `{`·`[`는 user-event 문법이라 이스케이프한다.
export async function fill(element: HTMLElement, value: string): Promise<void> {
  if (element instanceof HTMLSelectElement) {
    await userEvent.selectOptions(element, value)
    return
  }
  await userEvent.clear(element)
  if (value !== '') {
    await userEvent.type(element, value.replace(/[{[]/g, '$&$&'))
  }
}
