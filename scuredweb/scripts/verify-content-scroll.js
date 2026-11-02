const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

const baseUrl = (process.env.SCUREDWEB_BASE_URL || 'http://127.0.0.1:18080/scuredweb').replace(/\/$/, '');
const outputDir = path.resolve(process.env.SCUREDWEB_SCROLL_OUTPUT || 'target/scroll-verification');
const viewports = [360, 768, 1280, 1440].map(width => ({ width, height: 900 }));
const pages = [
  { key: 'home', route: '/', selector: '.home-left-rail, .home-right-rail', expectedCount: 2 },
  { key: 'board-free', route: '/board/list?boardCode=free', selector: '.board-shortcuts', expectedCount: 3 },
  { key: 'board-project-share', route: '/board/list?boardCode=project-share', selector: '.board-shortcuts', expectedCount: 3 }
];

function chromiumExecutable() {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE) {
    return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE;
  }
  const candidates = process.platform === 'win32'
    ? [
        'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
        'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
        'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
      ]
    : [];
  return candidates.find(candidate => fs.existsSync(candidate));
}

function rectanglesOverlap(first, second) {
  return first.left < second.right
    && first.right > second.left
    && first.top < second.bottom
    && first.bottom > second.top;
}

function overlapPairs(elements) {
  const pairs = [];
  for (let first = 0; first < elements.length; first++) {
    for (let second = first + 1; second < elements.length; second++) {
      if (rectanglesOverlap(elements[first].rect, elements[second].rect)) {
        pairs.push([first, second]);
      }
    }
  }
  return pairs;
}

async function snapshot(page, selector) {
  return page.evaluate(targetSelector => {
    const round = value => Math.round(value * 100) / 100;
    const elements = [...document.querySelectorAll(targetSelector)].map((element, index) => {
      const rect = element.getBoundingClientRect();
      return {
        index,
        position: getComputedStyle(element).position,
        rect: {
          top: round(rect.top),
          bottom: round(rect.bottom),
          left: round(rect.left),
          right: round(rect.right),
          width: round(rect.width),
          height: round(rect.height)
        }
      };
    });
    const footer = document.querySelector('.site-footer');
    const footerRect = footer ? footer.getBoundingClientRect() : null;
    return {
      scrollY: round(window.scrollY),
      pageHeight: document.documentElement.scrollHeight,
      maxScrollY: Math.max(0, document.documentElement.scrollHeight - window.innerHeight),
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: document.documentElement.scrollWidth,
      elements,
      footer: footerRect ? { top: round(footerRect.top), bottom: round(footerRect.bottom) } : null
    };
  }, selector);
}

async function main() {
  fs.mkdirSync(outputDir, { recursive: true });
  const launchOptions = { headless: true };
  const executablePath = chromiumExecutable();
  if (executablePath) {
    launchOptions.executablePath = executablePath;
  }

  const browser = await chromium.launch(launchOptions);
  const results = [];
  const failures = [];

  try {
    for (const viewport of viewports) {
      for (const pageSpec of pages) {
        const page = await browser.newPage({ viewport });
        const client = await page.context().newCDPSession(page);
        await client.send('Network.enable');
        await client.send('Network.setCacheDisabled', { cacheDisabled: true });

        const response = await page.goto(baseUrl + pageSpec.route, { waitUntil: 'networkidle' });
        await page.reload({ waitUntil: 'networkidle' });
        await page.locator(pageSpec.selector).first().waitFor({ state: 'visible' });

        await page.evaluate(() => window.scrollTo(0, 0));
        await page.waitForTimeout(150);
        const top = await snapshot(page, pageSpec.selector);
        await page.screenshot({ path: path.join(outputDir, `${pageSpec.key}-${viewport.width}-top.png`) });

        await page.evaluate(() => window.scrollTo(0, 800));
        await page.waitForTimeout(200);
        const middle = await snapshot(page, pageSpec.selector);
        await page.screenshot({ path: path.join(outputDir, `${pageSpec.key}-${viewport.width}-middle.png`) });

        await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
        await page.waitForTimeout(200);
        const bottom = await snapshot(page, pageSpec.selector);
        await page.screenshot({ path: path.join(outputDir, `${pageSpec.key}-${viewport.width}-bottom.png`) });

        const positions = [...new Set(top.elements.map(element => element.position))];
        const flowErrors = top.elements.map((element, index) => {
          const observedDelta = middle.elements[index].rect.top - element.rect.top;
          return Math.round((observedDelta + middle.scrollY) * 100) / 100;
        });
        const overlaps = {
          top: pageSpec.key.startsWith('board') ? overlapPairs(top.elements) : [],
          middle: pageSpec.key.startsWith('board') ? overlapPairs(middle.elements) : [],
          bottom: pageSpec.key.startsWith('board') ? overlapPairs(bottom.elements) : []
        };
        const result = {
          page: pageSpec.key,
          route: pageSpec.route,
          viewport,
          status: response.status(),
          positions,
          top,
          middle,
          bottom,
          flowErrors,
          overlaps,
          horizontalOverflow: Math.max(top.scrollWidth - top.clientWidth, middle.scrollWidth - middle.clientWidth, bottom.scrollWidth - bottom.clientWidth)
        };
        results.push(result);

        const prefix = `${pageSpec.key}@${viewport.width}`;
        if (response.status() !== 200) failures.push(`${prefix}: HTTP ${response.status()}`);
        if (top.elements.length !== pageSpec.expectedCount) failures.push(`${prefix}: expected ${pageSpec.expectedCount} targets, found ${top.elements.length}`);
        if (positions.some(position => position === 'sticky' || position === 'fixed')) failures.push(`${prefix}: fixed content position ${positions.join(',')}`);
        if (top.maxScrollY > 0 && middle.scrollY < 1) failures.push(`${prefix}: scrollTo(0, 800) did not move a scrollable page`);
        if (flowErrors.some(error => Math.abs(error) > 2)) failures.push(`${prefix}: element did not follow document flow (${flowErrors.join(',')})`);
        if ([...overlaps.top, ...overlaps.middle, ...overlaps.bottom].length) failures.push(`${prefix}: board shortcut overlap detected`);
        if (result.horizontalOverflow > 1) failures.push(`${prefix}: horizontal overflow ${result.horizontalOverflow}px`);

        console.log(JSON.stringify({
          page: pageSpec.key,
          width: viewport.width,
          positions,
          top: top.elements.map(element => element.rect.top),
          middleScrollY: middle.scrollY,
          maxScrollY: top.maxScrollY,
          middle: middle.elements.map(element => element.rect.top),
          bottomScrollY: bottom.scrollY,
          bottom: bottom.elements.map(element => element.rect.top),
          flowErrors,
          overlaps,
          horizontalOverflow: result.horizontalOverflow
        }));
        await page.close();
      }
    }
  } finally {
    await browser.close();
  }

  fs.writeFileSync(path.join(outputDir, 'results.json'), JSON.stringify({ baseUrl, results, failures }, null, 2));
  if (failures.length) {
    console.error(`Scroll verification failed:\n${failures.join('\n')}`);
    process.exitCode = 1;
  } else {
    console.log(`Scroll verification passed (${results.length} page/viewport combinations).`);
  }
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
