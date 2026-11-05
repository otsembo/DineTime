import type { SlideDef } from '../engine/types'
import { Reveal, Title } from '../components/ui'
import { Bullets, K, Slide } from './common'

export const errors: SlideDef[] = [
  {
    id: 'err-500-400', title: '500 vs 400', section: 'Errors', seconds: 90, steps: 3,
    notes: 'The rule is simple and keeps the client dumb. 5xx: the server retries, the user is never asked. 4xx: the request is wrong, retrying changes nothing, so the user has to act on device before we resubmit.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Failure policy">Who retries?</Title>
        <div className="row" style={{ flex: 1 }}>
          <Reveal show={step >= 1} grow>
            <div className="card col" style={{ flex: 1, borderColor: 'var(--processing)' }}>
              <h3>5xx · transient</h3>
              <p>The server retries with backoff.</p>
              <p>UI: stays PENDING / PROCESSING, shows <K>Retrying (n)</K>.</p>
            </div>
          </Reveal>
          <Reveal show={step >= 2} grow>
            <div className="card col" style={{ flex: 1, borderColor: 'var(--danger)' }}>
              <h3>4xx · user action</h3>
              <p>Command goes to <K>FAILED_USER_ACTION</K>.</p>
              <p>UI: <K>ERROR</K> on the temp doc with the payload error. User edits, then resubmits.</p>
            </div>
          </Reveal>
        </div>
      </Slide>
    ),
  },
  {
    id: 'err-idem', title: 'Idempotency', section: 'Errors', seconds: 90, steps: 4,
    notes: 'Retries and replication can deliver things twice. The command id makes every step idempotent. The real doc id derives from it, the reconciler is safe to re-run, and terminal states are never overwritten.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Distributed systems, small edition">Everything is safe to repeat</Title>
        <Bullets step={step} items={[
          <>Real doc id is derived from the command id: <K>meal::&lt;commandId&gt;</K></>,
          <>Reconciler can run any number of times, even after an app restart</>,
          <>Terminal commands are never overwritten (conditional save on revision)</>,
        ]} />
      </Slide>
    ),
  },
  {
    id: 'err-story', title: 'The simulator that ignored my 400s', section: 'Errors', seconds: 90, steps: 4,
    notes: 'A real bug in our demo. We queued 400s and 500s and everything still completed. Two apps were running against one Sync Gateway, and each ran its own fake server, so the one with default settings completed the command. Production lesson: be explicit about who is allowed to process a command.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="War story">Two servers, one queue</Title>
        <Bullets step={step} items={[
          <>Two installs, both simulating the server on the same feed</>,
          <>The one with no failures queued answered first</>,
          <>Fix: commands carry <K>deviceId</K>; only the owner processes them</>,
        ]} />
      </Slide>
    ),
  },
  {
    id: 'err-ux', title: 'Designing the error state', section: 'Errors', seconds: 90, steps: 4,
    notes: 'The error must be actionable. We keep the temp doc in ERROR, show the server’s message, and let the user edit and resubmit, which creates a fresh attempt on the same temp doc.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="UX">Errors the user can fix</Title>
        <Bullets step={step} items={[
          <>Message lives on the temp doc, so it survives restarts</>,
          <>Editing is blocked while a command is in flight, allowed in ERROR</>,
          <>Discard removes the temp doc and closes the command</>,
        ]} />
      </Slide>
    ),
  },
]

export const lessons: SlideDef[] = [
  {
    id: 'tradeoffs', title: 'Trade-offs', section: 'Lessons', seconds: 90, steps: 3,
    notes: 'Be honest: this is more moving parts. We accept them because offline is a requirement, not a nice-to-have.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Honest accounting">What it costs</Title>
        <div className="row">
          <Reveal show={step >= 1} grow><div className="card" style={{ flex: 1, height: 420 }}><h3>You gain</h3><ul><li>True offline writes</li><li>UI that never waits</li><li>Server stays the source of truth</li></ul></div></Reveal>
          <Reveal show={step >= 2} grow><div className="card" style={{ flex: 1, height: 420 }}><h3>You pay</h3><ul><li>Two collections to reason about</li><li>A reconciler to keep correct</li><li>Eventual consistency in the UI</li></ul></div></Reveal>
        </div>
      </Slide>
    ),
  },
  {
    id: 'pitfalls', title: 'Pitfalls we hit', section: 'Lessons', seconds: 90, steps: 5,
    notes: 'Concrete and short. Each one cost us time.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Scar tissue">Things that bit us</Title>
        <Bullets step={step} items={[
          <>Couchbase Lite 4 needs <b>Sync Gateway 4</b>: mismatches fail with “invalid revision ID”</>,
          <>Filter temp docs from push, or they leak to the server</>,
          <>Test the offline paths: airplane mode is a feature test, not a smoke test</>,
          <>Make ownership of a command explicit</>,
        ]} />
      </Slide>
    ),
  },
  {
    id: 'when-not', title: 'When not to do this', section: 'Lessons', seconds: 60, steps: 3,
    notes: 'Do not oversell. If your data is read mostly and writes can wait for a connection, a simple cache is enough.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Be skeptical">When this is overkill</Title>
        <Bullets step={step} items={[<>Read-mostly apps where a cache is enough</>, <>Writes that must be confirmed before the user continues</>]} />
      </Slide>
    ),
  },
]

// TODO: replace the placeholders with real details
const SOCIALS = [
  { label: 'X / Twitter', value: '@otsembo_' },
  { label: 'LinkedIn', value: 'linkedin.com/in/okumu-ian' },
  { label: 'GitHub', value: 'https://github.com/otsembo/DineTime' },
]

export const closing: SlideDef[] = [
  {
    id: 'takeaways', title: 'Takeaways', section: 'Wrap-up', seconds: 60, steps: 4,
    notes: 'Say these slowly. Then open for questions.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Remember three things">Takeaways</Title>
        <Bullets step={step} items={[
          <>Separate <b>what the user asked</b> from <b>what is true</b></>,
          <>Let replication carry the answers back; derive UI state from data</>,
          <>Failure policy is a product decision: retry 5xx, ask the user on 4xx</>,
        ]} />
      </Slide>
    ),
  },
  {
    id: 'qa', title: 'Questions', section: 'Wrap-up', seconds: 60,
    notes: 'Point to the repo and the README with the full setup. Keep the simulator slide in one tab for demonstrating answers.',
    render: () => (
      <Slide center>
        <h1>Asante sana</h1>
        <h3>Questions?</h3>
        <p className="muted">Repo: DineTime · slides/ · README has the full test script</p>
      </Slide>
    ),
  },
  {
    id: 'contact', title: 'Get in touch', section: 'Wrap-up', seconds: 0, steps: 1,
    notes: 'Leave this up during hallway chat. Replace the placeholders in src/slides/errors.tsx (the SOCIALS array) with the real handles and links.',
    render: () => (
      <Slide>
        <Title eyebrow="Let’s talk">Get in touch</Title>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          {SOCIALS.map((c) => (
            <div key={c.label} className="card" style={{ width: 'calc(50% - 20px)' }}>
              <div className="eyebrow" style={{ fontSize: 22 }}>{c.label}</div>
              <div className="mono" style={{ fontSize: 34, color: 'var(--tertiary)' }}>{c.value}</div>
            </div>
          ))}
        </div>
      </Slide>
    ),
  },
]
