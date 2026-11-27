import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { onApiCall, type ApiCall } from '../api/client'

const MAX_CALLS = 20
const InspectorContext = createContext<ApiCall[]>([])

/** Keeps the most recent API calls so any component can show their DB queries. */
export function InspectorProvider({ children }: { children: ReactNode }) {
  const [calls, setCalls] = useState<ApiCall[]>([])
  useEffect(() => onApiCall((c) => setCalls((prev) => [c, ...prev].slice(0, MAX_CALLS))), [])
  return <InspectorContext.Provider value={calls}>{children}</InspectorContext.Provider>
}

export function useApiCalls(): ApiCall[] {
  return useContext(InspectorContext)
}
