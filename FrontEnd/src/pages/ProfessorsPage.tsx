import { useEffect, useState } from 'react'
import { apiGet } from '../api/client'
import type { Professor } from '../api/types'
import { DbBadge } from '../components/DbBadge'

/**
 * Example feature: professors read from MongoDB through the backend.
 * GET /api/professors?q=...  ->  ProfessorController -> ProfessorService -> ProfessorRepository
 * (MongoProfessorRepository) -> MongoService. The raw Mongo query shows up in the inspector on the right.
 */
export function ProfessorsPage() {
  const [query, setQuery] = useState('')
  const [professors, setProfessors] = useState<Professor[] | null>(null)
  const [selected, setSelected] = useState<Professor | null>(null)
  const [error, setError] = useState<string | null>(null)

  // Debounced search: one request 300 ms after the user stops typing.
  useEffect(() => {
    const timer = setTimeout(async () => {
      const q = query.trim()
      const res = await apiGet<Professor[]>(`/api/professors${q ? `?q=${encodeURIComponent(q)}` : ''}`)
      setProfessors(res.data)
      setError(res.data ? null : (res.error?.message ?? 'Unknown error'))
    }, 300)
    return () => clearTimeout(timer)
  }, [query])

  async function open(id: string) {
    const res = await apiGet<Professor>(`/api/professors/${encodeURIComponent(id)}`)
    setSelected(res.data)
  }

  return (
    <section>
      <div className="flex items-center gap-3">
        <h1 className="text-2xl font-semibold">Professors</h1>
        <DbBadge db="mongodb" />
        <span className="text-sm text-slate-500">{professors ? `${professors.length} found` : 'Loading…'}</span>
      </div>

      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by first or last name"
        className="mt-4 w-full max-w-sm rounded-md border border-slate-300 px-3 py-1.5 text-sm focus:border-slate-500 focus:outline-none"
      />

      {error && <p className="mt-4 rounded-md bg-rose-50 p-3 text-sm text-rose-800">Backend error: {error}</p>}

      <div className="mt-4 grid gap-6 lg:grid-cols-[1fr_320px]">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
            <tr>
              <th className="py-2 font-medium">Name</th>
              <th className="py-2 font-medium">Position</th>
              <th className="py-2 text-right font-medium">Courses</th>
            </tr>
          </thead>
          <tbody>
            {professors?.map((p) => (
              <tr
                key={p.id}
                onClick={() => open(p.id)}
                className={`cursor-pointer border-b border-slate-100 hover:bg-slate-50 ${selected?.id === p.id ? 'bg-slate-100' : ''}`}
              >
                <td className="py-2 font-medium">{p.lastName}, {p.firstName}</td>
                <td className="max-w-xs truncate py-2 text-slate-600" title={p.position ?? ''}>{p.position || '-'}</td>
                <td className="py-2 text-right tabular-nums text-slate-600">{p.courses.length}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <aside className="h-fit rounded-lg border border-slate-200 p-4 text-sm lg:sticky lg:top-0">
          {selected ? (
            <>
              <h2 className="text-lg font-semibold">{selected.firstName} {selected.lastName}</h2>
              <p className="text-slate-600">{selected.position || 'No position listed'}</p>
              {selected.profileUrl && (
                <a href={selected.profileUrl} target="_blank" rel="noreferrer" className="text-sky-700 hover:underline">
                  USI profile
                </a>
              )}
              <h3 className="mt-4 text-xs font-medium uppercase text-slate-500">Teaches</h3>
              <ul className="mt-1 space-y-1">
                {selected.courses.map((c) => (
                  <li key={c.courseId} className="flex justify-between gap-2">
                    <span>{c.title}</span>
                    <span className="text-xs text-slate-500">{c.role}</span>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="text-slate-500">Select a professor to see their courses.</p>
          )}
        </aside>
      </div>
    </section>
  )
}
