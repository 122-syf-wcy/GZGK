import DOMPurify from 'dompurify'

let dompurifyHookInstalled = false
function ensureDompurifyLinkHook() {
  if (dompurifyHookInstalled) return
  dompurifyHookInstalled = true
  // DOMPurify 默认会剥离 a 标签的 target/rel；用官方推荐 hook 补回，
  // 保持来源链接「新标签打开」且带 rel="noopener noreferrer" 防 tabnabbing。
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    if (node instanceof Element && node.tagName === 'A' && node.getAttribute('href')) {
      node.setAttribute('target', '_blank')
      node.setAttribute('rel', 'noopener noreferrer')
    }
  })
}

function escapeHtml(str: string) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

export function sanitizeHttpUrl(url?: string | null): string {
  const value = String(url || '').trim()
  if (!value || /[\u0000-\u001F\u007F]/.test(value)) return ''
  try {
    const parsed = new URL(value)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return ''
    return parsed.href
  } catch {
    return ''
  }
}

function renderInline(text: string) {
  const codeSpans: string[] = []
  const withCodeTokens = text.replace(/`([^`]+)`/g, (_match, code: string) => {
    const index = codeSpans.push(code) - 1
    return `\u0000CODE${index}\u0000`
  })

  return escapeHtml(withCodeTokens)
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, (_match, label: string, rawUrl: string) => {
      const url = sanitizeHttpUrl(rawUrl)
      return url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${label}</a>` : label
    })
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+?)\*/g, '$1<em>$2</em>')
    .replace(/\*/g, '')
    .replace(/\u0000CODE(\d+)\u0000/g, (_match, index: string) => {
      const code = codeSpans[Number(index)] || ''
      return `<code>${escapeHtml(code)}</code>`
    })
}

interface RenderMarkdownOptions {
  autoSectionHeadings?: boolean
}

function renderList(lines: string[], ordered: boolean) {
  const tag = ordered ? 'ol' : 'ul'
  const items = lines
    .map((line) => line.replace(ordered ? /^\d+[.)]\s*/ : /^[-*]\s*/, '').trim())
    .filter(Boolean)
    .map((line) => `<li>${renderInline(line)}</li>`)
    .join('')
  return `<${tag}>${items}</${tag}>`
}

function isTableRow(line: string) {
  if (!line.includes('|')) return false
  if (/^https?:\/\//i.test(line)) return false
  const cells = splitTableRow(line)
  return cells.length >= 2
}

function splitTableRow(line: string) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map(cell => cell.trim())
}

function isTableDelimiter(line: string) {
  if (!isTableRow(line)) return false
  const cells = splitTableRow(line)
  return cells.length >= 2 && cells.every(cell => /^:?-{3,}:?$/.test(normalizeTableDelimiterCell(cell)))
}

function tableAlignments(delimiterLine: string) {
  return splitTableRow(delimiterLine).map((cell) => {
    const normalized = normalizeTableDelimiterCell(cell)
    if (/^:-{3,}:$/.test(normalized)) return 'center'
    if (/^-{3,}:$/.test(normalized)) return 'right'
    return 'left'
  })
}

function normalizeTableDelimiterCell(cell: string) {
  return cell.replace(/[—–－─]/g, '-')
}

function renderAlignAttr(align?: string) {
  if (!align || align === 'left') return ''
  return ` style="text-align:${align}"`
}

function renderTable(headerLine: string, delimiterLine: string, bodyLines: string[]) {
  const headers = splitTableRow(headerLine)
  const alignments = tableAlignments(delimiterLine)
  const width = headers.length
  const normalizeCells = (line: string) => {
    const cells = splitTableRow(line)
    if (cells.length >= width) return cells.slice(0, width)
    return [...cells, ...Array.from({ length: width - cells.length }, () => '')]
  }
  const head = headers
    .map((cell, index) => `<th${renderAlignAttr(alignments[index])}>${renderInline(cell)}</th>`)
    .join('')
  const body = bodyLines
    .map((line) => {
      const cells = normalizeCells(line)
      return `<tr>${cells.map((cell, index) => `<td${renderAlignAttr(alignments[index])}>${renderInline(cell || '--')}</td>`).join('')}</tr>`
    })
    .join('')
  return `<div class="markdown-table-wrap"><table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`
}

