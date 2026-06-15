package com.pqcenter.tarsmobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.ViewGroup;

import org.json.JSONObject;

final class MarkdownChartWebView extends WebView {
    MarkdownChartWebView(Context context) {
        super(context);
        configure();
    }

    MarkdownChartWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        configure();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void configure() {
        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setNestedScrollingEnabled(false);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        addJavascriptInterface(new HeightBridge(), "tarsRenderer");
        setWebViewClient(new LinkClient());
    }

    void renderMarkdown(String markdown) {
        loadDataWithBaseURL(
            "file:///android_asset/",
            html(markdown == null ? "" : markdown),
            "text/html",
            "UTF-8",
            null
        );
    }

    private String html(String markdown) {
        return "<!doctype html><html><head>"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
            + "<script src=\"vendor/markdown-it.min.js\"></script>"
            + "<script src=\"vendor/echarts.min.js\"></script>"
            + "<style>"
            + ":root{color-scheme:light dark;font-family:-apple-system,BlinkMacSystemFont,Roboto,sans-serif;font-size:16px;"
            + "--text:#1f2933;--muted:#637083;--line:rgba(31,41,51,.14);--soft:rgba(15,23,42,.04);--link:#0b7f7a;--code:rgba(15,23,42,.06)}"
            + "html,body{margin:0;padding:0;background:transparent;overflow:hidden;height:auto;min-height:0}"
            + "body{color:var(--text);line-height:1.45;overflow-wrap:anywhere}"
            + "#content{display:block;height:auto;min-height:0;overflow:visible}"
            + ".markdown-body>:first-child{margin-top:0}.markdown-body>:last-child{margin-bottom:0}"
            + "p,ul,ol,blockquote,pre,table{margin:0 0 12px}h1,h2,h3,h4,h5,h6{margin:16px 0 8px;line-height:1.22}"
            + "h1{font-size:24px}h2{font-size:21px}h3{font-size:18px}h4,h5,h6{font-size:16px}"
            + "ul,ol{padding-left:22px}li+li{margin-top:4px}blockquote{color:var(--muted);border-left:3px solid var(--line);padding-left:12px}"
            + "a{color:var(--link);font-weight:650;text-decoration-thickness:1px;text-underline-offset:2px}"
            + "table{width:100%;border-collapse:collapse;display:block;overflow-x:auto}th,td{border:1px solid var(--line);padding:7px 9px;text-align:left;vertical-align:top;white-space:nowrap}"
            + "th{background:var(--soft);font-weight:750}pre,code{font-family:monospace;font-size:14px}code{background:var(--code);border-radius:5px;padding:1px 4px}"
            + "pre{background:var(--code);border-radius:8px;overflow-x:auto;padding:10px 12px}pre code{background:transparent;border-radius:0;padding:0}"
            + ".chart-block{width:100%;min-height:280px;border:1px solid var(--line);border-radius:8px;background:rgba(255,255,255,.86);margin:0 0 12px}"
            + ".chart-error{border:1px solid rgba(180,35,24,.32);color:#b42318}"
            + "@media(prefers-color-scheme:dark){:root{--text:#e7edf5;--muted:#9ba7b7;--line:rgba(231,237,245,.16);--soft:rgba(255,255,255,.06);--link:#39c6bd;--code:rgba(255,255,255,.08)}.chart-block{background:rgba(16,24,32,.88)}}"
            + "</style></head><body><main id=\"content\" class=\"markdown-body\"></main>"
            + "<script>"
            + "const SOURCE=" + JSONObject.quote(markdown) + ";const content=document.getElementById('content');"
            + "const md=typeof window.markdownit==='function'?window.markdownit({html:false,linkify:true,typographer:false,breaks:true}):undefined;"
            + "let lastHeight=0;if(md)md.disable('image');render();postHeight();window.addEventListener('load',postHeight);window.addEventListener('resize',postHeight);setTimeout(postHeight,80);setTimeout(postHeight,320);"
            + "if(typeof ResizeObserver==='function'){const ro=new ResizeObserver(postHeight);ro.observe(content);}"
            + "function render(){content.innerHTML='';if(!md){content.textContent=SOURCE;return;}content.innerHTML=md.render(String(SOURCE||''));sanitize();}"
            + "function sanitize(){content.querySelectorAll('a').forEach(a=>{const href=safeHref(a.getAttribute('href')||'');if(!href){a.replaceWith(document.createTextNode(a.textContent||''));return;}a.href=href;a.target='_blank';a.rel='noreferrer';});content.querySelectorAll('img').forEach(i=>i.replaceWith(document.createTextNode(i.getAttribute('alt')||'')));renderCharts();}"
            + "function renderCharts(){content.querySelectorAll('pre>code.language-chart,pre>code.language-echarts').forEach(code=>{const pre=code.parentElement;const spec=normalize(parseJson(code.textContent||''));if(!pre||!spec||typeof window.echarts!=='object'){pre&&pre.classList.add('chart-error');return;}const block=document.createElement('div');block.className='chart-block';block.setAttribute('role','img');block.setAttribute('aria-label',spec.title||spec.type+' chart');pre.replaceWith(block);const chart=window.echarts.init(block,undefined,{renderer:'canvas'});chart.setOption(optionFromSpec(spec),true);if(typeof ResizeObserver==='function'){new ResizeObserver(()=>{chart.resize();postHeight();}).observe(block);}});}"
            + "function parseJson(s){try{return JSON.parse(s)}catch{return undefined}}"
            + "function normalize(v){if(!v||typeof v!=='object')return undefined;const type=['line','bar','pie'].includes(v.type)?v.type:undefined;const input=Array.isArray(v.series)?v.series:(Array.isArray(v.data)?[{name:v.title,data:v.data}]:[]);const series=input.map(normalizeSeries).filter(Boolean);if(!type||series.length===0)return undefined;const count=Math.max(...series.map(s=>s.data.length));return{type,title:text(v.title),labels:labels(v.labels,count),series};}"
            + "function normalizeSeries(v){if(!v||typeof v!=='object'||!Array.isArray(v.data))return undefined;const data=v.data.map(Number).filter(Number.isFinite).slice(0,100);return data.length?{name:text(v.name)||'Series',data}:undefined;}"
            + "function labels(v,count){const items=Array.isArray(v)?v.map(text).filter(Boolean):[];return Array.from({length:count},(_,i)=>items[i]||String(i+1)).slice(0,100);}"
            + "function text(v){return typeof v==='string'?v.trim().slice(0,120):''}"
            + "function optionFromSpec(spec){const dark=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches;const txt=dark?'#d8e1ec':'#243241';const muted=dark?'#9ba7b7':'#637083';const line=dark?'rgba(216,225,236,.18)':'rgba(36,50,65,.14)';const base={animation:false,color:['#14b8a6','#2563eb','#f59e0b','#ef4444','#8b5cf6'],textStyle:{color:txt},title:spec.title?{text:spec.title,left:'center',textStyle:{color:txt,fontSize:14,fontWeight:700}}:undefined,tooltip:{trigger:spec.type==='pie'?'item':'axis',renderMode:'richText'},legend:{bottom:0,textStyle:{color:muted}}};if(spec.type==='pie'){const s=spec.series[0];return{...base,series:[{name:s.name,type:'pie',radius:['35%','65%'],center:['50%','48%'],data:s.data.map((value,i)=>({name:spec.labels[i]||String(i+1),value}))}]};}return{...base,grid:{left:42,right:20,top:spec.title?54:20,bottom:48},xAxis:{type:'category',data:spec.labels,axisLabel:{color:muted},axisLine:{lineStyle:{color:line}},splitLine:{lineStyle:{color:line}}},yAxis:{type:'value',axisLabel:{color:muted},axisLine:{lineStyle:{color:line}},splitLine:{lineStyle:{color:line}}},series:spec.series.map(s=>({name:s.name,type:spec.type,data:s.data,smooth:spec.type==='line'}))};}"
            + "function safeHref(href){try{const u=new URL(String(href).trim(),window.location.href);return['http:','https:','mailto:'].includes(u.protocol)?u.href:undefined;}catch{return undefined}}"
            + "function postHeight(){const rect=content.getBoundingClientRect();const h=Math.max(32,Math.ceil(rect.height),Math.ceil(content.scrollHeight),Math.ceil(content.offsetHeight))+8;if(Math.abs(h-lastHeight)<2)return;lastHeight=h;window.tarsRenderer&&window.tarsRenderer.postHeight(h);}"
            + "</script></body></html>";
    }

    private final class HeightBridge {
        @JavascriptInterface
        public void postHeight(final int cssHeight) {
            post(() -> {
                float scale = getResources().getDisplayMetrics().density;
                int measuredHeight = Math.round(cssHeight * scale);
                int nextHeight = Math.min(Math.max(measuredHeight, dp(32)), dp(60000));
                ViewGroup.LayoutParams params = getLayoutParams();
                if (params != null && Math.abs(params.height - nextHeight) > 2) {
                    params.height = nextHeight;
                    setLayoutParams(params);
                }
            });
        }
    }

    private final class LinkClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

            if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme)) {
                view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }

            return true;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
