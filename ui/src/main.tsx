import { createRoot } from 'react-dom/client'
import { StrictMode } from 'react'
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import GlobalStyles from './app/styles/GlobalStyles.ts'
import AppProviders from './app/AppProviders.tsx'
import App from './app/App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <GlobalStyles />
      <App />
    </AppProviders>
  </StrictMode>,
)
