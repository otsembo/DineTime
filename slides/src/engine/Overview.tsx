import { useDeck } from './deck'
import { H, SlideView, W } from './Stage'

export function Overview() {
  const { slides, goto, setOverview, index } = useDeck()
  const thumb = 0.18
  return (
    <div
      style={{ position: 'fixed', inset: 0, background: '#000d', overflow: 'auto', zIndex: 20, padding: 30, display: 'flex', flexWrap: 'wrap', gap: 20, alignContent: 'flex-start' }}
      onClick={() => setOverview(false)}
    >
      {slides.map((s, i) => (
        <button
          key={s.id}
          onClick={(e) => { e.stopPropagation(); goto(i, 0); setOverview(false) }}
          style={{ width: W * thumb, height: H * thumb + 28, background: 'none', border: i === index ? '3px solid #ffb5a0' : '3px solid transparent', color: '#fff', cursor: 'pointer', padding: 0, textAlign: 'left' }}
        >
          <div style={{ position: 'relative', width: W * thumb, height: H * thumb, overflow: 'hidden' }}>
            <div className="stage" style={{ left: 0, top: 0, transform: `scale(${thumb})`, transformOrigin: '0 0', pointerEvents: 'none' }}>
              <SlideView slide={s} step={(s.steps ?? 1) - 1} />
            </div>
          </div>
          <div style={{ fontSize: 14, fontFamily: 'sans-serif' }}>{i + 1}. {s.title}</div>
        </button>
      ))}
    </div>
  )
}
