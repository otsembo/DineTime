interface Node { id: string; x: number; y: number; w?: number; label: string; sub?: string; cloud?: boolean }
interface Edge { id: string; from: [number, number]; to: [number, number]; step: number; label: string; lx: number; ly: number; color?: string }

const NODES: Node[] = [
  { id: 'ui', x: 250, y: 40, label: 'Compose UI', sub: 'observes Flows' },
  { id: 'reads', x: 20, y: 270, label: 'Reads DB', sub: 'meals collection' },
  { id: 'writes', x: 480, y: 270, label: 'Writes DB', sub: 'meal_commands' },
  { id: 'rec', x: 250, y: 500, label: 'Reconciler', sub: 'temp ↔ real' },
  { id: 'sg', x: 1000, y: 270, label: 'Sync Gateway', sub: 'websockets', cloud: true },
  { id: 'srv', x: 1400, y: 60, label: 'External server', sub: 'processes commands', cloud: true },
  { id: 'rr', x: 1400, y: 480, label: 'Remote reads', sub: 'Couchbase Server', cloud: true },
]

const EDGES: Edge[] = [
  { id: 'a1', from: [330, 130], to: [140, 268], step: 1, label: 'temp doc · PENDING', lx: 30, ly: 190 },
  { id: 'a2', from: [400, 130], to: [590, 268], step: 1, label: 'command', lx: 520, ly: 190 },
  { id: 'b1', from: [730, 315], to: [998, 315], step: 2, label: 'push command', lx: 760, ly: 292 },
  { id: 'b2', from: [1180, 275], to: [1400, 140], step: 2, label: 'process', lx: 1200, ly: 190 },
  { id: 'c1', from: [1450, 150], to: [1260, 285], step: 3, label: 'ack', lx: 1390, ly: 250, color: 'var(--processing)' },
  { id: 'c2', from: [998, 345], to: [730, 345], step: 3, label: 'ack replicated → PROCESSING', lx: 690, ly: 392, color: 'var(--processing)' },
  { id: 'd1', from: [1520, 480], to: [1520, 150], step: 4, label: 'writes real doc', lx: 1535, ly: 320, color: 'var(--active)' },
  { id: 'd2', from: [1400, 520], to: [1200, 360], step: 4, label: 'pull', lx: 1260, ly: 470, color: 'var(--active)' },
  { id: 'd3', from: [998, 330], to: [260, 340], step: 4, label: 'real doc replicated (never the temp doc)', lx: 520, ly: 625, color: 'var(--active)' },
  { id: 'e1', from: [140, 360], to: [300, 498], step: 5, label: 'lastCommandId matches', lx: 20, ly: 470, color: 'var(--primary)' },
]

const CAPTIONS = [
  'Two databases on device, two on the server side, one gateway between them.',
  '1 · The user saves. A temp doc (PENDING) goes to reads, a command goes to writes. Offline-safe.',
  '2 · The command replicates to the server through Sync Gateway.',
  '3 · The server acknowledges. The ack replicates back, the UI shows PROCESSING.',
  '4 · The server stores the real doc in the remote reads DB. It replicates to the device.',
  '5 · The reconciler finds the real doc, deletes the temp doc, and the UI shows ACTIVE.',
]

export function ArchitectureDiagram({ step }: { step: number }) {
  return (
    <div>
      <svg viewBox="0 0 1700 660" width="1680" height="600" style={{ overflow: 'visible' }}>
        <rect x="0" y="0" width="840" height="640" rx="36" fill="none" stroke="var(--primary)" strokeDasharray="10 10" opacity=".5" />
        <text x="24" y="-12" fill="var(--muted)" fontSize="26">ON DEVICE · works offline</text>
        <rect x="960" y="0" width="720" height="640" rx="36" fill="none" stroke="var(--secondary)" strokeDasharray="10 10" opacity=".5" />
        <text x="984" y="-12" fill="var(--muted)" fontSize="26">REMOTE</text>
        <defs>
          <marker id="arr" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
            <path d="M0 0L10 5L0 10z" fill="context-stroke" />
          </marker>
        </defs>
        {EDGES.map((e) => {
          const on = step >= e.step
          const hot = step === e.step
          const color = e.color ?? 'var(--tertiary)'
          const path = `M${e.from[0]},${e.from[1]} L${e.to[0]},${e.to[1]}`
          return (
            <g key={e.id} opacity={on ? 1 : 0} style={{ transition: 'opacity .4s' }}>
              <path d={path} stroke={color} strokeWidth={hot ? 5 : 3} fill="none" markerEnd="url(#arr)" opacity={hot ? 1 : 0.55} />
              {hot && (
                <circle r="9" fill={color}>
                  <animateMotion dur="1.6s" repeatCount="indefinite" path={path} />
                </circle>
              )}
              <text x={e.lx} y={e.ly} fill={color} fontSize="24" fontWeight="700">{e.label}</text>
            </g>
          )
        })}
        {NODES.map((n) => (
          <g key={n.id}>
            <rect x={n.x} y={n.y} width={n.w ?? 240} height="90" rx="22" fill="var(--bg-2)" stroke={n.cloud ? 'var(--secondary)' : 'var(--primary)'} strokeWidth="3" />
            <text x={n.x + 120} y={n.y + 40} textAnchor="middle" fill="var(--fg)" fontSize="30" fontWeight="700">{n.label}</text>
            <text x={n.x + 120} y={n.y + 72} textAnchor="middle" fill="var(--muted)" fontSize="22">{n.sub}</text>
          </g>
        ))}
      </svg>
      <p style={{ margin: '28px 0 0', fontSize: 38, color: 'var(--fg)' }}>{CAPTIONS[Math.min(step, CAPTIONS.length - 1)]}</p>
    </div>
  )
}
export const ARCH_STEPS = CAPTIONS.length
