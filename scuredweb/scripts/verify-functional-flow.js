const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const { spawnSync } = require('child_process');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_FUNCTIONAL_OUTPUT || 'target/functional-verification');
const classpath = process.env.SCUREDWEB_VERIFICATION_CLASSPATH || [
  'target/verification-classes',
  'target/app-classes.jar',
  process.env.SCUREDWEB_OJDBC_JAR || path.join(os.homedir(), '.m2', 'repository', 'com', 'oracle',
    'database', 'jdbc', 'ojdbc17', '23.26.3.0.0', 'ojdbc17-23.26.3.0.0.jar')
].join(path.delimiter);

function javaHarness(mode, value) {
  const result = spawnSync('java', ['-cp', classpath, 'AttachmentFaultHarness', mode, value], {
    cwd: process.cwd(), encoding: 'utf8'
  });
  if (result.status !== 0) throw new Error(result.stderr || result.stdout || 'Verification cleanup failed');
  return result.stdout.trim();
}

function browserExecutable() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  const candidates = process.platform === 'win32' ? [
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ] : [];
  return candidates.find(candidate => fs.existsSync(candidate));
}

async function submit(page, selector) {
  const submitUrl = await page.locator(selector).evaluate(element => element.form?.action || '');
  const [postResponse, navigationResponse] = await Promise.all([
    page.waitForResponse(response => response.request().method() === 'POST'
      && (!submitUrl || response.url() === submitUrl)),
    page.waitForNavigation({ waitUntil: 'networkidle' }),
    page.locator(selector).click()
  ]);
  const setCookie = (await postResponse.headerValues('set-cookie')).join('; ');
  return {
    postUrl: postResponse.url(),
    postStatus: postResponse.status(),
    finalStatus: navigationResponse?.status(),
    finalUrl: page.url(),
    cookieAttributes: {
      present: Boolean(setCookie),
      secure: /(?:^|;)\s*Secure(?:;|$)/i.test(setCookie),
      httpOnly: /(?:^|;)\s*HttpOnly(?:;|$)/i.test(setCookie),
      sameSiteLax: /(?:^|;)\s*SameSite=Lax(?:;|$)/i.test(setCookie)
    }
  };
}

function postIdFromUrl(url) {
  const value = new URL(url).searchParams.get('id');
  return value && /^\d+$/.test(value) ? Number(value) : null;
}

