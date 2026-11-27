import { QueryInspector } from './components/QueryInspector'
import { StatusPage } from './pages/StatusPage'

export default function App() {
  return (
    <div className="grid h-screen grid-cols-[1fr_380px] text-slate-900">
      <div className="flex flex-col overflow-hidden">
        <header className="flex items-center gap-6 border-b border-slate-200 px-6 py-3">
          <span className="font-semibold">USI Study System</span>
          <nav className="text-sm text-slate-500">Status</nav>
          <a href={import.meta.env.VITE_API_DOCS_URL ?? "http://localhost:8080/scalar"} target="_blank" className="ml-auto text-sm text-slate-500 hover:text-slate-900">
            API docs
          </a>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <StatusPage />
        </main>
      </div>
      <QueryInspector />
    </div>
  )
}
