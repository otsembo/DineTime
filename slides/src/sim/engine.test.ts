import { describe, expect, it } from 'vitest'
import { initialState, reduce, type Action, type SimState } from './engine'

const run = (s: SimState, ...a: Action[]) => a.reduce(reduce, s)
const ticks = (s: SimState, n: number) => run(s, ...Array<Action>(n).fill({ type: 'tick' }))
const status = (s: SimState, name: string) => s.reads.find((m) => m.name === name && m.isTemp)?.status

describe('round trip simulator', () => {
  it('goes PENDING → PROCESSING → ACTIVE and deletes the temp doc', () => {
    let s = run(initialState(), { type: 'save', name: 'Pasta' })
    expect(status(s, 'Pasta')).toBe('PENDING')
    s = ticks(s, 1)
    expect(status(s, 'Pasta')).toBe('PROCESSING')
    s = ticks(s, 3)
    expect(s.reads.filter((m) => m.isTemp)).toHaveLength(0)
    expect(s.reads.filter((m) => !m.isTemp)).toHaveLength(1)
    expect(s.commands[0].roundTripDone).toBe(true)
  })

  it('never replicates temp docs to the server', () => {
    const s = ticks(run(initialState(), { type: 'save', name: 'Pasta' }), 5)
    expect(s.serverReads.every((m) => !m.isTemp)).toBe(true)
  })

  it('holds commands while offline then drains on reconnect', () => {
    let s = run(initialState(), { type: 'controls', patch: { offline: true } }, { type: 'save', name: 'Rice' })
    s = ticks(s, 4)
    expect(status(s, 'Rice')).toBe('PENDING')
    s = ticks(run(s, { type: 'controls', patch: { offline: false } }), 4)
    expect(s.commands[0].roundTripDone).toBe(true)
  })

  it('400 → ERROR, then fix and resubmit → ACTIVE', () => {
    let s = run(initialState(), { type: 'controls', patch: { fail400Next: true } }, { type: 'save', name: 'Bad' })
    s = ticks(s, 1)
    expect(status(s, 'Bad')).toBe('ERROR')
    expect(s.reads[0].error).toContain('400')
    s = ticks(s, 3)
    expect(status(s, 'Bad')).toBe('ERROR')
    s = ticks(run(s, { type: 'resubmit', commandId: 'c1', name: 'Good' }), 4)
    expect(s.commands[0].roundTripDone).toBe(true)
    expect(s.reads.some((m) => !m.isTemp && m.name === 'Good')).toBe(true)
  })

  it('500 ×3 retries three times, shows PENDING with retry count, then succeeds', () => {
    let s = run(initialState(), { type: 'controls', patch: { fail500Next: 3 } }, { type: 'save', name: 'Soup' })
    s = ticks(s, 1)
    expect(status(s, 'Soup')).toBe('PENDING')
    expect(s.reads[0].retry).toBe(1)
    s = ticks(s, 2)
    expect(s.reads[0].retry).toBe(3)
    s = ticks(s, 5)
    expect(s.commands[0].roundTripDone).toBe(true)
  })

  it('persistent 500 never completes', () => {
    let s = run(initialState(), { type: 'controls', patch: { persist500: true } }, { type: 'save', name: 'Soup' })
    s = ticks(s, 10)
    expect(s.commands[0].roundTripDone).toBe(false)
    expect(s.commands[0].attempt).toBe(10)
    s = ticks(run(s, { type: 'controls', patch: { persist500: false } }), 4)
    expect(s.commands[0].roundTripDone).toBe(true)
  })

  it('failure injection is consumed once and applies to a single command', () => {
    let s = run(initialState(), { type: 'controls', patch: { fail400Next: true } }, { type: 'save', name: 'A' })
    s = ticks(s, 1)
    s = ticks(run(s, { type: 'save', name: 'B' }), 4)
    expect(s.commands.find((c) => c.name === 'B')?.roundTripDone).toBe(true)
  })
})
