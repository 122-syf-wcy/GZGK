/**
 * 把 public/school_photos.json（全量 1.7MB）按 schoolId 分成 64 个桶文件，
 * 输出到 public/school-photos/bucket-{n}.json，前端按需加载单桶（约 20-30KB）。
 *
 * 用法：node scripts/split-school-photos.mjs
 * 桶算法必须与 src/utils/school-photos.ts 的 bucketOf 保持一致。
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const BUCKETS = 64
const SRC = path.resolve(process.cwd(), 'public/school_photos.json')
const OUT_DIR = path.resolve(process.cwd(), 'public/school-photos')

function bucketOf(schoolId) {
  let hash = 0
  for (let i = 0; i < schoolId.length; i++) {
    hash = (hash * 31 + schoolId.charCodeAt(i)) >>> 0
  }
  return hash % BUCKETS
}

const raw = JSON.parse(await readFile(SRC, 'utf8'))
const buckets = Array.from({ length: BUCKETS }, () => ({}))

let schoolCount = 0
for (const [schoolId, photos] of Object.entries(raw)) {
  buckets[bucketOf(String(schoolId))][schoolId] = photos
  schoolCount++
}

await mkdir(OUT_DIR, { recursive: true })
let totalBytes = 0
for (let i = 0; i < BUCKETS; i++) {
  const content = JSON.stringify(buckets[i])
  totalBytes += content.length
  await writeFile(path.join(OUT_DIR, `bucket-${i}.json`), content, 'utf8')
}

console.log(`完成：${schoolCount} 所学校 → ${BUCKETS} 个桶，平均 ${(totalBytes / BUCKETS / 1024).toFixed(1)} KB/桶`)
