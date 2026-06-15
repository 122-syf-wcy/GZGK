import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('renderMarkdown', () => {
  it('renders markdown tables instead of exposing raw separators', () => {
    const html = renderMarkdown([
      '| 项目 | 当前数据 | 判断 |',
      '|---|:---:|---:|',
      '| 总志愿 | 96个 | 数量完整 |',
      '| 稳 | 43个 | 主体够厚 |',
    ].join('\n'))

    expect(html).toContain('<table>')
    expect(html).toContain('<th>项目</th>')
    expect(html).toContain('<th style="text-align:center">当前数据</th>')
    expect(html).toContain('<th style="text-align:right">判断</th>')
    expect(html).toContain('<td style="text-align:center">96个</td>')
    expect(html).not.toContain('|---|:---:|---:|')
  })

  it('keeps auto section headings opt-in for AI chat replies', () => {
    const plain = renderMarkdown('一句话总判断')
    const enhanced = renderMarkdown('一句话总判断', { autoSectionHeadings: true })

    expect(plain).toBe('<p>一句话总判断</p>')
    expect(enhanced).toContain('markdown-section-title')
  })

  it('renders common AI markdown blocks safely', () => {
    const html = renderMarkdown([
      '# 总结',
      '',
      '> 本内容由 AI 生成。',
      '',
      '- **重点**：优先看稳保',
      '* 风险：`低置信` 项需复核',
      '1. 导出表格',
      '2. 人工核验',
      '',
      '---',
      '',
      '```text',
      '<script>alert(1)</script>',
      '```',
    ].join('\n'))

    expect(html).toContain('<h2>总结</h2>')
    expect(html).toContain('<blockquote>本内容由 AI 生成。</blockquote>')
    expect(html).toContain('<strong>重点</strong>')
    expect(html).toContain('<code>低置信</code>')
    expect(html).toContain('<ul>')
    expect(html).toContain('<ol>')
    expect(html).toContain('<hr>')
    expect(html).toContain('<pre><code class="language-text">')
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;')
    expect(html).not.toContain('<script>')
  })

  it('keeps fenced code content out of markdown normalization', () => {
    const html = renderMarkdown([
      '```md',
      '**不是标题**',
      '|---|',
      '```',
    ].join('\n'))

    expect(html).toContain('<pre><code class="language-md">')
    expect(html).toContain('**不是标题**')
    expect(html).toContain('|---|')
    expect(html).not.toContain('<h3>不是标题</h3>')
  })

  it('renders loose AI report tables and colon headings', () => {
    const html = renderMarkdown([
      '> 本内容由 AI 生成，仅供参考。',
      '',
      '一句话总判断',
      '这份方案结构不激进，但数据上要谨慎。',
      '',
      '梯度结构诊断：',
      '| 项目 | 当前数据 | 判断 |',
      '|---|---|---|',
      '| 总志愿 | 96个 | 数量完整 |',
      '| 冲 | 14个 | 可控 |',
      '',
      '最值得保留的志愿：',
      '| 志愿 | 理由 | 注意点 |',
      '|———|———|———|',
      '| 重庆大学 | 方向匹配 | 需复核限制 |',
    ].join('\n'), { autoSectionHeadings: true })

    expect(html).toContain('<h4 class="markdown-section-title">一句话总判断</h4>')
    expect(html).toContain('<h4 class="markdown-section-title">梯度结构诊断：</h4>')
    expect(html).toContain('<h4 class="markdown-section-title">最值得保留的志愿：</h4>')
    expect((html.match(/<table>/g) || []).length).toBe(2)
    expect(html).toContain('<td>重庆大学</td>')
    expect(html).not.toContain('|---|---|---|')
    expect(html).not.toContain('|———|———|———|')
  })

  it('does not turn ordered list items into auto section headings', () => {
    const html = renderMarkdown([
      '下一步',
      '1. 导出表格',
      '2. 人工复核',
      '3. 等官方数据发布后再校准',
    ].join('\n'), { autoSectionHeadings: true })

    expect(html).toContain('<h4 class="markdown-section-title">下一步</h4>')
    expect(html).toContain('<ol>')
    expect(html).toContain('<li>导出表格</li>')
    expect(html).not.toContain('<h4 class="markdown-section-title">1. 导出表格</h4>')
  })
})
