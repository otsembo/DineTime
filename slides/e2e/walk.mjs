import { chromium } from 'playwright'
import { spawn } from 'node:child_process'
import { mkdirSync } from 'node:fs'

// Walks every slide and step in a real browser, fails on console errors, and saves a few screenshots.
const server = spawn('npx', ['vite', 'preview', '--port', '4173', '--strictPort'], { stdio: 'ignore' })
await new Promise((r) => setTimeout(r, 2500))
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
const errors = []
page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
page.on('pageerror', (e) => errors.push(String(e)))
mkdirSync('e2e/out', { recursive: true })

let failed = false
try {
  await page.goto('http://localhost:4173/#/1/0')
  const total = await page.evaluate(() => 0)
  void total
  let seen = 0
  for (let i = 0; i < 200; i++) {
    await page.waitForTimeout(120)
    const hash = await page.evaluate(() => location.hash)
    if (seen % 6 === 0) await page.screenshot({ path: `e2e/out/step-${String(seen).padStart(3, '0')}.png` })
    seen++
    await page.keyboard.press('ArrowRight')
    await page.waitForTimeout(60)
    if ((await page.evaluate(() => location.hash)) === hash && i > 5) break
  }
  console.log(`walked ${seen} steps`)
  if (errors.length) throw new Error('console errors:\n' + errors.join('\n'))
} catch (e) {
  failed = true
  console.error(e)
} finally {
  await browser.close()
  server.kill()
}
process.exit(failed ? 1 : 0)
