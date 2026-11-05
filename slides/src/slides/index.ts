import type { SlideDef } from '../engine/types'
import { opening, why } from './opening'
import { architecture, code, demo } from './core'
import { closing, errors, lessons } from './errors'

export const slides: SlideDef[] = [...opening, ...why, ...architecture, ...code, ...demo, ...errors, ...lessons, ...closing]

if (import.meta.env.DEV) {
  const total = slides.reduce((a, s) => a + s.seconds, 0)
  console.info(`[deck] ${slides.length} slides, planned ${(total / 60).toFixed(1)} min`)
}
