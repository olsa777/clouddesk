'use strict';

const statusNames = { NEW: 'Новая', APPROVED: 'Одобрена', PROVISIONED: 'Выделено', REJECTED: 'Отклонена' };
const environments = { DEVELOPMENT: 'DEV', STAGING: 'STAGING', PRODUCTION: 'PROD' };
const state = { requests: [], filter: '', query: '', loading: false, mutating: false };
const $ = (selector) => document.querySelector(selector);
const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const formatDate = (value) => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value));

async function api(path, options = {}) {
  let response;
  try { response = await fetch(path, { ...options, headers: { 'Content-Type': 'application/json', ...options.headers } }); }
  catch { throw new Error('Нет связи с сервером. Проверьте, что приложение запущено, и повторите попытку.'); }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const fields = Object.entries(body.fieldErrors || {}).map(([key, value]) => `${key}: ${value}`).join('\n');
    throw new Error([body.message || `Сервер вернул ошибку ${response.status}`, fields].filter(Boolean).join('\n'));
  }
  return body;
}

function notify(message, error = false) {
  $('#notice').textContent = message;
  $('#notice').className = `notice${error ? ' error' : ''}`;
  $('#notice').hidden = false;
}

function renderStats(stats) {
  for (const [id, value] of Object.entries({ 'stat-total': stats.total, 'stat-pending': stats.pending, 'stat-provisioned': stats.provisioned, 'stat-cpu': stats.totalCpu, 'stat-memory': stats.totalMemoryGb, 'stat-storage': stats.totalStorageGb, 'nav-count': stats.total })) {
    document.getElementById(id).textContent = value;
  }
}

function actionsFor(item) {
  const id = Number(item.id);
  const disabled = state.mutating ? 'disabled' : '';
  if (item.status === 'NEW') return `<button class="action-button" data-action="APPROVED" data-id="${id}" ${disabled}>Одобрить</button><button class="action-button reject" data-action="REJECTED" data-id="${id}" ${disabled}>Отклонить</button>`;
  if (item.status === 'APPROVED') return `<button class="action-button" data-action="PROVISIONED" data-id="${id}" ${disabled}>Отметить выделение</button>`;
  return '<span class="completed">Завершено</span>';
}

function renderRequests() {
  const items = state.requests.filter((item) => (!state.filter || item.status === state.filter) && item.title.toLocaleLowerCase('ru').includes(state.query));
  $('#result-count').textContent = items.length;
  $('#shown-count').textContent = `Показано ${items.length} из ${state.requests.length}`;
  if (!items.length) {
    const filtered = Boolean(state.filter || state.query);
    $('#request-list').innerHTML = `<div class="empty"><span class="empty-symbol" aria-hidden="true">▤</span><h3>${filtered ? 'Заявки не найдены' : 'Первая заявка — начало работы'}</h3><p>${filtered ? 'Измените статус или поисковый запрос.' : 'Укажите задачу и необходимые ресурсы. Новая заявка появится здесь.'}</p>${filtered ? '<button class="button secondary" data-clear-filters>Сбросить фильтры</button>' : '<button class="button secondary" data-create>Создать первую заявку</button>'}</div>`;
    return;
  }
  $('#request-list').innerHTML = items.map((item) => `<article class="request-row" aria-label="${escapeHtml(item.title)}"><div><button class="request-title" data-detail="${Number(item.id)}">${escapeHtml(item.title)}</button><div class="request-meta"><span class="environment">${environments[item.environment] || escapeHtml(item.environment)}</span><span>#${Number(item.id)} · ${escapeHtml(formatDate(item.createdAt))}</span></div></div><div class="resources"><strong>${escapeHtml(item.cpu)} vCPU</strong> <span class="separator">/</span> ${escapeHtml(item.memoryGb)} ГБ RAM<small>${escapeHtml(item.storageGb)} ГБ диска</small></div><div><span class="badge status-${escapeHtml(item.status)}">${statusNames[item.status] || escapeHtml(item.status)}</span></div><div class="actions">${actionsFor(item)}</div></article>`).join('');
}

// Queue refreshes so a read started before a mutation cannot hide its result.
let loadQueue = Promise.resolve();
function loadData() {
  loadQueue = loadQueue.then(fetchAndRender);
  return loadQueue;
}

async function fetchAndRender() {
  state.loading = true;
  $('#refresh').disabled = true;
  try {
    const [requests, stats] = await Promise.all([api('/api/requests'), api('/api/stats')]);
    state.requests = requests;
    renderStats(stats);
    renderRequests();
  } catch (error) {
    notify(error.message, true);
    if (!state.requests.length) $('#request-list').innerHTML = '<div class="empty"><h3>Не удалось загрузить заявки</h3><p>Проверьте соединение и нажмите «Обновить».</p></div>';
  } finally {
    state.loading = false;
    $('#refresh').disabled = false;
  }
}

