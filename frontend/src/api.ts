export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { ...init, credentials: 'same-origin' })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.detail || problem.title || `Request failed (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  // Preserve server money decimals before JSON parsing can round them to binary floats.
  const text = await response.text()
  return JSON.parse(text.replace(/("(?:amount|openingBalance|principalAmount|totalPayable|allocated|remaining|dueRemaining|totalPaid|outstandingBalance|planOutstandingBalance|percentagePaid|overdueAmount)"\s*:\s*)(-?\d+(?:\.\d+)?)(?=\s*[,}])/g, '$1"$2"')) as T
}
export async function mutate(path: string, body?: URLSearchParams) {
  // Fetch a fresh session CSRF token after each login/logout rotation.
  const csrf = await api<{ headerName: string; token: string }>('/auth/csrf')
  return api<void>(path, { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body })
}
export type Session = { id: string; email: string; currency: 'EUR' }

export async function jsonMutation<T>(path: string, body?: unknown, key?: string): Promise<T> {
  const csrf = await api<{ headerName: string; token: string }>('/auth/csrf')
  // Input money stays a decimal string until encoded as a JSON decimal number.
  const encoded = JSON.stringify(body)?.replace(/("(?:amount|openingBalance|principalAmount|totalPayable)":)"(-?\d+(?:\.\d{1,2})?)"/g, (_match, key: string, decimal: string) => key + decimal.replace(/^(-?)0+(?=\d)/, '$1'))
  return api<T>(path, { method: 'POST', body: encoded, headers: {
    'Content-Type': 'application/json', [csrf.headerName]: csrf.token,
    ...(key ? { 'Idempotency-Key': key } : {}),
  } })
}