function isAutoSectionHeading(line: string) {
  const raw = line.trim()
  if (/^([#>*-]|\d+[.)、.．]\s*)/.test(raw)) return false
  const value = normalizeSectionHeadingText(line)
  if (!value || value.length > 18) return false
  if (/^([#>*-]|\d+[.)])/.test(value)) return false
  if (/[|`]/.test(value)) return false
  if (/[。！？!?；;，,、]$/.test(value)) return false
  if (!/[\u4e00-\u9fa5]/.test(value)) return false
  if (/^(例如|比如|注意|结论是|建议|当前|这份|如果|但是|另外)/.test(value)) return false
  return true
}

function normalizeSectionHeadingText(line: string) {
  return line
    .trim()
    .replace(/^[一二三四五六七八九十]+[、.．]\s*/, '')
    .replace(/^\d+[、.．]\s*/, '')
    .replace(/[：:]\s*$/, '')
    .trim()
}

function flushParagraph(buffer: string[], html: string[]) {
  if (!buffer.length) return
  html.push(`<p>${buffer.map(line => renderInline(line)).join('<br>')}</p>`)
  buffer.length = 0
}

function renderCodeBlock(language: string, lines: string[]) {
  const safeLanguage = language.trim().replace(/[^\w+-]/g, '').slice(0, 32)
  const className = safeLanguage ? ` class="language-${escapeHtml(safeLanguage)}"` : ''
  return `<pre><code${className}>${escapeHtml(lines.join('\n'))}</code></pre>`
}

function isFenceLine(line: string) {
  return /^```/.test(line.trim())
}

function fenceLanguage(line: string) {
  return line.trim().replace(/^```/, '').trim().split(/\s+/)[0] || ''
}

function normalizeMarkdownText(text: string) {
  let inFence = false
  return text
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((raw) => {
      if (isFenceLine(raw)) {
        inFence = !inFence
        return raw
      }
      if (inFence) return raw

      let normalized = raw.replace(/｜/g, '|')
      if (/^\|?\s*:?[—–－─-]{3,}:?\s*(\|\s*:?[—–－─-]{3,}:?\s*)+\|?$/.test(normalized.trim())) {
        normalized = normalized.replace(/[—–－─]/g, '-')
      }
      normalized = normalized
        .replace(/^(\s*)[-*](?=\*\*)/, '$1- ')
        .replace(/^(\s*)[-*]\*(?!\*)\s*/, '$1- ')
      const line = normalized.trim()
      if (/^\*\*[^*]+\*\*$/.test(line)) {
        return `## ${line.replace(/^\*\*|\*\*$/g, '').trim()}`
      }
      if (/^【[^】]+】$/.test(line)) {
        return `## ${line.replace(/^【|】$/g, '').trim()}`
      }
      return normalized
    })
    .join('\n')
}

export function renderMarkdown(text: string, options: RenderMarkdownOptions = {}) {
  const normalizedText = normalizeMarkdownText(text)

  const lines = normalizedText.split('\n')
  const html: string[] = []
  const paragraphBuffer: string[] = []
  let listBuffer: string[] = []
  let listOrdered = false
  let quoteBuffer: string[] = []
  let codeFence: { language: string; lines: string[] } | null = null

  const flushList = () => {
    if (!listBuffer.length) return
    html.push(renderList(listBuffer, listOrdered))
    listBuffer = []
  }

  const flushQuote = () => {
    if (!quoteBuffer.length) return
    html.push(`<blockquote>${quoteBuffer.map(line => renderInline(line)).join('<br>')}</blockquote>`)
    quoteBuffer = []
  }

  const flushAll = () => {
    flushParagraph(paragraphBuffer, html)
    flushList()
    flushQuote()
  }

  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index]
    const line = rawLine.trim()

    if (codeFence) {
      if (isFenceLine(rawLine)) {
        html.push(renderCodeBlock(codeFence.language, codeFence.lines))
        codeFence = null
      } else {
        codeFence.lines.push(rawLine)
      }
      continue
    }

    if (isFenceLine(rawLine)) {
      flushAll()
      codeFence = { language: fenceLanguage(rawLine), lines: [] }
      continue
    }

    if (!line) {
      flushAll()
      continue
    }

    if (
      isTableRow(line) &&
      index + 1 < lines.length &&
      isTableDelimiter(lines[index + 1].trim())
    ) {
      flushAll()
      const delimiterLine = lines[index + 1].trim()
      const bodyLines: string[] = []
      index += 2
      while (index < lines.length) {
        const bodyLine = lines[index].trim()
        if (!isTableRow(bodyLine) || isTableDelimiter(bodyLine)) {
          index -= 1
          break
        }
        bodyLines.push(bodyLine)
        index += 1
      }
      html.push(renderTable(line, delimiterLine, bodyLines))
      continue
    }

    if (/^###\s*/.test(line)) {
      flushAll()
      html.push(`<h4>${renderInline(line.replace(/^###\s*/, ''))}</h4>`)
      continue
    }
    if (/^##\s*/.test(line)) {
      flushAll()
      html.push(`<h3>${renderInline(line.replace(/^##\s*/, ''))}</h3>`)
      continue
    }
    if (/^#\s*/.test(line)) {
      flushAll()
      html.push(`<h2>${renderInline(line.replace(/^#\s*/, ''))}</h2>`)
      continue
    }

    if (options.autoSectionHeadings && isAutoSectionHeading(line)) {
      flushAll()
      html.push(`<h4 class="markdown-section-title">${renderInline(line)}</h4>`)
      continue
    }

    if (/^([-*_])(?:\s*\1){2,}\s*$/.test(line)) {
      flushAll()
      html.push('<hr>')
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      flushParagraph(paragraphBuffer, html)
      flushQuote()
      const ordered = false
      if (listBuffer.length && listOrdered !== ordered) {
        flushList()
      }
      listOrdered = ordered
      listBuffer.push(line)
      continue
    }

    if (/^\d+[.)]\s+/.test(line)) {
      flushParagraph(paragraphBuffer, html)
      flushQuote()
      const ordered = true
      if (listBuffer.length && listOrdered !== ordered) {
        flushList()
      }
      listOrdered = ordered
      listBuffer.push(line)
      continue
    }

    if (line.startsWith('>')) {
      flushParagraph(paragraphBuffer, html)
      flushList()
      quoteBuffer.push(line.replace(/^>\s?/, ''))
      continue
    }

    flushList()
    flushQuote()
    paragraphBuffer.push(line)
  }

  flushAll()
  if (codeFence) {
    html.push(renderCodeBlock(codeFence.language, codeFence.lines))
  }
  const rendered = html.join('')
  // 二次防线：浏览器端用 DOMPurify 兜底清除危险标签/属性。
  // 无 DOM 环境（SSR/单测 node）下 renderMarkdown 的输出本身已对全部文本做过 escape，
  // 直接返回即可，避免依赖 window。
  if (typeof window === 'undefined') {
    return rendered
  }
  ensureDompurifyLinkHook()
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'rel'],
    ALLOWED_URI_REGEXP: /^(?:https?|mailto|tel):/i,
  })
}
