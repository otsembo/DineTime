import type { SlideDef } from '../engine/types'
import { ArchitectureDiagram, ARCH_STEPS } from '../components/ArchitectureDiagram'
import { CodeBlock } from '../components/CodeBlock'
import { Reveal, StatusBadge, Title } from '../components/ui'
import { Simulator } from '../components/Simulator'
import { Bullets, K, Slide } from './common'
import commandDoc from '../snippets/commandDoc.kt?raw'
import createMeal from '../snippets/createMeal.kt?raw'
import replicators from '../snippets/replicators.kt?raw'
import statusFor from '../snippets/statusFor.kt?raw'
import reconcile from '../snippets/reconcile.kt?raw'
import navKeys from '../snippets/navKeys.kt?raw'

type Range = Array<[number, number]>
const codeSlide = (o: { id: string; title: string; seconds: number; notes: string; code: string; file: string; stops: Array<{ focus?: Range; say: string }> }): SlideDef => ({
  id: o.id, title: o.title, section: 'Code', seconds: o.seconds, notes: o.notes, steps: o.stops.length + 1,
  render: ({ step }) => {
    const stop = step > 0 ? o.stops[step - 1] : undefined
    return (
      <Slide>
        <Title eyebrow="Code">{o.title}</Title>
        <CodeBlock code={o.code} title={o.file} focus={stop?.focus} fontSize={o.code.split('\n').length > 12 ? 22 : 30} />
        <div style={{ fontSize: 36, minHeight: 50, color: 'var(--tertiary)' }}>{stop?.say ?? ''}</div>
      </Slide>
    )
  },
})

export const architecture: SlideDef[] = [
  {
    id: 'arch', title: 'The round trip', section: 'Architecture', seconds: 180, steps: ARCH_STEPS,
    notes: 'Walk the diagram one step at a time, do not skip ahead. Key points: (1) the UI only ever reads the reads DB. (2) The temp doc is created locally and never replicated. (3) The ack comes back through replication, not an HTTP response. (4) The real doc arrives through the normal reads replication. (5) The reconciler uses the command’s id on the real doc to delete the temp one.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Architecture">One save, five hops</Title>
        <ArchitectureDiagram step={step} />
      </Slide>
    ),
  },
  {
    id: 'temp', title: 'The temp doc never leaves the device', section: 'Architecture', seconds: 90, steps: 5,
    notes: 'This is the trick. The temp doc exists only so the UI has something honest to show. It is excluded from push replication with a filter, it has a different id, and it is deleted the moment the real doc shows up.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Rule #1">The temp doc is local only</Title>
        <Bullets step={step} items={[
          <>Id <K>tmp::&lt;commandId&gt;</K>, flagged <K>isTemp</K></>,
          <>Excluded from replication by a <b>push filter</b></>,
          <>Real doc id is <K>meal::&lt;commandId&gt;</K> and carries <K>lastCommandId</K></>,
        ]} />
        <Reveal show={step >= 4}><div className="card">The command id is the join key between the temp doc, the command and the real doc.</div></Reveal>
      </Slide>
    ),
  },
  {
    id: 'states', title: 'What the UI shows', section: 'Architecture', seconds: 150, steps: 5,
    notes: 'The UI states fall out of the data. Pending: only the command exists, nothing is acknowledged. Processing: an ack replicated back. Active: the real reads doc arrived. Error: a 400, shown on the temp doc with the server’s message for the user to correct.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="UI reacts to data on device">Four states, derived not stored</Title>
        <div className="row">
          {([
            ['PENDING', 'Temp doc exists. Command submitted. The server has not acknowledged.'],
            ['PROCESSING', 'Ack replicated back. Server is working on it.'],
            ['ACTIVE', 'Real doc replicated into reads. Temp doc deleted.'],
            ['ERROR', '400: user must act. Payload error shown on the temp doc.'],
          ] as const).map(([s, d], i) => (
            <Reveal key={s} show={step >= i + 1} grow>
              <div className="card" style={{ height: 330 }}>
                <StatusBadge status={s} />
                <p style={{ fontSize: 30 }}>{d}</p>
              </div>
            </Reveal>
          ))}
        </div>
        <Reveal show={step >= 4}><div className="muted">Retries on 500 stay in PENDING or PROCESSING and show a “Retrying (n)” badge.</div></Reveal>
      </Slide>
    ),
  },
]

