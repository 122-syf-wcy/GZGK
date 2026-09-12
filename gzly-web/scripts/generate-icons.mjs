/**
 * UI 图标批量生成脚本（OpenAI 兼容图像接口）。
 *
 * 用法（PowerShell）：
 *   $env:AHRI_API_KEY="sk-..."; node scripts/generate-icons.mjs            # 生成全部
 *   $env:AHRI_API_KEY="sk-..."; node scripts/generate-icons.mjs logo      # 只生成指定名称
 *
 * 环境变量：
 *   AHRI_API_KEY     必填，API 密钥（不要写进任何仓库文件）
 *   AHRI_API_BASE    选填，默认 https://ahriapi.com/v1
 *   AHRI_IMAGE_MODEL 选填，默认 gpt-image-2
 *
 * 输出：public/icons/{name}.png（1024x1024）
 */
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'

const API_BASE = (process.env.AHRI_API_BASE || 'https://ahriapi.com/v1').replace(/\/+$/, '')
const API_KEY = process.env.AHRI_API_KEY
const MODEL = process.env.AHRI_IMAGE_MODEL || 'gpt-image-2'
const OUT_DIR = path.resolve(process.cwd(), 'public/icons')

if (!API_KEY) {
  console.error('缺少 AHRI_API_KEY 环境变量')
  process.exit(1)
}

/** 统一视觉语言：柔和粉彩粘土 3D 图标 + 奶油纸底，清新明亮、治愈不刺眼。 */
const STYLE =
  'Minimalist premium 3D icon, single centered object made of soft matte clay with smooth rounded edges, ' +
  'gentle pastel color palette, floating on a warm cream paper background (#F6F5F2), soft diffused daylight, ' +
  'one gentle realistic contact shadow, fresh airy and uplifting mood, cute but elegant, ' +
  'generous negative space around the object, high detail, no text, no letters, no watermark, no gradient background'

const ICONS = [
  {
    name: 'hero-landscape',
    size: '1536x1024',
    prompt:
      'Photorealistic landscape photography, breathtaking wide shot of lush green karst mountains of Guizhou China at sunrise, ' +
      'soft morning mist flowing through the valley, a winding river reflecting warm golden light, clear sky with gentle clouds, ' +
      'serene and hopeful atmosphere, cinematic natural color grading, extremely high detail, no text, no watermark, no people',
  },
  {
    name: 'logo',
    prompt: `${STYLE}. Subject: a soft sky-blue graduation cap (mortarboard) with a small warm golden star rising above it, symbolizing college admission success`,
  },
  {
    name: 'volunteer',
    prompt: `${STYLE}. Subject: a soft sky-blue checklist document with mint-green check marks and a small warm golden sparkle, symbolizing college application form filling`,
  },
  {
    name: 'university',
    prompt: `${STYLE}. Subject: a cream and warm-beige university main building with classical columns and a soft red flag, tiny green bushes beside it, symbolizing campus search`,
  },
  {
    name: 'scoreline',
    prompt: `${STYLE}. Subject: a fresh mint-green rising bar chart with a soft golden arrow curving upward, symbolizing admission score trends`,
  },
  {
    name: 'special',
    prompt: `${STYLE}. Subject: a soft warm-golden trophy cup with a coral-pink star badge, symbolizing special talent admission programs`,
  },
  {
    name: 'encourage',
    prompt: `${STYLE}. Subject: a soft coral-pink heart with tiny cream stars floating around it, symbolizing an encouragement wall for students`,
  },
  {
    name: 'myplans',
    prompt: `${STYLE}. Subject: a light apricot open folder with two rounded cream paper sheets tucked inside, symbolizing saved study plans`,
  },
  {
    name: 'ai',
    prompt: `${STYLE}. Subject: a soft lavender rounded speech bubble with a small warm golden four-pointed sparkle beside it, symbolizing smart analysis chat`,
  },
]

const DEFAULT_SIZE = process.env.AHRI_IMAGE_SIZE || '2048x2048'

async function generateOne(item, attempt = 1) {
  // 第 3 次尝试自动降级到 1024，兼容不支持 2K 的网关。
  const size = item.size || (attempt >= 3 ? '1024x1024' : DEFAULT_SIZE)
  const body = {
    model: MODEL,
    prompt: item.prompt,
    n: 1,
    size,
  }
  const res = await fetch(`${API_BASE}/images/generations`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = (await res.text()).slice(0, 500)
    throw new Error(`HTTP ${res.status}: ${text}`)
  }
  const json = await res.json()
  const data = json?.data?.[0]
  if (!data) {
    throw new Error(`响应缺少 data: ${JSON.stringify(json).slice(0, 300)}`)
  }

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
  console.log(`[OK] ${item.name} -> ${outPath} (${Math.round(buffer.length / 1024)} KB)`)
}

async function main() {
  await mkdir(OUT_DIR, { recursive: true })
  const only = process.argv.slice(2).filter(a => !a.startsWith('-'))
  const targets = only.length ? ICONS.filter(i => only.includes(i.name)) : ICONS
  if (!targets.length) {
    console.error(`没有匹配的图标名，可用: ${ICONS.map(i => i.name).join(', ')}`)
    process.exit(1)
  }
  console.log(`模型: ${MODEL} | 端点: ${API_BASE}/images/generations | 计划生成 ${targets.length} 张`)

  const failed = []
  for (const item of targets) {
    for (let attempt = 1; attempt <= 3; attempt++) {
      try {
        console.log(`生成 ${item.name}（第 ${attempt} 次尝试）...`)
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
  console.log('全部图标生成完成')
}

main().catch(e => {
  console.error(e)
  process.exit(1)
})
