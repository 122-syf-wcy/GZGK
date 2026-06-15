// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { renderMarkdown, sanitizeHttpUrl } from '@/utils/markdown'

/**
 * 在真实 DOM（jsdom）下验证 DOMPurify 二次防线：
 * 1) AI 问答 / 结果页常见 markdown（表格/列表/链接/加粗/代码/引用/标题）渲染不被破坏；
 * 2) 危险协议链接与脚本被剥离。
 */
describe('renderMarkdown + DOMPurify (browser DOM)', () => {
  it('preserves common AI markdown structures', () => {
    const html = renderMarkdown([
      '# 方向参考',
      '',
      '> 本内容由 AI 生成，仅供参考。',
      '',
      '- **重点**：先看官方招生章程',
      '1. 核对位次',
      '2. 人工复核',
      '',
      '| 院校 | 批次 | 备注 |',
      '|---|:---:|---:|',
      '| 示例大学 | 本科批 | 需复核 |',
      '',
      '参考 [官方来源](https://www.gov.cn/abc) 与 `选科要求`。',
    ].join('\n'))

    expect(html).toContain('<h2>方向参考</h2>')
    expect(html).toContain('<blockquote>')
    expect(html).toContain('<strong>重点</strong>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<ol>')
    expect(html).toContain('<table>')
    expect(html).toContain('示例大学')
    expect(html).toContain('text-align:center')
    expect(html).toContain('<code>选科要求</code>')
    // 合法 http 链接保留，且带 target=_blank
    expect(html).toContain('href="https://www.gov.cn/abc"')
    expect(html).toContain('target="_blank"')
  })

  it('strips dangerous protocols/scripts via DOMPurify', () => {
    // 危险协议不会被渲染成可点链接（renderInline 只识别 http(s) 链接），DOMPurify 再兜底
    const malicious = renderMarkdown('[点我](javascript:alert(1)) 普通文本')
    expect(malicious).not.toContain('<a ')
    expect(malicious).not.toContain('href="javascript')
    expect(malicious).not.toContain('<script')

    // 来源链接消毒：危险协议返回空
    expect(sanitizeHttpUrl('javascript:alert(1)')).toBe('')
    expect(sanitizeHttpUrl('data:text/html,<script>x</script>')).toBe('')
    expect(sanitizeHttpUrl('https://www.gov.cn/x')).toBe('https://www.gov.cn/x')
  })
})
