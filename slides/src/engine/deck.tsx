import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { SlideDef } from './types'

interface Pos { index: number; step: number }
interface Deck extends Pos {
  slides: SlideDef[]
  next: () => void
  prev: () => void
  goto: (index: number, step?: number) => void
  overview: boolean
  setOverview: (v: boolean) => void
  light: boolean
  toggleLight: () => void
}

const Ctx = createContext<Deck | null>(null)
export const useDeck = () => {
  const d = useContext(Ctx)
  if (!d) throw new Error('useDeck outside DeckProvider')
  return d
}

const stepsOf = (s: SlideDef) => s.steps ?? 1

function parseHash(n: number): Pos {
  const [i, st] = window.location.hash.replace('#/', '').split('/').map((x) => parseInt(x, 10))
  const index = Number.isFinite(i) ? Math.min(Math.max(i - 1, 0), n - 1) : 0
  return { index, step: Number.isFinite(st) ? Math.max(st, 0) : 0 }
}

export function DeckProvider({ slides, children, follower = false }: { slides: SlideDef[]; children: ReactNode; follower?: boolean }) {
  const [pos, setPos] = useState<Pos>(() => parseHash(slides.length))
  const [overview, setOverview] = useState(false)
  const [light, setLight] = useState(false)
  const chan = useRef<BroadcastChannel | null>(null)
  const fromRemote = useRef(false)

  useEffect(() => {
    document.documentElement.dataset.theme = light ? 'light' : 'dark'
  }, [light])

  useEffect(() => {
    const ch = new BroadcastChannel('dinetime-deck')
    chan.current = ch
    ch.onmessage = (e: MessageEvent<Pos>) => {
      fromRemote.current = true
      setPos(e.data)
    }
    return () => ch.close()
  }, [])

  useEffect(() => {
    if (!follower) window.history.replaceState(null, '', `#/${pos.index + 1}/${pos.step}`)
    if (fromRemote.current) fromRemote.current = false
    else chan.current?.postMessage(pos)
  }, [pos, follower])

  const goto = useCallback(
    (index: number, step = 0) => {
      const i = Math.min(Math.max(index, 0), slides.length - 1)
      setPos({ index: i, step: Math.min(step, stepsOf(slides[i]) - 1) })
    },
    [slides],
  )
  const next = useCallback(() => {
    setPos((p) => {
      if (p.step < stepsOf(slides[p.index]) - 1) return { ...p, step: p.step + 1 }
      return p.index < slides.length - 1 ? { index: p.index + 1, step: 0 } : p
    })
  }, [slides])
  const prev = useCallback(() => {
    setPos((p) => {
      if (p.step > 0) return { ...p, step: p.step - 1 }
      return p.index > 0 ? { index: p.index - 1, step: stepsOf(slides[p.index - 1]) - 1 } : p
    })
  }, [slides])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (t.closest('input, textarea, button, select')) {
        if (e.key === ' ' || e.key === 'Enter') return
      }
      if (e.metaKey || e.ctrlKey || e.altKey) return
      switch (e.key) {
        case 'ArrowRight': case 'PageDown': case ' ': e.preventDefault(); next(); break
        case 'ArrowLeft': case 'PageUp': e.preventDefault(); prev(); break
        case 'Home': goto(0); break
        case 'End': goto(slides.length - 1); break
        case 'o': case 'O': setOverview((v) => !v); break
        case 'Escape': setOverview(false); break
        case 'f': case 'F':
          if (document.fullscreenElement) void document.exitFullscreen()
          else void document.documentElement.requestFullscreen()
          break
        case 'l': case 'L': setLight((v) => !v); break
        case 's': case 'S': window.open(`${window.location.pathname}?presenter#/${pos.index + 1}/${pos.step}`, 'presenter', 'width=1100,height=700'); break
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [next, prev, goto, slides.length, pos])

  const value = useMemo<Deck>(
    () => ({ ...pos, slides, next, prev, goto, overview, setOverview, light, toggleLight: () => setLight((v) => !v) }),
    [pos, slides, next, prev, goto, overview, light],
  )
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}
