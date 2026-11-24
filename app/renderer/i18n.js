/* B+ interface languages. The app code keeps producing Arabic or English text; for the other
   languages this layer replaces the English text with its translation (exact text first, then a
   known prefix such as "Speed " followed by a number). Translations other than Arabic and English
   are machine-generated and not reviewed by native speakers. English is the fallback. */
(function () {
  const LANGS = [
    ['ar', 'العربية', true], ['en', 'English', false], ['es', 'Español', false], ['fr', 'Français', false],
    ['pt', 'Português', false], ['ru', 'Русский', false], ['zh', '中文 (简体)', false], ['hi', 'हिन्दी', false],
    ['bn', 'বাংলা', false], ['ur', 'اردو', true], ['id', 'Bahasa Indonesia', false], ['tr', 'Türkçe', false]
  ];
  const IDX = { es: 0, fr: 1, pt: 2, ru: 3, zh: 4, hi: 5, bn: 6, ur: 7, id: 8, tr: 9 };
  const data = window.BPLUS_I18N_DATA || {};
  const prefixKeys = Object.keys(data).filter(k => /(: |\s|\()$/.test(k)).sort((a, b) => b.length - a.length);
  let lang = 'en';
  const recs = new Set();           // {node|el, attr, orig, out}
  const outOf = new WeakMap();      // text node -> last translated value written by us
  const missing = new Set();
  let observer = null;

  const supported = c => LANGS.some(l => l[0] === c);
  const isRtl = c => (LANGS.find(l => l[0] === c) || [])[2] === true;
  function fromSystem() {
    const s = String((navigator.languages && navigator.languages[0]) || navigator.language || '').toLowerCase().split('-')[0];
    return supported(s) ? s : 'en';
  }
  function lookup(text) {
    const i = IDX[lang];
    if (i === undefined) return null;
    const t = text.replace(/\s+/g, ' ').trim();
    if (!t) return null;
    const fm = t.match(/^(\d+) file\(s\)$/);
    if (fm && data['file(s)'] && data['file(s)'][i]) return fm[1] + ' ' + data['file(s)'][i];
    let e = data[t];
    if (e && e[i]) return e[i];
    for (const k of prefixKeys) {
      if (t.length > k.length && t.startsWith(k) && data[k][i]) {
        const rest = t.slice(k.length);
        const r = lookup(rest);
        return data[k][i] + (r === null ? rest : r);
      }
    }
    if (/[A-Za-z]{3}/.test(t)) missing.add(t);
    return null;
  }
  function skip(el) { return !el || el.closest && el.closest('script,style,canvas,.nm,[data-noi18n],#tb-title,video'); }
  function tText(node) {
    if (lang === 'en' || lang === 'ar' || !IDX.hasOwnProperty(lang)) return;
    const cur = node.nodeValue;
    if (outOf.get(node) === cur) return;
    if (skip(node.parentElement)) return;
    const tr = lookup(cur);
    if (tr === null) return;
    const lead = cur.match(/^\s*/)[0], trail = cur.match(/\s*$/)[0];
    const out = lead + tr + trail;
    recs.add({ node, orig: cur, out });
    outOf.set(node, out);
    node.nodeValue = out;
  }
  function tAttr(el, name) {
    if (lang === 'en' || lang === 'ar' || !IDX.hasOwnProperty(lang)) return;
    const cur = el.getAttribute(name);
    if (!cur) return;
    for (const r of recs) if (r.el === el && r.attr === name && r.out === cur) return;
    if (skip(el)) return;
    const tr = lookup(cur);
    if (tr === null) return;
    recs.add({ el, attr: name, orig: cur, out: tr });
    el.setAttribute(name, tr);
  }
  const ATTRS = ['title', 'aria-label', 'placeholder'];
  function walk(root) {
    if (!root) return;
    if (root.nodeType === 3) { tText(root); return; }
    if (root.nodeType !== 1) return;
    const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let n; const list = [];
    while ((n = w.nextNode())) list.push(n);
    list.forEach(tText);
    const els = [root, ...root.querySelectorAll('[title],[aria-label],[placeholder]')];
    els.forEach(el => ATTRS.forEach(a => el.hasAttribute && el.hasAttribute(a) && tAttr(el, a)));
  }
  function revert() {
    for (const r of recs) {
      if (r.node) { if (r.node.nodeValue === r.out) r.node.nodeValue = r.orig; outOf.delete(r.node); }
      else if (r.el && r.el.getAttribute(r.attr) === r.out) r.el.setAttribute(r.attr, r.orig);
    }
    recs.clear();
  }
  function onMut(list) {
    for (const m of list) {
      if (m.type === 'childList') m.addedNodes.forEach(walk);
      else if (m.type === 'characterData') tText(m.target);
      else if (m.type === 'attributes') tAttr(m.target, m.attributeName);
    }
  }
  function setLang(code) {
    if (!supported(code)) code = 'en';
    if (observer) observer.disconnect();
    revert();
    lang = code;
    document.documentElement.setAttribute('data-ui-lang', code);
    if (IDX.hasOwnProperty(code)) {
      walk(document.documentElement);
      observer = observer || new MutationObserver(onMut);
      observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
    }
  }
  function tr(text) { const r = lookup(String(text)); return r === null ? text : r; }
  window.BPLUS_I18N = { LANGS, supported, isRtl, fromSystem, setLang, tr, missing, get lang() { return lang; } };
})();
