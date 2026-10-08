"""Read-only page smoke checks against a running demo, including real login/CSRF/logout.
Usage: python qa/http-smoke.py http://127.0.0.1:8080
Login may transparently migrate historical password hashes to BCrypt.
"""
import sys, json, re
from http.cookiejar import CookieJar
from urllib.request import build_opener, HTTPCookieProcessor, Request
from urllib.parse import urlencode
from urllib.error import HTTPError

base = sys.argv[1].rstrip('/') if len(sys.argv) > 1 else 'http://127.0.0.1:8080'
results=[]
def client(): return build_opener(HTTPCookieProcessor(CookieJar()))
def get(c,path):
    with c.open(base+path,timeout=10) as response:
        assert response.status == 200
        return response.read().decode('utf-8')
def token(page):
    m=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page)
    assert m, 'Missing rendered CSRF token'
    return m.group(1)
def post(c,path,data):
    return c.open(Request(base+path,urlencode(data).encode(),method='POST'),timeout=10)
c=client()
try:
    post(c,'/login',{'account':'admin','password':'admin123','type':'STAFF'})
    raise AssertionError('POST without CSRF was accepted')
except HTTPError as e:
    assert e.code == 403
    results.append('CSRF rejection: PASS')
roles=[('admin','admin123','STAFF','/admin',[
    '/admin','/admin/stats?startDate=2020-01-01&endDate=2020-01-03','/admin/seat','/admin/billing',
    '/admin/member','/admin/member/detail?id=1','/admin/product','/admin/equipment','/admin/reservation','/admin/user','/admin/notice']),
    ('cashier01','cashier123','STAFF','/cashier',[
    '/cashier','/cashier/open-card','/cashier/open-seat','/cashier/checkout','/cashier/product','/cashier/recharge','/cashier/rental','/cashier/reservation','/cashier/notices']),
    ('13800001111','123456','MEMBER','/member',[
    '/member','/member/balance','/member/recharge','/member/sessions','/member/fees?orderPage=1','/member/rentals',
    '/member/reservation','/member/notices','/member/profile','/member/orders'])]
for account,password,kind,home,paths in roles:
    c=client(); csrf=token(get(c,'/login'))
    with post(c,'/login',{'type':kind,'account':account,'password':password,'_csrf':csrf}) as response:
        assert response.geturl().endswith(home), 'Login did not reach role home'
    for path in paths:
        page=get(c,path)
        assert '<html' in page and 'name="_csrf"' in page
        assert '系统发生了一个错误' not in page
        results.append(path+': PASS')
    if home != '/admin':
        for path in ['/admin','/%61dmin','/%61dmin/member']:
            with c.open(base+path,timeout=10) as response:
                assert response.geturl().endswith(home), 'Role bypass: '+path
            results.append(home+' rejects '+path+': PASS')
    csrf=token(get(c,home))
    with post(c,'/logout',{'_csrf':csrf}) as response:
        assert response.geturl().endswith('/login')
    results.append(home+' login/logout: PASS')
print(json.dumps({'checks':len(results),'results':results},ensure_ascii=False,indent=2))
