/**
 * App 视觉资产生成脚本（OpenAI 兼容图像接口）。
 *
 * 用法（PowerShell）：
 *   $env:AHRI_API_KEY="sk-..."; node scripts/generate-assets.mjs           # 全部
 *   $env:AHRI_API_KEY="sk-..."; node scripts/generate-assets.mjs hero-dawn # 指定名称
 *
 * 环境变量：
 *   AHRI_API_KEY     必填，API 密钥（不要写进任何仓库文件）
 *   AHRI_API_BASE    选填，默认 https://ahriapi.com/v1
 *   AHRI_IMAGE_MODEL 选填，默认 gpt-image-2
 *
 * 输出：src/static/img/{name}.png
 *
 * 视觉方向：明亮、开阔、有希望感。参考图的共同语言是蓝天白云、绿野花海、
 * 自然阳光 + 干净的浅色界面底 + 大字标题，而不是深色奢侈品风。
 * 面向高考考生与家长，明亮基调也比深色更合适。
 */
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const API_BASE = (process.env.AHRI_API_BASE || 'https://ahriapi.com/v1').replace(/\/+$/, '')
const API_KEY = process.env.AHRI_API_KEY
const MODEL = process.env.AHRI_IMAGE_MODEL || 'gpt-image-2'
const OUT_DIR = path.resolve(process.cwd(), 'src/static/img')

if (!API_KEY) {
  console.error('缺少 AHRI_API_KEY 环境变量')
  process.exit(1)
}

/** 亮色图标语言：单一主体、柔和哑光材质、清新配色、纯白底、自然柔影、大量留白。 */
const ICON_STYLE =
  'Minimalist premium 3D icon, one single centered object made of soft matte clay with smooth rounded edges, '
  + 'fresh clean color palette of sky blue, mint green and warm sand, on a pure white background, '
  + 'soft natural daylight from the upper left, one gentle soft contact shadow beneath the object, '
  + 'bright airy optimistic and uplifting mood, elegant and restrained, generous negative space around the object, '
  + 'sharp high detail, no text, no letters, no numbers, no watermark, no busy background'

const ASSETS = [
  {
    name: 'hero-sky',
    size: '1536x1024',
    prompt:
      'Photorealistic landscape photography, gentle rolling green hills covered with soft wildflowers, '
      + 'a wide clear blue sky filled with soft white cumulus clouds, warm early morning sunlight raking across the grass, '
      + 'a distant soft mountain range on the horizon, fresh dewy spring air, '
      + 'bright airy optimistic and hopeful atmosphere, clean natural color grading, high key, '
      + 'extremely high detail, no text, no watermark, no people, no buildings',
  },
  {
    name: 'hero-campus',
    size: '1536x1024',
    prompt:
      'Photorealistic photography, a sunlit university campus pathway lined with tall green trees, '
      + 'dappled warm sunlight falling through the leaves onto the path, a classical academic building softly out of focus in the distance, '
      + 'clear blue sky, fresh green foliage, bright airy and hopeful, clean natural color grading, high key, '
      + 'extremely high detail, no text, no watermark, no people',
  },
  {
    name: 'icon-volunteer',
    prompt: `${ICON_STYLE}. Subject: a soft sky-blue document card with a mint-green check mark and a tiny warm sand colored spark above it, symbolizing filling in a college application plan`,
  },
  {
    name: 'icon-university',
    prompt: `${ICON_STYLE}. Subject: a warm sand and cream classical academic building with slim rounded columns and tiny green bushes at its base, viewed three-quarter, symbolizing searching universities`,
  },
  {
    name: 'icon-scoreline',
    prompt: `${ICON_STYLE}. Subject: three soft mint-green vertical bars of rising height with a smooth sky-blue arrow curving upward across them, symbolizing admission score trends`,
  },
  {
    name: 'icon-special',
    prompt: `${ICON_STYLE}. Subject: a soft coral-pink shield badge with a warm sand colored star inlaid at its center, symbolizing special category admission programs`,
  },
  {
    name: 'icon-ai',
    prompt: `${ICON_STYLE}. Subject: a soft lavender rounded speech bubble with a small four-pointed sky-blue spark floating beside its upper right corner, symbolizing smart analysis`,
  },
  {
    name: 'icon-archive',
    prompt: `${ICON_STYLE}. Subject: a soft apricot closed archive box with a cream band and a tiny mint-green key resting on its lid, symbolizing a saved plan protected by a safety code`,
  },
  {
    name: 'logo-mark',
    prompt: `${ICON_STYLE}. Subject: a soft sky-blue graduation mortarboard cap seen at a slight angle, with one small warm golden star rising just above its corner, symbolizing college admission`,
  },
]

const DEFAULT_SIZE = process.env.AHRI_IMAGE_SIZE || '2048x2048'

async function generateOne(item, attempt = 1) {
  // 第 3 次尝试降级到 1024，兼容不支持 2K 的网关。
  const size = attempt >= 3 ? '1024x1024' : (item.size || DEFAULT_SIZE)
  const res = await fetch(`${API_BASE}/images/generations`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ model: MODEL, prompt: item.prompt, n: 1, size }),
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}: ${(await res.text()).slice(0, 400)}`)
  }
  const json = await res.json()
  const data = json?.data?.[0]
  if (!data) throw new Error(`响应缺少 data: ${JSON.stringify(json).slice(0, 300)}`)

  let buffer
  if (data.b64_json) {
    buffer = Buffer.from(data.b64_json, 'base64')
  } else if (data.url) {
    const imgRes = await fetch(data.url)
    if (!imgRes.ok) throw new Error(`下载图片失败 HTTP ${imgRes.status}`)
    buffer = Buffer.from(await imgRes.arrayBuffer())
  } else {
    throw new Error(`响应既无 b64_json 也无 url: ${JSON.stringify(data).slice(0, 300)}`)
  }

  const outPath = path.join(OUT_DIR, `${item.name}.png`)
  await writeFile(outPath, buffer)
  console.log(`[OK] ${item.name} -> ${Math.round(buffer.length / 1024)} KB (${size})`)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const only = process.argv.slice(2).filter(a => !a.startsWith('-'))
  const targets = only.length ? ASSETS.filter(i => only.includes(i.name)) : ASSETS
  if (!targets.length) {
    console.error(`没有匹配的名称，可用: ${ASSETS.map(i => i.name).join(', ')}`)
    process.exit(1)
  }
  console.log(`模型 ${MODEL} | 端点 ${API_BASE}/images/generations | 共 ${targets.length} 张`)

  const failed = []
  for (const item of targets) {
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        console.log(`生成 ${item.name}（第 ${attempt} 次）...`)
        await generateOne(item, attempt)
        break
      } catch (e) {
        console.warn(`[FAIL] ${item.name} 第 ${attempt} 次: ${e.message}`)
        if (attempt === 3) failed.push(item.name)
        else await new Promise(r => setTimeout(r, 3000 * attempt))
      }
    }
  }

  if (failed.length) {
    console.error(`失败清单: ${failed.join(', ')}`)
    process.exit(2)
  }
  console.log('全部资产生成完成')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
