// Behaviour for the Routine Machine design reference. Plain JS, no framework.
// Only two screens behave: Today (checking a task) and Hillside (field and window switches).
// Every number here is sample data from the design canvas, not real records.

(function today() {
  const rows = document.querySelectorAll('[data-task]');
  if (!rows.length) return;
  const total = rows.length;

  function refresh() {
    const done = document.querySelectorAll('[data-task] .check.done').length;
    document.querySelectorAll('[data-done-count]').forEach((el) => { el.textContent = String(done); });
    const bar = document.querySelector('[data-done-bar]');
    if (bar) bar.style.width = Math.round((done / total) * 100) + '%';
  }

  rows.forEach((row) => {
    const btn = row.querySelector('.check');
    const name = row.querySelector('.tname');
    const chip = row.querySelector('.chip');
    const auto = row.dataset.auto === 'true';
    btn.addEventListener('click', () => {
      const done = !btn.classList.contains('done');
      btn.classList.toggle('done', done);
      btn.setAttribute('aria-pressed', String(done));
      btn.setAttribute('aria-label', (done ? 'Mark not done: ' : 'Mark done: ') + name.textContent);
      name.classList.toggle('done', done);
      if (auto) { chip.classList.toggle('ok', done); chip.classList.toggle('tint', !done); }
      refresh();
    });
  });
  refresh();
})();

// Switches: any role="switch" button flips aria-checked.
document.querySelectorAll('[role="switch"]').forEach((sw) => {
  sw.addEventListener('click', () => {
    const on = sw.getAttribute('aria-checked') !== 'true';
    sw.setAttribute('aria-checked', String(on));
    sw.dispatchEvent(new CustomEvent('switched', { detail: on }));
  });
});

// Chip pickers: multi-select buttons inside .pick.
document.querySelectorAll('.pick button').forEach((b) => {
  b.addEventListener('click', () => b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true')));
});

// Segmented controls: single choice inside .seg (buttons only).
document.querySelectorAll('.seg').forEach((seg) => {
  const buttons = seg.querySelectorAll('button');
  buttons.forEach((b) => b.addEventListener('click', () => {
    buttons.forEach((x) => { x.classList.toggle('on', x === b); x.setAttribute('aria-pressed', String(x === b)); });
  }));
});

// Profile: the analogy follows the chosen interest; the master switch shows the plain version.
(function profile() {
  const texts = document.querySelectorAll('[data-analogy-text]');
  if (!texts.length) return;
  let current = 'football';
  const show = (key) => texts.forEach((t) => { t.hidden = t.dataset.analogyText !== key; });
  document.querySelectorAll('[data-analogy]').forEach((b) => b.addEventListener('click', () => { current = b.dataset.analogy; show(current); }));
  const master = document.querySelector('.analogy');
  const plain = document.createElement('p');
  plain.hidden = true;
  plain.style.margin = '0';
  plain.innerHTML = '<b>Plain version.</b> An idempotency key is a unique value sent with a request so that, if the same request is retried, the server returns the original result instead of performing the action again.';
  master.append(plain);
  const sw = document.querySelector('[data-switch]');
  sw.addEventListener('switched', (e) => {
    if (e.detail) { plain.hidden = true; show(current); } else { texts.forEach((t) => { t.hidden = true; }); plain.hidden = false; }
  });
})();

// Hillside: hide the board for a personal-only view; the scope switch changes the label.
(function hillside() {
  const sw = document.querySelector('[data-hide-hillside]');
  if (!sw) return;
  const board = document.querySelector('[data-board]');
  const personal = document.querySelector('[data-personal-only]');
  sw.addEventListener('switched', (e) => { board.hidden = !e.detail; personal.hidden = e.detail; });
  const label = document.querySelector('[data-scope-label]');
  document.querySelectorAll('[data-scope]').forEach((b) => b.addEventListener('click', () => {
    label.textContent = (b.dataset.scope === 'cohort' ? 'Cohort 14' : 'Open hillside') + ' · this season';
  }));
})();

// Granary: storing today's grain turns the button into a confirmation.
(function granary() {
  const b = document.querySelector('[data-store]');
  if (!b) return;
  b.addEventListener('click', () => { b.textContent = 'Stored today'; b.classList.add('gray'); b.disabled = true; });
})();
