import type { ReactNode } from 'react'
import { Reveal } from '../components/ui'

export const Slide = ({ children, center = false }: { children: ReactNode; center?: boolean }) => (
  <div className={`slide ${center ? 'center' : ''}`}>{children}</div>
)

/** Bullet list where item i appears at step i + startAt. */
export function Bullets({ items, step, startAt = 1 }: { items: ReactNode[]; step: number; startAt?: number }) {
  return (
    <ul>
      {items.map((it, i) => (
        <li key={i} style={{ listStyle: 'disc' }}>
          <Reveal show={step >= i + startAt} from="left">{it}</Reveal>
        </li>
      ))}
    </ul>
  )
}

export const K = ({ children }: { children: ReactNode }) => <code className="inline">{children}</code>
