export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { ...init, credentials: 'same-origin' })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.detail || problem.title || `Request failed (${response.status})`)
  }
  return response.status === 204 ? undefined as T : response.json()
}
export async function mutate(path: string, body?: URLSearchParams) {
  // Fetch a fresh session CSRF token after each login/logout rotation.
  const csrf = await api<{ headerName: string; token: string }>('/auth/csrf')
  return api<void>(path, { method: 'POST', headers: { [csrf.headerName]: csrf.token }, body })
}
export type Session = { id: string; email: string; currency: 'EUR' }
