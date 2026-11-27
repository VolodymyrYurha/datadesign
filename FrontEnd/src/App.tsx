import { useState } from 'react'
import { QueryInspector } from './components/QueryInspector'
import { ProfessorsPage } from './pages/ProfessorsPage'
import { StatusPage } from './pages/StatusPage'

const pages = {
  status: { label: 'Status', component: StatusPage },
  professors: { label: 'Professors', component: ProfessorsPage },
} as const

type PageKey = keyof typeof pages

export default function App() {
  const [page, setPage] = useState<PageKey>('professors')
  const Page = pages[page].component

  return (
    <div className="grid h-screen grid-cols-[1fr_380px] text-slate-900">
      <div className="flex flex-col overflow-hidden">
        <header className="flex items-center gap-6 border-b border-slate-200 px-6 py-3">
          <span className="font-semibold">USI Study System</span>
          <nav className="flex gap-4 text-sm">
            {(Object.keys(pages) as PageKey[]).map((key) => (
              <button
                key={key}
                onClick={() => setPage(key)}
                className={key === page ? 'font-medium text-slate-900' : 'text-slate-500 hover:text-slate-900'}
              >
                {pages[key].label}
              </button>
            ))}
          </nav>
          <a href={import.meta.env.VITE_API_DOCS_URL ?? "http://localhost:8080/scalar"} target="_blank" className="ml-auto text-sm text-slate-500 hover:text-slate-900">
            API docs
          </a>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Page />
        </main>
      </div>
      <QueryInspector />
    </div>
  )
}
