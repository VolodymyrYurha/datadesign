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
  startedAt: string // ISO timestamp
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

// Mirrors ch.usi.studysystem.domain.professor.Professor.
export interface TaughtCourse {
  courseId: string
  title: string
  role: 'director' | 'assistant'
}

export interface Professor {
  id: string
  firstName: string
  lastName: string
  position: string | null
  profileUrl: string | null
  courses: TaughtCourse[]
}
