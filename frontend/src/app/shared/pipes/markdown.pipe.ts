import { Pipe, PipeTransform } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { marked } from 'marked';
import DOMPurify from 'dompurify';

// Render fenced code blocks with a data attribute carrying the language, so
// message-item.component.ts can attach a "Copy" button + language label
// without re-parsing the markdown itself.
const renderer = new marked.Renderer();
renderer.code = ({ text, lang }: { text: string; lang?: string }) => {
  const language = (lang || 'text').split(/\s+/)[0].toLowerCase();
  const escaped = escapeHtml(text);
  return `<pre class="code-block" data-language="${escapeHtml(language)}"><code>${escaped}</code></pre>`;
};

marked.setOptions({ renderer, breaks: true, gfm: true });

function escapeHtml(input: string): string {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Renders assistant message content as sanitized HTML.
 *
 * Two-step safety: `marked` converts Markdown -> HTML, then `DOMPurify`
 * strips anything dangerous (script tags, inline event handlers, javascript:
 * URLs, etc.) before the result is ever marked as "safe" for Angular's
 * `[innerHTML]` binding. We NEVER bind raw model output to innerHTML
 * without this pipe.
 */
@Pipe({
  name: 'markdown',
  standalone: true,
})
export class MarkdownPipe implements PipeTransform {
  constructor(private readonly sanitizer: DomSanitizer) {}

  transform(value: string | null | undefined): SafeHtml {
    if (!value) {
      return '';
    }
    const rawHtml = marked.parse(value, { async: false }) as string;
    const cleanHtml = DOMPurify.sanitize(rawHtml, {
      ALLOWED_TAGS: [
        'p', 'br', 'strong', 'em', 'del', 'ul', 'ol', 'li', 'blockquote',
        'a', 'code', 'pre', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'table',
        'thead', 'tbody', 'tr', 'th', 'td', 'hr', 'span',
      ],
      ALLOWED_ATTR: ['href', 'class', 'data-language', 'target', 'rel'],
    });
    return this.sanitizer.bypassSecurityTrustHtml(cleanHtml);
  }
}
