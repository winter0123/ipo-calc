import { useEffect } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { AllocationRegisterForm } from '@/features/allocation-register/index.ts'
import styles from './AllocationRegisterPage.module.css'

export default function AllocationRegisterPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const subscriptionId = searchParams.get('subscriptionId')

  useEffect(() => {
    if (!subscriptionId) {
      navigate('/subscriptions')
    }
  }, [subscriptionId, navigate])

  if (!subscriptionId) {
    return null
  }

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>배정 등록</h1>
      <AllocationRegisterForm />
    </div>
  )
}
