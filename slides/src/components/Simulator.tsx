import { useEffect, useReducer, useState } from 'react'
import { StatusBadge } from './ui'
import { initialState, reduce, type Meal } from '../sim/engine'

const NAMES = ['Jollof rice', 'Ugali & sukuma', 'Chapati', 'Pilau', 'Nyama choma', 'Mandazi']

function DocList({ title, docs, empty }: { title: string; docs: Meal[]; empty: string }) {
  return (
    <div className="card" style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
      <h3 style={{ fontSize: 30 }}>{title}</h3>
      {docs.length === 0 && <div className="muted" style={{ fontSize: 26 }}>{empty}</div>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 10 }}>
        {docs.map((m) => (
          <div key={m.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
            <div style={{ minWidth: 0 }}>
              <div style={{ fontSize: 28, fontWeight: 700 }}>{m.name}</div>
              <div className="mono muted" style={{ fontSize: 18 }}>{m.id}</div>
              {m.error && <div style={{ color: 'var(--danger)', fontSize: 20 }}>{m.error}</div>}
            </div>
            {m.isTemp ? <StatusBadge status={m.status ?? 'PENDING'} retry={m.retry} /> : <StatusBadge status="ACTIVE" />}
          </div>
        ))}
      </div>
    </div>
  )
}

export function Simulator() {
  const [s, dispatch] = useReducer(reduce, undefined, initialState)
  const [auto, setAuto] = useState(true)
  const [count, setCount] = useState(0)
  const k = s.controls

  useEffect(() => {
    if (!auto) return
    const t = setInterval(() => dispatch({ type: 'tick' }), 1400)
    return () => clearInterval(t)
  }, [auto])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'n' || e.key === 'N') dispatch({ type: 'tick' })
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const save = () => {
    dispatch({ type: 'save', name: NAMES[count % NAMES.length] })
    setCount((c) => c + 1)
  }
  const errored = s.commands.find((c) => c.state === 'FAILED_USER_ACTION')
  const last = s.commands[s.commands.length - 1]
  const lastTemp = last && s.reads.find((m) => m.clientRef === last.id)
  const inspect = last ? JSON.stringify({ command: { id: last.id, state: last.state, attempt: last.attempt, roundTripDone: last.roundTripDone }, tempDoc: lastTemp ? { id: lastTemp.id, status: lastTemp.status, isTemp: true } : 'deleted' }, null, 1) : '// save a meal to begin'

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18, flex: 1, minHeight: 0 }}>
      <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'center' }}>
        <button className="btn on" onClick={save}>＋ Save meal</button>
        <button className={`btn ${k.offline ? 'on' : ''}`} onClick={() => dispatch({ type: 'controls', patch: { offline: !k.offline } })}>✈ Offline</button>
        <button className="btn" onClick={() => dispatch({ type: 'controls', patch: { fail400Next: true } })}>400 next{k.fail400Next ? ' ✓' : ''}</button>
        <button className="btn" onClick={() => dispatch({ type: 'controls', patch: { fail500Next: 3 } })}>500 ×3{k.fail500Next ? ' ✓' : ''}</button>
        <button className={`btn ${k.persist500 ? 'on' : ''}`} onClick={() => dispatch({ type: 'controls', patch: { persist500: !k.persist500 } })}>Always 500</button>
        <button className="btn" disabled={!errored} onClick={() => errored && dispatch({ type: 'resubmit', commandId: errored.id, name: errored.name + ' (fixed)' })}>Fix &amp; resubmit</button>
        <span style={{ flex: 1 }} />
        <button className={`btn ${auto ? 'on' : ''}`} onClick={() => setAuto((a) => !a)}>{auto ? 'Auto' : 'Step'}</button>
        <button className="btn" disabled={auto} onClick={() => dispatch({ type: 'tick' })}>Step ▶ (N)</button>
        <button className="btn" onClick={() => { dispatch({ type: 'reset' }); setCount(0) }}>Reset</button>
      </div>
      <div className="row" style={{ flex: 1, minHeight: 0 }}>
        <div className="col">
          <DocList title="📱 Device · Reads DB" docs={s.reads} empty="no meals yet" />
          <div className="card" style={{ height: 190, overflow: 'hidden' }}>
            <h3 style={{ fontSize: 30 }}>📱 Device · Writes DB</h3>
            {s.commands.slice(-3).map((c) => (
              <div key={c.id} className="mono" style={{ fontSize: 22 }}>{c.id} · {c.state}{c.attempt ? ` · attempt ${c.attempt}` : ''}{c.roundTripDone ? ' · roundTripDone' : ''}</div>
            ))}
          </div>
        </div>
        <div className="col" style={{ flex: 0.8 }}>
          <div className="card" style={{ textAlign: 'center', borderColor: k.offline ? 'var(--danger)' : undefined }}>
            <div style={{ fontSize: 36, fontWeight: 700 }}>{k.offline ? '✈ OFFLINE · commands queue locally' : '🌐 Online · Sync Gateway'}</div>
            <div className="muted" style={{ fontSize: 24 }}>
              {k.persist500 ? 'persistent 500 · ' : ''}{k.fail400Next ? 'next → 400 · ' : ''}{k.fail500Next ? `next → 500 ×${k.fail500Next} · ` : ''}each tick = one hop
            </div>
          </div>
          <DocList title="☁ Server · Remote reads" docs={s.serverReads} empty="empty (temp docs never arrive)" />
          <pre className="mono card" style={{ margin: 0, fontSize: 17, flex: 1, minHeight: 0, overflow: 'hidden' }}>{inspect}</pre>
        </div>
        <div className="col" style={{ flex: 0.9 }}>
          <div className="card" style={{ flex: 1, overflow: 'hidden' }}>
            <h3 style={{ fontSize: 30 }}>Event log</h3>
            <div className="mono" style={{ fontSize: 20, display: 'flex', flexDirection: 'column', gap: 6, marginTop: 8 }}>
              {s.log.slice(-9).map((l, i) => <div key={i} className="muted">▸ {l}</div>)}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
