const { chromium } = require('playwright');
(async() => {
  const browser = await chromium.launch({headless:true});
  const page = await browser.newPage();
  page.on('console', msg => console.log('BROWSER_LOG', msg.type(), msg.text()));
  page.on('pageerror', err => console.log('PAGE_ERROR', err.toString()));
  await page.goto('http://localhost:8080/login');
  await page.fill('#username', 'admin');
  await page.fill('#password', 'admin123');
  await page.click('button[type="submit"]');
  await page.waitForURL('**/');
  const pageUrl = page.url();
  console.log('AFTER_LOGIN', pageUrl);
  await page.goto('http://localhost:8080/employees');
  await page.waitForLoadState('networkidle');
  const rows = await page.locator('tbody tr').count();
  console.log('ROWS', rows);
  const href = await page.locator('a[href*="/employees/"]').first().getAttribute('href').catch(()=>null);
  console.log('FIRST_HREF', href);
  if (href) {
    await page.goto('http://localhost:8080' + href);
    await page.waitForLoadState('networkidle');
    const doctext = await page.locator('text=Documents').count();
    console.log('DOCUMENTS_SECTION', doctext);
    const viewBtn = page.locator('a[data-preview-url]').first();
    console.log('VIEW_COUNT', await viewBtn.count());
    if (await viewBtn.count()) {
      await viewBtn.click();
      await page.waitForTimeout(1000);
      const modalDisplay = await page.locator('#file-preview-modal').evaluate(el => getComputedStyle(el).display);
      console.log('MODAL_DISPLAY', modalDisplay);
      const iframeSrc = await page.locator('#file-preview-iframe').getAttribute('src');
      console.log('IFRAME_SRC', iframeSrc);
    }
    const delBtn = page.locator('button[data-confirm-delete]').first();
    console.log('DEL_COUNT', await delBtn.count());
    if (await delBtn.count()) {
      await delBtn.click();
      await page.waitForTimeout(500);
      const confirmDisplay = await page.locator('#confirm-modal').evaluate(el => getComputedStyle(el).display);
      console.log('CONFIRM_DISPLAY', confirmDisplay);
      const confirmText = await page.locator('#confirm-text').textContent();
      console.log('CONFIRM_TEXT', confirmText);
    }
  }
  await browser.close();
})();
