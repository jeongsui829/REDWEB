const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');

const mode = process.argv[2];
const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_RESTART_OUTPUT || 'target/restart-persistence-verification');
const statePath = path.join(outputDir, 'private-state.json');
const uploadRoot = process.env.REDWEB_UPLOAD_ROOT || path.join(os.homedir(), 'RedWebUploads');
const uploadDir = path.join(uploadRoot, 'attachments');
const classpath = process.env.SCUREDWEB_VERIFICATION_CLASSPATH || [
  'target/verification-classes',
  'target/app-classes.jar',
  process.env.SCUREDWEB_OJDBC_JAR || path.join(os.homedir(), '.m2', 'repository', 'com', 'oracle',
    'database', 'jdbc', 'ojdbc17', '23.26.3.0.0', 'ojdbc17-23.26.3.0.0.jar')
].join(path.delimiter);

function javaHarness(command, value) {
  const args = ['-cp', classpath, 'AttachmentFaultHarness', command];
  if (value) args.push(value);
  const result = spawnSync('java', args, { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
}

function uploadFiles() {
  return fs.existsSync(uploadDir) ? fs.readdirSync(uploadDir).sort() : [];
}

function executablePath() {
  return [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe'
  ].find(candidate => fs.existsSync(candidate));
}

async function submit(page, selector) {
  const action = await page.locator(selector).evaluate(element => element.form.action);
  const [postResponse, finalResponse] = await Promise.all([
    page.waitForResponse(response => response.request().method() === 'POST' && response.url() === action),
    page.waitForNavigation({ waitUntil: 'networkidle' }),
    page.locator(selector).click()
  ]);
  return { postStatus: postResponse.status(), finalStatus: finalResponse?.status(), finalUrl: page.url() };
}

async function login(page, account) {
  await page.goto(baseUrl + '/login', { waitUntil: 'networkidle' });
  await page.locator('input[name="loginId"]').fill(account.loginId);
  await page.locator('input[name="password"]').fill(account.password);
  return submit(page, 'form[action$="/login"] button[type="submit"]');
}

async function create(browser) {
  fs.mkdirSync(outputDir, { recursive: true });
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
  const account = {
    loginId: `verify_restart_${suffix}`,
    userName: 'Restart Verification',
    nickname: `verify_restart_${suffix}`,
    email: `verify_restart_${suffix}@example.invalid`,
    phone: '010-0000-0000',
    password: `V!${crypto.randomBytes(12).toString('base64url')}9a`,
    securityQuestion: 'verification only',
    securityAnswer: crypto.randomBytes(12).toString('hex')
  };
  const title = `VERIFY_RESTART_PERSISTENCE_${suffix}`;
  const attachmentPath = path.join(outputDir, 'restart-persistence.txt');
  fs.writeFileSync(attachmentPath, 'restart persistence verification');
  const beforeFiles = uploadFiles();
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  await page.goto(baseUrl + '/register', { waitUntil: 'networkidle' });
  for (const [name, value] of Object.entries(account)) await page.locator(`[name="${name}"]`).fill(value);
  const registration = await submit(page, 'form[action$="/register"] button[type="submit"]');
  const loginResult = await login(page, account);
  await page.goto(baseUrl + '/board/write?boardCode=free', { waitUntil: 'networkidle' });
  await page.locator('select[name="boardCode"]').selectOption('free');
  await page.locator('input[name="title"]').fill(title);
  await page.locator('textarea[name="content"]').fill('Tomcat restart persistence verification');
  await page.locator('input[name="attachments"]').setInputFiles(attachmentPath);
  const post = await submit(page, 'form[action$="/board/write"] button[type="submit"]');
  const postId = Number(new URL(post.finalUrl).searchParams.get('id'));
  const downloadHref = await page.locator('a[href*="/files/download"]').first().getAttribute('href');
  const download = await context.request.get(new URL(downloadHref, baseUrl).toString());
  const newPhysicalFiles = uploadFiles().filter(file => !beforeFiles.includes(file));
  if (!postId || !download.ok() || newPhysicalFiles.length !== 1) throw new Error('Normal attachment creation did not persist');
  const state = { account, title, postId, downloadHref, newPhysicalFiles };
  fs.writeFileSync(statePath, JSON.stringify(state));
  fs.writeFileSync(path.join(outputDir, 'before-restart.json'), JSON.stringify({
    registration, login: loginResult, post, postId,
    database: javaHarness('find-title', title),
    sequences: javaHarness('sequences'),
    downloadStatus: download.status(), newPhysicalFiles
  }, null, 2));
  await context.close();
}

async function verifyAndCleanup(browser) {
  const state = JSON.parse(fs.readFileSync(statePath, 'utf8'));
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();
  const detailResponse = await page.goto(`${baseUrl}/board/detail?id=${state.postId}`, { waitUntil: 'networkidle' });
  const titleVisible = await page.getByText(state.title, { exact: true }).count() > 0;
  const loginResult = await login(page, state.account);
  await page.goto(`${baseUrl}/board/detail?id=${state.postId}`, { waitUntil: 'networkidle' });
  const download = await context.request.get(new URL(state.downloadHref, baseUrl).toString());
  const filesBeforeCleanup = state.newPhysicalFiles.map(file => fs.existsSync(path.join(uploadDir, file)));
  if (detailResponse.status() !== 200 || !titleVisible || !download.ok() || filesBeforeCleanup.some(value => !value)) {
    throw new Error('Post, DB row, or attachment did not survive Tomcat restart');
  }
  const databaseBeforeCleanup = javaHarness('find-title', state.title);
  if (!databaseBeforeCleanup.includes('POST_COUNT=1') || !databaseBeforeCleanup.includes('ATTACHMENT_COUNT=1')) {
    throw new Error('Database rows did not survive Tomcat restart');
  }
  const deletion = await submit(page, 'form[action$="/board/delete"] button[type="submit"]');
  javaHarness('cleanup-user', state.account.loginId);
  const filesAfterCleanup = state.newPhysicalFiles.map(file => fs.existsSync(path.join(uploadDir, file)));
  const result = {
    detailStatus: detailResponse.status(), titleVisible, login: loginResult,
    downloadStatus: download.status(), filesBeforeCleanup,
    databaseBeforeCleanup,
    deletion, databaseAfterCleanup: javaHarness('find-title', state.title),
    filesAfterCleanup, sequences: javaHarness('sequences')
  };
  fs.writeFileSync(path.join(outputDir, 'after-restart.json'), JSON.stringify(result, null, 2));
  fs.rmSync(statePath);
  await context.close();
  if (!result.databaseAfterCleanup.includes('POST_COUNT=0') || filesAfterCleanup.some(Boolean)) {
    throw new Error('Exact verification data cleanup failed');
  }
}

async function main() {
  if (!['create', 'verify-cleanup'].includes(mode)) throw new Error('Use create or verify-cleanup');
  const options = { headless: true };
  const browserPath = executablePath();
  if (browserPath) options.executablePath = browserPath;
  const browser = await chromium.launch(options);
  try {
    if (mode === 'create') await create(browser);
    else await verifyAndCleanup(browser);
  } finally {
    await browser.close();
  }
  console.log(`Restart persistence ${mode} passed`);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
