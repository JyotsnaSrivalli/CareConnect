import client from './client'
import type { Inventory, Medication, MedicationPayload, Schedule } from '../types'

export const listMedications = (parentId?: number) =>
  client.get<Medication[]>('/medications', { params: parentId ? { parentId } : {} }).then((r) => r.data)

export const createMedication = (payload: MedicationPayload) =>
  client.post<Medication>('/medications', payload).then((r) => r.data)

export const updateMedication = (id: number, payload: MedicationPayload) =>
  client.put<Medication>(`/medications/${id}`, payload).then((r) => r.data)

export const deleteMedication = (id: number) => client.delete(`/medications/${id}`)

export const addSchedule = (medicationId: number, doseTime: string) =>
  client.post<Schedule>(`/medications/${medicationId}/schedules`, { doseTime }).then((r) => r.data)

export const removeSchedule = (medicationId: number, scheduleId: number) =>
  client.delete(`/medications/${medicationId}/schedules/${scheduleId}`)

export const updateInventory = (medicationId: number, currentQuantity: number, lowStockThreshold: number) =>
  client.put<Inventory>(`/medications/${medicationId}/inventory`, { currentQuantity, lowStockThreshold }).then((r) => r.data)
