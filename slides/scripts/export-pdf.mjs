import { chromium } from 'playwright'
import { spawn } from 'node:child_process'

// Renders ?print (every slide at its final step, one per page) to slides.pdf as a backup for the talk.
const server = spawn('npx', ['vite', 'preview', '--port', '4174', '--strictPort'], { stdio: 'ignore' })
await new Promise((r) => setTimeout(r, 2500))
const browser = await chromium.launch()
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } })
await page.goto('http://localhost:4174/?print')
await page.waitForTimeout(1500)
await page.pdf({ path: 'slides.pdf', width: '1920px', height: '1080px', printBackground: true })
await browser.close()
server.kill()
console.log('wrote slides.pdf')
