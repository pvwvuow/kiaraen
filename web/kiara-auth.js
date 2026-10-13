/*
 * kiara auth + cloud sync + dark theme
 * Adds account screens (login / signup) in the approved design language,
 * a real Supabase backend connection (GoTrue REST, email+password), cloud
 * sync of the learner's saved state (app_state blob, last-writer-wins) and
 * the dark theme toggle in settings. Everything degrades gracefully:
 * without KIARA_CFG the app behaves exactly like v0.5.0 (guest only).
 * Config: window.KIARA_CFG = {url, anon} — injected by the build (the
 * publishable anon key only; security is enforced server-side by RLS).
 */
(function () {
  'use strict';
  var CFG = window.KIARA_CFG || null;
  var AUTH_OK = !!(CFG && CFG.url && CFG.anon && /^https:/.test(CFG.url));
  var SKEY = 'kiara.auth.v1', GKEY = 'kiara.guest', DKEY = 'kiara.dark',
      YKEY = 'kiara.sync.v1', STKEY = 'kiara.v1', AKEY = 'kiara.adopted';
  var N = window.KiaraNative || null;
  var K = null; // window.KIARA (prototype export) — captured on wire()
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }

  /* ── theme (applied immediately, before first paint) ── */
  (function earlyTheme() {
    var d = null;
    try { d = localStorage.getItem(DKEY); } catch (e) {}
    if (d !== '0' && d !== '1') {
      try { d = window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? '1' : '0'; }
      catch (e) { d = '0'; }
    }
    if (d === '1') document.documentElement.classList.add('kdark');
  })();
  function isDark() { return document.documentElement.classList.contains('kdark'); }
  function setDark(on) {
    try { localStorage.setItem(DKEY, on ? '1' : '0'); } catch (e) {}
    document.documentElement.classList.toggle('kdark', on);
    paintDark();
    pushTheme();
    try { localStorage.setItem('kiara.dark.set', '1'); } catch (e) {}
  }
  function paintDark() {
    var tg = document.querySelector('#k-dark-row .mtoggle');
    if (tg) tg.classList.toggle('on', isDark());
  }
  function pushTheme() {
    if (N && N.setStatusBarTheme) { try { N.setStatusBarTheme(isDark()); } catch (e) {} }
  }

  /* ── session ── */
  function getSess() {
    try { return JSON.parse(localStorage.getItem(SKEY) || 'null'); } catch (e) { return null; }
  }
  function setSess(s) {
    try { if (s) localStorage.setItem(SKEY, JSON.stringify(s)); else localStorage.removeItem(SKEY); } catch (e) {}
  }
  function isLogged() { return AUTH_OK && !!getSess(); }
  function isGuest() { try { return localStorage.getItem(GKEY) === '1'; } catch (e) { return false; } }
  function setGuest(v) {
    try { if (v) localStorage.setItem(GKEY, '1'); else localStorage.removeItem(GKEY); } catch (e) {}
  }
  function canLeave() { return !AUTH_OK || isLogged() || isGuest(); }

  /* ── supabase REST ── */
  function hdrs(at, extra) {
    var h = { 'apikey': CFG.anon, 'Content-Type': 'application/json' };
    if (at) h['Authorization'] = 'Bearer ' + at;
    if (extra) for (var k in extra) h[k] = extra[k];
    return h;
  }
  function api(path, opt, at) {
    opt = opt || {};
    opt.headers = hdrs(at, opt.headers);
    return fetch(CFG.url + path, opt).then(function (r) {
      return r.text().then(function (t) {
        var j = null;
        try { j = t ? JSON.parse(t) : null; } catch (e) {}
        if (!r.ok) {
          var msg = (j && (j.msg || j.error_description || j.error || j.message)) || ('HTTP ' + r.status);
          var err = new Error(msg); err.status = r.status; err.body = j; throw err;
        }
        return j;
      });
    });
  }
  function saveAuth(r) {
    if (!r || !r.access_token) return r;
    setSess({
      at: r.access_token, rt: r.refresh_token,
      exp: r.expires_at || (Math.floor(Date.now() / 1000) + (r.expires_in || 3600)),
      uid: (r.user && r.user.id) || '', email: (r.user && r.user.email) || '',
      name: (r.user && r.user.user_metadata && r.user.user_metadata.display_name) || ''
    });
    return r;
  }
  var refreshing = null;
  function refresh() {
    var s = getSess();
    if (!s || !s.rt) return Promise.reject(new Error('no session'));
    if (!refreshing) {
      refreshing = api('/auth/v1/token?grant_type=refresh_token', {
        method: 'POST', body: JSON.stringify({ refresh_token: s.rt })
      }).then(function (r) { saveAuth(r); refreshing = null; return true; })
        .catch(function (e) { refreshing = null; if (e.status === 400 || e.status === 401) setSess(null); return false; });
    }
    return refreshing;
  }
  function token() {
    var s = getSess();
    if (!s) return Promise.reject(new Error('no session'));
    if (s.exp - 150 > Math.floor(Date.now() / 1000)) return Promise.resolve(s.at);
    return refresh().then(function (ok) { if (!ok) throw new Error('session expired'); return getSess().at; });
  }
  function authed(path, opt) {
    return token().then(function (at) {
      return api(path, opt, at).catch(function (e) {
        if (e.status === 401) return refresh().then(function (ok) {
          if (!ok) throw e;
          return api(path, opt, getSess().at);
        });
        throw e;
      });
    });
  }

  /* ── auth actions ── */
  function signUp(email, pw, name) {
    var body = { email: email, password: pw };
    if (name) body.data = { display_name: name };
    return api('/auth/v1/signup', { method: 'POST', body: JSON.stringify(body) })
      .then(function (r) { if (!r.access_token) throw new Error('ایمیل تأیید لازم است'); return saveAuth(r); });
  }
  function signIn(email, pw) {
    return api('/auth/v1/token?grant_type=password', {
      method: 'POST', body: JSON.stringify({ email: email, password: pw })
    }).then(saveAuth);
  }
  function signOut() {
    var s = getSess();
    setSess(null);
    if (s && s.at) { try { api('/auth/v1/logout?scope=local', { method: 'POST' }, s.at).catch(function () {}); } catch (e) {} }
  }
  function updateProfile(fields) { // {data:{display_name}} or {password}
    return authed('/auth/v1/user', { method: 'PUT', body: JSON.stringify(fields) })
      .then(function (u) { var s = getSess(); if (s && u) { s.name = (u.user_metadata || {}).display_name || s.name; setSess(s); } return u; });
  }
  function patchProfileRow(uid, fields) {
    return authed('/rest/v1/profiles?id=eq.' + uid, {
      method: 'PATCH', headers: { Prefer: 'return=minimal' }, body: JSON.stringify(fields)
    }).catch(function () {});
  }
  function pullProfileRow(uid) {
    return authed('/rest/v1/profiles?id=eq.' + uid + '&select=display_name,daily_goal')
      .then(function (j) { return (j && j[0]) || null; }).catch(function () { return null; });
  }

  /* ── cloud sync of the saved state (app_state blob) ── */
  var Sync = { tableOk: null, warned: false, pushT: 0, pullAt: 0, lastName: null };
  function meta() { try { return JSON.parse(localStorage.getItem(YKEY) || '{}'); } catch (e) { return {}; } }
  function bumpTs(ms) {
    var m = meta(); m.ts = ms || Date.now();
    try { localStorage.setItem(YKEY, JSON.stringify(m)); } catch (e) {}
    return m.ts;
  }
  function uid() { var s = getSess(); return s ? s.uid : ''; }
  Sync.probe = function () {
    if (!AUTH_OK || Sync.tableOk !== null) return Promise.resolve(Sync.tableOk);
    return authed('/rest/v1/app_state?select=user_id&limit=1')
      .then(function () { Sync.tableOk = true; return true; })
      .catch(function (e) {
        Sync.tableOk = !(e.status === 404 || (e.body && (e.body.code === 'PGRST205' || e.body.code === '42P01')));
        return Sync.tableOk;
      });
  };
  Sync.push = function () {
    if (!isLogged()) return Promise.resolve(false);
    if (Sync.tableOk === false) { warnNoTable(); return Promise.resolve(false); }
    return Sync.probe().then(function (ok) {
      if (!ok) { warnNoTable(); return false; }
      var raw = null;
      try { raw = localStorage.getItem(STKEY); } catch (e) {}
      if (!raw) return false;
      var blob; try { blob = JSON.parse(raw); } catch (e) { return false; }
      var s = getSess();
      var tsIso = new Date(bumpTs()).toISOString();
      var jobs = [authed('/rest/v1/app_state?on_conflict=user_id', {
        method: 'POST',
        headers: { Prefer: 'resolution=merge-duplicates,return=minimal' },
        body: JSON.stringify({ user_id: s.uid, data: blob, client_updated_at: tsIso })
      })];
      if (s.name !== Sync.lastName && s.name) {
        Sync.lastName = s.name;
        jobs.push(updateProfile({ data: { display_name: s.name } }));
        jobs.push(patchProfileRow(s.uid, { display_name: s.name }));
      }
      return Promise.all(jobs).then(function () { return true; }).catch(function () { return false; });
    });
  };
  function warnNoTable() {
    if (Sync.warned || !K) return;
    Sync.warned = true;
    K.toast('همگام‌سازی ابری نیازمند اجرای فایل SQL شماره ۶ در پنل ساپابیس است');
  }
  Sync.pull = function (force) {
    if (!isLogged()) return Promise.resolve(false);
    var now = Date.now();
    if (!force && now - Sync.pullAt < 45000) return Promise.resolve(false);
    Sync.pullAt = now;
    if (Sync.tableOk === false) { warnNoTable(); return Promise.resolve(false); }
    return Sync.probe().then(function (ok) {
      if (!ok) { warnNoTable(); return false; }
      var s = getSess();
      return authed('/rest/v1/app_state?select=data,client_updated_at&user_id=eq.' + s.uid)
        .then(function (rows) {
          var row = rows && rows[0];
          var loc = meta().ts || 0;
          var srv = row && row.client_updated_at ? Date.parse(row.client_updated_at) : 0;
          var hasData = !!(row && row.data && typeof row.data === 'object' && Object.keys(row.data).length);
          if (hasData && srv > loc + 3000 && !sessionStorage.getItem(AKEY)) {
            // server is newer: adopt it wholesale, then reload to re-init
            try {
              sessionStorage.setItem(AKEY, '1');
              localStorage.setItem(STKEY, JSON.stringify(row.data));
              bumpTs(srv);
              location.reload();
              return true;
            } catch (e) { sessionStorage.removeItem(AKEY); }
          }
          if (hasData && srv <= loc + 3000 && loc > srv + 3000) return Sync.push();
          if (!hasData) return Sync.push(); // fresh account: upload what we have
          return false;
        })
        .catch(function () { return false; });
    });
  };
  Sync.onStateSaved = function () {
    if (!isLogged() || Sync.tableOk === false) return;
    bumpTs();
    clearTimeout(Sync.pushT);
    Sync.pushT = setTimeout(function () { Sync.push(); }, 4000);
  };
  function hiddenFlush() {
    clearTimeout(Sync.pushT);
    if (isLogged() && Sync.tableOk !== false) Sync.push();
  }
  window.addEventListener('pagehide', hiddenFlush);
  document.addEventListener('visibilitychange', function () { if (document.hidden) hiddenFlush(); });

  window.KiaraSync = Sync;

  /* ── auth screens (settings-screen archetype, 853-unit design space) ── */
  var CSS = [
    '#scr-login,#scr-signup{direction:rtl}',
    '.kwrap{padding:20px 46px 60px}',
    '.khero{text-align:center;padding:44px 10px 10px}',
    '.khero .klogo{width:190px;height:190px;margin:0 auto;display:block}',
    '.khero b{display:block;font-size:46px;font-weight:800;color:var(--ink);margin-top:22px;font-family:\'Lalezar\',\'Estedad\',sans-serif;font-weight:400}',
    '.khero span{display:block;font-size:27px;color:var(--mut);margin-top:12px;line-height:1.7}',
    '.kform{background:var(--panel);border-radius:36px;padding:40px 38px;margin-top:44px;',
    '  box-shadow:0 24px 44px rgba(120,105,70,.12)}',
    '.klab{font-size:26px;font-weight:700;color:var(--mut);margin:0 8px 14px}',
    '.kin{width:100%;height:118px;border-radius:32px;border:3px solid rgba(120,105,70,.25);',
    '  background:var(--bg);padding:0 40px;font-size:31px;font-family:inherit;color:var(--ink);',
    '  outline:none;box-sizing:border-box;margin-bottom:30px;direction:ltr;text-align:left}',
    '.kin::placeholder{color:var(--mut);opacity:.75}',
    '.kin:focus{border-color:var(--gold-d)}',
    '.kbtn{width:100%;height:124px;border:0;border-radius:999px;background:var(--gold);',
    '  color:#29261F;font-size:33px;font-weight:800;font-family:inherit;cursor:pointer;',
    '  box-shadow:0 16px 30px rgba(255,183,0,.35);transition:transform .12s ease;margin-top:8px}',
    '.kbtn:active{transform:scale(.97)}',
    '.kbtn[disabled]{opacity:.6}',
    '.kalt{text-align:center;margin-top:36px}',
    '.kalt a,.kalt button{background:none;border:0;padding:0;font-family:inherit;cursor:pointer;',
    '  font-size:27px;font-weight:700;color:var(--gold-d);text-decoration:underline;text-underline-offset:6px}',
    '.kalt p{font-size:25px;color:var(--mut);margin:0 0 10px}',
    '.kguest{display:block;width:100%;text-align:center;background:none;border:0;padding:26px 0 0;',
    '  font-family:inherit;font-size:26px;font-weight:700;color:var(--mut);cursor:pointer;margin-top:30px;text-decoration:underline;text-underline-offset:6px}',
    '.kerr{font-size:25px;color:#C4402F;background:rgba(196,64,47,.08);border-radius:24px;',
    '  padding:20px 28px;margin-bottom:26px;display:none;line-height:1.7}',
    '.kerr.on{display:block}',
    'html.kdark .kerr{color:#FF8A70;background:rgba(255,138,112,.1)}'
  ].join('\n');
  var MOON = '<svg class="mic" viewBox="0 0 24 24" fill="#1C1A17"><path d="M20.7 15.1A8.8 8.8 0 0 1 9 3.4 9.3 9.3 0 1 0 20.7 15.1z"/></svg>';
  var USER = '<svg class="mic" viewBox="0 0 24 24" fill="#1C1A17"><path d="M12 12.4a4.4 4.4 0 1 0 0-8.8 4.4 4.4 0 0 0 0 8.8zm0 2.2c-4.1 0-7.6 2.4-7.6 5.4 0 .8.7 1.4 1.5 1.4h12.2c.8 0 1.5-.6 1.5-1.4 0-3-3.5-5.4-7.6-5.4z"/></svg>';
  var CLOUD = '<svg class="mic" viewBox="0 0 24 24" fill="#1C1A17"><path d="M18.3 10.1A6.4 6.4 0 0 0 5.8 9 5.2 5.2 0 0 0 6.5 19.3h11.2a4.6 4.6 0 0 0 .6-9.2z"/></svg>';

  function buildScreens() {
    if (document.getElementById('scr-login')) return;
    var style = document.createElement('style');
    style.textContent = CSS;
    document.head.appendChild(style);
    var anchor = document.getElementById('scr-help');
    var status = '';
    var vstat = document.querySelector('#scr-help .vstatus');
    if (vstat) status = vstat.outerHTML;
    var back = document.querySelector('#scr-help .sback');
    var backHtml = back ? back.outerHTML : '<button class="sback" aria-label="بازگشت"></button>';
    var wrap = document.createElement('div');
    wrap.innerHTML =
      '<section class="scr" id="scr-login"><div class="scroll0"><div class="spacer0"><div class="stage0">' +
        '<header class="vtop">' + status +
          '<div class="vrow" style="direction:rtl">' + backHtml +
            '<div class="mtxt" style="font-size:38px;font-weight:800;color:var(--ink)">ورود به کیارا</div>' +
            '<span style="width:88px;flex:none"></span>' +
          '</div>' +
        '</header>' +
        '<div class="kwrap">' +
          '<div class="khero"><b>خوش برگشتی!</b><span>با حسابت ادامه بده تا پیشرفتت <br>هوا-همیشه همراهت باشه</span></div>' +
          '<div class="kform">' +
            '<div class="kerr" id="k-lerr"></div>' +
            '<div class="klab">ایمیل</div>' +
            '<input class="kin" id="k-lemail" type="email" inputmode="email" autocomplete="email" placeholder="you@example.com" autocapitalize="off" spellcheck="false">' +
            '<div class="klab">رمز عبور</div>' +
            '<input class="kin" id="k-lpass" type="password" autocomplete="current-password" placeholder="••••••••">' +
            '<button class="kbtn" id="k-lgo">ورود</button>' +
          '</div>' +
          '<div class="kalt"><p>حساب نداری؟</p><button id="k-go-signup">بیا یه حساب بسازیم!</button></div>' +
          '<button class="kguest" id="k-guest">فعلاً بدون حساب ادامه بده</button>' +
        '</div>' +
      '</div></div></div></section>' +
      '<section class="scr" id="scr-signup"><div class="scroll0"><div class="spacer0"><div class="stage0">' +
        '<header class="vtop">' + status +
          '<div class="vrow" style="direction:rtl">' + backHtml +
            '<div class="mtxt" style="font-size:38px;font-weight:800;color:var(--ink)">' + 'ساخت حساب کیارا' + '</div>' +
            '<span style="width:88px;flex:none"></span>' +
          '</div>' +
        '</header>' +
        '<div class="kwrap">' +
          '<div class="khero"><b>سلام رفیق!</b><span>چند ثانیه وقت بذار تا پیشرفتت <br>روی همه‌ی گوشی‌هات بمونه</span></div>' +
          '<div class="kform">' +
            '<div class="kerr" id="k-serr"></div>' +
            '<div class="klab">اسمت چیست؟</div>' +
            '<input class="kin" id="k-sname" type="text" autocomplete="name" placeholder="مثلاً: زیبان‌آموز" style="direction:rtl;text-align:right">' +
            '<div class="klab">ایمیل</div>' +
            '<input class="kin" id="k-semail" type="email" inputmode="email" autocomplete="email" placeholder="you@example.com" autocapitalize="off" spellcheck="false">' +
            '<div class="klab">رمز عبور (حداقل ۶ حرف)</div>' +
            '<input class="kin" id="k-spass" type="password" autocomplete="new-password" placeholder="••••••••">' +
            '<button class="kbtn" id="k-sgo">ساختن حساب</button>' +
          '</div>' +
          '<div class="kalt"><p>قبلاً ثبت‌نام کردی؟</p><button id="k-go-login">ورود</button></div>' +
          '<button class="kguest" id="k-guest2">فعلاً بدون حساب ادامه بده</button>' +
        '</div>' +
      '</div></div></div></section>';
    var frag = document.createDocumentFragment();
    while (wrap.firstChild) frag.appendChild(wrap.firstChild);
    anchor.parentNode.insertBefore(frag, anchor);
  }

  function errMsg(e) {
    var m = String((e && e.message) || '');
    if (/Failed to fetch|NetworkError|network/i.test(m)) return 'اینترنت در دسترس نیست؛ دوباره تلاش کن';
    if (/Invalid login|invalid_grant/i.test(m)) return 'ایمیل یا رمز عبور درست نیست';
    if (/already registered|already exists|duplicate/i.test(m)) return 'این ایمیل قبلاً ثبت شده؛ وارد شو';
    if (/Password should be|at least/i.test(m)) return 'رمز عبور باید حداقل ۶ حرف باشد';
    if (/valid email|unable to validate/i.test(m)) return 'ایمیل معتبر نیست';
    if (/rate limit|Too many/i.test(m)) return 'چند بار تلاش کردی؛ کمی صبر کن';
    if (/Database error|saving error/i.test(m)) return 'خطای سرور؛ کمی بعد دوباره تلاش کن';
    return m || 'خطای نامشخص';
  }
  function busy(btn, on, label) {
    if (!btn) return;
    btn.disabled = !!on;
    if (label) btn.textContent = on ? 'یه لحظه…' : label;
  }
  function showErr(id, e) {
    var box = document.getElementById(id);
    if (!box) return;
    box.textContent = errMsg(e);
    box.classList.add('on');
  }

  function afterAuth(kind) {
    setGuest(false);
    sessionStorage.removeItem(AKEY);
    var s = getSess();
    var done = function () {
      if (K) { K.go('home', false); K.toast(kind === 'up' ? 'حسابت ساخته شد؛ خوش اومدی!' : 'خوش برگشتی!'); }
      paintAccount();
      pushTheme();
    };
    if (!s) { done(); return; }
    Sync.pull(true).then(function (adopted) {
      if (adopted) return; // reload happens inside pull
      if (s.name && K && window.KL) {
        // let the live layer adopt the account name if the local one is empty
        try { KL.adoptName && KL.adoptName(s.name); } catch (e) {}
      }
      done();
    }).catch(done);
  }

  function bindAuth() {
    function on(id, fn) {
      var el = document.getElementById(id);
      if (el) el.addEventListener('click', function (e) { e.preventDefault(); fn(e); });
    }
    on('k-go-signup', function () { if (K) K.go('signup', false); });
    on('k-go-login', function () { if (K) K.go('login', false); });
    on('k-guest', guestGo);
    on('k-guest2', guestGo);
    on('k-lgo', function () {
      var email = (document.getElementById('k-lemail') || {}).value || '';
      var pw = (document.getElementById('k-lpass') || {}).value || '';
      var box = document.getElementById('k-lerr');
      if (box) box.classList.remove('on');
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) return showErr('k-lerr', { message: 'ایمیل معتبر نیست' });
      if (pw.length < 6) return showErr('k-lerr', { message: 'رمز عبور باید حداقل ۶ حرف باشد' });
      busy(document.getElementById('k-lgo'), true, 'ورود');
      signIn(email.trim(), pw)
        .then(function () { busy(document.getElementById('k-lgo'), false, 'ورود'); afterAuth('in'); })
        .catch(function (e) { busy(document.getElementById('k-lgo'), false, 'ورود'); showErr('k-lerr', e); });
    });
    on('k-sgo', function () {
      var name = (document.getElementById('k-sname') || {}).value || '';
      var email = (document.getElementById('k-semail') || {}).value || '';
      var pw = (document.getElementById('k-spass') || {}).value || '';
      if (name.trim().length < 2) return showErr('k-serr', { message: 'اسمت را بنویس (حداقل ۲ حرف)' });
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) return showErr('k-serr', { message: 'ایمیل معتبر نیست' });
      if (pw.length < 6) return showErr('k-serr', { message: 'رمز عبور باید حداقل ۶ حرف باشد' });
      busy(document.getElementById('k-sgo'), true, 'ساختن حساب');
      signUp(email.trim(), pw, name.trim())
        .then(function () {
          var s = getSess();
          if (s) { s.name = name.trim(); setSess(s); }
          busy(document.getElementById('k-sgo'), false, 'ساختن حساب');
          afterAuth('up');
        })
        .catch(function (e) { busy(document.getElementById('k-sgo'), false, 'ساختن حساب'); showErr('k-serr', e); });
    });
  }
  function guestGo() {
    setGuest(true);
    if (K) { K.go('home', false); K.toast('فعلاً مهمان هستی؛ پیشرفتت روی همین گوشی ذخیره می‌شه'); }
    paintAccount();
  }

  /* ── settings additions: dark toggle + account row ── */
  function injectSettingsRows() {
    var darkRow = document.getElementById('k-dark-row');
    if (!darkRow) {
      var card = document.querySelector('#scr-settings .mcard');
      // insert AFTER the last toggle row of the first card: kiara-live's
      // applySet/readSet address .mtoggle by index (0=sound, 1=auto) and must
      // not see ours — appending at the end keeps their indexing intact.
      var refRow = null, rows = card ? card.querySelectorAll('.mrow') : [];
      for (var i = rows.length - 1; i >= 0; i--) {
        if (rows[i].querySelector('.mtoggle')) { refRow = rows[i]; break; }
      }
      if (refRow) {
        var row = document.createElement('div');
        row.className = 'mrow';
        row.id = 'k-dark-row';
        row.style.cursor = 'pointer';
        row.innerHTML = MOON +
          '<div class="mtxt"><b>تم تاریک</b><span>حالت شب برای چشم راحت‌تر</span></div>' +
          '<button class="mtoggle' + (isDark() ? ' on' : '') + '" aria-label="تم تاریک"></button>';
        refRow.parentNode.insertBefore(row, refRow.nextSibling);
      }
    }
    var acct = document.getElementById('s-acct');
    if (!acct) {
      var ref = document.getElementById('s-edit');
      if (ref) {
        var row2 = document.createElement('div');
        row2.className = 'mrow';
        row2.id = 's-acct';
        row2.style.cursor = 'pointer';
        row2.innerHTML = USER +
          '<div class="mtxt"><b id="k-acct-t">ورود / ثبت‌نام</b><span id="k-acct-s">پیشرفتت رو ابری ذخیره کن</span></div>' +
          '<span class="val" id="k-acct-v"></span>';
        ref.parentNode.insertBefore(row2, ref);
      }
    }
    // account card gets a cloud icon row subtitle when logged in
    paintAccount();
  }
  function paintAccount() {
    var t = document.getElementById('k-acct-t');
    if (!t) return;
    var s = getSess();
    var sub = document.getElementById('k-acct-s');
    var val = document.getElementById('k-acct-v');
    if (isLogged() && s) {
      t.textContent = s.name || 'حساب من';
      sub.textContent = s.email || '';
      val.textContent = Sync.tableOk === false ? 'آفلاین' : (Sync.tableOk === true ? 'همگام' : '');
      val.setAttribute('dir', 'rtl');
    } else {
      t.textContent = 'ورود / ثبت‌نام';
      sub.textContent = 'پیشرفتت رو ابری ذخیره کن';
      val.textContent = '';
    }
    paintDark();
  }
  function doLogout() {
    if (!K) return;
    if (isLogged()) {
      signOut();
      setGuest(false);
      K.toast('از حساب خارج شدی؛ پیشرفتت روی همین گوشی موند');
      setTimeout(function () { location.reload(); }, 650);
    } else {
      setGuest(false);
      K.go('login', false);
      K.toast('وارد حسابت شو یا بدون حساب ادامه بده');
    }
  }

  /* ── global taps (registered BEFORE kiara-live's capture listener) ── */
  document.addEventListener('click', function (e) {
    var t = e.target;
    if (!t || !t.closest) return;
    function hit(s) { return t.closest(s); }
    function stop() { e.stopPropagation(); e.preventDefault(); }
    if (hit('#k-dark-row')) {
      stop(); e.stopImmediatePropagation();
      setDark(!isDark());
    } else if (hit('#s-logout')) {
      stop(); e.stopImmediatePropagation();
      doLogout();
    } else if (hit('#s-acct')) {
      stop();
      if (!isLogged()) { if (K) K.go('login', false); }
      else if (K) { Sync.pull(true); K.toast('در حال همگام‌سازی…'); }
    }
  }, true);

  /* ── wire into the live layer (after its IIFE ran) ── */
  function paintAuthChrome(scr) {
    var auth = scr === 'login' || scr === 'signup';
    // the shared bottom nav does not belong on the account screens
    $$('.nav').forEach(function (n) { n.style.visibility = auth ? 'hidden' : ''; });
    // the cushion back button only leaves the auth screens when allowed
    $$('#scr-' + scr + ' .vrow .sback').forEach(function (b) {
      b.style.visibility = canLeave() ? 'visible' : 'hidden';
      b.onclick = function (ev) { ev.preventDefault(); if (canLeave()) K.go('home', false); };
    });
    if (auth) paintAccount();
  }

  function wire() {
    K = window.KIARA || null;
    if (!K) return;
    buildScreens();
    bindAuth();
    injectSettingsRows();
    var KL = window.KL;
    if (!KL) return;

    KL.homeScr = function () { return canLeave() ? 'home' : 'login'; };
    KL.canLeave = canLeave;

    var prevBefore = KL.beforeGo, prevAfter = KL.afterGo, prevResume = KL.onResume;
    var FREE = ['login', 'signup', 'help'];
    KL.beforeGo = function (scr, push) {
      if (AUTH_OK && !canLeave() && FREE.indexOf(scr) < 0) {
        K.toast('اول وارد حسابت شو یا مهمان باش');
        return 'login';
      }
      if ((scr === 'login' || scr === 'signup') && isLogged()) return 'home';
      return prevBefore ? prevBefore(scr, push) : undefined;
    };
    KL.afterGo = function (scr) {
      if (prevAfter) prevAfter(scr);
      paintAuthChrome(scr);
    };
    KL.onResume = function () {
      if (prevResume) prevResume();
      Sync.pull(false);
      pushTheme();
    };
    KL.adoptName = function (name) {
      // let the live layer show the account name if the local profile is empty
      try {
        var raw = localStorage.getItem(STKEY);
        var st = raw ? JSON.parse(raw) : null;
        if (st && !st.name) { st.name = name; localStorage.setItem(STKEY, JSON.stringify(st)); }
      } catch (e) {}
    };

    if (isLogged()) Sync.pull(false);

    // cold start ran go('login') before these hooks existed: apply now
    var cur = document.querySelector('.scr.on');
    paintAuthChrome(cur ? cur.id.replace('scr-', '') : 'home');
  }

  /* kiara-live.js runs right after this file; its IIFE is synchronous. The
     DOM work (auth screens, settings rows, theme) happens IMMEDIATELY so the
     first paint already shows the right screen; only the KL hook wrapping
     waits for a macrotask (lands after kiara-live's IIFE). */
  buildScreens();
  bindAuth();
  injectSettingsRows();
  pushTheme();
  if (AUTH_OK && !isLogged() && !isGuest() && window.KIARA) {
    try { window.KIARA.go('login', false); } catch (e) {}
  }
  setTimeout(wire, 0);
})();
