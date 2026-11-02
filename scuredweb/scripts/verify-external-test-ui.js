const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_UI_OUTPUT || 'target/external-test-verification');
const databaseState = process.env.SCUREDWEB_DATABASE_STATE || 'unspecified';
const viewports = [360, 768, 1280, 1440].map(width => ({ width, height: 900 }));

function executablePath() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  const candidates = process.platform === 'win32' ? [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ] : [];
  return candidates.find(candidate => fs.existsSync(candidate));
}

async function inspectPage(page, route, key, viewport) {
  const consoleErrors = [];
  const failedRequests = [];
  const badAssets = [];
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });
  page.on('pageerror', error => consoleErrors.push(error.message));
  page.on('requestfailed', request => failedRequests.push({ url: request.url(), error: request.failure()?.errorText }));
  page.on('response', response => {
    if (response.status() >= 400 && /\.(css|js|png|jpe?g|gif|svg|ico|woff2?)(\?|$)/i.test(response.url())) {
      badAssets.push({ url: response.url(), status: response.status() });
    }
  });

  const response = await page.goto(baseUrl + route, { waitUntil: 'networkidle' });
  await page.evaluate(() => window.scrollTo(0, 0));
  const top = await page.evaluate(pageState);
  await page.screenshot({ path: path.join(outputDir, `${key}-${viewport.width}-top.png`) });
  await page.evaluate(() => window.scrollTo(0, Math.min(800, document.documentElement.scrollHeight)));
  await page.waitForTimeout(100);
  const middle = await page.evaluate(pageState);
  await page.screenshot({ path: path.join(outputDir, `${key}-${viewport.width}-middle.png`) });
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await page.waitForTimeout(100);
  const bottom = await page.evaluate(pageState);
  await page.screenshot({ path: path.join(outputDir, `${key}-${viewport.width}-bottom.png`) });

  return {
    key,
    route,
    viewport,
    status: response?.status(),
    finalUrl: page.url(),
    top,
    middle,
    bottom,
    consoleErrors,
    failedRequests,
    badAssets
  };
}

function pageState() {
  const round = value => Math.round(value * 100) / 100;
  const rects = selector => [...document.querySelectorAll(selector)].map(element => {
    const rect = element.getBoundingClientRect();
    return {
      position: getComputedStyle(element).position,
      top: round(rect.top),
      bottom: round(rect.bottom),
      left: round(rect.left),
      right: round(rect.right)
    };
  });
  const filterTops = [...document.querySelectorAll('.board-filter-tabs a')]
    .map(element => round(element.getBoundingClientRect().top));
  const boardMenuToggle = document.querySelector('[data-board-menu-toggle]');
  const headerElement = document.querySelector('.site-header');
  const railElements = [...document.querySelectorAll('.home-left-rail, .home-right-rail, .board-shortcuts')];
  const railRectangles = railElements.map(element => element.getBoundingClientRect());
  let contentRailOverlapCount = 0;
  for (let left = 0; left < railRectangles.length; left += 1) {
    for (let right = left + 1; right < railRectangles.length; right += 1) {
      const a = railRectangles[left];
      const b = railRectangles[right];
      if (a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top) {
        contentRailOverlapCount += 1;
      }
    }
  }
  return {
    scrollY: round(window.scrollY),
    scrollHeight: document.documentElement.scrollHeight,
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: document.documentElement.scrollWidth,
    header: rects('.site-header'),
    headerHeight: headerElement ? round(headerElement.getBoundingClientRect().height) : 0,
    headFaviconCount: document.head.querySelectorAll('link[rel~="icon"]').length,
    bodyFaviconCount: document.body.querySelectorAll('link[rel~="icon"]').length,
    contentRails: rects('.home-left-rail, .home-right-rail, .board-shortcuts'),
    contentRailOverlapCount,
    boardColumns: rects('.board-left-rail, .board-center-rail, .board-right-rail'),
    filterRows: new Set(filterTops).size,
    boardMenuToggle: boardMenuToggle ? {
      display: getComputedStyle(boardMenuToggle).display,
      expanded: boardMenuToggle.getAttribute('aria-expanded')
    } : null,
    loadErrorCount: document.querySelectorAll('[role="alert"]').length
  };
}

