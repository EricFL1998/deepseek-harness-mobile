// Reproduce the plugin-ecosystem numbers cited in the feasibility report.
// Usage: node registry-stats.mjs ../data/plugins.json
import fs from 'node:fs'

const file = process.argv[2] || '../data/plugins.json'
const j = JSON.parse(fs.readFileSync(file, 'utf8'))
const arr = j.plugins
const cat = {}
for (const p of arr) cat[p.category] = (cat[p.category] || 0) + 1

const uiRe = /(Web UI|Web 界面|web GUI|网页|悬浮|侧边栏|右侧栏|设置页|settings panel|overlay|panel|widget|UI)/i
let uiMention = 0
for (const p of arr) if (uiRe.test(JSON.stringify(p.description || {}))) uiMention++

const uiStrong = (cat.ui || 0) + (cat.theme || 0) + (cat.fun || 0)
console.log(JSON.stringify({
  source: j.url,
  updated: j.updated,
  total: arr.length,
  categories: cat,
  uiStrong,
  uiStrongRatio: +(uiStrong / arr.length * 100).toFixed(1),
  descriptionMentionsUi: uiMention,
  descriptionMentionsUiRatio: +(uiMention / arr.length * 100).toFixed(1),
}, null, 2))
