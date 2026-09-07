/**
 * 合规词自检：扫描 src 下所有源码文本，命中违禁词即失败退出。
 *
 * 词表与 `src/constants/compliance.ts` 及后端 SensitiveWordMatcher 同源。
 * 服务端会清洗自己产出的内容，但客户端写死的文案（按钮、空态、提示、
 * 商店描述）不经过服务端，必须在构建前拦截。
 *
 * 用法：node scripts/check-compliance.mjs
 */
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const SRC = path.resolve(process.cwd(), 'src')

/** 与 constants/compliance.ts 的 BANNED_TERMS_HARD 保持一致 */
const BANNED = [
  '录取概率', '上岸概率', '保证录取', '保录', '确保录取', '铁定录取',
  '包录取', '包上', '稳上', '必上', '必录', '一定能上', '一定录取',
  '100%录取', '百分百录取', '绝对安全', '没有风险', '零风险',
  '保证不滑档', '闭眼报', '随便报都能上',
]

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
  const files = await collect(SRC)
  const hits = []

  for (const file of files) {
    const rel = path.relative(SRC, file)
    if (EXCLUDE.some(ex => rel.endsWith(ex))) continue
    const text = await readFile(file, 'utf-8')
    const lines = text.split(/\r?\n/)
    lines.forEach((line, idx) => {
      for (const term of BANNED) {
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
