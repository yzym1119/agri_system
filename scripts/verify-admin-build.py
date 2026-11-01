"""对实际静态正式构建做只读浏览器启动检查；需Python Playwright及Chromium。"""
import argparse
from urllib.parse import urlparse
from playwright.sync_api import sync_playwright

parser=argparse.ArgumentParser()
parser.add_argument('--url',default='http://127.0.0.1:3093')
args=parser.parse_args()
origin=args.url.rstrip('/')
if urlparse(origin).hostname not in ('127.0.0.1','localhost'):
    parser.error('仅允许本机验收环境')
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    errors=[]
    page=browser.new_page()
    page.on('pageerror',lambda error:errors.append(error.stack))
    try:
        for path in ('/login','/order'):
            page.goto(origin+path,wait_until='networkidle')
            assert not errors, '正式构建启动异常: '+str(errors)
            page.locator('input[type="password"]').wait_for(state='visible',timeout=10000)
            assert page.locator('input').count()>=2,'登录输入控件缺失'
            assert '/login' in page.url,'未登录访问订单页没有回到登录页'
        print('PASS: 正式构建登录页及未登录订单跳转，无启动异常')
    finally:
        browser.close()
