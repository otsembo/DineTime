import type { ReactNode } from 'react'

export interface SlideDef {
  id: string
  title: string
  section: string
  /** planned speaking time, used by the presenter timer */
  seconds: number
  notes: string
  /** number of reveal steps (default 1) */
  steps?: number
  render: (ctx: { step: number }) => ReactNode
}
