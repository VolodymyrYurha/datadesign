import type { Db } from '../api/types'

const styles: Record<Db, string> = {
  neo4j: 'bg-sky-100 text-sky-800',
  mongodb: 'bg-emerald-100 text-emerald-800',
  elasticsearch: 'bg-amber-100 text-amber-800',
}

export function DbBadge({ db }: { db: Db }) {
  return <span className={`rounded px-2 py-0.5 text-xs font-semibold ${styles[db]}`}>{db}</span>
}
