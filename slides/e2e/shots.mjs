import { chromium } from 'playwright'
import { spawn } from 'node:child_process'
import { mkdirSync } from 'node:fs'

// Screenshots key slides (architecture, code focus, simulator with a 500 in flight) for visual review.
const server = spawn('npx', ['vite', 'preview', '--port', '4175', '--strictPort'], { stdio: 'ignore' })
await new Promise((r) => setTimeout(r, 2500))
const b = await chromium.launch()
const p = await b.newPage({ viewport: { width: 1920, height: 1080 } })
mkdirSync('e2e/out', { recursive: true })
for (const [n, h] of [['cqrs', '#/6/2'], ['field', '#/3/4'], ['contact', '#/31/0'], ['states', '#/11/4']]) {
  await p.goto('http://localhost:4175/' + h)
  await p.reload()
  await p.waitForTimeout(1200)
  if (n === 'sim') {
    await p.getByText('500 ×3').click()
    await p.getByText('＋ Save meal').click()
    await p.waitForTimeout(3000)
  }
  await p.screenshot({ path: `e2e/out/${n}.png` })
}
await b.close()
server.kill()
