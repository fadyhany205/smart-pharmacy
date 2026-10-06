/* Pharmacy point of sale. Requires app.js and the POS config object from pos.html. */
(function () {
    /*
     * Cart line: {id, name, sci, barcode, units:[{code,name,factor,price}], unit, factor, price, qty,
     *             discount (typed value), discPct, sellable (smallest units, not expired), taxable, rx, fridge, expiry}
     */
    const cart = [];
    const $ = id => document.getElementById(id);
    const tbody = $('cart');
    const scan = $('scan');
    const invDisc = initDiscountToggle($('discountType'), () => updateTotals());
    const r2 = x => Math.round(x * 100) / 100;

    function method() {
        return document.querySelector('input[name="method"]:checked').value;
    }

    const gross = l => r2(l.qty * l.price);
    const lineDisc = l => discountAmount(gross(l), l.discount, l.discPct);
    const lineTotal = l => r2(gross(l) - lineDisc(l));

    /** Same rules as the server: VAT only on taxable lines, after their share of the invoice discount. */
    function totals() {
        const subtotal = r2(cart.reduce((s, l) => s + lineTotal(l), 0));
        const taxableLines = r2(cart.filter(l => l.taxable).reduce((s, l) => s + lineTotal(l), 0));
        const discount = discountAmount(subtotal, $('discount').value, invDisc.isPct());
        const taxableNet = subtotal === 0 ? 0 : r2(taxableLines * (subtotal - discount) / subtotal);
        const tax = Math.round(taxableNet * POS.taxRate) / 100;
        return { subtotal, discount, tax, total: r2(subtotal - discount + tax) };
    }

    function percentOk() {
        const bad = v => num(v) > 100;
        return !((invDisc.isPct() && bad($('discount').value)) || cart.some(l => l.discPct && bad(l.discount)));
    }

    /** Smallest units needed for this product across all its lines. */
    const neededFor = id => cart.filter(l => l.id === id).reduce((s, l) => s + l.qty * l.factor, 0);

    function soon(expiry) {
        if (!expiry) return false;
        const days = (new Date(expiry) - new Date()) / 86400000;
        return days <= 90;
    }

    function badges(l) {
        let h = '';
        if (l.rx) h += ` <span class="badge text-bg-danger" title="${escapeHtml(POS.msg.rx)}">${escapeHtml(POS.msg.rxShort)}</span>`;
        if (l.fridge) h += ` <span class="badge text-bg-info" title="${escapeHtml(POS.msg.fridge)}"><i class="bi bi-snow"></i></span>`;
        if (l.taxable) h += ` <span class="badge text-bg-warning">${escapeHtml(POS.msg.taxable)}</span>`;
        (l.warn || []).forEach(w => h += ` <span class="badge text-bg-danger bg-opacity-75" title="${escapeHtml(l.warnings || '')}"><i class="bi bi-exclamation-triangle"></i> ${escapeHtml(POS.warn[w] || w)}</span>`);
        if (l.uses) h += ` <i class="bi bi-info-circle text-primary" title="${escapeHtml(l.uses)}"></i>`;
        if (soon(l.expiry)) h += `<br><small class="text-warning-emphasis"><i class="bi bi-hourglass-split"></i> ${escapeHtml(POS.msg.expiresSoon.replace('{0}', l.expiry))}</small>`;
        return h;
    }

    function render() {
        tbody.innerHTML = cart.map((l, i) => `
            <tr>
              <td class="text-muted">${i + 1}</td>
              <td><div class="fw-semibold">${escapeHtml(l.name)}${badges(l)}</div>
                  <small class="text-muted" dir="ltr">${escapeHtml(l.sci || l.barcode || '')}</small>
                  ${neededFor(l.id) > l.sellable ? `<br><small class="text-danger"><i class="bi bi-exclamation-triangle"></i> ${escapeHtml(POS.msg.lowStock)} (${escapeHtml(l.stockText)})</small>` : ''}
                  ${l.sci ? `<button type="button" class="btn btn-link btn-sm p-0 ms-1" data-i="${i}" data-a="subs">${escapeHtml(POS.msg.subs)}</button>` : ''}</td>
              <td>${l.units.length > 1
                    ? `<select class="form-select form-select-sm" style="width:95px" data-i="${i}" data-f="unit">
                         ${l.units.map(u => `<option value="${u.code}" ${u.code === l.unit ? 'selected' : ''}>${escapeHtml(u.name)}</option>`).join('')}
                       </select>`
                    : `<span class="small">${escapeHtml(l.units[0].name)}</span>`}</td>
              <td><input type="number" step="0.01" min="0" class="form-control form-control-sm" style="width:90px" data-i="${i}" data-f="price" value="${l.price}"></td>
              <td>
                <div class="input-group input-group-sm" style="width:120px">
                  <button class="btn btn-outline-secondary" type="button" data-i="${i}" data-a="dec">−</button>
                  <input type="number" step="1" min="1" class="form-control text-center" data-i="${i}" data-f="qty" value="${l.qty}">
                  <button class="btn btn-outline-secondary" type="button" data-i="${i}" data-a="inc">+</button>
                </div>
              </td>
              <td>
                <div class="input-group input-group-sm" style="width:115px">
                  <input type="number" step="0.01" min="0" class="form-control" data-i="${i}" data-f="discount" value="${l.discount}">
                  <button class="btn ${l.discPct ? 'btn-warning' : 'btn-outline-secondary'}" type="button" data-i="${i}" data-a="disctype"
                          title="${escapeHtml(POS.msg.toggle || '')}">${l.discPct ? '%' : escapeHtml(POS.currency)}</button>
                </div>
              </td>
              <td class="num fw-semibold">${money(lineTotal(l))}</td>
              <td><button class="btn btn-sm btn-link text-danger" type="button" data-i="${i}" data-a="del"><i class="bi bi-x-lg"></i></button></td>
            </tr>`).join('');
        $('emptyCart').style.display = cart.length ? 'none' : '';
        updateTotals();
    }

    function updateTotals() {
        const t = totals();
        $('subtotal').textContent = money(t.subtotal);
        if ($('tax')) $('tax').textContent = money(t.tax);
        $('total').textContent = money(t.total);
        const paid = $('paid').value === '' ? t.total : num($('paid').value);
        $('change').textContent = money(Math.max(0, paid - t.total));
        // Refresh line totals without re-rendering inputs (keeps focus while typing)
        tbody.querySelectorAll('tr').forEach((tr, i) => {
            const l = cart[i];
            if (l) tr.children[6].textContent = money(lineTotal(l));
        });
    }

    function add(p, unitCode) {
        if (Number(p.sellableQty) <= 0) {
            beep(false);
            if (p.scientificName && confirm(POS.msg.outOfStock)) showSubstitutes(p.id, p.name);
            else toast(POS.msg.lowStock + ': ' + p.name, 'danger');
            return;
        }
        const unit = p.units.find(u => u.code === unitCode) || p.units[0];
        const existing = cart.find(l => l.id === p.id && l.unit === unit.code);
        if (existing) existing.qty += 1;
        else cart.push({
            id: p.id, name: p.name, sci: [p.scientificName, p.strength].filter(Boolean).join(' '), barcode: p.barcode,
            units: p.units, unit: unit.code, factor: unit.factor, price: Number(unit.price), qty: 1, discount: 0, discPct: false,
            sellable: Number(p.sellableQty), stockText: p.stockText, taxable: p.taxable, rx: p.prescriptionRequired,
            fridge: p.needsFridge, expiry: p.nearestExpiry, warn: p.warnFlags, uses: p.uses, warnings: p.warnings
        });
        if (p.prescriptionRequired) toast(POS.msg.rx + ': ' + p.name, 'warning');
        beep(true);
        render();
    }

    tbody.addEventListener('input', e => {
        const f = e.target.dataset.f;
        if (!f || f === 'unit') return;
        const l = cart[+e.target.dataset.i];
        l[f] = f === 'qty' ? Math.max(1, Math.round(num(e.target.value))) : Math.max(0, num(e.target.value));
        updateTotals();
    });
    tbody.addEventListener('change', e => {
        const f = e.target.dataset.f;
        const l = cart[+e.target.dataset.i];
        if (f === 'unit') {
            const u = l.units.find(x => x.code === e.target.value);
            l.unit = u.code; l.factor = u.factor; l.price = Number(u.price);
        }
        if (f === 'qty' || f === 'unit') render();
    });
    tbody.addEventListener('click', e => {
        const b = e.target.closest('[data-a]');
        if (!b) return;
        const i = +b.dataset.i;
        if (b.dataset.a === 'inc') cart[i].qty += 1;
        if (b.dataset.a === 'dec') cart[i].qty = Math.max(1, cart[i].qty - 1);
        if (b.dataset.a === 'del') cart.splice(i, 1);
        if (b.dataset.a === 'disctype') { cart[i].discPct = !cart[i].discPct; render(); return; }
        if (b.dataset.a === 'subs') { showSubstitutes(cart[i].id, cart[i].name); return; }
        render();
        scan.focus();
    });
    ['discount', 'paid'].forEach(id => $(id).addEventListener('input', updateTotals));

    // ---------- substitutes (same active ingredient, in stock) ----------
    async function showSubstitutes(id, name) {
        const box = $('subsList');
        $('subsFor').textContent = name;
        box.innerHTML = '<div class="text-center text-muted py-3"><div class="spinner-border spinner-border-sm"></div></div>';
        bootstrap.Modal.getOrCreateInstance($('subsModal')).show();
        try {
            const list = await api('/api/products/' + id + '/substitutes');
            box.innerHTML = list.length ? list.map((p, k) => `
                <button type="button" class="list-group-item list-group-item-action d-flex justify-content-between" data-k="${k}">
                  <span><strong>${escapeHtml(p.name)}</strong> <small class="text-muted" dir="ltr">${escapeHtml([p.strength, p.dosageForm].filter(Boolean).join(' - '))}</small>
                        <br><small class="text-muted">${escapeHtml(p.stockText)}</small></span>
                  <span class="num fw-semibold">${money(p.salePrice)}</span>
                </button>`).join('')
                : `<div class="text-center text-muted py-3">${escapeHtml(POS.msg.noSubs)}</div>`;
            box.querySelectorAll('[data-k]').forEach(b => b.addEventListener('click', () => {
                add(list[+b.dataset.k]);
                bootstrap.Modal.getInstance($('subsModal')).hide();
            }));
        } catch (e) { box.innerHTML = `<div class="text-danger p-3">${escapeHtml(e.message)}</div>`; }
    }

    productPicker(scan, p => add(p), POS.msg.notFound);
    const customerPicker = partyPicker($('customerSearch'), $('customer'), '/api/customers/search');
    $('customerClear').addEventListener('click', () => { customerPicker.clear(); scan.focus(); });

    function clear() {
        cart.length = 0;
        $('discount').value = 0;
        invDisc.reset();
        $('paid').value = '';
        $('notes').value = '';
        customerPicker.clear();
        $('m-cash').checked = true;
        render();
        scan.focus();
    }

    $('clearBtn').addEventListener('click', () => { if (!cart.length || confirm(POS.msg.confirmClear)) clear(); });

    let saving = false;
    async function pay() {
        if (saving) return;
        if (!cart.length) { toast(POS.msg.empty, 'danger'); beep(false); return; }
        if (!percentOk()) { toast(POS.msg.invalidPercent, 'danger'); beep(false); return; }
        const t = totals();
        const customerId = $('customer').value || null;
        const m = method();
        const paidInput = $('paid').value === '' ? null : num($('paid').value);
        if ((m === 'CREDIT' || (paidInput !== null && paidInput < t.total)) && !customerId) {
            toast(POS.msg.customerRequired, 'danger'); beep(false); return;
        }
        saving = true;
        $('payBtn').disabled = true;
        try {
            const res = await api('/api/sales', {
                method: 'POST',
                body: {
                    customerId,
                    items: cart.map(l => ({ productId: l.id, qty: l.qty, sellUnit: l.unit, unitPrice: l.price,
                                            discount: lineDisc(l), discountPercent: l.discPct ? num(l.discount) : null })),
                    discount: t.discount,
                    discountPercent: invDisc.isPct() ? num($('discount').value) : null,
                    paymentMethod: m,
                    paid: m === 'CREDIT' && paidInput === null ? 0 : paidInput,
                    notes: $('notes').value || null
                }
            });
            $('doneNo').textContent = res.invoiceNo;
            $('doneChange').textContent = money(res.change);
            $('printThermal').href = `/sales/${res.id}/print?format=thermal&auto=true`;
            $('printA4').href = `/sales/${res.id}/print?format=a4&auto=true`;
            if ($('autoPrint').checked) window.open(`/sales/${res.id}/print?format=thermal&auto=true`, '_blank', 'width=420,height=700');
            clear();
            bootstrap.Modal.getOrCreateInstance($('doneModal')).show();
        } catch (e) {
            toast(e.message, 'danger');
            beep(false);
        } finally {
            saving = false;
            $('payBtn').disabled = false;
        }
    }

    $('payBtn').addEventListener('click', pay);
    $('doneModal').addEventListener('hidden.bs.modal', () => scan.focus());
    $('doneModal').addEventListener('keydown', e => {
        if (e.key === 'Enter') { e.preventDefault(); bootstrap.Modal.getInstance($('doneModal')).hide(); }
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'F9') { e.preventDefault(); pay(); }
        if (e.key === 'F2') { e.preventDefault(); scan.focus(); }
    });

    render();
})();
