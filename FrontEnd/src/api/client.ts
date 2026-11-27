import type { ApiResponse } from './types'

/** One finished API call, as recorded for the query inspector. */
export interface ApiCall {
  method: string
  path: string
  status: number
  at: Date
  response: ApiResponse<unknown>
}

type Listener = (call: ApiCall) => void
const listeners = new Set<Listener>()

/** Subscribe to every API call (used by the query inspector). */
export function onApiCall(listener: Listener): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/**
 * GET a backend endpoint. Returns the whole envelope; read `.data` for the
 * payload. Non-2xx responses still resolve, check `.error`.
 */
export async function apiGet<T>(path: string): Promise<ApiResponse<T>> {
  const res = await fetch(path, { headers: { Accept: 'application/json' } })
  let body: ApiResponse<T>
  try {
    body = await res.json()
  } catch {
    body = {
      data: null,
      meta: { traceId: res.headers.get('X-Trace-Id'), durationMs: 0, queries: [] },
      error: { code: `HTTP_${res.status}`, message: res.statusText || 'Backend unreachable' },
    }
  }
  const call: ApiCall = { method: 'GET', path, status: res.status, at: new Date(), response: body }
  listeners.forEach((l) => l(call))
  return body
}
