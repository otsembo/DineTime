import type { ReactNode } from 'react'
import { motion } from 'framer-motion'
import type { MealStatus } from '../sim/engine'

export function StatusBadge({ status, retry = 0 }: { status: MealStatus; retry?: number }) {
  const label = retry > 0 && (status === 'PENDING' || status === 'PROCESSING') ? `Retrying (${retry})` : status[0] + status.slice(1).toLowerCase()
  return <span className={`badge ${status}`}>{label}</span>
}

/** Fades/slides children in once `show` is true; keeps layout space so slides do not jump. */
export function Reveal({ show, children, from = 'bottom', grow = false }: { show: boolean; children: ReactNode; from?: 'bottom' | 'left' | 'right' | 'none'; grow?: boolean }) {
  const off = from === 'bottom' ? { y: 30 } : from === 'left' ? { x: -40 } : from === 'right' ? { x: 40 } : {}
  return (
    <motion.div initial={false} animate={show ? { opacity: 1, x: 0, y: 0 } : { opacity: 0, ...off }} transition={{ duration: 0.4 }} style={{ pointerEvents: show ? 'auto' : 'none', ...(grow ? { flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' } : {}) }}>
      {children}
    </motion.div>
  )
}

export function Title({ eyebrow, children }: { eyebrow?: string; children: ReactNode }) {
  return (
    <div>
      {eyebrow && <div className="eyebrow">{eyebrow}</div>}
      <h2>{children}</h2>
    </div>
  )
}

export function PhoneFrame({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <div style={{ width: 420, border: '10px solid #3b2d2a', borderRadius: 56, background: 'var(--bg)', padding: 22, display: 'flex', flexDirection: 'column', gap: 14, minHeight: 640 }}>
      {label && <div className="eyebrow" style={{ fontSize: 20 }}>{label}</div>}
      {children}
    </div>
  )
}