async function createPost(page, title, content, files = {}) {
  await page.goto(baseUrl + '/board/write?boardCode=free', { waitUntil: 'networkidle' });
  await page.locator('select[name="boardCode"]').selectOption('free');
  await page.locator('input[name="title"]').fill(title);
  await page.locator('textarea[name="content"]').fill(content);
  if (files.attachment) await page.locator('input[name="attachments"]').setInputFiles(files.attachment);
  if (files.contentImage) await page.locator('input[name="contentImages"]').setInputFiles(files.contentImage);
  const result = await submit(page, 'form[action$="/board/write"] button[type="submit"]');
  return { ...result, postId: postIdFromUrl(result.finalUrl) };
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const suffix = `${Date.now().toString(36)}${crypto.randomBytes(3).toString('hex')}`;
  const account = {
    loginId: `verify_${suffix}`,
    userName: 'Functional Verification',
    nickname: `verify_${suffix}`,
    email: `verify_${suffix}@example.invalid`,
    phone: '',
    password: `V!${crypto.randomBytes(12).toString('base64url')}9a`,
    securityQuestion: 'verification only',
    securityAnswer: crypto.randomBytes(12).toString('hex')
  };
  const attachmentPath = path.join(outputDir, 'upload-verification.txt');
  fs.writeFileSync(attachmentPath, `REDWEB upload verification ${new Date().toISOString()}\n`);
  const contentImagePath = path.resolve('src/main/webapp/assets/images/profile-default.png');
  const results = {
    baseUrl,
    databaseState: process.env.SCUREDWEB_DATABASE_STATE || 'unspecified',
    generatedAt: new Date().toISOString(),
    invalidPhoneRegistration: null,
    invalidPhoneDatabase: null,
    registration: null,
    registrationDatabase: null,
    login: null,
    plainPost: null,
    attachmentPost: null,
    comment: null,
    reply: null,
    download: null,
    logout: null,
    protectedAfterLogin: null,
    regularUserAdminAccess: null,
    protectedAfterLogout: null,
    deletion: [],
    withdrawal: null,
    cleanup: null,
    failures: []
  };
  results.sequencesBefore = javaHarness('sequences', 'unused');

  const launchOptions = { headless: true };
  const executablePath = browserExecutable();
  if (executablePath) launchOptions.executablePath = executablePath;
  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  try {
    await page.goto(baseUrl + '/register', { waitUntil: 'networkidle' });
    for (const [name, value] of Object.entries(account)) {
      await page.locator(`[name="${name}"]`).fill(value);
    }
    await page.locator('[name="phone"]').fill('invalid-phone');
    results.invalidPhoneRegistration = await submit(page, 'form[action$="/register"] button[type="submit"]');
    if (!results.invalidPhoneRegistration.finalUrl.startsWith(baseUrl + '/register')) {
      results.failures.push('invalid phone was not rejected');
      throw new Error('invalid phone was not rejected');
    }
    results.invalidPhoneDatabase = javaHarness('find-user', account.loginId);
    if (!results.invalidPhoneDatabase.includes('USER_COUNT=0')) {
      results.failures.push('invalid phone created a database user row');
      throw new Error('invalid phone created a database user row');
    }

    await page.goto(baseUrl + '/register', { waitUntil: 'networkidle' });
    for (const [name, value] of Object.entries(account)) {
      await page.locator(`[name="${name}"]`).fill(value);
    }
    results.registration = await submit(page, 'form[action$="/register"] button[type="submit"]');
    if (!results.registration.finalUrl.startsWith(baseUrl + '/login')) {
      results.failures.push('registration did not redirect to login');
      throw new Error('registration did not redirect to login');
    }
    results.registrationDatabase = javaHarness('find-user', account.loginId);
    if (!results.registrationDatabase.includes('USER_COUNT=1')
        || !results.registrationDatabase.includes('PHONE_IS_NULL=1')) {
      results.failures.push('phone-less registration was not persisted with a null phone');
      throw new Error('phone-less registration was not persisted with a null phone');
    }

    await page.locator('input[name="loginId"]').fill(account.loginId);
    await page.locator('input[name="password"]').fill(account.password);
    results.login = await submit(page, 'form[action$="/login"] button[type="submit"]');
    if (!results.login.finalUrl.startsWith(baseUrl + '/home')) {
      results.failures.push('login did not redirect to home');
      throw new Error('login did not redirect to home');
    }
    const expectedSecure = new URL(baseUrl).protocol === 'https:';
    const loginCookie = results.login.cookieAttributes;
    if (!loginCookie.present || !loginCookie.httpOnly || !loginCookie.sameSiteLax
        || loginCookie.secure !== expectedSecure) {
      results.failures.push('login session cookie attributes do not match the selected transport');
      throw new Error('login session cookie attributes do not match the selected transport');
    }

    const protectedAfterLoginResponse = await page.goto(baseUrl + '/board/write', { waitUntil: 'networkidle' });
    results.protectedAfterLogin = {
      status: protectedAfterLoginResponse?.status(),
      finalUrl: page.url()
    };
    if (!page.url().startsWith(baseUrl + '/board/write')) {
      results.failures.push('authenticated session did not retain access to a protected URL');
      throw new Error('authenticated session did not retain access to a protected URL');
    }

    const regularAdminResponse = await context.request.get(baseUrl + '/admin/dashboard', {
      maxRedirects: 0
    });
    results.regularUserAdminAccess = { status: regularAdminResponse.status() };
    if (regularAdminResponse.status() !== 403) {
      results.failures.push(`regular user admin URL returned ${regularAdminResponse.status()}`);
      throw new Error('regular user was not blocked from the admin URL');
    }

    results.plainPost = await createPost(
      page,
      `Plain verification ${suffix}`,
      `Plain post persisted by functional verification ${suffix}.`
    );
    if (!results.plainPost.postId) {
      results.failures.push('plain post did not redirect to detail');
      throw new Error('plain post did not redirect to detail');
    }
    results.plainPost.database = javaHarness('find-title', `Plain verification ${suffix}`);
    if (!results.plainPost.database.includes('POST_COUNT=1')) {
      results.failures.push('plain post database row was not found');
      throw new Error('plain post database row was not found');
    }

    results.attachmentPost = await createPost(
      page,
      `Attachment verification ${suffix}`,
      `Attachment post persisted by functional verification ${suffix}.`,
      { attachment: attachmentPath, contentImage: contentImagePath }
    );
    if (!results.attachmentPost.postId) {
      results.failures.push('attachment post did not redirect to detail');
      throw new Error('attachment post did not redirect to detail');
    }

    const downloadHref = await page.locator('a[href*="/files/download"]').first().getAttribute('href');
    const inlineImageCount = await page.locator('.detail-content img').count();
    if (!downloadHref) {
      results.failures.push('attachment download link missing');
    } else {
      const response = await context.request.get(new URL(downloadHref, baseUrl).toString());
      results.download = {
        status: response.status(),
        contentType: response.headers()['content-type'],
        dispositionPresent: Boolean(response.headers()['content-disposition'])
      };
      if (!response.ok()) results.failures.push(`attachment download returned ${response.status()}`);
    }
    results.attachmentPost.inlineImageCount = inlineImageCount;
    if (inlineImageCount < 1) results.failures.push('inline content image missing');

    await page.goto(`${baseUrl}/board/detail?id=${results.plainPost.postId}`, { waitUntil: 'networkidle' });
    const commentText = `Comment verification ${suffix}`;
    await page.locator('.comment-form textarea[name="content"]').fill(commentText);
    const commentResponse = await submit(page, '.comment-form button[type="submit"]');
    const commentVisible = await page.getByText(commentText, { exact: true }).count() > 0;
    results.comment = { ...commentResponse, visible: commentVisible };
    if (!commentVisible) results.failures.push('comment not visible after POST');

    const replyText = `Reply verification ${suffix}`;
    await page.locator('.reply-toggle[data-target]').first().click();
    const replyForm = page.locator('.reply-form').first();
    await replyForm.locator('textarea[name="content"]').fill(replyText);
    const replyResponse = await submit(page, '.reply-form:not([hidden]) button[type="submit"]');
    const replyVisible = await page.getByText(replyText, { exact: true }).count() > 0;
    results.reply = { ...replyResponse, visible: replyVisible };
    if (!replyVisible) results.failures.push('reply not visible after POST');

    await page.goto(`${baseUrl}/board/list?boardCode=free`, { waitUntil: 'networkidle' });
    results.plainPost.listed = await page.getByText(`Plain verification ${suffix}`, { exact: true }).count() > 0;
    results.attachmentPost.listed = await page.getByText(`Attachment verification ${suffix}`, { exact: true }).count() > 0;
    if (!results.plainPost.listed || !results.attachmentPost.listed) results.failures.push('created post missing from board list');

    results.logout = await submit(page, 'form[action$="/logout"] button[type="submit"]');
    const loggedIn = await page.locator('.site-header').getAttribute('data-logged-in');
    results.logout.loggedInAfter = loggedIn;
    if (loggedIn !== 'false') results.failures.push('logout did not clear login state');

    const protectedResponse = await page.goto(baseUrl + '/board/write', { waitUntil: 'networkidle' });
    results.protectedAfterLogout = { status: protectedResponse?.status(), finalUrl: page.url() };
    if (!page.url().startsWith(baseUrl + '/login')) results.failures.push('protected URL remained accessible after logout');

    await page.locator('input[name="loginId"]').fill(account.loginId);
    await page.locator('input[name="password"]').fill(account.password);
    await submit(page, 'form[action$="/login"] button[type="submit"]');

    for (const postId of [results.attachmentPost.postId, results.plainPost.postId]) {
      await page.goto(`${baseUrl}/board/detail?id=${postId}`, { waitUntil: 'networkidle' });
      results.deletion.push(await submit(page, 'form[action$="/board/delete"] button[type="submit"]'));
    }

    await page.goto(baseUrl + '/mypage/withdraw', { waitUntil: 'networkidle' });
    page.once('dialog', dialog => dialog.accept());
    results.withdrawal = await submit(page, 'form[action$="/mypage/withdraw"] button[type="submit"]');
    if (!results.withdrawal.finalUrl.startsWith(baseUrl + '/login')) {
      results.failures.push('withdrawal did not complete');
    }
  } catch (error) {
    if (!results.failures.includes(error.message)) results.failures.push(error.message);
    throw error;
  } finally {
    await browser.close();
    results.sequencesAfter = javaHarness('sequences', 'unused');
    results.cleanup = javaHarness('cleanup-user', account.loginId);
    fs.writeFileSync(path.join(outputDir, 'results.json'), JSON.stringify(results, null, 2));
  }

  if (results.failures.length) {
    console.error(results.failures.join('\n'));
    process.exitCode = 1;
  } else {
    console.log(`Functional verification passed; results: ${path.join(outputDir, 'results.json')}`);
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
