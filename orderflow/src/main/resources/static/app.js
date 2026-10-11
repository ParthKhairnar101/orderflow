const state = { products: [], orders: [], cart: new Map() };
const $ = (selector) => document.querySelector(selector);
const money = (value) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value || 0));
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
const demoApi = '/api/v1/demo';
let demoExpiresAt = Date.now() + 20 * 60 * 1000;
let expiryRefreshStarted = false;

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers }
  });
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try { const problem = await response.json(); message = problem.detail || problem.title || message; } catch { /* response may have no body */ }
    throw new Error(message);
  }
  const result = response.status === 204 ? null : await response.json();
  if (path.startsWith(demoApi)) {
    demoExpiresAt = path.endsWith('/status') && result?.expiresAt
      ? Date.parse(result.expiresAt)
      : Date.now() + 20 * 60 * 1000;
    expiryRefreshStarted = false;
    renderExpiry();
  }
  return result;
}

async function loadWorkspace() {
  const [status, products, orders] = await Promise.all([
    api(`${demoApi}/status`), api(`${demoApi}/products`), api(`${demoApi}/orders`)
  ]);
  demoExpiresAt = Date.parse(status.expiresAt);
  state.products = products;
  state.orders = orders;
  render();
  renderExpiry();
}

function render() {
  const query = $('#product-search').value.trim().toLowerCase();
  const products = [...state.products].filter((p) => `${p.name} ${p.sku}`.toLowerCase().includes(query));
  $('#product-count').textContent = `${state.products.length} ${state.products.length === 1 ? 'product' : 'products'}`;
  $('#product-empty').hidden = products.length > 0;
  $('#product-rows').innerHTML = products.map((p) => {
    const stockState = p.availableQuantity === 0 ? 'out' : p.availableQuantity < 6 ? 'low' : '';
    return `<tr><td><div class="product-name"><span class="product-thumb">${escapeHtml(p.name.trim().charAt(0).toUpperCase() || '•')}</span><strong>${escapeHtml(p.name)}</strong></div></td><td><span class="sku">${escapeHtml(p.sku)}</span></td><td>${money(p.unitPrice)}</td><td><span class="stock ${stockState}"><i class="stock-dot"></i>${Number(p.availableQuantity).toLocaleString()} ${p.availableQuantity < 6 ? (p.availableQuantity ? 'low' : 'out') : 'in stock'}</span></td><td class="row-actions"><button class="text-button" data-restock="${p.id}">Adjust</button><button class="text-button" data-add="${p.id}" ${p.availableQuantity < 1 ? 'disabled' : ''}>＋ Order</button></td></tr>`;
  }).join('');

  const cart = [...state.cart.values()];
  $('#cart-items').innerHTML = cart.length ? cart.map(({ product, quantity }) => `<div class="cart-line"><div><strong>${escapeHtml(product.name)}</strong><small>${money(product.unitPrice)} each</small></div><div class="cart-line-end"><strong>${money(product.unitPrice * quantity)}</strong><div class="quantity-controls"><button data-quantity="${product.id}" data-change="-1" aria-label="Decrease quantity">−</button><span>${quantity}</span><button data-quantity="${product.id}" data-change="1" aria-label="Increase quantity" ${quantity >= product.availableQuantity ? 'disabled' : ''}>＋</button></div></div></div>`).join('') : '<div class="cart-empty">Choose a product from your catalog to get started.</div>';
  $('#cart-total').textContent = money(cart.reduce((sum, item) => sum + item.product.unitPrice * item.quantity, 0));
  $('#checkout-button').disabled = cart.length === 0;

  const orders = [...state.orders].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  $('#order-rows').innerHTML = orders.slice(0, 10).map((order) => `<tr><td><span class="order-id">#${escapeHtml(order.id.slice(0, 8).toUpperCase())}</span></td><td>${new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(new Date(order.createdAt))}</td><td>${order.items.reduce((sum, item) => sum + item.quantity, 0)} units · ${order.items.length} ${order.items.length === 1 ? 'item' : 'items'}</td><td><strong>${money(order.totalAmount)}</strong></td><td><span class="status-pill status-${escapeHtml(order.status.toLowerCase())}">${escapeHtml(order.status.toLowerCase())}</span></td><td class="row-actions">${order.status === 'CREATED' ? `<button class="text-button" data-cancel="${order.id}">Cancel</button>` : ''}</td></tr>`).join('');
  $('#order-empty').hidden = orders.length > 0;
  $('#orders-total').textContent = `${orders.length} ${orders.length === 1 ? 'order' : 'orders'}`;
  $('#nav-order-count').textContent = orders.filter((order) => order.status === 'CREATED').length;
  $('#metric-products').textContent = state.products.length.toLocaleString();
  $('#metric-units').textContent = state.products.reduce((sum, product) => sum + product.availableQuantity, 0).toLocaleString();
  const activeOrders = orders.filter((order) => order.status === 'CREATED');
  $('#metric-orders').textContent = activeOrders.length.toLocaleString();
  $('#metric-value').textContent = money(activeOrders.reduce((sum, order) => sum + Number(order.totalAmount), 0));
}

