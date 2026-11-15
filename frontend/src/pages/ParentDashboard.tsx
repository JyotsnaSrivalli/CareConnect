import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import AdherenceSummary from '../components/AdherenceSummary'
import BlisterStrips from '../components/BlisterStrips'
import DoseTracker from '../components/DoseTracker'
import InviteCodeCard from '../components/InviteCodeCard'
import ParentCheckins from '../components/ParentCheckins'
import ParentConnections from '../components/ParentConnections'
import ParentMedications from '../components/ParentMedications'

export default function ParentDashboard() {
  const { user } = useAuth()
  // Bumped whenever medicines, schedules, stock or doses change, so every section re-reads the server.
  const [version, setVersion] = useState(0)
  const changed = () => setVersion((v) => v + 1)

  return (
    <>
      <h1 className="text-2xl font-semibold text-stone-900">Welcome, {user?.name}</h1>

      <AdherenceSummary version={version} />
      <BlisterStrips editable version={version} onChanged={changed} />
      <DoseTracker editable version={version} onChanged={changed} />
      <ParentCheckins />
      <ParentMedications version={version} onChanged={changed} />
      <InviteCodeCard />
      <ParentConnections />
    </>
  )
}
