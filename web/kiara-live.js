/*
 * kiara live layer
 * Turns the approved prototype (kiara-app.html) into the real app without
 * changing how it looks: real lessons from the 5000-word bank, spaced review,
 * quiz results, streak, daily goal, coins, badges, bookmarks, search,
 * reminders, settings and profile, all saved on the phone. The Android shell
 * (window.KiaraNative) adds speech, notifications, share, haptics and a backup
 * of the saved state. Hooks: window.KIARA (exported by the prototype) and the
 * KL.* callbacks the build patches into it (web/patches.json).
 */
(function () {
  'use strict';
  var K = window.KIARA;
  if (!K) return;
  var N = window.KiaraNative || null;
  var KL = window.KL = {};

  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function txt(el, v) { if (el) el.textContent = v; }
  function safe(fn) { try { fn(); } catch (e) { if (window.console) console.warn('kiara-live', e); } }
  var fa = K.fa;
  function faN(n) { return fa(String(Math.round(n)).replace(/\B(?=(\d{3})+(?!\d))/g, '٬')); }
  function lat(s) { return String(s || '').replace(/[۰-۹]/g, function (d) { return String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)); }); }

  var LV = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
  var IV = [1, 1, 2, 4, 7, 15, 30, 60];          // review interval (days) per box
  var LESSON = 5;                                 // words per lesson
  var KEY = 'kiara.v1';
  var DAYS_EN = ['SATURDAY', 'SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'];
  var WEEK_FA = ['ش', 'ی', 'د', 'س', 'چ', 'پ', 'ج'];
  var RATES = [0.65, 0.95, 1.35];
  var POS = {n: 'noun', v: 'verb', adj: 'adjective', adv: 'adverb', expr: 'expression', num: 'number',
    pron: 'pronoun', int: 'interjection', prep: 'preposition', det: 'determiner', conj: 'conjunction'};

  /* ── days (local calendar, Saturday-first week) ── */
  function today() { var n = new Date(); return Math.floor((n.getTime() - n.getTimezoneOffset() * 60000) / 86400000); }
  function pIdx(d) { return ((d + 4) % 7 + 1) % 7; }

  /* ── saved state ── */
  function defaults() {
    return {v: 1, lvl: 'A1', goal: 10, coins: 0, quizzes: 0, perfect: 0, night: 0, best: 0,
      name: '', phone: '', av: 0,
      rem: {on: true, t: '20:00', days: [1, 1, 1, 1, 1, 1, 0], smart: false},
      set: {sound: true, rate: 1, auto: true},
      my: null, bm: {}, w: {}, d: {}, m: {}};
  }
  function load() {
    var raw = null, s = null;
    try { raw = localStorage.getItem(KEY); } catch (e) {}
    if (!raw && N && N.loadBackup) { try { raw = N.loadBackup(); } catch (e) {} }
    try { s = raw ? JSON.parse(raw) : null; } catch (e) { s = null; }
    var d = defaults();
    if (!s || typeof s !== 'object') return d;
    Object.keys(d).forEach(function (k) { if (!(k in s) || s[k] === undefined) s[k] = d[k]; });
    ['rem', 'set'].forEach(function (k) {
      if (!s[k] || typeof s[k] !== 'object') s[k] = d[k];
      Object.keys(d[k]).forEach(function (j) { if (!(j in s[k])) s[k][j] = d[k][j]; });
    });
    if (LV.indexOf(s.lvl) < 0) s.lvl = 'A1';
    return s;
  }
  var ST = load();
  var saveT = 0;
  function flushSave() {
    clearTimeout(saveT);
    var j = JSON.stringify(ST);
    try { localStorage.setItem(KEY, j); } catch (e) {}
    if (N && N.saveBackup) { try { N.saveBackup(j); } catch (e) {} }
    if (window.KiaraSync) { try { window.KiaraSync.onStateSaved(j); } catch (e) {} }
  }
  function save() { clearTimeout(saveT); saveT = setTimeout(flushSave, 250); }
  var cache = null;
  function dirty() { cache = null; save(); }

  /* ── mistakes — every wrong answer lands here, one list (v0.8.0) ── */
  function addMistake(en) {
    if (!en) return;
    var m = ST.m[en] || (ST.m[en] = {n: 0, t: 0});
    m.n++; m.t = today();
  }
  function mistakeList() {
    return Object.keys(ST.m).sort(function (a, b) { return ST.m[b].t - ST.m[a].t || ST.m[b].n - ST.m[a].n; })
      .map(function (en) {
        var fa = (IX[en] && IX[en].e[2]) || (ST.w[en] && ST.w[en].f) || '';
        return [en, ST.m[en], fa];
      });
  }
  function mistakeCount() { return Object.keys(ST.m).length; }

  function day(d) { var k = String(d == null ? today() : d); return ST.d[k] || (ST.d[k] = {l: 0, r: 0, q: 0, s: 0}); }
  function dayGet(d) { return ST.d[String(d)] || null; }
  function active(d) { var x = dayGet(d); return !!x && (x.l + x.r + x.q) > 0; }
  function goalMet(d) { var x = dayGet(d); return !!x && (x.l + x.r) >= ST.goal; }
  function streak() { var d = today(), n = 0; if (!active(d)) d--; while (active(d)) { n++; d--; } return n; }

  /* ── word bank ── */
  var IX = {};
  LV.forEach(function (lv) { (K.KBW[lv] || []).forEach(function (e) { if (!IX[e[0]]) IX[e[0]] = {e: e, lv: lv}; }); });
  function catName(lv, ci) { var c = K.KBC[lv] && K.KBC[lv][ci]; return c ? c[1] : ''; }
  // prototype card format: [word, meaning, phonetic slot, example slot, level]
  function card(e, lv) {
    var cn = catName(lv, e[3]);
    return [e[0], e[2], (POS[e[1]] || e[1] || '') + ' · ' + lv, 'سطح ' + lv + (cn ? ' · ' + cn : ''), lv];
  }
  function cardOf(en, fallbackFa) {
    var x = IX[en];
    if (x) return card(x.e, x.lv);
    var w = ST.w[en];
    return [en, fallbackFa || (w && w.f) || '', '', '', (w && w.l) || ST.lvl];
  }
  function nextLevel(lv) { var i = LV.indexOf(lv); return i >= 0 && i < LV.length - 1 ? LV[i + 1] : ''; }

  function stats() {
    if (cache) return cache;
    var t = today(), learned = 0, mastered = 0, due = 0, per = {};
    Object.keys(ST.w).forEach(function (en) {
      var w = ST.w[en];
      if (w.b >= 1) learned++;
      if (w.b >= 4) mastered++;
      if (w.d <= t) due++;
    });
    LV.forEach(function (lv) {
      var list = K.KBW[lv] || [], n = 0, seen = 0;
      list.forEach(function (e) { var w = ST.w[e[0]]; if (w) { seen++; if (w.b >= 1) n++; } });
      per[lv] = {learned: n, seen: seen, total: list.length};
    });
    cache = {learned: learned, mastered: mastered, due: due, per: per, streak: streak()};
    return cache;
  }
  function weekInfo() {
    var t = today(), ws = t - pIdx(t), vals = [], goalDays = 0, learnedWeek = 0;
    for (var i = 0; i < 7; i++) {
      var d = ws + i, x = dayGet(d);
      vals.push(x ? x.l + x.r + x.q : 0);
      if (x) learnedWeek += x.l;
      if (d <= t && goalMet(d)) goalDays++;
    }
    return {start: ws, ti: t - ws, vals: vals, goalDays: goalDays, learned: learnedWeek};
  }
  function badges() {
    var s = stats(), best = Math.max(ST.best || 0, s.streak);
    return {
      firstColl: s.learned >= LESSON, w100: s.learned >= 100, d7: best >= 7,
      a2: LV.indexOf(ST.lvl) >= 1, c1000: ST.coins >= 1000, week: weekInfo().goalDays >= 7,
      night: ST.night > 0, quiz1: ST.quizzes > 0, gift: best >= 30,
      star: ST.perfect > 0, chest: ST.coins >= 500
    };
  }

  /* ── feedback ── */
  KL.say = function (t) {
    var rate = RATES[ST.set.rate] || 0.95;
    if (N && N.speak) { try { N.speak(String(t), rate); return; } catch (e) {} }
    try {
      var u = new SpeechSynthesisUtterance(t);
      u.lang = 'en-US'; u.rate = rate;
      speechSynthesis.cancel(); speechSynthesis.speak(u);
    } catch (e) {}
  };
  function fx(kind) { if (ST.set.sound && N && N.haptic) { try { N.haptic(kind); } catch (e) {} } }
  function later(msg, ms) { setTimeout(function () { K.toast(msg); }, ms || 700); }

  function goalCheck() {
    var x = day();
    if (!x.g && (x.l + x.r) >= ST.goal) {
      x.g = 1; ST.coins += 12;
      later('هدف امروز کامل شد! ۱۲ سکه گرفتی', 900);
    }
  }
  function levelCheck() {
    cache = null;
    var p = stats().per[ST.lvl], nx = nextLevel(ST.lvl);
    if (p && p.seen >= p.total && nx) { ST.lvl = nx; later('آفرین! رسیدی به سطح ' + nx, 1600); }
  }

  /* ── learn (lessons from the word bank) ── */
  var lastAct = '';
  var sess = {known: 0, coins: 0, practice: false, words: null};
  function lessonPick(lv) {
    var out = [], list = K.KBW[lv] || [];
    for (var i = 0; i < list.length && out.length < LESSON; i++) if (!ST.w[list[i][0]]) out.push(card(list[i], lv));
    return out;
  }
  function lessonInfo(lv) {
    var p = stats().per[lv], list = K.KBW[lv] || [], first = null;
    for (var i = 0; i < list.length; i++) if (!ST.w[list[i][0]]) { first = list[i]; break; }
    return {no: Math.min(Math.floor(p.seen / LESSON) + 1, Math.ceil(p.total / LESSON)), of: Math.ceil(p.total / LESSON),
      title: first ? catName(lv, first[3]) : 'سطح ' + lv};
  }
  function setDots(sel, n) {
    var box = $(sel);
    if (!box) return;
    box.innerHTML = '';
    for (var i = 0; i < n; i++) box.appendChild(document.createElement('i'));
  }
  function startLearn(src) {
    var arr = [], title = '';
    if (src === 'topic') {
      var tp = K.topic() || {}, fresh = [], seen = [];
      (tp.words || []).forEach(function (w) { (ST.w[w[0]] ? seen : fresh).push(cardOf(w[0], w[1])); });
      arr = fresh.concat(seen).slice(0, LESSON);
      title = tp.title || '';
    } else {
      if (src === 'level') { var L = K.level(); if (L && LV.indexOf(L.code) >= 0) ST.lvl = L.code; }
      var lv = ST.lvl;
      arr = lessonPick(lv);
      while (!arr.length && nextLevel(lv)) { lv = nextLevel(lv); arr = lessonPick(lv); }
      ST.lvl = lv;
      cache = null;
      var li = lessonInfo(lv);
      title = li.title + ' · درس ' + fa(li.no);
    }
    if (!arr.length) { K.toast('همه‌ی واژه‌ها رو دیدی! حالا وقت مروره'); return false; }
    sess = {known: 0, coins: 0, practice: false, words: arr};
    setDots('#l-dots', arr.length);
    txt($('#scr-learn .shead b'), title);
    K.setLearn(arr);
    dirty();
    return true;
  }
  /* v0.8.0: reading never grants «learned» — the card is only marked as seen
     (b:0). The word becomes «learned» when its quiz is completed (onQuizDone). */
  KL.onLearn = function (w, idx, total) {
    if (!w) return;
    var act = lastAct || 'know', t = today(), en = w[0], cur = ST.w[en];
    lastAct = '';
    if (act === 'know') {
      if (!cur) {
        ST.w[en] = {b: 0, d: t + 1, l: w[4] || ST.lvl, f: w[1], t: t};
        day().l++; ST.coins += 3; sess.known++; sess.coins += 3;
        goalCheck();
      } else if (cur.b < 1) {
        cur.d = t + 1;
      }
    } else {
      /* «بلد نیستم» — a wrong signal: the word lands in the mistakes list */
      if (!cur) ST.w[en] = {b: 0, d: t + 1, l: w[4] || ST.lvl, f: w[1], t: t};
      else cur.d = t + 1;
      addMistake(en);
    }
    if (idx + 1 >= total) {
      txt($('#l-done p'), fa(sess.known) + ' واژه خوندی و ' + fa(sess.coins) + ' سکه گرفتی — حالا آزمونشو بزن');
      levelCheck();
    }
    dirty();
  };

  /* ── review (spaced repetition) ── */
  function startReview() {
    var t = today();
    var list = Object.keys(ST.w).filter(function (en) { return ST.w[en].d <= t; })
      .sort(function (a, b) { return ST.w[a].d - ST.w[b].d; });
    var practice = false;
    if (!list.length) {
      list = Object.keys(ST.w).filter(function (en) { return ST.w[en].b >= 1; });
      list.sort(function () { return Math.random() - 0.5; });
      practice = true;
    }
    if (!list.length) { K.toast('اول چند واژه یاد بگیر، بعد مرورشون می‌کنیم'); return false; }
    var arr = list.slice(0, 20).map(function (en) { return cardOf(en); });
    sess = {known: 0, coins: 0, practice: practice};
    K.setReview(arr, arr.length);
    if (practice) later('مرور امروز تمومه؛ این یه تمرین آزاده', 350);
    return true;
  }
  KL.onReview = function (w, rv, total) {
    if (!w) return;
    var act = lastAct || 'ok', t = today(), x = ST.w[w[0]];
    lastAct = '';
    if (x) {
      if (act === 'ok') {
        /* review keeps an un-learned word (b:0) at «learning»; only a completed
           quiz promotes it (v0.8.0) */
        if (!sess.practice) {
          if (x.b >= 1) { x.b = Math.min(x.b + 1, IV.length - 1); x.d = t + IV[x.b]; }
          else x.d = t + 1;
          ST.coins += 1;
        }
      } else {
        x.b = x.b >= 1 ? 1 : 0; x.d = t + 1;
        addMistake(w[0]);
      }
    }
    day().r++;
    goalCheck();
    if (rv + 1 >= total) {
      setTimeout(function () {
        var r = document.getElementById('scr-review');
        if (r && r.classList.contains('on')) K.go('home');
      }, 1500);
    }
    dirty();
  };

  /* ── quiz ── */
  var placement = false, qlog = [];
  KL.quizPool = function () {
    var ws = Object.keys(ST.w).filter(function (en) { return ST.w[en].b >= 1; })
      .map(function (en) { return [en, ST.w[en].f || (IX[en] ? IX[en].e[2] : '')]; });
    if (ws.length >= 4) return ws;
    return (K.KBW[ST.lvl] || []).slice(0, 60).map(function (e) { return [e[0], e[2]]; });
  };
  KL.quizSize = function (words) { return Math.min(placement ? 12 : 10, words.length); };
  KL.onAnswer = function (q, ok) {
    if (!q) return;
    qlog.push({w: q.w, ok: ok});
    var x = ST.w[q.w];
    if (x && !ok) { x.d = today(); x.b = Math.min(x.b, 1); }
    /* placement is a diagnostic — unseen bank words are not «mistakes» to review */
    if (!ok && !placement) addMistake(q.w);
    fx(ok ? 'ok' : 'bad');
    dirty();
  };
  /* v0.8.0 — the learning gate: only a COMPLETED quiz (hearts not exhausted)
     pays out «learned» for its correct answers and clears their mistakes;
     every wrong answer is already in the mistakes list (onAnswer). */
  KL.onQuizDone = function (okN, total, coins) {
    ST.coins += coins || 0;
    ST.quizzes++;
    if (total && okN === total) ST.perfect++;
    var h = new Date().getHours();
    if (h >= 22 || h < 4) ST.night++;
    day().q++;
    var hs = $$('#q-hearts img'), dim = $$('#q-hearts img.dim');
    var completed = hs.length ? dim.length < hs.length : true;
    var fresh = 0, t = today();
    if (placement) { finishPlacement(); refreshSoon(); qlog = []; dirty(); return; }
    qlog.forEach(function (r) {
      if (!completed || !r || !r.w || !r.ok) return;
      var x = ST.w[r.w];
      if (!x) x = ST.w[r.w] = {b: 0, d: t, l: (IX[r.w] && IX[r.w].lv) || ST.lvl,
        f: (IX[r.w] && IX[r.w].e[2]) || '', t: t};
      if (x.b < 1) { x.b = 1; fresh++; x.d = t + IV[1]; }
      else { x.b = Math.min(x.b + 1, IV.length - 1); x.d = t + IV[x.b]; }
      if (ST.m[r.w]) delete ST.m[r.w];
    });
    if (completed) {
      if (fresh > 0) later(fa(fresh) + ' واژه به یادگرفته‌ها اضافه شد', 500);
    } else {
      later('آزمون کامل نشد؛ واژه‌ای به یادگرفته‌ها اضافه نشد', 500);
    }
    refreshSoon();
    qlog = [];
    dirty();
  };

  /* ── mistakes review (v0.8.0) ── */
  var mquiz = false;
  function openMistakes() {
    var list = mistakeList();
    if (!list.length) { K.toast('هنوز اشتباهی ثبت نشده'); return; }
    var ws = list.map(function (x) { return [x[0], x[2] || '', 'A1']; });
    K.openTopic('اشتباه‌های من', IMG.xmark, fa(ws.length) + ' واژه که جوابشون رو اشتباه زدی؛ با آزمون ازشون بگذر', ws);
    setTimeout(function () {
      $$('#t-list .trowi').forEach(function (row) {
        var b = row.querySelector('.tw b');
        if (!b) return;
        var m = ST.m[b.textContent.trim()];
        if (!m) return;
        var chip = document.createElement('i');
        chip.className = 'mchip';
        chip.textContent = '×' + fa(m.n);
        var sb = row.querySelector('.tsb');
        if (sb) row.insertBefore(chip, sb); else row.appendChild(chip);
      });
    }, 0);
  }
  function startMistakesQuiz() {
    if (!mistakeCount()) { K.toast('هنوز اشتباهی ثبت نشده'); return; }
    mquiz = true;
    K.startQuiz(null, 'مرور اشتباهات');
  }
  function startLessonQuiz() {
    var ws = (sess.words || []).map(function (w) { return [w[0], w[1]]; });
    if (ws.length < 4) { K.toast('اول درس رو کامل ببین، بعد آزمونش'); return; }
    K.startQuiz(ws, 'آزمون درس');
  }
  /* called by the quiz-override patch inside startQuiz: builds the whole
     mistakes-only quiz (targets = mistakes, distractors = level bank) */
  KL.quizOverride = function () {
    if (!mquiz) return null;
    mquiz = false;
    var targets = mistakeList().slice(0, 10).map(function (x) { return [x[0], x[2]]; })
      .filter(function (t2) { return t2[1]; });
    if (!targets.length) return null;
    var seenM = {};
    targets.forEach(function (t2) { seenM[t2[1]] = 1; });
    var extra = [];
    (K.KBW[ST.lvl] || []).forEach(function (e) {
      if (extra.length < 24 && !ST.m[e[0]] && !seenM[e[2]]) { seenM[e[2]] = 1; extra.push([e[0], e[2]]); }
    });
    var pool = targets.concat(extra);
    return targets.map(function (t2) {
      var wrong = [];
      pool.forEach(function (o) {
        if (o[0] !== t2[0] && o[1] !== t2[1] && wrong.length < 3 && wrong.indexOf(o[1]) < 0) wrong.push(o[1]);
      });
      var opts = [[t2[1], 1]];
      wrong.forEach(function (x) { opts.push([x, 0]); });
      return {w: t2[0], opts: opts};
    });
  };
  function startPlacement() {
    var pool = [];
    LV.forEach(function (lv) {
      var list = (K.KBW[lv] || []).slice(0, 400);
      for (var i = 0; i < 2 && list.length; i++) {
        var e = list[Math.floor(Math.random() * list.length)];
        if (e && !pool.some(function (p) { return p[0] === e[0]; })) pool.push([e[0], e[2]]);
      }
    });
    placement = true; qlog = [];
    K.startQuiz(pool, 'تعیین سطح');
  }
  function finishPlacement() {
    placement = false;
    var ok = {};
    qlog.forEach(function (r) { var x = IX[r.w]; if (x && r.ok) ok[x.lv] = (ok[x.lv] || 0) + 1; });
    var start = 'C2';
    for (var i = 0; i < LV.length; i++) if ((ok[LV[i]] || 0) < 2) { start = LV[i]; break; }
    ST.lvl = start;
    later('سطح پیشنهادی تو: ' + start + ' — درس‌ها از همین سطح شروع می‌شن', 900);
  }

  /* ── bookmarks + saved words ── */
  KL.isBm = function (en) { return !!ST.bm[en]; };
  function toggleBm(en, f) {
    if (!en) return;
    if (ST.bm[en]) delete ST.bm[en];
    else ST.bm[en] = f || (IX[en] ? IX[en].e[2] : '');
    dirty(); refreshSoon();
  }
  function openSaved() {
    var ws = Object.keys(ST.bm).map(function (en) { return [en, ST.bm[en], IX[en] ? IX[en].lv : 'A1']; });
    if (!ws.length) { K.toast('هنوز واژه‌ای ذخیره نکردی؛ روی نشانک کارت بزن'); return; }
    var ic = $('#w-saved .vic');
    K.openTopic('واژه‌های ذخیره‌شده', ic ? ic.src : null, 'واژه‌هایی که نشان کردی', ws);
  }

  /* ── search (words tab) ── */
  function search(q) {
    q = (q || '').trim();
    if (!q) return;
    var ci = -1;
    K.KBCOLLS.forEach(function (n, i) { if (ci < 0 && n.indexOf(q) >= 0) ci = i; });
    if (ci >= 0 && K.DISCOVER[ci]) { K.openTopic(K.DISCOVER[ci].n, K.DISCOVER[ci].img, ''); return; }
    var ql = q.toLowerCase(), out = [], seen = {};
    LV.forEach(function (lv) {
      (K.KBW[lv] || []).forEach(function (e) {
        if (out.length >= 80 || seen[e[0]]) return;
        if (e[0].toLowerCase().indexOf(ql) === 0 || e[2].indexOf(q) >= 0) { seen[e[0]] = 1; out.push([e[0], e[2], lv]); }
      });
    });
    if (!out.length) { K.toast('چیزی پیدا نشد'); return; }
    K.openTopic('«' + q + '»', null, fa(out.length) + ' نتیجه برای جست‌وجوی تو', out);
  }
  var vin = $('#scr-words .vinput'), lastQ = '', lastQt = 0;
  if (vin) {
    vin.setAttribute('enterkeyhint', 'search');
    vin.addEventListener('keydown', function (e) {
      if (e.key !== 'Enter' && e.keyCode !== 13) return;
      e.preventDefault();
      var q = vin.value.trim();
      if (!q || (q === lastQ && Date.now() - lastQt < 800)) return;
      lastQ = q; lastQt = Date.now();
      vin.blur();
      search(q);
    });
  }

  /* ── cards: bookmark state + automatic meaning ── */
  var flipT = 0;
  KL.afterCard = function (kind, w, el) {
    clearTimeout(flipT);
    if (!w) return;
    var ex = $(kind === 'l' ? '#l-ex' : '#r-ex');
    if (ex) ex.dir = /[\u0600-\u06FF]/.test(ex.textContent) ? 'rtl' : 'ltr';
    if (kind === 'l') { var bm = $('#l-bm'); if (bm) bm.classList.toggle('on', !!ST.bm[w[0]]); }
    if (ST.set.auto) {
      flipT = setTimeout(function () { if (el && el.offsetParent !== null) el.classList.add('flip'); }, 2600);
    }
  };

  /* ── reminder / settings / profile screens ── */
  function remDays() { return $$('#scr-remind .mdays button'); }
  function applyRem() {
    var tg = $$('#scr-remind .mtoggle');
    if (tg[0]) tg[0].classList.toggle('on', !!ST.rem.on);
    if (tg[1]) tg[1].classList.toggle('on', !!ST.rem.smart);
    var tFa = fa(ST.rem.t);
    $$('#scr-remind .mtime').forEach(function (c) { c.classList.toggle('on', lat(c.getAttribute('data-t')) === ST.rem.t); });
    txt($('#m-rem-time'), tFa);
    txt($('#m-rem-sub'), ST.rem.on ? 'هر روز ساعت ' + tFa : 'یادآور خاموش است');
    remDays().forEach(function (b, i) { b.classList.toggle('on', !!ST.rem.days[i]); });
    txt($('#m-rem-days'), fa(ST.rem.days.filter(Boolean).length) + ' روز فعال است');
  }
  function syncRem(fromUser) {
    if (!N || !N.setReminder) return;
    var hm = String(ST.rem.t).split(':'), h = parseInt(hm[0], 10), m = parseInt(hm[1], 10);
    var days = DAYS_EN.filter(function (_, i) { return !!ST.rem.days[i]; }).join(',');
    try { N.setReminder(!!ST.rem.on, isNaN(h) ? 20 : h, isNaN(m) ? 0 : m, days, !!ST.rem.smart, !!fromUser); } catch (e) {}
  }
  function readRem() {
    var tg = $$('#scr-remind .mtoggle'), c = $('#scr-remind .mtime.on');
    ST.rem = {
      on: !!(tg[0] && tg[0].classList.contains('on')),
      smart: !!(tg[1] && tg[1].classList.contains('on')),
      t: c ? lat(c.getAttribute('data-t')) : ST.rem.t,
      days: remDays().map(function (b) { return b.classList.contains('on') ? 1 : 0; })
    };
    dirty(); syncRem(true); applyRem(); refreshSoon();
  }
  function applySet() {
    var tg = $$('#scr-settings .mtoggle');
    if (tg[0]) tg[0].classList.toggle('on', !!ST.set.sound);
    if (tg[1]) tg[1].classList.toggle('on', !!ST.set.auto);
    $$('#scr-settings .mseg button').forEach(function (b, i) { b.classList.toggle('on', i === ST.set.rate); });
    if (ST.name) txt($('#s-edit .val'), ST.name);
    txt($('#s-bmk .val'), fa(Object.keys(ST.bm).length) + ' واژه');
    if (N && N.appVersion) { try { txt($('#scr-settings .val[dir="ltr"]'), N.appVersion()); } catch (e) {} }
  }
  function readSet() {
    var tg = $$('#scr-settings .mtoggle');
    ST.set.sound = !!(tg[0] && tg[0].classList.contains('on'));
    ST.set.auto = !!(tg[1] && tg[1].classList.contains('on'));
    $$('#scr-settings .mseg button').forEach(function (b, i) { if (b.classList.contains('on')) ST.set.rate = i; });
    dirty();
  }
  var AV0 = {};
  function applyAvatar() {
    var opts = $$('#scr-edit .eopt');
    if (ST.av >= opts.length) ST.av = 0;
    opts.forEach(function (o, i) { o.classList.toggle('on', i === ST.av); });
    var pick = ST.av > 0 && opts[ST.av] ? opts[ST.av].querySelector('img') : null;
    Object.keys(AV0).forEach(function (sel) { var el = $(sel); if (el && AV0[sel]) el.src = pick ? pick.src : AV0[sel]; });
  }
  function applyEdit() {
    var n = $('#e-name');
    if (n && ST.name) n.value = ST.name;
    var p = $('#e-phone');
    if (p) { p.value = ST.phone || ''; p.placeholder = '۰۹۱۲ ۰۰۰ ۰۰۰۰'; }
  }
  function applyMy() {
    if (!ST.my) { ST.my = K.MYCOLLS.map(function (m) { return m.n; }); return; }
    K.MYCOLLS.length = 0;
    ST.my.forEach(function (n) {
      var d = K.DISCOVER.filter(function (x) { return x.n === n; })[0];
      if (d) K.MYCOLLS.push({n: d.n, c: d.c, img: d.img});
    });
    K.renderMy(); K.renderColls();
  }
  function applyGoal() {
    var p = $('#scr-goal .gpick[data-n="' + ST.goal + '"]');
    if (p) p.click(); // the prototype's own handler updates the estimate + subtitle
  }
  function share() {
    var msg = 'kiara — هر روز چند واژه‌ی انگلیسی، با فلش‌کارت و مرور هوشمند. یه قدم جلوتر، هر روز!';
    if (N && N.share) { try { N.share(msg); return; } catch (e) {} }
    K.toast('لینک kiara کپی شد');
  }

  /* ── taps: actions the prototype only faked ── */
  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t || !t.closest) return;
    function hit(s) { return t.closest(s); }
    function stop() { e.stopPropagation(); e.preventDefault(); }
    if (hit('#l-know')) lastAct = 'know';
    else if (hit('#l-later')) lastAct = 'later';
    else if (hit('#r-ok')) lastAct = 'ok';
    else if (hit('#r-tom')) lastAct = 'tom';
    else if (hit('#l-bm')) { var lw = K.learnWord(); if (lw) toggleBm(lw[0], lw[1]); }
    else if (hit('.tbm')) {
      var row = hit('.trowi');
      if (row) {
        var b = row.querySelector('.tw b'), s = row.querySelector('.tw span');
        toggleBm(b && b.textContent.trim(), s && s.textContent.trim());
      }
    }
    else if (hit('#w-saved') || hit('#s-bmk')) { stop(); openSaved(); }
    else if (hit('#s-share')) { stop(); share(); }
    else if (hit('#s-rate')) { stop(); if (N && N.rate) { try { N.rate(); } catch (x) {} } else K.toast('ممنون از حمایتت!'); }
    else if (hit('#s-logout')) { stop(); K.toast('فعلاً بدون حساب کار می‌کنی؛ پیشرفتت روی همین گوشی ذخیره می‌شه'); }
    else if (hit('#r-test')) {
      stop();
      if (N && N.testReminder) { try { N.testReminder(); } catch (x) {} }
      K.toast('یادآور آزمایشی فرستاده شد');
    }
    else if (hit('#h-learned')) { stop(); K.go('progress'); }
    else if (hit('#h-mistakes-pill')) { stop(); startMistakesQuiz(); }
    else if (hit('#h-mistakes')) { stop(); openMistakes(); }
    else if (hit('#l-toquiz')) { stop(); startLessonQuiz(); }
    else if (hit('#h-streak')) { stop(); K.go('goal'); }
    else if (hit('#scr-words .vguide')) { stop(); startPlacement(); }
    else if (hit('#scr-words .vadd')) { stop(); K.go('colls'); }
    else if (hit('#g-save')) {
      var gp = $('#scr-goal .gpick.on');
      if (gp) { ST.goal = parseInt(gp.getAttribute('data-n'), 10) || 10; dirty(); refreshSoon(); }
    }
    else if (hit('#e-save')) {
      var en = $('#e-name'), ph = $('#e-phone');
      if (en && en.value.trim()) ST.name = en.value.trim();
      if (ph) ST.phone = ph.value.trim();
      dirty(); refreshSoon();
    }
    else if (hit('#scr-edit .eopt')) {
      setTimeout(function () {
        $$('#scr-edit .eopt').forEach(function (o, i) { if (o.classList.contains('on')) ST.av = i; });
        dirty(); applyAvatar();
      }, 0);
    }
    else if (hit('.clcard')) { setTimeout(function () { ST.my = K.MYCOLLS.map(function (m) { return m.n; }); dirty(); }, 0); }
    else if (hit('#scr-remind')) { setTimeout(readRem, 0); }
    else if (hit('#scr-settings .mtoggle') || hit('#scr-settings .mseg button')) { setTimeout(readSet, 0); }
  }, true);

  /* ── navigation hooks ── */
  var ACTIVE = {learn: 1, review: 1, quiz: 1}, tScr = 'home', t0 = 0;
  function flushTime() {
    if (tScr && t0) {
      var dt = Math.min(Date.now() - t0, 10 * 60000);
      if (dt > 0) { day().s += Math.round(dt / 1000); dirty(); }
    }
    t0 = ACTIVE[tScr] ? Date.now() : 0;
  }
  KL.beforeGo = function (scr, push) {
    if (push === false) return undefined;
    var cur = $('.scr.on'), from = cur ? cur.id.replace('scr-', '') : '';
    /* leaving the quiz mid-way cancels a pending placement (v0.8.0 guard) */
    if (from === 'quiz' && scr !== 'quiz' && placement) { placement = false; qlog = []; }
    if (scr === 'learn') return startLearn(from === 'topic' ? 'topic' : from === 'level' ? 'level' : 'main') ? undefined : false;
    if (scr === 'review') return startReview() ? undefined : false;
    return undefined;
  };
  KL.afterGo = function (scr) {
    flushTime();
    tScr = scr;
    t0 = ACTIVE[scr] ? Date.now() : 0;
    refresh();
  };
  KL.levelDone = function (code) { return (stats().per[code] || {}).learned || 0; };
  KL.onResume = function () { refresh(); };
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) { flushTime(); flushSave(); }
    else { t0 = ACTIVE[tScr] ? Date.now() : 0; refresh(); }
  });

  /* ── paint real numbers into the approved screens ── */
  var IMG = {};
  function setFill(el, pct) { if (el) el.style.width = Math.max(0, Math.min(100, pct)) + '%'; }
  function lock(el, unlocked, inline) {
    if (!el) return;
    if (inline) { el.style.filter = unlocked ? '' : 'grayscale(1)'; el.style.opacity = unlocked ? '' : '0.45'; }
    else el.classList.toggle('lock', !unlocked);
  }
  var refT = 0;
  function refreshSoon() { clearTimeout(refT); refT = setTimeout(refresh, 0); }
  /* style for the ×N counter chip shown on the mistakes topic list */
  safe(function () {
    var st = document.createElement('style');
    st.textContent =
      '#scr-topic .trowi .mchip{flex:none;align-self:center;min-width:64px;height:52px;padding:0 16px;' +
      'border-radius:999px;background:rgba(226,74,54,.12);color:#C4402C;font-size:26px;font-weight:800;' +
      'display:flex;align-items:center;justify-content:center}' +
      'html.kdark #scr-topic .trowi .mchip{background:rgba(255,120,100,.14);color:#FF9A87}';
    document.head.appendChild(st);
  });
  function refresh() {
    cache = null;
    var s = stats(), t = today(), wk = weekInfo(), x = dayGet(t) || {l: 0, r: 0, q: 0, s: 0};
    if (s.streak > (ST.best || 0)) { ST.best = s.streak; save(); }
    var lv = ST.lvl, nx = nextLevel(lv), pl = s.per[lv], remain = Math.max(0, pl.total - pl.learned);
    var todayOn = active(t), work = x.l + x.r, B = badges();

    safe(function () { /* home */
      txt($('#h-streak b'), fa(s.streak));
      var pill = $('#h-streak'), hot = $('#h-daytoday'), days = $$('#scr-home .week .day');
      if (pill) pill.classList.toggle('off', !todayOn);
      if (hot && days.length === 7) {
        var tgt = days[wk.ti];
        if (hot.parentNode !== tgt) {
          var fresh = document.createElement('img');
          fresh.className = 'chip'; fresh.alt = ''; fresh.setAttribute('draggable', 'false');
          var old = tgt.querySelector('img.chip');
          hot.parentNode.replaceChild(fresh, hot);
          if (old) tgt.replaceChild(hot, old); else tgt.appendChild(hot);
        }
        hot.classList.toggle('off', !todayOn);
        days.forEach(function (dv, i) {
          if (i === wk.ti) return;
          var im = dv.querySelector('img.chip'), d = wk.start + i;
          if (im) im.src = d > t ? IMG.future : (active(d) ? IMG.done : IMG.missed);
        });
      }
      var li = lessonInfo(lv);
      txt($('#h-folder .fmeta h2'), li.title);
      txt($('#h-folder .lesson'), 'درس ' + fa(li.no) + ' از ' + fa(li.of));
      setFill($('#h-folder [data-fill]'), work / ST.goal * 100);
      txt($('#scr-home .review .rmeta b'), s.due ? 'وقت مرور رسیده' : 'مرورت به‌روزه');
      txt($('#scr-home .review .rmeta span'), s.due ? fa(s.due) + ' واژه منتظره' : 'فعلاً واژه‌ای منتظر نیست');
      /* learned total + mistakes rows (v0.8.0) */
      txt($('#h-learned-sub'), 'تا حالا ' + faN(s.learned) + ' واژه یاد گرفتی');
      txt($('#h-learned-pill'), faN(s.learned) + ' واژه');
      var mc = mistakeCount();
      txt($('#h-mistakes-sub'), mc ? fa(mc) + ' واژه اشتباه داشتی' : 'هنوز اشتباهی ثبت نشده');
      var mp = $('#h-mistakes-pill');
      if (mp) mp.style.display = mc ? '' : 'none';
    });
    safe(function () { /* words */
      txt($('#w-saved div span'), fa(Object.keys(ST.bm).length) + ' واژه');
      txt($('#scr-words .vpill b'), lv);
    });
    safe(function () { /* progress */
      txt($('#scr-progress .mlvl b'), lv);
      txt($('#scr-progress .phero h3 small'), lv);
      setFill($('#scr-progress .phero .fl'), pl.total ? pl.learned / pl.total * 100 : 0);
      var sp = $('#scr-progress .phero .t > span');
      if (sp) sp.innerHTML = fa(remain) + (nx ? ' واژه تا سطح <b dir="ltr">' + nx + '</b>' : ' واژه تا پایان سطح <b dir="ltr">C2</b>');
      var vals = [faN(ST.coins), faN(s.learned), fa(s.streak), fa(Math.round(x.s / 60))];
      $$('#scr-progress .tiles .tile b').forEach(function (b, i) { if (vals[i] != null) b.textContent = vals[i]; });
      var mx = Math.max.apply(null, wk.vals.concat([1]));
      $$('#scr-progress .bars .bcol').forEach(function (c, i) {
        var h = wk.vals[i] ? Math.round(52 + wk.vals[i] / mx * 133) : 30, isT = i === wk.ti;
        var bar = '<i class="bimg" style="height:' + h + 'px"></i>';
        c.classList.toggle('today', isT);
        c.innerHTML = (isT ? '<span class="bring">' + bar + '</span>' : bar) + '<span>' + WEEK_FA[i] + '</span>';
      });
      var pct = s.learned ? Math.round(s.mastered / s.learned * 100) : 0;
      txt($('#scr-progress .mcard .ring b'), fa(pct) + '٪');
      var left = Math.max(0, ST.goal * 7 - wk.learned);
      txt($('#scr-progress .mcard .mtxt p'), s.learned
        ? fa(s.mastered) + ' واژه از ' + fa(s.learned) + ' واژه کامل در ذهنت نشسته؛ ' +
          (left ? 'فقط ' + fa(left) + ' واژه تا هدف هفته مونده.' : 'هدف این هفته رو زدی!')
        : 'هنوز واژه‌ای یاد نگرفتی؛ از خانه اولین درس رو شروع کن.');
      var sh = $$('#scr-progress .ashelf .bdg');
      [B.week, B.star, B.chest].forEach(function (u, i) { lock(sh[i], u); });
    });
    safe(function () { /* profile + awards */
      if (ST.name) txt($('#scr-profile .pname'), ST.name);
      txt($('#scr-profile .plevel b'), 'سطح ' + lv);
      txt($('#scr-profile .stat-r b'), faN(s.learned));
      txt($('#scr-profile .stat-l b'), fa(s.streak));
      txt($('#scr-profile .row.h1 .val'), fa(ST.goal) + ' واژه');
      txt($('#scr-profile .row.h2 .val'), ST.rem.on ? fa(ST.rem.t) : 'خاموش');
      lock($('#scr-profile .trophy-b'), B.firstColl, true);
      lock($('#scr-profile .books-b'), B.w100, true);
      lock($('#scr-profile .flame-b'), B.d7, true);
      var aw = $$('#scr-awards .bdg');
      [B.firstColl, B.w100, B.d7, B.a2, B.c1000, B.week, B.night, B.quiz1, B.gift].forEach(function (u, i) { lock(aw[i], u); });
    });
    safe(function () { /* daily goal */
      txt($('#scr-goal .ghero .st'), fa(s.streak));
      setFill($('#scr-goal .ghero .pbar i'), work / ST.goal * 100);
      txt($('#scr-goal .ghero .t > span'), 'امروز ' + fa(Math.min(work, ST.goal)) + ' از ' + fa(ST.goal) + ' واژه‌ات کامل شده');
      $$('#scr-goal .gday img').forEach(function (im, i) {
        var d = wk.start + i;
        im.src = d > t ? IMG.future : goalMet(d) ? IMG.done : d === t ? IMG.today : IMG.missed;
        im.classList.toggle('ktoday', d === t); // dark theme keeps the gold today chip
      });
      txt($('#scr-goal .gweek .cap'), fa(wk.goalDays) + ' روز از ۷ روز این هفته کامل شده' +
        (goalMet(t) ? '' : '؛ با کامل کردن امروز می‌شود ' + fa(Math.min(7, wk.goalDays + 1)) + '!'));
    });
    safe(function () { /* edit + settings */
      txt($('#scr-edit .elev .lvl b'), 'سطح ' + lv);
      txt($('#scr-edit .elev .mtxt span'), nx ? fa(remain) + ' واژه تا سطح ' + nx + ' باقی مانده' : 'در بالاترین سطح هستی');
      applySet();
    });
  }

  /* ── start ── */
  safe(function () {
    var hd = $$('#scr-home .week .day > img.chip'), gd = $$('#scr-goal .gday img');
    IMG.done = hd[0] ? hd[0].src : '';
    IMG.future = hd.length ? hd[hd.length - 1].src : IMG.done;
    IMG.missed = gd[0] ? gd[0].src : IMG.future;
    IMG.today = gd[3] ? gd[3].src : IMG.done;
    /* 3D icons for the two new home rows (already in the page — no extra bytes) */
    IMG.star = ($('#l-done img.big') || {}).src || '';
    IMG.xmark = ($('#ic-mxs') || {}).src || '';
    var hic = $('#h-learned-ic'); if (hic) hic.src = IMG.star;
    var mic = $('#h-mistakes-ic'); if (mic) mic.src = IMG.xmark;
    var pava = $('#scr-profile .pava'), eava = $('#scr-edit .eava2');
    if (pava) AV0['#scr-profile .pava'] = pava.src;
    if (eava) AV0['#scr-edit .eava2'] = eava.src;
  });
  safe(applyMy);
  safe(applyGoal);
  safe(applyRem);
  safe(applySet);
  safe(applyEdit);
  safe(applyAvatar);
  safe(function () {
    var cur = $('.scr.on');
    if (cur && /scr-(learn|review|quiz)$/.test(cur.id)) K.go('home', false);
  });
  refresh();
  syncRem(false);
  flushSave();
})();
