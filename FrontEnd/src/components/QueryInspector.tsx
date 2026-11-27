import { useApiCalls } from '../inspector/InspectorContext'
import { DbBadge } from './DbBadge'

/** Shows the raw DB queries, timing and row counts behind each API call. */
export function QueryInspector() {
  const calls = useApiCalls()

  return (
    <aside className="flex h-full flex-col border-l border-slate-200 bg-slate-50">
      <h2 className="border-b border-slate-200 px-4 py-3 text-sm font-semibold uppercase tracking-wide text-slate-600">
        Query inspector
      </h2>
      <div className="flex-1 space-y-4 overflow-y-auto p-4">
        {calls.length === 0 && <p className="text-sm text-slate-500">No API calls yet.</p>}
        {calls.map((call, i) => (
          <div key={i} className="rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
            <div className="flex items-center justify-between text-sm">
              <span className="font-mono">
                {call.method} {call.path}
              </span>
              <span className={call.status < 400 ? 'text-emerald-700' : 'text-rose-700'}>
                {call.status} · {call.response.meta.durationMs} ms
              </span>
            </div>
            <div className="mt-1 font-mono text-[11px] text-slate-400">trace {call.response.meta.traceId ?? '-'}</div>
            <ul className="mt-2 space-y-2">
              {call.response.meta.queries.map((q, j) => (
                <li key={j} className="rounded bg-slate-50 p-2">
                  <div className="flex items-center gap-2 text-xs text-slate-600">
                    <DbBadge db={q.db} />
                    <span>{q.operation}</span>
                    <span className="ml-auto">
                      {q.durationMs} ms{q.rows != null && ` · ${q.rows} rows`}
                    </span>
                  </div>
                  <pre className="mt-1 whitespace-pre-wrap break-all font-mono text-xs text-slate-800">{q.query}</pre>
                  {q.error && <div className="mt-1 text-xs text-rose-700">{q.error}</div>}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </aside>
  )
}
