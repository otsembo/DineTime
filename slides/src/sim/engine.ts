export type CommandState = 'SUBMITTED' | 'ACKNOWLEDGED' | 'COMPLETED' | 'FAILED_RETRYABLE' | 'FAILED_USER_ACTION'
export type MealStatus = 'PENDING' | 'PROCESSING' | 'ACTIVE' | 'ERROR'

export interface Command {
  id: string
  state: CommandState
  name: string
  attempt: number
  acked: boolean
  roundTripDone: boolean
  error?: string
  plan?: { fail500: number; fail400: boolean }
}
export interface Meal {
  id: string
  name: string
  isTemp: boolean
  status?: MealStatus
  error?: string
  retry?: number
  lastCommandId?: string
  clientRef?: string
}
export interface Controls {
  offline: boolean
  fail500Next: number
  fail400Next: boolean
  persist500: boolean
  persist400: boolean
}
export interface SimState {
  seq: number
  /** reads database on the device */
  reads: Meal[]
  /** writes database on the device */
  commands: Command[]
  /** reads collection on the server side (never contains temp docs) */
  serverReads: Meal[]
  controls: Controls
  log: string[]
}

export type Action =
  | { type: 'save'; name: string }
  | { type: 'resubmit'; commandId: string; name: string }
  | { type: 'controls'; patch: Partial<Controls> }
  | { type: 'tick' }
  | { type: 'reset' }

export const initialState = (): SimState => ({
  seq: 0,
  reads: [],
  commands: [],
  serverReads: [],
  controls: { offline: false, fail500Next: 0, fail400Next: false, persist500: false, persist400: false },
  log: [],
})

/** Mirrors MealItems.statusFor in the Android app. */
export function statusFor(c: Command): { status: MealStatus; error?: string } {
  switch (c.state) {
    case 'SUBMITTED':
      return { status: 'PENDING' }
    case 'ACKNOWLEDGED':
    case 'COMPLETED':
      return { status: 'PROCESSING' }
    case 'FAILED_RETRYABLE':
      return { status: c.acked ? 'PROCESSING' : 'PENDING' }
    case 'FAILED_USER_ACTION':
      return { status: 'ERROR', error: c.error }
  }
}

const say = (s: SimState, msg: string): SimState => ({ ...s, log: [...s.log, msg].slice(-40) })

function takePlan(s: SimState, c: Command): { s: SimState; c: Command } {
  if (c.plan) return { s, c }
  const k = s.controls
  const plan = { fail500: k.fail500Next, fail400: k.fail400Next }
  return {
    s: { ...s, controls: { ...k, fail500Next: 0, fail400Next: false } },
    c: { ...c, plan },
  }
}

const patchCommand = (s: SimState, c: Command): SimState => ({
  ...s,
  commands: s.commands.map((x) => (x.id === c.id ? c : x)),
})

/** One tick = one hop of the round trip for every in-flight command. */
function advance(s0: SimState, id: string): SimState {
  let s = s0
  let c = s.commands.find((x) => x.id === id)!
  if (c.roundTripDone || c.state === 'FAILED_USER_ACTION') return s
  const online = !s.controls.offline
  const temp = s.reads.find((m) => m.clientRef === c.id)

  if (c.state === 'SUBMITTED' || c.state === 'FAILED_RETRYABLE') {
    if (!online) return say(s, `${c.name}: queued offline, writes DB holds the command`)
    const taken = takePlan(s, c)
    s = taken.s
    c = taken.c
    const k = s.controls
    if (k.persist400 || c.plan?.fail400) {
      c = { ...c, state: 'FAILED_USER_ACTION', error: 'HTTP 400 · validation failed, edit the meal and resubmit' }
      s = patchCommand(s, c)
      return say(s, `server → 400 for ${c.name}; response replicated back, state FAILED_USER_ACTION`)
    }
    if (k.persist500 || (c.plan?.fail500 ?? 0) > 0) {
      c = {
        ...c,
        state: 'FAILED_RETRYABLE',
        attempt: c.attempt + 1,
        plan: c.plan && !k.persist500 ? { ...c.plan, fail500: c.plan.fail500 - 1 } : c.plan,
      }
      s = patchCommand(s, c)
      return say(s, `server → 500 for ${c.name}; server will retry (attempt ${c.attempt})`)
    }
    c = { ...c, state: 'ACKNOWLEDGED', acked: true }
    s = patchCommand(s, c)
    return say(s, `server accepted ${c.name}; ack replicated → PROCESSING`)
  }

  if (c.state === 'ACKNOWLEDGED') {
    if (!online) return say(s, `${c.name}: server work done but offline, nothing replicates`)
    const real: Meal = { id: `meal::${c.id}`, name: c.name, isTemp: false, lastCommandId: c.id }
    s = { ...s, serverReads: [...s.serverReads, real] }
    s = patchCommand(s, { ...c, state: 'COMPLETED' })
    return say(s, `server wrote ${real.id} to remote reads DB, command COMPLETED`)
  }

  // COMPLETED
  if (!online) return s
  const real = s.serverReads.find((m) => m.lastCommandId === c.id)
  const replicated = s.reads.some((m) => !m.isTemp && m.lastCommandId === c.id)
  if (real && !replicated) {
    s = { ...s, reads: [...s.reads, real] }
    return say(s, `reads replication delivered ${real.id}`)
  }
  if (replicated) {
    s = { ...s, reads: s.reads.filter((m) => m.clientRef !== c.id) }
    s = patchCommand(s, { ...c, roundTripDone: true })
    return say(s, `reconciler matched lastCommandId, deleted ${temp?.id ?? 'temp doc'} → ACTIVE`)
  }
  return s
}

/** Reconciler: keep temp docs in step with command state (idempotent). */
function reconcile(s: SimState): SimState {
  return {
    ...s,
    reads: s.reads.map((m) => {
      if (!m.isTemp) return m
      const c = s.commands.find((x) => x.id === m.clientRef)
      if (!c) return m
      const { status, error } = statusFor(c)
      return { ...m, status, error, retry: c.state === 'FAILED_RETRYABLE' ? c.attempt : 0 }
    }),
  }
}

export function reduce(s: SimState, a: Action): SimState {
  switch (a.type) {
    case 'reset':
      return initialState()
    case 'controls':
      return { ...s, controls: { ...s.controls, ...a.patch } }
    case 'save': {
      const id = `c${s.seq + 1}`
      const temp: Meal = { id: `tmp::${id}`, name: a.name, isTemp: true, status: 'PENDING', clientRef: id }
      const cmd: Command = { id, state: 'SUBMITTED', name: a.name, attempt: 0, acked: false, roundTripDone: false }
      return say({ ...s, seq: s.seq + 1, reads: [...s.reads, temp], commands: [...s.commands, cmd] }, `user saved "${a.name}": temp doc + command written locally`)
    }
    case 'resubmit': {
      const c = s.commands.find((x) => x.id === a.commandId)
      if (!c || c.state !== 'FAILED_USER_ACTION') return s
      const next: Command = { ...c, name: a.name, state: 'SUBMITTED', attempt: 0, error: undefined, plan: undefined, acked: false }
      return reconcile(say(patchCommand(s, next), `user fixed ${a.name} and resubmitted the command`))
    }
    case 'tick': {
      let n = s
      for (const c of s.commands) n = advance(n, c.id)
      return reconcile(n)
    }
  }
}
