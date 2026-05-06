#!/usr/bin/env node
/**
 * GZLY 前端合规扫描器：扫描 src/ 下所有 .vue/.ts/.tsx/.js/.md，
 * 命中"承诺性词汇黑名单"即非零退出。CI 在 npm run build 之前必跑。
 *
 * 黑名单按重要性分组：
 *  - HARD：强制录取语义（命中率/包过/百分百…），直接拒绝；
 *  - SOFT：可能引入误读的口语化词（一定/绝对/铁定）；
 *
 * 例外白名单：
 *  - "AI 命中" / "命中专业意向" 等内部诊断字段在合规审查时被允许，
 *    在 src/utils/compliance-allowlist.ts 中以 `allowList` 形式声明，使用时需要审核。
 *
 * 用法：
 *   node scripts/check-compliance.mjs                # 默认扫描 src/
 *   node scripts/check-compliance.mjs --paths src docs
 *   node scripts/check-compliance.mjs --json         # 机器可读输出
 */

import { readFileSync, statSync } from 'node:fs'
import { readdir } from 'node:fs/promises'
import { dirname, extname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)
const ROOT = join(__dirname, '..')

/** 强制禁用关键词：命中即非零退出。与 gzly-server AiService.BANNED_TERMS 全量对齐。 */
const HARD_BLOCKLIST = [
  // 用户白皮书 10 项必须禁用
  '录取概率',
  '保证录取',
  '稳上',
  '包录取',
  '百分百录取',
  '100%录取',
  '一定能上',
  '保证不滑档',
  '命中率',
  '上岸概率',
  // 历史既有禁用词（保留，覆盖更广的承诺性表达）
  '包过',
  '稳过',
  '保录',
  '确定能上',
  '百分百',
  '肯定能上',
  '一定录取',
  '绝对录取',
  '保上',
  '不滑档',
  '不脱档',
  '成功率',
  '录取率',
  '上榜率',
  '稳进',
  '必上',
  '必录',
]

/** 软提示关键词：命中给警告但不阻断（可在 --strict 下变阻断）。 */
const SOFT_BLOCKLIST = [
  '一定能',
  '绝对能',
  '铁定',
]

const SCAN_EXTENSIONS = new Set(['.vue', '.ts', '.tsx', '.js', '.mjs', '.md'])
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git', 'coverage', '.vite', '.cache'])

function parseArgs(argv) {
  const args = { paths: [], json: false, strict: false }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--paths') {
      while (argv[i + 1] && !argv[i + 1].startsWith('--')) {
        args.paths.push(argv[++i])
      }
    } else if (a === '--json') {
      args.json = true
    } else if (a === '--strict') {
      args.strict = true
    }
  }
  if (args.paths.length === 0) args.paths.push('src')
  return args
}

async function walk(dir, files) {
  let entries
  try {
    entries = await readdir(dir, { withFileTypes: true })
  } catch (err) {
    if (err.code === 'ENOENT') return
    throw err
  }
  for (const entry of entries) {
    if (SKIP_DIRS.has(entry.name)) continue
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      await walk(full, files)
    } else if (entry.isFile() && SCAN_EXTENSIONS.has(extname(entry.name))) {
      files.push(full)
    }
  }
}

function scanFile(filePath, hardList, softList) {
  const text = readFileSync(filePath, 'utf-8')
  const hits = []
  const lines = text.split(/\r?\n/)
  lines.forEach((line, idx) => {
    for (const kw of hardList) {
      if (line.includes(kw)) {
        hits.push({ severity: 'hard', keyword: kw, line: idx + 1, text: line.trim().slice(0, 240) })
      }
    }
    for (const kw of softList) {
      if (line.includes(kw)) {
        hits.push({ severity: 'soft', keyword: kw, line: idx + 1, text: line.trim().slice(0, 240) })
      }
    }
  })
  return hits
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const files = []
  for (const p of args.paths) {
    const abs = join(ROOT, p)
    try {
      const st = statSync(abs)
      if (st.isDirectory()) {
        await walk(abs, files)
      } else if (st.isFile() && SCAN_EXTENSIONS.has(extname(abs))) {
        files.push(abs)
      }
    } catch (err) {
      if (err.code === 'ENOENT') {
        console.warn(`[compliance] skip missing path: ${p}`)
        continue
      }
      throw err
    }
  }

  let hardCount = 0
  let softCount = 0
  const report = []
  for (const file of files) {
    const hits = scanFile(file, HARD_BLOCKLIST, SOFT_BLOCKLIST)
    if (hits.length === 0) continue
    const rel = relative(ROOT, file)
    for (const hit of hits) {
      if (hit.severity === 'hard') hardCount++
      else softCount++
      report.push({ file: rel, ...hit })
    }
  }

  if (args.json) {
    process.stdout.write(JSON.stringify({
      filesScanned: files.length,
      hardCount,
      softCount,
      hits: report,
    }, null, 2) + '\n')
  } else {
    console.log(`[compliance] scanned ${files.length} files`)
    if (report.length === 0) {
      console.log('[compliance] OK: no blocked keywords found')
    } else {
      for (const r of report) {
        const tag = r.severity === 'hard' ? 'HARD' : 'SOFT'
        console.log(`[compliance][${tag}] ${r.file}:${r.line}  «${r.keyword}»  ${r.text}`)
      }
      console.log(`[compliance] summary: HARD=${hardCount}, SOFT=${softCount}`)
    }
  }

  if (hardCount > 0) process.exit(2)
  if (args.strict && softCount > 0) process.exit(3)
  process.exit(0)
}

main().catch((err) => {
  console.error('[compliance][FATAL]', err)
  process.exit(1)
})