async function login(context, loginId, password) {
  const page = await context.newPage();
  try {
    await page.goto(baseUrl + '/login', { waitUntil: 'networkidle' });
    await page.locator('input[name="loginId"]').fill(loginId);
    await page.locator('input[name="password"]').fill(password);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.locator('form[action$="/login"] button[type="submit"]').click()
    ]);
    return page.url().startsWith(baseUrl + '/home');
  } finally {
    await page.close();
  }
}

async function verifyAdmin(context, password) {
  const page = await context.newPage();
  try {
    await page.goto(baseUrl + '/admin/dashboard', { waitUntil: 'networkidle' });
    if (!page.url().includes('/admin/verify')) return page.url().includes('/admin/dashboard');
    await page.locator('input[name="password"]').fill(password);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.locator('form[action$="/admin/verify"] button[type="submit"]').click()
    ]);
    return page.url().includes('/admin/dashboard');
  } finally {
    await page.close();
  }
}

function validate(result, failures) {
  const prefix = `${result.key}@${result.viewport.width}`;
  const states = [result.top, result.middle, result.bottom];
  if (result.status !== 200) failures.push(`${prefix}: HTTP ${result.status}`);
  if (states.some(state => state.scrollWidth - state.clientWidth > 1)) failures.push(`${prefix}: horizontal overflow`);
  if (result.consoleErrors.length) failures.push(`${prefix}: console errors`);
  if (result.failedRequests.length) failures.push(`${prefix}: failed network requests`);
  if (result.badAssets.length) failures.push(`${prefix}: asset errors`);
  if (states.some(state => state.headFaviconCount !== 1 || state.bodyFaviconCount !== 0)) {
    failures.push(`${prefix}: favicon declaration is not exclusively in head`);
  }
  if (states.some(state => state.contentRails.some(rail => ['sticky', 'fixed'].includes(rail.position)))) {
    failures.push(`${prefix}: sticky/fixed content rail`);
  }
  if (states.some(state => state.contentRailOverlapCount > 0)) {
    failures.push(`${prefix}: content rails overlap`);
  }
  if (result.viewport.width <= 820 && result.top.header.some(header => header.position === 'sticky')) {
    failures.push(`${prefix}: mobile header remains sticky`);
  }
  if (result.viewport.width <= 820 && result.top.headerHeight > 170) {
    failures.push(`${prefix}: mobile header height is ${result.top.headerHeight}px`);
  }
  if (result.key.startsWith('board') && result.viewport.width <= 820 && result.top.filterRows > 2) {
    failures.push(`${prefix}: board filters use ${result.top.filterRows} rows`);
  }
  if (result.key.startsWith('board') && result.viewport.width <= 820 && result.top.boardColumns.length === 3) {
    const [left, center] = result.top.boardColumns;
    if (center.top > left.top) failures.push(`${prefix}: board menu precedes main list`);
  }
  if (result.key.startsWith('board') && result.viewport.width <= 820
      && result.top.boardMenuToggle?.display === 'none') {
    failures.push(`${prefix}: collapsible board menu trigger is hidden`);
  }
  if (databaseState.includes('outage')
      && (result.key === 'home' || result.key.startsWith('board'))
      && result.top.loadErrorCount < 1) {
    failures.push(`${prefix}: database outage is not distinguished from an empty state`);
  }
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const options = { headless: true };
  const browserPath = executablePath();
  if (browserPath) options.executablePath = browserPath;
  const browser = await chromium.launch(options);
  const results = [];
  const failures = [];
  const skipped = [];

  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport });
      for (const spec of [
        { key: 'home', route: '/' },
        { key: 'board-free', route: '/board/list?boardCode=free' },
        { key: 'login', route: '/login' }
      ]) {
        const page = await context.newPage();
        const result = await inspectPage(page, spec.route, spec.key, viewport);
        results.push(result);
        validate(result, failures);
        await page.close();
      }
      await context.close();
    }

    const protocolContext = await browser.newContext();
    const protocolPage = await protocolContext.newPage();
    const missing = await protocolPage.goto(baseUrl + '/missing-' + Date.now(), { waitUntil: 'networkidle' });
    const favicon = await protocolPage.request.get(baseUrl + '/favicon.svg');
    const faviconIco = await protocolPage.request.get(baseUrl + '/favicon.ico');
    const online = await protocolPage.request.get(baseUrl + '/api/online-users');
    const onlineData = online.ok() ? await online.json() : null;
    const protocol = {
      missingStatus: missing?.status(),
      faviconStatus: favicon.status(),
      faviconMime: favicon.headers()['content-type'],
      faviconIcoStatus: faviconIco.status(),
      onlineStatus: online.status(),
      onlineData
    };
    if (protocol.missingStatus !== 404) failures.push(`protocol: missing URL returned ${protocol.missingStatus}`);
    if (protocol.faviconStatus !== 200 || !protocol.faviconMime?.startsWith('image/svg+xml')) failures.push('protocol: favicon.svg response invalid');
    if (protocol.faviconIcoStatus !== 404) failures.push(`protocol: favicon.ico returned ${protocol.faviconIcoStatus}`);
    if (!onlineData || onlineData.activeSessionCount !== onlineData.authenticatedSessionCount + onlineData.guestSessionCount) {
      failures.push('protocol: online session count equation failed');
    }
    if (onlineData?.users?.some(user => user.authenticated !== true)) failures.push('protocol: guest appeared in user list');
    await protocolContext.close();

    const userId = process.env.SCUREDWEB_LOGIN_ID;
    const userPassword = process.env.SCUREDWEB_LOGIN_PASSWORD;
    let authenticated = { status: 'skipped' };
    if (userId && userPassword) {
      const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
      const success = await login(context, userId, userPassword);
      authenticated = { status: success ? 'passed' : 'failed' };
      if (!success) failures.push('authenticated: login failed');
      if (success) {
        for (const spec of [
          { key: 'board-write-auth', route: '/board/write' },
          { key: 'mypage-auth', route: '/mypage' }
        ]) {
          const page = await context.newPage();
          const result = await inspectPage(page, spec.route, spec.key, { width: 1280, height: 900 });
          results.push(result);
          validate(result, failures);
          await page.close();
        }
      }
      await context.close();
    } else {
      skipped.push('authenticated pages: SCUREDWEB_LOGIN_ID/SCUREDWEB_LOGIN_PASSWORD not supplied');
    }

    const adminId = process.env.SCUREDWEB_ADMIN_LOGIN_ID;
    const adminPassword = process.env.SCUREDWEB_ADMIN_LOGIN_PASSWORD;
    let admin = { status: 'skipped' };
    if (adminId && adminPassword) {
      admin = { status: 'passed', viewports: [] };
      for (const viewport of viewports) {
        const context = await browser.newContext({ viewport });
        const loggedIn = await login(context, adminId, adminPassword);
        const verified = loggedIn && await verifyAdmin(context, adminPassword);
        admin.viewports.push({ width: viewport.width, verified });
        if (!verified) {
          admin.status = 'failed';
          failures.push(`admin@${viewport.width}: login or additional verification failed`);
        } else {
          for (const route of ['dashboard', 'users', 'posts', 'reports', 'notices']) {
            const page = await context.newPage();
            const result = await inspectPage(page, `/admin/${route}`, `admin-${route}`, viewport);
            results.push(result);
            validate(result, failures);
            await page.close();
          }
        }
        await context.close();
      }
    } else {
      skipped.push('admin pages: SCUREDWEB_ADMIN_LOGIN_ID/SCUREDWEB_ADMIN_LOGIN_PASSWORD not supplied');
    }

    fs.writeFileSync(path.join(outputDir, 'results.json'), JSON.stringify({
      baseUrl,
      databaseState,
      generatedAt: new Date().toISOString(),
      results,
      protocol,
      authenticated,
      admin,
      skipped,
      failures
    }, null, 2));
  } finally {
    await browser.close();
  }

  if (failures.length) {
    console.error(failures.join('\n'));
    process.exitCode = 1;
  } else {
    console.log(`External test UI verification passed; results: ${path.join(outputDir, 'results.json')}`);
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
