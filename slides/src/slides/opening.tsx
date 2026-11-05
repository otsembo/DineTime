import { motion } from 'framer-motion'
import type { SlideDef } from '../engine/types'
import { Reveal, Title } from '../components/ui'
import { Bullets, K, Slide } from './common'

export const opening: SlideDef[] = [
  {
    id: 'title', title: 'Title', section: 'Opening', seconds: 30,
    notes: 'Welcome. One sentence on who you are and where you work. Promise: by the end they can sketch an offline-first CQRS flow on a napkin, and they will see it fail and recover live.',
    render: () => (
      <Slide center>
        <div className="eyebrow">droidcon Kenya 2026</div>
        <motion.h1 initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }}>Offline-first, on purpose</motion.h1>
        <h3>CQRS with Jetpack Compose, Navigation 3 and Couchbase Lite</h3>
        <p className="muted">Reads, writes, and a server that is allowed to be late</p>
      </Slide>
    ),
  },
  {
    id: 'hook', title: 'The train in the tunnel', section: 'Opening', seconds: 60, steps: 5,
    notes: 'Tell the story slowly, one line per step. Ask the room: how many of your apps show a spinner here? The goal is a UI that never waits on the network, and still tells the truth about what has happened.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Imagine">A user on a matatu, in a tunnel, taps <K>Save</K></Title>
        <Bullets step={step} items={[
          <>No signal. The request cannot leave the phone.</>,
          <>A spinner would be a lie. An error would be unfair.</>,
          <>What they want: <b>“it is saved, and I can see it”</b></>,
          <><span className="big" style={{ color: 'var(--primary)' }}>That is the whole talk.</span></>,
        ]} />
      </Slide>
    ),
  },
  {
    id: 'field', title: 'Our reality: the field app', section: 'Opening', seconds: 90, steps: 5,
    notes: 'This is why we care. Our staff use a field app for agricultural operations, often in remote areas. Connectivity is slow, intermittent or metered. The app also talks to many microservices owned by different parts of the organisation, so any one of them can be slow or down while the others work. The DineTime sample server stands in for one of these services. Landing line: the app cannot assume the network, so the UI must not either.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="At work">A field app for agricultural operations</Title>
        <div className="row">
          <Reveal show={step >= 1} grow><div className="card" style={{ flex: 1, height: 330 }}><h3>📍 Remote areas</h3><p>Staff work far from towns, where signal is a luxury.</p></div></Reveal>
          <Reveal show={step >= 2} grow><div className="card" style={{ flex: 1, height: 330 }}><h3>📶 Bad networks</h3><p>Slow, intermittent, or metered: every megabyte counts.</p></div></Reveal>
          <Reveal show={step >= 3} grow><div className="card" style={{ flex: 1, height: 330 }}><h3>🧩 Many services</h3><p>One app talks to multiple microservices run by different parts of the org.</p></div></Reveal>
        </div>
        <Reveal show={step >= 4}><div className="card big" style={{ borderColor: 'var(--primary)' }}>Any one service can be down while the others work. The app must still work.</div></Reveal>
      </Slide>
    ),
  },
  {
    id: 'agenda', title: 'Agenda', section: 'Opening', seconds: 90, steps: 7,
    notes: 'Set expectations: 45 minutes, about 15 of them real code, 5 of them a live demo. Slides are a web page, the demo is a simulator that runs the same state machine as the Android app.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="45 minutes">What we will cover</Title>
        <Bullets step={step} items={[
          <>Why we split reads from writes on a phone</>,
          <>The round trip, hop by hop</>,
          <>The code that makes it work (Kotlin, Couchbase Lite)</>,
          <>A live demo you can break</>,
          <>When the server says 500 or 400</>,
          <>Lessons, trade-offs and when not to do this</>,
        ]} />
      </Slide>
    ),
  },
]

export const why: SlideDef[] = [
  {
    id: 'naive', title: 'The naive approach', section: 'Why CQRS', seconds: 75, steps: 5,
    notes: 'Most apps write straight to a local DB and also call an API. The trouble is that the server computes things: ids, validation, reminders, totals. The local copy is a guess.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="One document, two owners">Why a single local copy hurts</Title>
        <Bullets step={step} items={[
          <>Server assigns the real id, so the local doc is a <b>guess</b></>,
          <>Server can reject: validation, quotas, permissions</>,
          <>Retry logic leaks into every screen</>,
        ]} />
        <Reveal show={step >= 4}><div className="card big">Who is right when they disagree?</div></Reveal>
      </Slide>
    ),
  },
  {
    id: 'cqrs', title: 'Split reads and writes', section: 'Why CQRS', seconds: 75, steps: 3,
    notes: 'CQRS: Command Query Responsibility Segregation. Here it is literal: two databases. The reads DB is what the UI renders. The writes DB is a log of what the user asked for.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Command Query Responsibility Segregation">Two databases, two jobs</Title>
        <div className="row" style={{ alignItems: 'stretch' }}>
          <Reveal show grow><div className="card col" style={{ flex: 1 }}><h3>Reads DB</h3><p>What the UI renders. Real docs from the server, plus <b>temporary</b> docs for work in flight.</p></div></Reveal>
          <Reveal show={step >= 1} grow><div className="card col" style={{ flex: 1 }}><h3>Writes DB</h3><p>What the user asked for. Commands, their state, and the server’s responses.</p></div></Reveal>
          <Reveal show={step >= 2} grow><div className="card col" style={{ flex: 1, borderColor: 'var(--active)' }}><h3>Result</h3><p>The UI reads from one place and never waits on the network.</p></div></Reveal>
        </div>
      </Slide>
    ),
  },
  {
    id: 'app', title: 'DineTime', section: 'Why CQRS', seconds: 75, steps: 5,
    notes: 'DineTime is the demo app, a meal planner. Real flows from our work are the same shape: user intent, server work, eventual real document.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="The demo app">DineTime, a meal planner</Title>
        <Bullets step={step} items={[
          <>View meals grouped by day of the week</>,
          <>Record a meal you did not plan, or plan one for later</>,
          <>Reminders, customisable time before the meal</>,
        ]} />
        <Reveal show={step >= 4}><div className="card">Everything works with no network. The server is simulated in the app, so the demo is self-contained.</div></Reveal>
      </Slide>
    ),
  },
  {
    id: 'stack', title: 'The stack', section: 'Why CQRS', seconds: 75, steps: 4,
    notes: 'Quick tour. Collections group docs by domain. Couchbase Lite 4 pairs with Sync Gateway 4: a version mismatch gave us “invalid revision ID” errors. Websockets keep both sides live.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Technology">What it is built with</Title>
        <div className="row">
          {[
            ['Jetpack Compose', 'UI driven by Flows over the reads DB'],
            ['Navigation 3', 'Back stack as state: @Serializable NavKeys'],
            ['Couchbase Lite 4', 'Embedded DB, collections per domain'],
            ['Sync Gateway 4', 'Websocket replication to Couchbase Server'],
          ].map(([t, d], i) => (
            <Reveal key={t} show={step >= i} grow>
              <div className="card" style={{ height: 260 }}><h3 style={{ fontSize: 36 }}>{t}</h3><p style={{ fontSize: 28 }}>{d}</p></div>
            </Reveal>
          ))}
        </div>
      </Slide>
    ),
  },
]
