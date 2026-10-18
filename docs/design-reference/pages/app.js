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

(function hillside() {
  const fieldsBox = document.querySelector('[data-fields]');
  if (!fieldsBox) return;

  // Per field: how a day counts, the cap, your days shown up (of 7), rank, people opted in,
  // the histogram of people by days shown up (index = days 0..7), and the neighbours' detail.
  const F = {
    general: { label: 'All fields', unit: 'days with 3 or more routines tended', cap: 'A day counts once you tend 3 routines. More does not add.', you: 5, rank: 96, of: 212, hist: [4, 9, 14, 22, 38, 51, 44, 30], detail: ['7 days', '6 days', '6 days', '5 days', '5 days', '5 days', '4 days'] },
    reading: { label: 'Reading', unit: 'days with a counted reader’s note', cap: 'One counted note a day. Copied notes never count.', you: 4, rank: 38, of: 131, hist: [6, 10, 15, 24, 27, 22, 16, 11], detail: ['ch. 3 to 5', 'ch. 9 to 11', 'ch. 1 to 3', 'ch. 13 to 14', 'ch. 6 to 7', 'ch. 2 to 3', 'ch. 4'] },
    movement: { label: 'Movement', unit: 'days with 30 active minutes', cap: 'Capped at 90 minutes a day. Rest days are expected.', you: 5, rank: 61, of: 188, hist: [5, 8, 12, 25, 34, 41, 36, 27], detail: ['walks, cycling', 'lifting', 'runs', 'lifting, walks', 'swims', 'walks', 'yoga'] },
    deep: { label: 'Deep work', unit: 'days with 60 focused minutes', cap: 'Counted up to 4 hours a day. Past that it is not a better day.', you: 4, rank: 47, of: 119, hist: [4, 7, 12, 19, 26, 24, 17, 10], detail: ['WakaTime', 'ActivityWatch', 'WakaTime', 'WakaTime', 'RescueTime', 'WakaTime', 'ActivityWatch'] },
    learning: { label: 'Learning', unit: 'days with a learning note', cap: 'One note a day, two lines is enough.', you: 5, rank: 22, of: 97, hist: [3, 6, 9, 14, 18, 21, 15, 11], detail: ['3 topics', '2 topics', '4 topics', '2 topics', '1 topic', '3 topics', '2 topics'] },
    sleep: { label: 'Sleep', unit: 'nights in bed by your own bedtime', cap: 'Measured against the bedtime you set, not anyone else’s.', you: 3, rank: 102, of: 164, hist: [9, 14, 21, 30, 33, 27, 19, 11], detail: ['7 nights', '6 nights', '5 nights', '3 nights', '3 nights', '3 nights', '2 nights'] },
    writing: { label: 'Writing', unit: 'days with a journal or article commit', cap: 'One commit a day counts. Word count is never ranked.', you: 5, rank: 14, of: 58, hist: [2, 4, 6, 8, 9, 12, 10, 7], detail: ['journal', 'journal, article', 'journal', 'journal', 'article', 'journal', 'journal'] }
  };
  const ORDER = ['general', 'reading', 'movement', 'deep', 'learning', 'sleep', 'writing'];
  const NAMES = ['Still Brook 1187', 'Amber Ridge 0093', 'Slow Fern 7730', 'Quiet Cedar 0412', 'Low Hill 2251', 'Patient Reed 5906', 'Early Juniper 3348'];
  const state = { field: 'general', month: false };

  const el = (tag, cls, text) => { const n = document.createElement(tag); if (cls) n.className = cls; if (text != null) n.textContent = text; return n; };
  const set = (key, value) => { const n = document.querySelector('[data-h="' + key + '"]'); if (n) n.textContent = value; };

  function render() {
    const c = F[state.field];
    const m = state.month;

    // Field segmented control.
    fieldsBox.replaceChildren(...ORDER.map((k) => {
      const b = el('button', k === state.field ? 'on' : '', F[k].label);
      b.setAttribute('aria-pressed', String(k === state.field));
      b.addEventListener('click', () => { state.field = k; render(); });
      return b;
    }));

    // Window segmented control.
    document.querySelectorAll('[data-window]').forEach((b) => {
      const on = (b.dataset.window === '28') === m;
      b.classList.toggle('on', on);
      b.setAttribute('aria-pressed', String(on));
    });

    // Your band.
    const pct = Math.round((1 - c.rank / c.of) * 100);
    set('label', c.label);
    set('window', m ? 'last 28 days' : 'last 7 days');
    set('band', pct >= 66 ? 'Near the front of the flock.' : pct >= 33 ? 'In the middle of the flock.' : 'Walking at the back, and still walking.');
    set('rankText', c.rank + ' of ' + c.of + ' on this field. Ahead of ' + pct + '% of people who chose it.');
    set('counts', 'You showed up on ' + (m ? c.you * 4 + ' of 28' : c.you + ' of 7') + ' ' + c.unit + '.');
    set('cap', c.cap);

    // Histogram: people by days shown up, your bar in amber.
    const hist = document.querySelector('[data-hist]');
    const max = Math.max.apply(null, c.hist);
    hist.setAttribute('aria-label', 'Distribution of people by days shown up; you are at ' + c.you + ' days');
    hist.replaceChildren(...c.hist.map((v, d) => {
      const col = el('div', 'hcol');
      const bar = el('div', d === c.you ? 'hbar you' : 'hbar');
      bar.style.height = Math.round((v / max) * 110) + 'px';
      col.append(bar, el('span', '', String(m ? d * 4 : d)));
      return col;
    }));

    // Three ahead, you, three behind.
    const days = [c.you + 2, c.you + 1, c.you + 1, c.you, c.you, c.you, c.you - 1].map((d) => Math.max(0, Math.min(7, d)));
    document.querySelector('[data-rows]').replaceChildren(...NAMES.map((n, i) => {
      const you = i === 3;
      const row = el('div', 'lrow' + (i === 0 ? ' first' : '') + (you ? ' you' : ''));
      const who = el('span', 'who', you ? n + ' (you)' : n);
      who.append(el('small', '', you ? 'Steady for 6 weeks' : i < 3 ? 'Ahead by a day' : 'Close behind'));
      const dots = el('span', 'dots');
      dots.setAttribute('role', 'img');
      dots.setAttribute('aria-label', (m ? days[i] * 4 + ' of 28 days' : days[i] + ' of 7 days'));
      for (let k = 0; k < 7; k++) dots.append(el('i', k < days[i] ? 'on' : ''));
      row.append(el('span', 'rank', String(c.rank - 3 + i)), who, dots,
        el('span', 'muted', (m ? days[i] * 4 + ' of 28' : days[i] + ' of 7') + ' · ' + c.detail[i]));
      return row;
    }));
  }

  document.querySelectorAll('[data-window]').forEach((b) => b.addEventListener('click', () => { state.month = b.dataset.window === '28'; render(); }));
  render();
})();
