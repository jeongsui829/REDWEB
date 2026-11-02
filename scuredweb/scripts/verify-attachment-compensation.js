const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_ATTACHMENT_OUTPUT || 'target/attachment-compensation-verification');
const classpath = process.env.SCUREDWEB_VERIFICATION_CLASSPATH || [
  'target/verification-classes',
  'target/app-classes.jar',
  process.env.SCUREDWEB_OJDBC_JAR || path.join(os.homedir(), '.m2', 'repository', 'com', 'oracle',
    'database', 'jdbc', 'ojdbc17', '23.26.3.0.0', 'ojdbc17-23.26.3.0.0.jar')
].join(path.delimiter);
const uploadRoot = process.env.REDWEB_UPLOAD_ROOT || path.join(os.homedir(), 'RedWebUploads');
const uploadDir = path.join(uploadRoot, 'attachments');

function javaHarness(mode, value) {
  const args = ['-cp', classpath, 'AttachmentFaultHarness', mode];
  if (value) args.push(value);
  const result = spawnSync('java', args, { cwd: process.cwd(), encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || `Java harness failed: ${mode}`);
  return result.stdout.trim();
}

function filesInUploadDir() {
  if (!fs.existsSync(uploadDir)) return [];
  return fs.readdirSync(uploadDir).sort();
}

function browserExecutable() {
  const candidates = process.platform === 'win32' ? [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe'
  ] : [];
  return candidates.find(candidate => fs.existsSync(candidate));
}

async function submit(page, selector) {
  const submitUrl = await page.locator(selector).evaluate(element => element.form.action);
  const [postResponse, navigationResponse] = await Promise.all([
    page.waitForResponse(response => response.request().method() === 'POST' && response.url() === submitUrl),
    page.waitForNavigation({ waitUntil: 'networkidle' }),
    page.locator(selector).click()
  ]);
  return {
    postStatus: postResponse.status(),
    finalStatus: navigationResponse?.status(),
    finalUrl: page.url(),
    pageText: await page.locator('body').innerText()
  };
}

async function createPost(page, title, attachmentPath) {
  await page.goto(baseUrl + '/board/write?boardCode=free', { waitUntil: 'networkidle' });
  await page.locator('select[name="boardCode"]').selectOption('free');
  await page.locator('input[name="title"]').fill(title);
  await page.locator('textarea[name="content"]').fill('Forced attachment failure verification');
  await page.locator('input[name="attachments"]').setInputFiles(attachmentPath);
  return submit(page, 'form[action$="/board/write"] button[type="submit"]');
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
  const account = {
    loginId: `verify_attachment_${suffix}`,
    userName: 'Attachment Verification',
    nickname: `verify_attachment_${suffix}`,
    email: `verify_attachment_${suffix}@example.invalid`,
    phone: '010-0000-0000',
    password: `V!${crypto.randomBytes(12).toString('base64url')}9a`,
    securityQuestion: 'verification only',
    securityAnswer: crypto.randomBytes(12).toString('hex')
  };
  const successTitle = `VERIFY_ATTACHMENT_COMPENSATION_${suffix}`;
  const partialTitle = `VERIFY_ATTACHMENT_PARTIAL_${suffix}`;
  const attachmentPath = path.join(outputDir, 'forced-failure.txt');
  fs.writeFileSync(attachmentPath, 'forced attachment failure');
  const results = { baseUrl, generatedAt: new Date().toISOString(), sequencesBefore: javaHarness('sequences') };
  const options = { headless: true };
  const executablePath = browserExecutable();
  if (executablePath) options.executablePath = executablePath;
  const browser = await chromium.launch(options);
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto(baseUrl + '/register', { waitUntil: 'networkidle' });
    for (const [name, value] of Object.entries(account)) await page.locator(`[name="${name}"]`).fill(value);
    results.registration = await submit(page, 'form[action$="/register"] button[type="submit"]');
    await page.locator('input[name="loginId"]').fill(account.loginId);
    await page.locator('input[name="password"]').fill(account.password);
    results.login = await submit(page, 'form[action$="/login"] button[type="submit"]');

    const filesBefore = filesInUploadDir();
    javaHarness('install-save-failure');
    results.compensated = await createPost(page, successTitle, attachmentPath);
    results.compensated.database = javaHarness('find-title', successTitle);
    results.compensated.newPhysicalFiles = filesInUploadDir().filter(file => !filesBefore.includes(file));
    if (!results.compensated.database.includes('POST_COUNT=0')) throw new Error('Compensated post remains in RW_POST');
    if (results.compensated.newPhysicalFiles.length) throw new Error('Compensated upload left a physical file');
    if (!results.compensated.pageText.includes('등록을 취소했습니다')) throw new Error('Compensation success message is missing');

    javaHarness('install-save-and-delete-failure');
    results.partial = await createPost(page, partialTitle, attachmentPath);
    results.partial.database = javaHarness('find-title', partialTitle);
    results.partial.newPhysicalFiles = filesInUploadDir().filter(file => !filesBefore.includes(file));
    if (!results.partial.database.includes('POST_COUNT=1') || !results.partial.database.includes('ATTACHMENT_COUNT=0')) {
      throw new Error('Partial compensation DB state was not observed');
    }
    if (results.partial.newPhysicalFiles.length) throw new Error('Partial compensation left a physical file');
    if (!results.partial.pageText.includes('관리자 확인이 필요합니다')) throw new Error('Partial compensation warning is missing');
  } finally {
    javaHarness('clear');
    javaHarness('cleanup-title', partialTitle);
    javaHarness('cleanup-title', successTitle);
    javaHarness('cleanup-user', account.loginId);
    results.sequencesAfter = javaHarness('sequences');
    await browser.close();
    fs.writeFileSync(path.join(outputDir, 'results.json'), JSON.stringify(results, null, 2));
  }
  console.log(`Attachment compensation verification passed: ${path.join(outputDir, 'results.json')}`);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
