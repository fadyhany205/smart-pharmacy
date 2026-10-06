/* Shared helpers for all pages. */
(function () {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    /** fetch() wrapper that sends/receives JSON and the CSRF token. Throws Error(message) on failure. */
    window.api = async function (url, options = {}) {
        const headers = { 'Accept': 'application/json', ...(options.headers || {}) };
        if (options.body && typeof options.body !== 'string') {
            options.body = JSON.stringify(options.body);
            headers['Content-Type'] = 'application/json';
        }
        if (csrfToken && csrfHeader) headers[csrfHeader] = csrfToken;
        const res = await fetch(url, { ...options, headers });
        let data = null;
        try { data = await res.json(); } catch (e) { /* no body */ }
        if (!res.ok) {
            const err = new Error((data && data.error) || ('HTTP ' + res.status));
            err.status = res.status;
            throw err;
        }
        return data;
    };

    window.money = n => (Math.round((Number(n) || 0) * 100) / 100).toFixed(2);
    window.num = v => { const n = parseFloat(String(v).replace(',', '.')); return isNaN(n) ? 0 : n; };
    window.escapeHtml = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    /**
     * Discount amount from what the user typed: a fixed amount, or a percentage (0-100) of base.
     * Rounded to 2 decimals and never more than base (same rules as the server).
     */
    window.discountAmount = function (base, value, isPct) {
        const v = Math.max(0, num(value));
        const amount = isPct ? Math.round(base * Math.min(v, 100)) / 100 : v;
        return Math.min(Math.round(amount * 100) / 100, base);
    };

    /** Amount / percentage switch button: shows the currency or "%", keeps the state in data-pct. */
    window.initDiscountToggle = function (btn, onChange) {
        btn.addEventListener('click', () => {
            const pct = btn.dataset.pct !== 'true';
            btn.dataset.pct = String(pct);
            btn.textContent = pct ? '%' : btn.dataset.cur;
            btn.classList.toggle('btn-warning', pct);
            btn.classList.toggle('btn-outline-secondary', !pct);
            if (onChange) onChange(pct);
        });
        return { isPct: () => btn.dataset.pct === 'true',
                 reset: () => { btn.dataset.pct = 'false'; btn.textContent = btn.dataset.cur;
                                btn.classList.remove('btn-warning'); btn.classList.add('btn-outline-secondary'); } };
    };

    /** Switch language keeping the current URL. */
    window.setLang = function (lang) {
        const u = new URL(window.location.href);
        u.searchParams.set('lang', lang);
        window.location.href = u.toString();
    };

    /** Pagination: keep the current filters and change only ?page= */
    window.goPage = function (p) {
        const u = new URL(window.location.href);
        u.searchParams.set('page', p);
        u.searchParams.delete('lang');
        window.location.href = u.toString();
    };

    /**
     * Product search / barcode input.
     * - Enter with a scanned barcode adds the product directly.
     * - Typing shows a dropdown list of matches (arrow keys + Enter to choose).
     */
    window.productPicker = function (input, onPick, notFoundMsg) {
        const wrap = input.closest('.picker');
        const list = wrap.querySelector('.picker-list');
        let items = [], active = -1, timer = null, seq = 0;

        const hide = () => { list.classList.remove('show'); active = -1; };
        const render = () => {
            if (!items.length) { hide(); return; }
            list.innerHTML = items.map((p, i) =>
                `<button type="button" class="list-group-item list-group-item-action d-flex justify-content-between ${i === active ? 'active' : ''}" data-i="${i}">
                   <span><strong>${escapeHtml(p.name)}</strong><br><small class="${i === active ? '' : 'text-muted'}">${escapeHtml(p.barcode || '')}</small></span>
                   <span class="text-end num">${money(p.salePrice)}<br><small class="${i === active ? '' : 'text-muted'}">${escapeHtml(p.stockQty)} ${escapeHtml(p.unit)}</small></span>
                 </button>`).join('');
            list.classList.add('show');
        };
        const pick = p => { hide(); input.value = ''; items = []; onPick(p); input.focus(); };

        list.addEventListener('mousedown', e => {
            const b = e.target.closest('[data-i]');
            if (b) { e.preventDefault(); pick(items[+b.dataset.i]); }
        });
        input.addEventListener('blur', () => setTimeout(hide, 150));
        input.addEventListener('input', () => {
            clearTimeout(timer);
            const q = input.value.trim();
            if (q.length < 2) { items = []; hide(); return; }
            timer = setTimeout(async () => {
                const my = ++seq;
                try {
                    const res = await api('/api/products/search?q=' + encodeURIComponent(q));
                    if (my !== seq) return;
                    items = res; active = items.length ? 0 : -1; render();
                } catch (e) { /* ignore */ }
            }, 250);
        });
        input.addEventListener('keydown', async e => {
            if (e.key === 'ArrowDown' && items.length) { e.preventDefault(); active = (active + 1) % items.length; render(); }
            else if (e.key === 'ArrowUp' && items.length) { e.preventDefault(); active = (active - 1 + items.length) % items.length; render(); }
            else if (e.key === 'Escape') { hide(); }
            else if (e.key === 'Enter') {
                e.preventDefault();
                const q = input.value.trim();
                if (!q) return;
                clearTimeout(timer);
                seq++;
                // Barcode scanners type fast and press Enter: try exact barcode first.
                try {
                    const p = await api('/api/products/barcode/' + encodeURIComponent(q));
                    pick(p);
                    return;
                } catch (err) { /* not a barcode */ }
                if (active >= 0 && items[active]) { pick(items[active]); return; }
                try {
                    const res = await api('/api/products/search?q=' + encodeURIComponent(q));
                    if (res.length === 1) pick(res[0]);
                    else if (res.length > 1) { items = res; active = 0; render(); }
                    else toast(notFoundMsg.replace('{0}', q), 'danger');
                } catch (err) { toast(err.message, 'danger'); }
            }
        });
    };

    /**
     * Customer / supplier search box (replaces a dropdown with thousands of entries).
     * input: visible text box inside .picker; hidden: <input type=hidden> that receives the id.
     * Returns {clear()}. Editing the text after choosing clears the selection.
     */
    window.partyPicker = function (input, hidden, url, onChange) {
        const wrap = input.closest('.picker');
        const list = wrap.querySelector('.picker-list');
        let items = [], active = -1, timer = null, seq = 0;

        const hide = () => { list.classList.remove('show'); active = -1; };
        const render = () => {
            if (!items.length) { hide(); return; }
            list.innerHTML = items.map((p, i) =>
                `<button type="button" class="list-group-item list-group-item-action d-flex justify-content-between ${i === active ? 'active' : ''}" data-i="${i}">
                   <span><strong>${escapeHtml(p.name)}</strong><br><small class="${i === active ? '' : 'text-muted'}" dir="ltr">${escapeHtml(p.phone || '')}</small></span>
                   <span class="num small ${Number(p.balance) > 0 ? 'text-danger' : ''}">${money(p.balance)}</span>
                 </button>`).join('');
            list.classList.add('show');
        };
        const set = p => {
            hidden.value = p ? p.id : '';
            input.value = p ? p.name + (p.phone ? ' - ' + p.phone : '') : '';
            input.classList.toggle('is-valid', !!p);
            hide();
            if (onChange) onChange(p);
        };
        const search = async q => {
            const my = ++seq;
            try {
                const res = await api(url + '?q=' + encodeURIComponent(q));
                if (my !== seq) return;
                items = res; active = items.length ? 0 : -1; render();
            } catch (e) { /* ignore */ }
        };

        list.addEventListener('mousedown', e => {
            const b = e.target.closest('[data-i]');
            if (b) { e.preventDefault(); set(items[+b.dataset.i]); }
        });
        input.addEventListener('blur', () => setTimeout(hide, 150));
        input.addEventListener('input', () => {
            if (hidden.value) { hidden.value = ''; input.classList.remove('is-valid'); if (onChange) onChange(null); }
            clearTimeout(timer);
            const q = input.value.trim();
            if (!q) { items = []; hide(); return; }
            timer = setTimeout(() => search(q), 250);
        });
        input.addEventListener('keydown', e => {
            if (e.key === 'ArrowDown' && items.length) { e.preventDefault(); active = (active + 1) % items.length; render(); }
            else if (e.key === 'ArrowUp' && items.length) { e.preventDefault(); active = (active - 1 + items.length) % items.length; render(); }
            else if (e.key === 'Escape') { hide(); }
            else if (e.key === 'Enter') { e.preventDefault(); if (active >= 0 && items[active]) set(items[active]); }
        });
        return { clear: () => set(null) };
    };

    /** Small notification at the bottom of the screen. */
    window.toast = function (message, type = 'success') {
        let box = document.getElementById('toast-box');
        if (!box) {
            box = document.createElement('div');
            box.id = 'toast-box';
            box.className = 'toast-container position-fixed bottom-0 start-50 translate-middle-x p-3';
            box.style.zIndex = 2000;
            document.body.appendChild(box);
        }
        const el = document.createElement('div');
        el.className = `toast align-items-center text-bg-${type} border-0 show`;
        el.innerHTML = `<div class="d-flex"><div class="toast-body">${escapeHtml(message)}</div>
            <button type="button" class="btn-close btn-close-white m-auto me-2" onclick="this.closest('.toast').remove()"></button></div>`;
        box.appendChild(el);
        setTimeout(() => el.remove(), 4000);
    };

    /** Beep on scan errors (no audio file needed). */
    window.beep = function (ok = true) {
        try {
            const ctx = new (window.AudioContext || window.webkitAudioContext)();
            const o = ctx.createOscillator();
            o.frequency.value = ok ? 1200 : 300;
            o.connect(ctx.destination);
            o.start();
            setTimeout(() => { o.stop(); ctx.close(); }, ok ? 60 : 250);
        } catch (e) { /* ignore */ }
    };

    document.addEventListener('DOMContentLoaded', () => {
        // Mobile sidebar toggle
        document.querySelectorAll('[data-toggle-sidebar]').forEach(b =>
            b.addEventListener('click', () => document.body.classList.toggle('sidebar-open')));
        document.body.addEventListener('click', e => {
            if (document.body.classList.contains('sidebar-open') && !e.target.closest('.sidebar') && !e.target.closest('[data-toggle-sidebar]'))
                document.body.classList.remove('sidebar-open');
        });
        // Confirm dangerous actions: <form data-confirm="...">
        document.querySelectorAll('form[data-confirm]').forEach(f =>
            f.addEventListener('submit', e => { if (!confirm(f.dataset.confirm)) e.preventDefault(); }));
        // Auto-hide flash messages
        setTimeout(() => document.querySelectorAll('.alert-dismissible.auto-hide').forEach(a => a.remove()), 6000);
    });
})();
