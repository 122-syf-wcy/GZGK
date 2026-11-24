export const STANDARD_DISCLAIMER =
  '数据源自省教育考试院及高校公开招生章程，辅助分析仅供填报参考，不构成任何录取承诺。';

// Regulatory hard terms synchronized with contracts/compliance-terms.json
export const HARD_COMPLIANCE_TERMS = [
  '内部信息', '内部数据', '保录取', '不滑档', '不脱档', '100%录取', '成功率',
  '录取率', '上榜率', '录取概率', '命中率', '上岸概率', '稳上', '必上',
  '保送', '包过', '稳进', '保证录取', '包录取', '百分百录取', '一定能上',
  '保证不滑档', '稳过', '保录', '确定能上', '百分百', '肯定能上', '一定录取',
  '绝对录取', '保上', '必录', '确保录取', '铁定录取', '包上', '绝对安全',
  '没有风险', '零风险', '闭眼报', '随便报都能上',
] as const;

export function sanitizeDisplayText(text: string): string {
  if (!text) return '';
  let out = text;
  const sorted = [...HARD_COMPLIANCE_TERMS].sort((a, b) => b.length - a.length);
  for (const term of sorted) {
    if (out.includes(term)) {
      out = out.split(term).join(term.includes('率') || term.includes('概率') ? '机会参考度' : '历史参考评估');
    }
  }
  return out;
}
