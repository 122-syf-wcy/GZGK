/**
 * 极简 Markdown 解析。
 *
 * 后端 AI 解读只用到标题、段落和无序列表三种结构，为此引入 marked
 * （约 40 KB）不划算，而且 uni-app App 端没有 rich-text 的完整 HTML 支持，
 * 解析成结构化块再用原生组件渲染更可控。
 */

export type MdBlock =
  | { type: 'h2'; text: string }
  | { type: 'h3'; text: string }
  | { type: 'p'; text: string }
  | { type: 'li'; text: string }

export function parseMarkdown(src: string): MdBlock[] {
  if (!src) return []
  const blocks: MdBlock[] = []

  src.split(/\r?\n/).forEach((raw) => {
    const line = raw.trim()
    if (!line) return

    if (line.startsWith('### ')) {
      blocks.push({ type: 'h3', text: stripInline(line.slice(4)) })
      return
    }
    if (line.startsWith('## ')) {
      blocks.push({ type: 'h2', text: stripInline(line.slice(3)) })
      return
    }
    if (line.startsWith('# ')) {
      blocks.push({ type: 'h2', text: stripInline(line.slice(2)) })
      return
    }
    if (/^[-*·]\s+/.test(line)) {
      blocks.push({ type: 'li', text: stripInline(line.replace(/^[-*·]\s+/, '')) })
      return
    }
    if (/^\d+[.、]\s*/.test(line)) {
      blocks.push({ type: 'li', text: stripInline(line.replace(/^\d+[.、]\s*/, '')) })
      return
    }
    blocks.push({ type: 'p', text: stripInline(line) })
  })

  return blocks
}

/** 去掉行内标记符号。不做加粗渲染——正文里混排粗体在小屏上更乱。 */
function stripInline(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/`(.+?)`/g, '$1')
    .replace(/\[(.+?)\]\((.+?)\)/g, '$1')
}