function toast(message, isError = false) {
  const element = $('#toast');
  element.textContent = message;
  element.className = `toast visible${isError ? ' error' : ''}`;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => element.classList.remove('visible'), 3200);
}

function renderExpiry() {
  const remaining = Math.max(0, demoExpiresAt - Date.now());
  const minutes = Math.floor(remaining / 60000).toString().padStart(2, '0');
  const seconds = Math.floor((remaining % 60000) / 1000).toString().padStart(2, '0');
  $('#demo-countdown').textContent = `${minutes}:${seconds}`;
  if (remaining === 0 && !expiryRefreshStarted) {
    expiryRefreshStarted = true;
    loadWorkspace().then(() => toast('The shared demo was reset after inactivity.')).catch(() => {
      toast('The demo is refreshing. Try the refresh button in a moment.', true);
    });
  }
}

setInterval(renderExpiry, 1000);

$('#refresh-button').addEventListener('click', async () => {
  try { await loadWorkspace(); toast('Workspace is up to date.'); }
  catch (error) { toast(error.message, true); }
});
$('#reset-button').addEventListener('click', async () => {
  if (!window.confirm('Reset the shared demo for everyone and restore the sample products?')) return;
  try {
    await api(`${demoApi}/reset`, { method: 'POST' });
    state.cart.clear();
    await loadWorkspace();
    toast('Demo reset. The sample catalog is ready.');
  } catch (error) { toast(error.message, true); }
});
$('#product-search').addEventListener('input', render);
$('#product-rows').addEventListener('click', async (event) => {
  const add = event.target.closest('[data-add]');
  if (add) {
    const product = state.products.find((item) => item.id === add.dataset.add);
    if (!product) return;
    const entry = state.cart.get(product.id);
    if (!entry) state.cart.set(product.id, { product, quantity: 1 });
    else if (entry.quantity < product.availableQuantity) entry.quantity += 1;
    render();
  }
  const restock = event.target.closest('[data-restock]');
  if (restock) {
    const product = state.products.find((item) => item.id === restock.dataset.restock);
    const quantity = window.prompt(`Set available stock for ${product.name}:`, product.availableQuantity);
    if (quantity === null) return;
    if (!/^\d+$/.test(quantity.trim())) { toast('Enter a whole number of zero or more.', true); return; }
    try {
      const updated = await api(`${demoApi}/products/${product.id}/inventory`, { method: 'PATCH', body: JSON.stringify({ availableQuantity: Number(quantity) }) });
      state.products = state.products.map((item) => item.id === updated.id ? updated : item);
      if (state.cart.has(updated.id)) state.cart.get(updated.id).product = updated;
      render(); toast('Inventory updated.');
    } catch (error) { toast(error.message, true); }
  }
});
$('#cart-items').addEventListener('click', (event) => {
  const control = event.target.closest('[data-quantity]');
  if (!control) return;
  const entry = state.cart.get(control.dataset.quantity);
  if (!entry) return;
  entry.quantity += Number(control.dataset.change);
  if (entry.quantity <= 0) state.cart.delete(control.dataset.quantity);
  render();
});
$('#checkout-button').addEventListener('click', async () => {
  const button = $('#checkout-button');
  button.disabled = true;
  const items = [...state.cart.values()].map(({ product, quantity }) => ({ productId: product.id, quantity }));
  const idempotencyKey = crypto.randomUUID();
  try {
    const order = await api(`${demoApi}/orders`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ items }) });
    state.cart.clear();
    state.orders.unshift(order);
    const freshProducts = await api(`${demoApi}/products`);
    state.products = freshProducts;
    render();
    toast(`Order ${order.id.slice(0, 8).toUpperCase()} created · inventory reserved.`);
  } catch (error) { toast(error.message, true); }
  finally { button.disabled = state.cart.size === 0; }
});
$('#order-rows').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-cancel]');
  if (!button || !window.confirm('Cancel this order and return its items to available inventory?')) return;
  button.disabled = true;
  try {
    const order = await api(`${demoApi}/orders/${button.dataset.cancel}/cancel`, { method: 'POST' });
    state.orders = state.orders.map((item) => item.id === order.id ? order : item);
    state.products = await api(`${demoApi}/products`);
    render();
    toast('Order cancelled · stock returned to inventory.');
  } catch (error) { toast(error.message, true); }
});
$('#add-product-button').addEventListener('click', () => { $('#product-form-error').textContent = ''; $('#product-form').reset(); $('#product-dialog').showModal(); });
$('#product-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const save = $('#save-product-button');
  const values = Object.fromEntries(new FormData(form).entries());
  const request = { ...values, unitPrice: Number(values.unitPrice), availableQuantity: Number(values.availableQuantity), description: values.description || null };
  save.disabled = true;
  try {
    const product = await api(`${demoApi}/products`, { method: 'POST', body: JSON.stringify(request) });
    state.products.unshift(product);
    $('#product-dialog').close();
    render();
    toast('Product added to your catalog.');
  } catch (error) { $('#product-form-error').textContent = error.message; }
  finally { save.disabled = false; }
});

loadWorkspace().catch((error) => {
  toast(`Could not connect to the demo API: ${error.message}`, true);
});
