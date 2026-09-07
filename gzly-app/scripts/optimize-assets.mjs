/**
 * 视觉资产压缩。
 *
 * 生图接口出的是 2K PNG，单张 4-5 MB，直接进 App 包不可接受
 * （8 张就 35 MB，而它们在屏幕上最大只占 96rpx 到满屏宽）。
 *
 * 这里按实际展示尺寸重采样并转 WebP：
 *   hero  -> 宽 1440，quality 82
 *   icon  -> 256x256，quality 88（3x 屏下 96rpx ≈ 144px，256 足够且留余量）
 *   logo  -> 128x128
 *
 * 用法：node scripts/optimize-assets.mjs
 * 原始 PNG 保留在 raw/ 下，不进构建产物。
 */
import { mkdir, readdir, rename, stat } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import sharp from 'sharp'

const IMG_DIR = path.resolve(process.cwd(), 'src/static/img')
const RAW_DIR = path.resolve(process.cwd(), 'assets-raw')

/** 按文件名前缀决定目标尺寸 */
function targetFor(name) {
  if (name.startsWith('hero-')) return { width: 1440, quality: 82 }
  if (name.startsWith('logo-')) return { width: 128, quality: 90 }
  return { width: 256, quality: 88 }
}

async function main() {
  await mkdir(RAW_DIR, { recursive: true })
  const files = (await readdir(IMG_DIR)).filter(f => f.endsWith('.png'))
  if (!files.length) {
    console.log('没有待处理的 PNG')
    return
  }

  let before = 0
  let after = 0

  for (const file of files) {
    const name = path.basename(file, '.png')
    const src = path.join(IMG_DIR, file)
    const { width, quality } = targetFor(name)

    const srcSize = (await stat(src)).size
    before += srcSize

    const outPath = path.join(IMG_DIR, `${name}.webp`)
    await sharp(src)
      .resize({ width, withoutEnlargement: true })
      .webp({ quality })
      .toFile(outPath)

    const outSize = (await stat(outPath)).size
    after += outSize

    // 原始 PNG 移出 static，避免被打进包
    await rename(src, path.join(RAW_DIR, file))

    console.log(
      `${name}: ${Math.round(srcSize / 1024)} KB -> ${Math.round(outSize / 1024)} KB (宽 ${width})`,
    )
  }

  console.log(
    `\n合计 ${Math.round(before / 1024 / 1024 * 10) / 10} MB -> ${Math.round(after / 1024)} KB`,
  )
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
