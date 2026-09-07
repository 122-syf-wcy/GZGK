/**
 * 应用图标生成：从 assets-raw/logo-mark.png 出 1024 主图标与各密度尺寸。
 * 输出到项目根 icons/（不进 src/static，避免被打进运行时包）。
 *
 * 用法：node scripts/make-app-icon.mjs
 */
import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import sharp from 'sharp'

const SRC = path.resolve(process.cwd(), 'assets-raw/logo-mark.png')
const OUT = path.resolve(process.cwd(), 'icons')
const SIZES = [1024, 192, 144, 96, 72]

async function main() {
  await mkdir(OUT, { recursive: true })
  for (const size of SIZES) {
    const name = size === 1024 ? 'app-icon-1024.png' : `${size}x${size}.png`
    await sharp(SRC)
      .resize(size, size, { fit: 'cover' })
      .flatten({ background: '#ffffff' })
      .png()
      .toFile(path.join(OUT, name))
    console.log(`icons/${name}`)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
