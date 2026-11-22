#!/usr/bin/env python3
"""읽기 전용 KIND 공고 탐색 계약 진단. 운영 DB/공고 원문에 쓰지 않는다."""
import re
import urllib.parse
import urllib.request
import html as html_module

HOST = "https://kind.krx.co.kr"
CODE = "121850"
RECEIPT = "20260205001019"
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"

def fetch(path, params, post=True):
    form = urllib.parse.urlencode(params).encode("utf-8")
    url = HOST + path if post else HOST + path + "?" + form.decode()
    req = urllib.request.Request(
        url,
        data=form if post else None,
        headers={"User-Agent": UA, "Referer": HOST + "/",
                 "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"})
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            raw = response.read(900_000)
            return raw.decode("utf-8", errors="replace")
    except Exception as error:
        print("PROBE_FETCH_FAILED", path, type(error).__name__, str(error)[:120], flush=True)
        return ""

def details(filter_title, start="2026-02-04", end="2026-02-06",
            issuer="A121850"):
    p = {
        "method": "searchDetailsSub", "forward": "details_sub",
        "currentPageSize": "100", "pageIndex": "1", "orderMode": "1",
        "orderStat": "D", "repIsuSrtCd": issuer, "reportNm": filter_title,
        "lastReport": "", "bfrDsclsType": "on", "fromDate": start, "toDate": end
    }
    for key in ("searchCodeType", "allRepIsuSrtCd", "oldSearchCorpName",
                "disclosureType", "disTypevalue", "reportCd", "searchCorpName",
                "business", "marketType", "kosdaqSegment", "settlementMonth",
                "securities", "submitOblgNm", "enterprise", "reportNmTemp",
                "reportNmPop"):
        p[key] = ""
    for category in ("01", "02", "03", "04", "05", "06", "07", "08",
                     "09", "10", "11", "13", "14", "20"):
        p["disclosureType" + category] = ""
        p["pDisclosureType" + category] = ""
    return fetch("/disclosure/details.do", p)

def stock_issue(issuer, listing_from="2026-02-09", listing_to="2026-02-11"):
    p = {
        "method": "searchStockIssueList", "forward": "searchStockIssueList",
        "searchMode": "", "searchCodeType": "", "pageIndex": "1",
        "currentPageSize": "100", "orderMode": "1", "orderStat": "D",
        "marketType": "all", "listingType": "2", "searchCorpName": "",
        "comAbbrv": "", "repIsuSrtCd": "", "repIsuCd": "",
        "isurCd": issuer, "bzProcsNo": "", "paxreq": "", "outsvcno": "",
        "fromDate": listing_from, "toDate": listing_to,
    }
    return fetch("/corpgeneral/stockissuelist.do", p)

def summarize(label, html):
    rows = re.findall(r"<tr\b[^>]*>.*?</tr>", html, re.I | re.S)
    receipts = re.findall(
        r"(?:openDisclsViewer|fnDetailView)\(\s*['\"](\d{14})", html, re.I)
    companies = re.findall(r"companysummary_open\(['\"]([0-9A-Za-z]+)", html)
    text = html_module.unescape(
        re.sub(r"\s+", " ", re.sub(r"<[^>]*>", " ", html)))
    print("PROBE", label, "bytes", len(html), "rows", len(rows),
          "receipt_found", RECEIPT in html, "receipts", receipts[:12],
          "company_codes", list(dict.fromkeys(companies))[:8],
          "text_excerpt", text[:450], flush=True)
    return companies

if __name__ == "__main__":
    a = summarize("detail_title", details("상장"))
    b = summarize("detail_all", details(""))
    summarize("detail_all_long", details("", "2026-01-01", "2026-02-06"))
    summarize("detail_all_companyless", details("상장", issuer=""))
    issuer = (b or a or [""])[0]
    if issuer:
        summarize("stock_issue_issuer=" + issuer, stock_issue(issuer))
    summarize("stock_issue_all", stock_issue(""))
    summarize("stock_issue_all_month", stock_issue("", "2026-02-01", "2026-02-28"))
    summarize("main_page", fetch("/main.do", {"method": "loadInitPage"}, False))
