/** 在 canvas 上绘制重复水印文本 */
export function drawWatermark(
  canvas: HTMLCanvasElement,
  text: string = 'AI生成 仅供参考',
  options: { fontSize?: number; color?: string; angle?: number; gap?: number } = {},
) {
  const { fontSize = 16, color = 'rgba(0,0,0,0.08)', angle = -30, gap = 120 } = options
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.save()
  ctx.font = `${fontSize}px sans-serif`
  ctx.fillStyle = color
  ctx.rotate((angle * Math.PI) / 180)

  const w = canvas.width * 2
  const h = canvas.height * 2

  for (let y = -h; y < h; y += gap) {
    for (let x = -w; x < w; x += gap + ctx.measureText(text).width) {
      ctx.fillText(text, x, y)
    }
  }

  ctx.restore()
}
