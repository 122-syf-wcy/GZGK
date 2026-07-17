/**
 * uni-app 无 DOM，AI 返回的 Markdown 这里粗略转成「按行纯文本」，
 * 供页面用 <text> 逐行渲染（跨端稳定，避免引入 html 解析依赖）。
 */
export function mdToLines(md?: string): string[] {
  if (!md) return []
  return md
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) =>
      line
        .replace(/^>\s?/, '')
        .replace(/^#{1,6}\s*/, '')
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/`([^`]+)`/g, '$1')
        .replace(/^\s*[-*]\s+/, '· ')
        .trimEnd(),
    )
}

export interface MdBlock {
  type: 'h' | 'li' | 'p'
  text: string
}

/** 把 markdown 解析成「标题 / 要点 / 段落」分块，供页面结构化渲染 */
export function mdToBlocks(md?: string): MdBlock[] {
  if (!md) return []
  const out: MdBlock[] = []
  md.replace(/\r\n/g, '\n')
    .split('\n')
    .forEach((raw) => {
      let line = raw.trim()
      if (!line) return
      line = line.replace(/^>\s?/, '').replace(/`([^`]+)`/g, '$1')
      const h = line.match(/^#{1,6}\s*(.+)$/)
      if (h) {
        out.push({ type: 'h', text: h[1].replace(/\*\*/g, '').trim() })
        return
      }
      const b = line.match(/^\*\*(.+?)\*\*[:：]?$/)
      if (b) {
        out.push({ type: 'h', text: b[1].trim() })
        return
      }
      const li = line.match(/^([-*·]|\d+[.、])\s*(.+)$/)
      if (li) {
        out.push({ type: 'li', text: li[2].replace(/\*\*(.+?)\*\*/g, '$1').trim() })
        return
      }
      out.push({ type: 'p', text: line.replace(/\*\*(.+?)\*\*/g, '$1').trim() })
    })
  return out
}
