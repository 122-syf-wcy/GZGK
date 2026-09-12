/**
 * 全站色板迁移：旧"蓝/黄/绿高饱和"色板 → 新"黑白编辑风低饱和"色板。
 * 只替换颜色字面量，不改类名与结构。可重复执行（幂等）。
 *
 * 用法：node scripts/migrate-palette.mjs [--dry]
 */
import { readdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const ROOT = path.resolve(process.cwd(), 'src')
const DRY = process.argv.includes('--dry')

/** 顺序敏感：rgba 前缀先替换，再替换 hex。 */
const RGBA_MAP = [
  ['rgba(37, 99, 235', 'rgba(23, 24, 28'],
  ['rgba(29, 78, 216', 'rgba(23, 24, 28'],
  ['rgba(59, 130, 246', 'rgba(43, 45, 51'],
  ['rgba(191, 219, 254', 'rgba(217, 216, 211'],
  ['rgba(15, 23, 42', 'rgba(23, 24, 28'],
  ['rgba(16, 185, 129', 'rgba(47, 125, 93'],
  ['rgba(245, 158, 11', 'rgba(185, 138, 47'],
  ['rgba(180, 83, 9', 'rgba(138, 109, 59'],
  ['rgba(239, 68, 68', 'rgba(192, 72, 72'],
  ['rgba(220, 38, 38', 'rgba(179, 64, 64'],
]

const HEX_MAP = [
  // 蓝系 → 墨/中性
  ['#1e40af', '#17181c'],
  ['#1d4ed8', '#17181c'],
  ['#2563eb', '#17181c'],
  ['#3b82f6', '#2b2d33'],
  ['#60a5fa', '#55575e'],
  ['#93c5fd', '#b9bbc0'],
  ['#bfdbfe', '#d9d8d3'],
  ['#dbeafe', '#e7e6e1'],
  ['#eff6ff', '#f4f4f2'],
  // 青系 → 中性
  ['#ecfeff', '#f4f4f2'],
  ['#cffafe', '#eceded'],
  ['#22d3ee', '#55575e'],
  ['#06b6d4', '#55575e'],
  ['#0891b2', '#4b4d54'],
  ['#0e7490', '#4b4d54'],
  ['#0f766e', '#4b4d54'],
  // 黄 / 棕（警示语义，降饱和暖棕）
  ['#fffbeb', '#faf7ef'],
  ['#fefce8', '#faf7ef'],
  ['#fef3c7', '#f3ecd9'],
  ['#fde68a', '#e6dcbd'],
  ['#fde047', '#e6dcbd'],
  ['#fed7aa', '#e8dcc5'],
  ['#fbbf24', '#c29a45'],
  ['#f59e0b', '#b98a2f'],
  ['#d97706', '#a5793a'],
  ['#b45309', '#8a6d3b'],
  ['#92400e', '#7c5f33'],
  ['#854d0e', '#7c5f33'],
  ['#78350f', '#6f5730'],
  // 绿（成功语义降饱和）
  ['#ecfdf5', '#eef2ee'],
  ['#d1fae5', '#dbe5db'],
  ['#a7f3d0', '#c2d5c5'],
  ['#34d399', '#4f9578'],
  ['#10b981', '#2f7d5d'],
  ['#059669', '#2f7d5d'],
  ['#047857', '#2f6650'],
  ['#065f46', '#2c5745'],
  // 红（危险语义降饱和）
  ['#fef2f2', '#f7efef'],
  ['#fecaca', '#e3cbcb'],
  ['#f87171', '#cc6b6b'],
  ['#ef4444', '#c04848'],
  ['#dc2626', '#b34040'],
  ['#b91c1c', '#a03535'],
  ['#991b1b', '#8f3030'],
  // 紫（标签用色降饱和）
  ['#8b5cf6', '#6b5d8a'],
  ['#a78bfa', '#8a7ba8'],
  // 近黑与冷灰 → 墨调 / 暖灰
  ['#0f172a', '#17181c'],
  ['#111827', '#17181c'],
  ['#1e293b', '#22242a'],
  ['#334155', '#383a40'],
  ['#475569', '#4b4d54'],
  ['#64748b', '#6a6c72'],
  ['#94a3b8', '#97999e'],
  ['#cbd5e1', '#cdccc7'],
  ['#e2e8f0', '#e3e2de'],
  ['#f1f5f9', '#f2f2ef'],
  ['#f8fafc', '#fafaf8'],
  ['#fffdfa', '#ffffff'],
  ['#fbf6e8', '#f6f5f2'],
]

async function collectFiles(dir) {
  const entries = await readdir(dir, { withFileTypes: true })
  const files = []
  for (const entry of entries) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      files.push(...(await collectFiles(full)))
    } else if (/\.(vue|css)$/.test(entry.name)) {
      files.push(full)
    }
  }
  return files
}

function applyMap(content) {
  let out = content
  let hits = 0
  for (const [from, to] of RGBA_MAP) {
    const re = new RegExp(from.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi')
    out = out.replace(re, () => {
      hits++
      return to
    })
  }
  for (const [from, to] of HEX_MAP) {
    // hex 后必须跟非十六进制字符，避免误伤 8 位 hex
    const re = new RegExp(`${from}(?![0-9a-fA-F])`, 'gi')
    out = out.replace(re, () => {
      hits++
      return to
    })
  }
  return { out, hits }
}

const files = await collectFiles(ROOT)
let totalHits = 0
let touched = 0
for (const file of files) {
  const content = await readFile(file, 'utf8')
  const { out, hits } = applyMap(content)
  if (hits > 0) {
    totalHits += hits
    touched++
    if (!DRY) {
      await writeFile(file, out, 'utf8')
    }
    console.log(`${DRY ? '[dry] ' : ''}${path.relative(process.cwd(), file)}: ${hits}`)
  }
}
console.log(`\n${DRY ? '试运行' : '完成'}：${touched} 个文件，共 ${totalHits} 处替换`)
