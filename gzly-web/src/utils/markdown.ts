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
  return escapeHtml(text)
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, (_match, label: string, rawUrl: string) => {
      const url = sanitizeHttpUrl(rawUrl)
      return url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer">${label}</a>` : label
    })
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/\*/g, '')
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

function flushParagraph(buffer: string[], html: string[]) {
  if (!buffer.length) return
  html.push(`<p>${buffer.map(line => renderInline(line)).join('<br>')}</p>`)
  buffer.length = 0
}

export function renderMarkdown(text: string) {
  const normalizedText = text
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((raw) => {
      const normalized = raw
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

  const lines = normalizedText.split('\n')
  const html: string[] = []
  const paragraphBuffer: string[] = []
  let listBuffer: string[] = []
  let listOrdered = false
  let quoteBuffer: string[] = []

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

  for (const rawLine of lines) {
    const line = rawLine.trim()

    if (!line) {
      flushAll()
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
  return html.join('')
}
