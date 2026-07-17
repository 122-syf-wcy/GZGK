/**
 * 省控线（本科线 / 特控线）官方公布值。
 *
 * 用途：后端「省控线结构化表」补齐并部署前，作为带「官方来源」标注的兜底，
 * 让分数线分布图能标出本科线/特控线。后端 control-lines 返回 AVAILABLE 后，
 * App 会自动改用后端数据（见 scoreline.vue 的优先级）。
 *
 * 合规：这里只收录各省招生考试院 / 教育主管部门「官方公告原文」的控制线，标注来源与发布日期，
 * 绝不臆造。新增省份/年份时，请附官方链接核验后再填。
 */
export interface OfficialControlLine {
  source: string
  sourceUrl: string
  publishDate: string
  lines: { label: '本科线' | '特控线'; score: number }[]
}

/** key 格式：`${省份代码}-${年份}-${科类}`，科类需与该省 capability 的 subjectOptions 完全一致 */
export const OFFICIAL_CONTROL_LINES: Record<string, OfficialControlLine> = {
  // 贵州 2025 普通类（来源：贵州省教育高质量发展委员会 2025-06-25 划定公告）
  'GZ-2025-物理类': {
    source: '贵州省招生考试院',
    sourceUrl: 'https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html',
    publishDate: '2025-06-25',
    lines: [
      { label: '本科线', score: 387 },
      { label: '特控线', score: 483 },
    ],
  },
  'GZ-2025-历史类': {
    source: '贵州省招生考试院',
    sourceUrl: 'https://mzt.guizhou.gov.cn/xwzx/mzyw/202506/t20250625_88186360.html',
    publishDate: '2025-06-25',
    lines: [
      { label: '本科线', score: 458 },
      { label: '特控线', score: 517 },
    ],
  },
}

export function lookupControlLines(provinceCode: string, year?: number, subject?: string): OfficialControlLine | null {
  if (!year || !subject) return null
  return OFFICIAL_CONTROL_LINES[`${provinceCode}-${year}-${subject}`] || null
}
