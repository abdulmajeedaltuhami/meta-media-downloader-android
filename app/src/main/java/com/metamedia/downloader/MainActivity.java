package com.metamedia.downloader;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.mapper.VideoInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "MetaVideosBot/files");
    private final File statusDir = new File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses");

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestPermissionsIfNeeded();
        try { YoutubeDL.getInstance().init(this); FFmpeg.getInstance().init(this); }
        catch (Exception e) { Toast.makeText(this, "تعذر تهيئة محرك التنزيل: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        outputDir.mkdirs();
        webView = new WebView(this);
        WebSettings s = webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(true); s.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient()); webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge"); setContentView(webView); webView.loadUrl("file:///android_asset/index.html");
    }
    private void requestPermissionsIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 10);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES}, 11);
    }
    private void js(String name, String json) { runOnUiThread(() -> webView.evaluateJavascript("window." + name + "(" + JSONObject.quote(json) + ")", null)); }
    private static String esc(String s) { return JSONObject.quote(s == null ? "" : s); }

    public class Bridge {
        @JavascriptInterface public void inspect(String raw) { worker.submit(() -> { try { com.yausername.youtubedl_android.YoutubeDLRequest r = new YoutubeDLRequest(raw); VideoInfo i = YoutubeDL.getInstance().getInfo(r); JSONObject o=new JSONObject(); o.put("ok",true); o.put("title",i.getTitle()); o.put("uploader",i.getUploader()); o.put("duration",i.getDuration()); js("onInspect", o.toString()); } catch(Exception e) { js("onInspect", error(e).toString()); } }); }
        @JavascriptInterface public void download(String url, String quality, boolean subtitles, boolean playlist) { worker.submit(() -> { try { String key = safeKey(url, quality, subtitles, playlist); File marker = new File(outputDir, "."+key+".done"); if(marker.exists()) { js("onDownload", new JSONObject().put("ok",true).put("duplicate",true).toString()); return; } YoutubeDLRequest r=new YoutubeDLRequest(url); String fmt=quality.equals("audio")?"bestaudio/best":quality.equals("best")?"bestvideo+bestaudio/best":"bestvideo[height<="+quality+"]+bestaudio/best[height<="+quality+"]/best[height<="+quality+"]"; r.addOption("-f",fmt); r.addOption("-o",outputDir.getAbsolutePath()+"/%(title).180B [%(id)s].%(ext)s"); r.addOption("--no-playlist", playlist?"false":"true"); r.addOption("--merge-output-format","mp4"); if(subtitles){r.addOption("--write-subs");r.addOption("--write-auto-subs");r.addOption("--sub-langs","ar,en");r.addOption("--embed-subs");} if(quality.equals("audio")){r.addOption("-x");r.addOption("--audio-format","mp3");} js("onProgress", "0"); YoutubeDL.getInstance().execute(r, "download", (p, eta, line) -> { js("onProgress", String.valueOf(p)); return kotlin.Unit.INSTANCE; }); marker.createNewFile(); js("onDownload", new JSONObject().put("ok",true).put("duplicate",false).toString()); } catch(Exception e) { js("onDownload", error(e).toString()); } }); }
        @JavascriptInterface public void statuses() { try { JSONArray a=new JSONArray(); if(statusDir.exists()){File[] fs=statusDir.listFiles(); if(fs!=null) for(File f:fs) if(f.isFile()&&!f.getName().startsWith(".")) a.put(new JSONObject().put("name",f.getName()).put("size",f.length()));} js("onStatuses",new JSONObject().put("ok",true).put("files",a).toString()); } catch(Exception e){js("onStatuses",error(e).toString());} }
        @JavascriptInterface public void copyStatus(String name) { try { File src=new File(statusDir,new File(name).getName()); File destDir=new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"MetaVideosBot/WhatsAppStatus"); destDir.mkdirs(); Files.copy(src.toPath(),new File(destDir,src.getName()).toPath(),StandardCopyOption.REPLACE_EXISTING); js("onCopy",new JSONObject().put("ok",true).put("name",src.getName()).toString()); } catch(Exception e){js("onCopy",error(e).toString());} }
        @JavascriptInterface public void open(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch(Exception ignored){} }
        @JavascriptInterface public String health() { return "Download/MetaVideosBot/files"; }
        private JSONObject error(Exception e){try{return new JSONObject().put("ok",false).put("error",e.getMessage()==null?e.toString():e.getMessage());}catch(Exception x){return new JSONObject();}}
        private String safeKey(String u,String q,boolean s,boolean p){return Integer.toHexString((u+"|"+q+"|"+s+"|"+p).hashCode());}
    }
    @Override protected void onDestroy(){worker.shutdownNow(); if(webView!=null) webView.destroy(); super.onDestroy();}
}
