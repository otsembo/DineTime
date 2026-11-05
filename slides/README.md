# DineTime talk deck

Interactive slides for the droidcon KE 2026 session "Offline-first, on purpose" (45 minutes). React + TypeScript + Vite, Framer Motion for animation, Shiki for code highlighting. Works fully offline once built (fonts are bundled, no CDN).

## Run

Needs Node 20+.

```bash
cd slides
npm install
npm run dev        # http://localhost:5173
npm run build      # static site in dist/ (relative paths, host anywhere)
npm run preview    # serve dist/
```

## Presenting

| Key | Action |
|---|---|
| → / Space / PageDown | next step or slide |
| ← / PageUp | previous |
| Home / End | first / last slide |
| O | slide overview, click to jump |
| S | presenter window (notes, timer vs 45:00, next slide), synced over BroadcastChannel |
| F | fullscreen |
| L | light / dark stage |
| N | (simulator slide) advance one hop in Step mode |

The URL hash is `#/<slide>/<step>`, so you can bookmark any point. `?print` renders every slide at its final step, one per page.

## Live demo (slide 20)

An in-browser simulator of the DineTime round trip: save a meal and watch the temp doc go PENDING → PROCESSING → ACTIVE. Controls: offline mode, `400 next`, `500 ×3`, `Always 500`, `Fix & resubmit`, Auto/Step mode. The state machine lives in `src/sim/engine.ts` and mirrors the app's `MealItems.statusFor`, reconciler and fake server rules.

## Authoring

- Slides are `SlideDef` objects in `src/slides/*.tsx`, registered in `src/slides/index.ts`. Each has `seconds` (for the presenter timer), `notes`, `steps` (number of reveal steps; the slide renders with `step` from `0` to `steps - 1`).
- Code slides use `codeSlide()` in `core.tsx` with line ranges to focus on each step.
- Code in `src/snippets/*.kt` is copied from the Android app. `npm run check:snippets` fails if a snippet line no longer exists in the app source.

## Checks

```bash
npm test                 # simulator unit tests (Vitest)
npm run lint
npm run check:snippets
npm run build            # type checks and bundles
npx playwright install chromium   # once
npm run e2e              # walks every slide/step, fails on console errors
node e2e/shots.mjs       # screenshots of key slides into e2e/out/
npm run export:pdf       # slides.pdf backup (final step of each slide)
```
