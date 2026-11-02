const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputPath = path.resolve(process.env.SCUREDWEB_ONLINE_OUTPUT || 'target/online-users-verification/results.json');
const classpath = process.env.SCUREDWEB_VERIFICATION_CLASSPATH || [
  'target/verification-classes',
  'target/app-classes.jar',
  process.env.SCUREDWEB_OJDBC_JAR || path.join(os.homedir(), '.m2', 'repository', 'com', 'oracle',
    'database', 'jdbc', 'ojdbc17', '23.26.3.0.0', 'ojdbc17-23.26.3.0.0.jar')
].join(path.delimiter);
let loginId = process.env.SCUREDWEB_TEST_USER_ID;
let password = process.env.SCUREDWEB_TEST_USER_PASSWORD;

function browserExecutable() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  const candidates = process.platform === 'win32' ? [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ] : [];
  return candidates.find(candidate => fs.existsSync(candidate));
}

async function heartbeat(context) {
  const response = await context.request.get(`${baseUrl}/api/online-users`);
  if (!response.ok()) throw new Error(`online-users returned HTTP ${response.status()}`);
  return response.json();
}

async function login(page) {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' });
  await page.locator('input[name="loginId"]').fill(loginId);
  await page.locator('input[name="password"]').fill(password);
  await Promise.all([
    page.waitForNavigation({ waitUntil: 'networkidle' }),
    page.locator('form[action$="/login"] button[type="submit"]').click()
  ]);
  return page.url();
}

async function registerVerificationUser(browser) {
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
  loginId = `verify_online_${suffix}`;
  password = `V!${crypto.randomBytes(12).toString('base64url')}9a`;
  const account = {
    loginId,
    userName: 'Online Verification',
    nickname: loginId,
    email: `${loginId}@example.invalid`,
    phone: '',
    password,
    securityQuestion: 'verification only',
    securityAnswer: crypto.randomBytes(12).toString('hex')
  };
  const context = await browser.newContext();
  const page = await context.newPage();
  try {
    await page.goto(`${baseUrl}/register`, { waitUntil: 'networkidle' });
    for (const [name, value] of Object.entries(account)) await page.locator(`[name="${name}"]`).fill(value);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.locator('form[action$="/register"] button[type="submit"]').click()
    ]);
    if (!page.url().startsWith(`${baseUrl}/login`)) throw new Error('online verifier account registration failed');
  } finally {
    await context.close();
  }
  return loginId;
}

function cleanupVerificationUser(bootstrapLoginId) {
  if (!bootstrapLoginId) return;
  const result = spawnSync('java', ['-cp', classpath, 'AttachmentFaultHarness', 'cleanup-user', bootstrapLoginId], {
    cwd: process.cwd(), encoding: 'utf8'
  });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || 'online verifier cleanup failed');
}

async function main() {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const launchOptions = { headless: true };
  const executablePath = browserExecutable();
  if (executablePath) launchOptions.executablePath = executablePath;
  const browser = await chromium.launch(launchOptions);
  const bootstrapLoginId = !loginId || !password ? await registerVerificationUser(browser) : null;
  const failures = [];
  const results = { baseUrl, generatedAt: new Date().toISOString(), steps: {}, failures };

  const observer = await browser.newContext();
  const loginOnly = await browser.newContext();
  const registerOnly = await browser.newContext();
  const active = await browser.newContext();
  try {
    results.steps.observerBaseline = await heartbeat(observer);
    const baseline = results.steps.observerBaseline.activeSessionCount;
    const baselineAuthenticated = results.steps.observerBaseline.authenticatedSessionCount;
    const baselineGuests = results.steps.observerBaseline.guestSessionCount;

    await (await loginOnly.newPage()).goto(`${baseUrl}/login`, { waitUntil: 'networkidle' });
    results.steps.afterLoginGet = await heartbeat(observer);
    if (results.steps.afterLoginGet.activeSessionCount !== baseline) failures.push('login GET created an active session');

    await (await registerOnly.newPage()).goto(`${baseUrl}/register`, { waitUntil: 'networkidle' });
    results.steps.afterRegisterGet = await heartbeat(observer);
    if (results.steps.afterRegisterGet.activeSessionCount !== baseline) failures.push('register GET created an active session');

    const activePage = await active.newPage();
    await activePage.goto(`${baseUrl}/board/list?boardCode=free`, { waitUntil: 'networkidle' });
    results.steps.afterGuestHeartbeat = await heartbeat(observer);
    if (results.steps.afterGuestHeartbeat.activeSessionCount !== baseline + 1) failures.push('board heartbeat did not add exactly one guest session');

    const secondTab = await active.newPage();
    await secondTab.goto(`${baseUrl}/board/list?boardCode=free`, { waitUntil: 'networkidle' });
    results.steps.afterSameSessionTab = await heartbeat(observer);
    if (results.steps.afterSameSessionTab.activeSessionCount !== baseline + 1) failures.push('same-session tab changed the session count');

    results.steps.loginFinalUrl = await login(activePage);
    results.steps.afterAuthenticatedHeartbeat = await heartbeat(active);
    if (results.steps.afterAuthenticatedHeartbeat.activeSessionCount !== baseline + 1
        || results.steps.afterAuthenticatedHeartbeat.authenticatedSessionCount !== baselineAuthenticated + 1
        || results.steps.afterAuthenticatedHeartbeat.guestSessionCount !== baselineGuests) {
      failures.push('login did not replace the active guest with one authenticated session');
    }
    if (!results.steps.afterAuthenticatedHeartbeat.userListVisible
        || !results.steps.afterAuthenticatedHeartbeat.users.some(user => user.name === loginId)) {
      failures.push('authenticated user list visibility is incorrect');
    }

    results.steps.anonymousView = await heartbeat(observer);
    if (results.steps.anonymousView.userListVisible || results.steps.anonymousView.users.length !== 0) {
      failures.push('anonymous session received authenticated user names');
    }

    await activePage.goto(`${baseUrl}/home`, { waitUntil: 'networkidle' });
    await Promise.all([
      activePage.waitForNavigation({ waitUntil: 'networkidle' }),
      activePage.locator('form[action$="/logout"] button[type="submit"]').click()
    ]);
    results.steps.afterLogout = await heartbeat(observer);
    if (results.steps.afterLogout.authenticatedSessionCount !== baselineAuthenticated
        || results.steps.afterLogout.guestSessionCount !== baselineGuests + 1) {
      failures.push('logout did not remove the authenticated session before the redirected home heartbeat');
    }
  } finally {
    await Promise.all([observer.close(), loginOnly.close(), registerOnly.close(), active.close()]);
    await browser.close();
    cleanupVerificationUser(bootstrapLoginId);
    fs.writeFileSync(outputPath, JSON.stringify(results, null, 2));
  }

  if (failures.length) throw new Error(failures.join('\n'));
  console.log(`Online-user HTTP verification passed; results: ${outputPath}`);
}

main().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
