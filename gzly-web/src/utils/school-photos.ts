/**
 * 校园图按需加载：全量 school_photos.json 有 1.7MB，
 * 已由 scripts/split-school-photos.mjs 分成 64 个桶，这里按 schoolId 取对应桶。
 * 桶算法必须与分桶脚本保持一致。
 */
const BUCKETS = 64
const bucketCache = new Map<number, Promise<Record<string, string[]>>>()

function bucketOf(schoolId: string): number {
  let hash = 0
  for (let i = 0; i < schoolId.length; i++) {
    hash = (hash * 31 + schoolId.charCodeAt(i)) >>> 0
  }
  return hash % BUCKETS
}

async function loadBucket(bucket: number): Promise<Record<string, string[]>> {
  let pending = bucketCache.get(bucket)
  if (!pending) {
    pending = fetch(`/school-photos/bucket-${bucket}.json`)
      .then(res => (res.ok ? res.json() : {}))
      .catch(() => ({}))
    bucketCache.set(bucket, pending)
  }
  return pending
}

/** 取某所学校的校园图列表；桶缺失或学校无图时返回空数组。 */
export async function fetchSchoolPhotos(schoolId: string): Promise<string[]> {
  if (!schoolId) return []
  const bucket = await loadBucket(bucketOf(schoolId))
  const photos = bucket[schoolId]
  return Array.isArray(photos) ? photos : []
}
