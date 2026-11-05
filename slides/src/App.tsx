import { DeckProvider, useDeck } from './engine/deck'
import { Overview } from './engine/Overview'
import { Presenter } from './engine/Presenter'
import { Stage } from './engine/Stage'
import { slides } from './slides'

const params = new URLSearchParams(window.location.search)

function PrintView() {
  return (
    <div style={{ position: 'static', background: '#000' }}>
      <style>{`@page { size: 1920px 1080px; margin: 0 } html, body, #root { overflow: visible !important; height: auto !important }
        .page { width: 1920px; height: 1080px; position: relative; page-break-after: always; overflow: hidden }
        .page .stage { left: 0 !important; top: 0 !important; transform: none !important }`}</style>
      {slides.map((s) => (
        <div className="page" key={s.id}>
          <div className="stage">{s.render({ step: (s.steps ?? 1) - 1 })}</div>
        </div>
      ))}
    </div>
  )
}

function Main() {
  const { overview } = useDeck()
  return (
    <>
      <Stage />
      {overview && <Overview />}
    </>
  )
}

export default function App() {
  if (params.has('print')) return <PrintView />
  if (params.has('presenter')) {
    return (
      <DeckProvider slides={slides} follower>
        <Presenter />
      </DeckProvider>
    )
  }
  return (
    <DeckProvider slides={slides}>
      <Main />
    </DeckProvider>
  )
}
