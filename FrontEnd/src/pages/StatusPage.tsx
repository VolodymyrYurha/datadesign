import { useCallback, useEffect, useState } from 'react'
import { apiGet } from '../api/client'
import type { Db } from '../api/types'
import { DbBadge } from '../components/DbBadge'

interface DbStatus {
  db: Db
  up: boolean
  latencyMs: number
  version: string | null
  detail: string
}

/** Pings all three databases through the backend. */
export function StatusPage() {
  const [statuses, setStatuses] = useState<DbStatus[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const refresh = useCallback(async () => {
    setLoading(true)
    const res = await apiGet<DbStatus[]>('/api/system/health').catch((e: Error) => {
      setError(e.message)
      return null
    })
    if (res) {
      setStatuses(res.data)
      setError(res.data ? null : (res.error?.message ?? 'Unknown error'))
    }
    setLoading(false)
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  return (
    <section>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">System status</h1>
        <button
          onClick={refresh}
          disabled={loading}
          className="rounded-md bg-slate-900 px-3 py-1.5 text-sm text-white disabled:opacity-50"
        >
          {loading ? 'Checking…' : 'Refresh'}
        </button>
      </div>

      {error && (
        <p className="mt-4 rounded-md bg-rose-50 p-3 text-sm text-rose-800">
          Backend error: {error}. Is the API running on port 8080?
        </p>
      )}

      <div className="mt-6 grid gap-4 sm:grid-cols-3">
        {statuses?.map((s) => (
          <div key={s.db} className="rounded-lg border border-slate-200 p-4">
            <div className="flex items-center justify-between">
              <DbBadge db={s.db} />
              <span className={`text-sm font-medium ${s.up ? 'text-emerald-700' : 'text-rose-700'}`}>
                {s.up ? 'UP' : 'DOWN'}
              </span>
            </div>
            <div className="mt-3 text-sm text-slate-700">{s.version ? `v${s.version}` : '-'}</div>
            <div className="text-xs text-slate-500">{s.detail}</div>
            <div className="mt-2 text-xs text-slate-500">{s.latencyMs} ms</div>
          </div>
        ))}
      </div>
    </section>
  )
}
