import { Routes, Route } from 'react-router-dom'
import LoginPage from './app/pages/LoginPage/LoginPage.tsx'
import SignupPage from './app/pages/SignupPage/SignupPage.tsx'

function App() {
  return (
    <Routes>
      <Route path="/" element={<div>Hello</div>} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
    </Routes>
  )
}

export default App
