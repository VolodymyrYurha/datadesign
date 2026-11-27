import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import { InspectorProvider } from './inspector/InspectorContext'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <InspectorProvider>
      <App />
    </InspectorProvider>
  </StrictMode>,
)
