import { useEffect, useState } from 'react'
import { createHighlighterCore, type HighlighterCore, type ThemedToken } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'

let hl: Promise<HighlighterCore> | undefined
const getHighlighter = () =>
  (hl ??= createHighlighterCore({
    themes: [import('shiki/themes/vitesse-dark.mjs'), import('shiki/themes/vitesse-light.mjs')],
    langs: [import('shiki/langs/kotlin.mjs'), import('shiki/langs/json.mjs'), import('shiki/langs/typescript.mjs'), import('shiki/langs/bash.mjs')],
    engine: createJavaScriptRegexEngine(),
  }))

export type Lang = 'kotlin' | 'json' | 'typescript' | 'bash'

/**
 * Syntax highlighted code. `focus` is a list of 1-based inclusive [from, to] ranges;
 * when set, every other line is dimmed (use with slide steps to walk through code).
 */
export function CodeBlock({ code, lang = 'kotlin', title, focus, fontSize }: {
  code: string
  lang?: Lang
  title?: string
  focus?: Array<[number, number]>
  fontSize?: number
}) {
  const [lines, setLines] = useState<ThemedToken[][] | null>(null)
  useEffect(() => {
    let live = true
    void getHighlighter().then((h) => {
      if (live) setLines(h.codeToTokens(code.replace(/\n$/, ''), { lang, theme: 'vitesse-dark' }).tokens)
    })
    return () => { live = false }
  }, [code, lang])

  const fallback = code.replace(/\n$/, '').split('\n')
  const total = lines?.length ?? fallback.length
  return (
    <div className="code">
      {title && <div className="code-title">{title}</div>}
      <pre style={fontSize ? { fontSize } : undefined}>
        {Array.from({ length: total }, (_, i) => {
          const n = i + 1
          const hit = focus?.some(([a, b]) => n >= a && n <= b)
          const cls = focus ? (hit ? 'line hl' : 'line dim') : 'line'
          return (
            <span key={i} className={cls}>
              {lines ? lines[i].map((t, j) => <span key={j} style={{ color: t.color }}>{t.content}</span>) : fallback[i]}
              {'\n'}
            </span>
          )
        })}
      </pre>
    </div>
  )
}
