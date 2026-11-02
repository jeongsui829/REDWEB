const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_ADMIN_OUTPUT || 'target/admin-ui-verification');
const classpath = process.env.SCUREDWEB_VERIFICATION_CLASSPATH || [
  'target/verification-classes',
  'target/app-classes.jar',
  process.env.SCUREDWEB_OJDBC_JAR || path.join(os.homedir(), '.m2', 'repository', 'com', 'oracle',
    'database', 'jdbc', 'ojdbc17', '23.26.3.0.0', 'ojdbc17-23.26.3.0.0.jar')
].join(path.delimiter);

function javaHarness(command, value) {
  const result = spawnSync('java', ['-cp', classpath, 'AttachmentFaultHarness', command, value], { encoding: 'utf8' });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout);
  return result.stdout.trim();
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

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
  const account = {
    loginId: `verify_admin_${suffix}`,
    userName: 'Admin Verification',
    nickname: `verify_admin_${suffix}`,
    email: `verify_admin_${suffix}@example.invalid`,
    phone: '010-0000-0000',
    password: `V!${crypto.randomBytes(12).toString('base64url')}9a`,
    securityQuestion: 'verification only',
    securityAnswer: crypto.randomBytes(12).toString('hex')
  };
  const options = { headless: true };
  const browserPath = executablePath();
  if (browserPath) options.executablePath = browserPath;
  const browser = await chromium.launch(options);
  let registration;
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    await page.goto(baseUrl + '/register', { waitUntil: 'networkidle' });
    for (const [name, value] of Object.entries(account)) await page.locator(`[name="${name}"]`).fill(value);
    registration = await submit(page, 'form[action$="/register"] button[type="submit"]');
    await page.close();
    javaHarness('promote-admin', account.loginId);

    const verification = spawnSync(process.execPath, ['scripts/verify-external-test-ui.js'], {
      cwd: process.cwd(),
      encoding: 'utf8',
      env: {
        ...process.env,
        SCUREDWEB_LOGIN_ID: account.loginId,
        SCUREDWEB_LOGIN_PASSWORD: account.password,
        SCUREDWEB_ADMIN_LOGIN_ID: account.loginId,
        SCUREDWEB_ADMIN_LOGIN_PASSWORD: account.password,
        SCUREDWEB_DATABASE_STATE: 'local-oracle-xe-connected',
        SCUREDWEB_UI_OUTPUT: outputDir
      }
    });
    fs.writeFileSync(path.join(outputDir, 'admin-bootstrap.json'), JSON.stringify({
      registration,
      verificationExitCode: verification.status,
      verificationStdout: verification.stdout.trim(),
      verificationStderr: verification.stderr.trim()
    }, null, 2));
    if (verification.status !== 0) throw new Error(verification.stderr || verification.stdout);
  } finally {
    await browser.close();
    javaHarness('cleanup-user', account.loginId);
  }
  console.log(`Admin UI verification passed: ${path.join(outputDir, 'results.json')}`);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
