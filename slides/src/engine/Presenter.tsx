import { useEffect, useState } from 'react'
import { useDeck } from './deck'
import { H, SlideView, W } from './Stage'

const TOTAL = 45 * 60
const fmt = (s: number) => `${s < 0 ? '-' : ''}${Math.floor(Math.abs(s) / 60)}:${String(Math.abs(s) % 60).padStart(2, '0')}`

export function Presenter() {
  const { slides, index, step, next, prev } = useDeck()
  const [elapsed, setElapsed] = useState(0)
  const [running, setRunning] = useState(false)
  useEffect(() => {
    if (!running) return
    const t = setInterval(() => setElapsed((e) => e + 1), 1000)
    return () => clearInterval(t)
  }, [running])

  const planned = slides.slice(0, index).reduce((a, s) => a + s.seconds, 0)
  const drift = elapsed - planned
  const slide = slides[index]
  const nextSlide = slides[index + 1]
  const box = (s: typeof slide, st: number, w: number) => (
    <div style={{ width: w, height: (w * H) / W, position: 'relative', overflow: 'hidden', border: '1px solid #555' }}>
      <div className="stage" style={{ left: 0, top: 0, transform: `scale(${w / W})`, transformOrigin: '0 0', pointerEvents: 'none' }}>
        <SlideView slide={s} step={st} />
      </div>
    </div>
  )

  return (
    <div style={{ position: 'fixed', inset: 0, overflow: 'auto', background: '#111', color: '#eee', fontFamily: 'system-ui', padding: 20, display: 'grid', gridTemplateColumns: '560px 1fr', gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {box(slide, step, 540)}
        <div>Next:</div>
        {nextSlide ? box(nextSlide, 0, 320) : <em>End</em>}
        <div style={{ fontSize: 40 }}>{fmt(TOTAL - elapsed)} left</div>
        <div style={{ color: Math.abs(drift) < 60 ? '#6c6' : drift > 0 ? '#e66' : '#ec6' }}>
          {drift > 0 ? `${fmt(drift)} behind` : `${fmt(-drift)} ahead`} plan
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => setRunning((r) => !r)}>{running ? 'Pause' : 'Start'} timer</button>
          <button onClick={() => { setElapsed(0); setRunning(false) }}>Reset</button>
          <button onClick={prev}>◀</button>
          <button onClick={next}>▶</button>
        </div>
      </div>
      <div>
        <div style={{ color: '#999' }}>{index + 1}/{slides.length} · {slide.section} · {Math.round(slide.seconds / 60 * 10) / 10} min · step {step + 1}/{slide.steps ?? 1}</div>
        <h2>{slide.title}</h2>
        <p style={{ fontSize: 26, lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{slide.notes}</p>
      </div>
    </div>
  )
}
