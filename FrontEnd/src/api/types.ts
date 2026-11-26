// Mirrors ch.usi.studysystem.api.dto.ApiResponse on the backend.

export type Db = 'neo4j' | 'mongodb' | 'elasticsearch'

export interface QuerySpan {
  db: Db
  operation: string
  query: string
  params: Record<string, unknown>
  durationMs: number
  rows: number | null
  error: string | null
}

export interface ApiMeta {
  traceId: string | null
  durationMs: number
  queries: QuerySpan[]
}

export interface ApiResponse<T> {
  data: T | null
  meta: ApiMeta
  error: { code: string; message: string } | null
}
