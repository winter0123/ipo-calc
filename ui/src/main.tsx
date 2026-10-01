import { createRoot } from 'react-dom/client'
import { StrictMode } from 'react'
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './app/styles/index.css'
import AppProviders from './app/AppProviders.tsx'
import App from './app/App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <App />
    </AppProviders>
  </StrictMode>,
)
