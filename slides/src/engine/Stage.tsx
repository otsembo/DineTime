import { useEffect, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useDeck } from './deck'
import type { SlideDef } from './types'

export const W = 1920
export const H = 1080

export function useScale(boxW = W, boxH = H) {
  const [scale, setScale] = useState(1)
  useEffect(() => {
    const f = () => setScale(Math.min(window.innerWidth / boxW, window.innerHeight / boxH))
    f()
    window.addEventListener('resize', f)
    return () => window.removeEventListener('resize', f)
  }, [boxW, boxH])
  return scale
}

export function ScaledStage({ children, scale }: { children: ReactNode; scale: number }) {
  return (
    <div className="stage" style={{ transform: `translate(-50%, -50%) scale(${scale})` }}>
      {children}
    </div>
  )
}

export function SlideView({ slide, step }: { slide: SlideDef; step: number }) {
  return <>{slide.render({ step })}</>
}

export function Stage() {
  const { slides, index, step } = useDeck()
  const scale = useScale()
  const slide = slides[index]
  return (
    <>
      <ScaledStage scale={scale}>
        <AnimatePresence mode="wait">
          <motion.div
            key={slide.id}
            style={{ position: 'absolute', inset: 0 }}
            initial={{ opacity: 0, x: 40 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: -40 }}
            transition={{ duration: 0.25 }}
          >
            <SlideView slide={slide} step={step} />
          </motion.div>
        </AnimatePresence>
      </ScaledStage>
      <div className="progress" style={{ width: `${((index + 1) / slides.length) * 100}%` }} />
      <div className="hud">{index + 1}/{slides.length} · O overview · S presenter · F fullscreen · L theme</div>
    </>
  )
}
