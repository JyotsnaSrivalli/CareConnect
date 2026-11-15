import { useState } from 'react'
import { todayLocal } from '../utils/format'
import CheckinForm from './CheckinForm'
import CheckinHistory from './CheckinHistory'

/** Parent side: the form and the history share one "selected date", so "Edit" in the history loads that day into the form. */
export default function ParentCheckins() {
  const [date, setDate] = useState(todayLocal())
  const [saves, setSaves] = useState(0)      // bumped after every save so the history re-reads the server

  const edit = (d: string) => {
    setDate(d)
    document.getElementById('checkin-form')?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  }

  return (
    <>
      <CheckinForm date={date} onDateChange={setDate} onSaved={() => setSaves((n) => n + 1)} />
      <CheckinHistory refreshKey={saves} selectedDate={date} onEdit={edit} />
    </>
  )
}