export const code: SlideDef[] = [
  codeSlide({
    id: 'code-command', title: 'The command document', seconds: 90, code: commandDoc, file: 'domain/Models.kt',
    notes: 'The writes DB is a log of intent. Walk the highlighted fields: state machine, links to the temp and real docs, roundTripDone, and the deviceId we added after a bug (more on that later).',
    stops: [
      { focus: [[5, 5], [9, 9]], say: 'What was asked, and where it is in its life' },
      { focus: [[7, 8], [11, 11]], say: 'Links to the temp doc, the target doc and the real doc' },
      { focus: [[14, 15]], say: 'Set only when the reads doc has made it back' },
      { focus: [[17, 18]], say: 'Which install owns this command' },
    ],
  }),
  codeSlide({
    id: 'code-create', title: 'Saving a meal', seconds: 90, code: createMeal, file: 'data/reads/MealRepository.kt',
    notes: 'No network call anywhere. Two local writes. The UI flow reacts instantly because the reads DB changed.',
    stops: [
      { focus: [[2, 5]], say: 'A command id ties everything together' },
      { focus: [[6, 6]], say: 'Temp doc into the reads DB: UI shows PENDING' },
      { focus: [[7, 12]], say: 'Command into the writes DB: replication will carry it' },
    ],
  }),
  codeSlide({
    id: 'code-repl', title: 'Replication, one per job', seconds: 90, code: replicators, file: 'data/couchbase/ReplicatorManager.kt',
    notes: 'Two continuous replicators over websockets. Reads is pull only. Writes is push and pull because acknowledgements come back on the same collection.',
    stops: [
      { focus: [[4, 7]], say: 'Reads: pull only' },
      { focus: [[6, 6]], say: 'Belt and braces: temp docs are filtered from push' },
      { focus: [[8, 8]], say: 'Writes: push commands, pull acknowledgements' },
    ],
  }),
  codeSlide({
    id: 'code-reconcile', title: 'The reconciler', seconds: 120, code: reconcile, file: 'sync/Reconciler.kt',
    notes: 'It watches both databases and is idempotent, so it also repairs state after an app restart. Real doc present plus COMPLETED command means delete the temp doc and mark the round trip done.',
    stops: [
      { focus: [[6, 10]], say: 'Real doc with our lastCommandId + COMPLETED = done' },
      { focus: [[12, 15]], say: 'Otherwise keep the temp doc’s status in step with the command' },
      { focus: [[1, 5]], say: 'Temp doc already gone? Just close the loop' },
    ],
  }),
  codeSlide({
    id: 'code-status', title: 'Status is a pure function', seconds: 90, code: statusFor, file: 'domain/MealItems.kt',
    notes: 'This is unit-testable without a device. Note the retryable case: ack or no ack decides whether the user sees PENDING or PROCESSING.',
    stops: [
      { focus: [[2, 2]], say: 'Nothing acknowledged: PENDING' },
      { focus: [[3, 3]], say: 'Ack received: PROCESSING' },
      { focus: [[4, 5]], say: '500 retry keeps whichever state we were in' },
      { focus: [[6, 6]], say: '400: ERROR with the server’s message' },
    ],
  }),
  {
    id: 'code-ui', title: 'Compose just collects', section: 'Code', seconds: 60, steps: 3,
    notes: 'Simplified from the real screen. The UI has no idea about replication. It collects a Flow of MealItems that already merge real docs, temp docs and commands.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Code (simplified)">Compose just collects</Title>
        <CodeBlock title="HomeScreen.kt (simplified)" focus={step === 1 ? [[1, 1]] : step === 2 ? [[2, 4]] : undefined} code={`val items by repo.observeItems().collectAsStateWithLifecycle(emptyList())
LazyColumn {
    items(items, key = { it.id }) { item ->
        MealCard(item) { StatusBadge(item.status, item.retryAttempt) }
    }
}`} />
        <div style={{ fontSize: 36, color: 'var(--tertiary)' }}>{step === 1 ? 'One Flow, merged on device' : step === 2 ? 'No spinners, no network state in the screen' : ''}</div>
      </Slide>
    ),
  },
  codeSlide({
    id: 'code-nav', title: 'Navigation 3 keys', seconds: 60, code: navKeys, file: 'ui/Navigation.kt',
    notes: 'Quick aside. Nav3 back stack is plain state. Keys are serializable so the stack survives process death, which matters for an app people open between tunnels.',
    stops: [{ focus: [[2, 3]], say: 'Keys carry just ids: the data comes from the reads DB' }],
  }),
]

export const demo: SlideDef[] = [
  {
    id: 'demo-intro', title: 'Live demo', section: 'Demo', seconds: 30, steps: 4,
    notes: 'Tell them what to watch for before starting: badge changes, the temp doc vanishing, the real doc arriving. Then press next.',
    render: ({ step }) => (
      <Slide center>
        <div className="eyebrow">Live</div>
        <h1>Now let’s break it</h1>
        <Bullets step={step} startAt={1} items={[<>Save a meal and follow the hops</>, <>Go offline, queue some meals</>, <>Inject a 400 and a 500</>]} />
      </Slide>
    ),
  },
  {
    id: 'demo', title: 'Interactive simulator', section: 'Demo', seconds: 300,
    notes: 'Run this order. 1) Save one meal, auto mode, narrate. 2) Switch to Step mode and press N to walk each hop. 3) Offline, save two meals, they stay PENDING; go online, they drain. 4) 400 next, save: ERROR with the message; Fix & resubmit. 5) 500 ×3: Retrying (1)(2)(3) then ACTIVE. 6) Always 500: stays pending until you switch it off. The same state machine is unit tested in the repo. Fallback if anything misbehaves: press Reset.',
    render: () => (
      <Slide>
        <Title eyebrow="Same state machine as the app">Break the round trip</Title>
        <Simulator />
      </Slide>
    ),
  },
  {
    id: 'demo-debrief', title: 'What we just saw', section: 'Demo', seconds: 150, steps: 5,
    notes: 'Recap what the audience observed and tie it to the code they saw.',
    render: ({ step }) => (
      <Slide>
        <Title eyebrow="Debrief">What just happened</Title>
        <Bullets step={step} items={[
          <>Offline writes never blocked: the command waited in the writes DB</>,
          <>Acknowledgement and result arrived through <b>replication</b>, not responses</>,
          <>A 400 became data on the temp doc, and the UI simply rendered it</>,
          <>A 500 was invisible to the user except a retry count</>,
        ]} />
      </Slide>
    ),
  },
]
