const fs = require('node:fs')
const path = require('node:path')

const root = path.resolve(__dirname, '../../app/src/main/java/com/dinetime')
const map = {
  'commandDoc.kt': 'domain/Models.kt',
  'createMeal.kt': 'data/reads/MealRepository.kt',
  'replicators.kt': 'data/couchbase/ReplicatorManager.kt',
  'statusFor.kt': 'domain/MealItems.kt',
  'reconcile.kt': 'sync/Reconciler.kt',
  'navKeys.kt': 'ui/Navigation.kt',
}
const norm = (s) => s.trim().replace(/\s+/g, ' ')
let bad = 0
for (const [snip, src] of Object.entries(map)) {
  const source = new Set(fs.readFileSync(path.join(root, src), 'utf8').split('\n').map(norm))
  const lines = fs.readFileSync(path.join(__dirname, '../src/snippets', snip), 'utf8').split('\n').map(norm).filter(Boolean)
  for (const l of lines) {
    if (!source.has(l)) {
      console.error(`DRIFT ${snip}: "${l}" not found in ${src}`)
      bad++
    }
  }
}
if (bad) process.exit(1)
console.log('All snippets match the app source.')