function openCreate() {
  $('#create-form').reset();
  $('#form-error').hidden = true;
  $('#create-dialog').showModal();
}

function showDetail(id) {
  const item = state.requests.find((request) => Number(request.id) === id);
  if (!item) return;
  $('#detail-title').textContent = item.title;
  $('#detail-number').textContent = `ЗАЯВКА #${item.id}`;
  $('#detail-content').innerHTML = `<span class="badge status-${escapeHtml(item.status)}">${statusNames[item.status]}</span><p class="detail-description">${escapeHtml(item.description)}</p><dl class="detail-grid"><div><dt>Среда</dt><dd>${escapeHtml(item.environment)}</dd></div><div><dt>Конфигурация</dt><dd>${escapeHtml(item.cpu)} vCPU · ${escapeHtml(item.memoryGb)} ГБ RAM · ${escapeHtml(item.storageGb)} ГБ</dd></div><div><dt>Создана</dt><dd>${escapeHtml(formatDate(item.createdAt))}</dd></div><div><dt>Обновлена</dt><dd>${escapeHtml(formatDate(item.updatedAt))}</dd></div></dl><p class="detail-note">Учебная симуляция. Реальные облачные ресурсы не создаются.</p>`;
  $('#detail-dialog').showModal();
}

async function updateStatus(id, status) {
  if (state.mutating) return;
  state.mutating = true;
  renderRequests();
  try {
    await api(`/api/requests/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) });
    notify(`Заявка #${id}: статус изменён на «${statusNames[status]}».`);
    await loadData();
  } catch (error) { notify(error.message, true); }
  finally { state.mutating = false; renderRequests(); }
}

$('#new-request').addEventListener('click', openCreate);
$('#refresh').addEventListener('click', () => { $('#notice').hidden = true; loadData(); });
$('#search').addEventListener('input', (event) => { state.query = event.target.value.trim().toLocaleLowerCase('ru'); renderRequests(); });
document.querySelectorAll('[data-status]').forEach((button) => button.addEventListener('click', () => {
  state.filter = button.dataset.status;
  document.querySelectorAll('[data-status]').forEach((filter) => { const selected = filter === button; filter.classList.toggle('selected', selected); filter.setAttribute('aria-pressed', String(selected)); });
  renderRequests();
}));
document.querySelectorAll('[data-close]').forEach((button) => button.addEventListener('click', () => document.getElementById(button.dataset.close).close()));
$('#request-list').addEventListener('click', (event) => {
  const button = event.target.closest('button');
  if (!button) return;
  if (button.hasAttribute('data-create')) openCreate();
  if (button.hasAttribute('data-detail')) showDetail(Number(button.dataset.detail));
  if (button.hasAttribute('data-action')) updateStatus(Number(button.dataset.id), button.dataset.action);
  if (button.hasAttribute('data-clear-filters')) { $('#search').value = ''; state.query = ''; document.querySelector('[data-status=""]').click(); }
});
$('#fill-example').addEventListener('click', () => {
  const form = $('#create-form');
  for (const [name, value] of Object.entries({ title: 'Сервер для учебного API', description: 'Тестовый сервер для REST API и базы данных учебного проекта. Нужен команде для проверки новых функций.', environment: 'DEVELOPMENT', cpu: 2, memoryGb: 4, storageGb: 40 })) form.elements[name].value = value;
});
$('#create-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const submit = $('#submit-request');
  if (submit.disabled) return;
  const form = new FormData(event.currentTarget);
  const body = { title: String(form.get('title')).trim(), description: String(form.get('description')).trim(), environment: form.get('environment'), cpu: Number(form.get('cpu')), memoryGb: Number(form.get('memoryGb')), storageGb: Number(form.get('storageGb')) };
  if (!body.title || !body.description) { $('#form-error').textContent = 'Заполните название и описание: они не могут состоять только из пробелов.'; $('#form-error').hidden = false; return; }
  submit.disabled = true;
  submit.textContent = 'Сохраняем…';
  $('#form-error').hidden = true;
  try {
    const request = await api('/api/requests', { method: 'POST', body: JSON.stringify(body) });
    $('#create-dialog').close();
    state.filter = '';
    state.query = '';
    $('#search').value = '';
    document.querySelector('[data-status=""]').click();
    notify(`Заявка #${request.id} создана и ожидает решения.`);
    await loadData();
  } catch (error) { $('#form-error').textContent = error.message; $('#form-error').hidden = false; }
  finally { submit.disabled = false; submit.textContent = 'Создать заявку'; }
});

loadData();
