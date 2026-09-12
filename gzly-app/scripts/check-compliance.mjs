/**
 * 合规词自检：扫描 src 下所有源码文本，命中违禁词即失败退出。
 *
 * 词表为单一事实源：gzly-server/src/main/resources/compliance-terms.json，
 * 与 `src/constants/compliance.ts` 及 web 端 check-compliance.mjs 共读同一文件。
 * 服务端会清洗自己产出的内容，但客户端写死的文案（按钮、空态、提示、
 * 商店描述）不经过服务端，必须在构建前拦截。
 *
 * 用法：node scripts/check-compliance.mjs
 */
import { readdir, readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import process from 'node:process'

// 以脚本自身位置定位，保证从仓库根或 gzly-app 目录运行结果一致
const APP_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(APP_ROOT, 'src')

/** 合规违禁词单一事实源：三端共读同一份 JSON，本文件禁止再维护硬编码词条。 */
const CANONICAL_TERMS_PATH = path.join(
  APP_ROOT, '..', 'gzly-server', 'src', 'main', 'resources', 'compliance-terms.json',
)

async function loadCanonicalTerms() {
  let raw
  try {
    raw = (await readFile(CANONICAL_TERMS_PATH, 'utf-8')).replace(/^﻿/, '')
  } catch (err) {
    console.error(`[合规][FATAL] 无法读取规范词表 ${CANONICAL_TERMS_PATH}: ${err.message}`)
    process.exit(1)
  }
  let parsed
  try {
    parsed = JSON.parse(raw)
  } catch (err) {
    console.error(`[合规][FATAL] 规范词表解析失败 ${CANONICAL_TERMS_PATH}: ${err.message}`)
    process.exit(1)
  }
  const hard = Array.isArray(parsed.hard) ? parsed.hard.filter(t => typeof t === 'string' && t.length > 0) : []
  if (hard.length === 0) {
    console.error('[合规][FATAL] 规范词表 hard 为空，拒绝以空词表通过合规门禁')
    process.exit(1)
  }
  return hard
}

/** 词表自身的定义文件不扫，否则永远命中 */
const EXCLUDE = [path.join('constants', 'compliance.ts')]

const EXTS = new Set(['.vue', '.ts', '.json', '.scss'])

async function collect(dir) {
  const out = []
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'static') continue
      out.push(...await collect(full))
    } else if (EXTS.has(path.extname(entry.name))) {
      out.push(full)
    }
  }
  return out
}

async function main() {
  const banned = await loadCanonicalTerms()
  const files = await collect(SRC)
  const hits = []

  for (const file of files) {
    const rel = path.relative(SRC, file)
    if (EXCLUDE.some(ex => rel.endsWith(ex))) continue
    const text = await readFile(file, 'utf-8')
    const lines = text.split(/\r?\n/)
    lines.forEach((line, idx) => {
      for (const term of banned) {
        if (line.includes(term)) {
          hits.push({ file: rel, line: idx + 1, term, sample: line.trim().slice(0, 80) })
        }
      }
    })
  }

  if (hits.length) {
    console.error(`[合规] 命中 ${hits.length} 处违禁词：\n`)
    hits.forEach(h => console.error(`  ${h.file}:${h.line}  「${h.term}」  ${h.sample}`))
    console.error('\n请改用「机会指数」「参考概率」等合规表述后重试。')
    process.exit(1)
  }
  console.log(`[合规] 已扫描 ${files.length} 个源文件，未发现违禁词`)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
