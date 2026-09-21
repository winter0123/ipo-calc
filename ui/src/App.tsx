import { Routes, Route } from 'react-router-dom'
import LoginPage from './app/pages/LoginPage/LoginPage.tsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<div>Hello</div>} />
      <Route path="/login" element={<LoginPage />} />
    </Routes>
  )
}

export default App
