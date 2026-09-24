#!/usr/bin/env node
// Export the deck to slides.pdf, without the feedback-poll QR code.
//
// The QR points at a poll that goes stale days after the talk, so it belongs on
// the live screen only — never in a PDF people download later.
//
// Slidev offers no export-time conditional we can use for this: RenderContext
// has no 'print' value (only none | slide | overview | presenter | previewNext),
// and the exporter calls emulateMedia({ media: 'screen' }) before page.pdf(),
// so an @media print rule never applies either. Exporting from a transformed
// copy of the deck is the one approach that does not depend on Slidev internals.

import { readFileSync, writeFileSync, rmSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const entry = resolve(root, 'slides.md')
const temp = resolve(root, '.slides-print.md')

const QR_BLOCK = /\n<div class="qr-block">[\s\S]*?<\/div>\n/

const src = readFileSync(entry, 'utf8')
if (!QR_BLOCK.test(src)) {
  console.error(
    'export-pdf: no <div class="qr-block"> found in slides.md.\n' +
    'The thank-you slide changed shape — check whether the QR still needs stripping,\n' +
    'and update this script rather than shipping a PDF with a stale poll link.',
  )
  process.exit(1)
}

writeFileSync(temp, src.replace(QR_BLOCK, '\n'))
try {
  // Forward any extra flags, so `npm run export -- --with-clicks` still works.
  const args = ['slidev', 'export', '.slides-print.md', '--output', 'slides.pdf', ...process.argv.slice(2)]
  execFileSync('npx', args, {
    cwd: root,
    stdio: 'inherit',
  })
} finally {
  rmSync(temp, { force: true })
}
