#!/usr/bin/env node
/**
 * GZLY 前端合规扫描器：扫描 src/ 下所有 .vue/.ts/.tsx/.js/.md，
 * 命中"承诺性词汇黑名单"即非零退出。CI 在 npm run build 之前必跑。
 *
 * 黑名单按重要性分组：
 *  - HARD：强制录取语义（命中率/包过/百分百…），直接拒绝；
 *  - SOFT：可能引入误读的口语化词（一定/绝对/铁定）；
 *
 * 扫描例外：
 *  - src/constants/compliance.ts 是合规声明源文件，其中「本系统不会提供…绝对安全…」
 *    等否定式表述属合规声明本身；与 gzly-app 脚本的 EXCLUDE 口径一致，不参与扫描。
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

/**
 * 合规违禁词单一事实源：gzly-server/src/main/resources/compliance-terms.json。
 * 三端共读同一份文件（后端 AiService 启动期加载、web/app 构建脚本读取），
 * 本文件内禁止再维护任何硬编码词条数组，避免三端漂移。词表只增不删。
 */
const CANONICAL_TERMS_PATH = join(ROOT, '..', 'gzly-server', 'src', 'main', 'resources', 'compliance-terms.json')

function loadCanonicalTerms() {
  let raw
  try {
    raw = readFileSync(CANONICAL_TERMS_PATH, 'utf-8').replace(/^﻿/, '')
  } catch (err) {
    console.error(`[compliance][FATAL] 无法读取规范词表 ${CANONICAL_TERMS_PATH}: ${err.message}`)
    process.exit(1)
  }
  let parsed
  try {
    parsed = JSON.parse(raw)
  } catch (err) {
    console.error(`[compliance][FATAL] 规范词表解析失败 ${CANONICAL_TERMS_PATH}: ${err.message}`)
    process.exit(1)
  }
  const hard = Array.isArray(parsed.hard) ? parsed.hard.filter(t => typeof t === 'string' && t.length > 0) : []
  const soft = Array.isArray(parsed.soft) ? parsed.soft.filter(t => typeof t === 'string' && t.length > 0) : []
  if (hard.length === 0) {
    console.error('[compliance][FATAL] 规范词表 hard 为空，拒绝以空词表通过合规门禁')
    process.exit(1)
  }
  return { hard, soft }
}

/** 强制禁用关键词：命中即非零退出。词源为 compliance-terms.json 的 hard 数组。 */
const { hard: HARD_BLOCKLIST, soft: SOFT_BLOCKLIST } = loadCanonicalTerms()

const SCAN_EXTENSIONS = new Set(['.vue', '.ts', '.tsx', '.js', '.mjs', '.md'])
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git', 'coverage', '.vite', '.cache'])

/** 合规声明/词表定义文件不扫：其内容为合规声明本身，与 gzly-app 脚本 EXCLUDE 口径一致。 */
const EXCLUDE_SUFFIX = join('constants', 'compliance.ts')

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
    const rel = relative(ROOT, file)
    if (rel.endsWith(EXCLUDE_SUFFIX)) continue
    const hits = scanFile(file, HARD_BLOCKLIST, SOFT_BLOCKLIST)
    if (hits.length === 0) continue
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
