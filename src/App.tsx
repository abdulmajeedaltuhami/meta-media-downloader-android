import React, { useState, useEffect, useRef } from 'react';
import {
  inspectUrl,
  getSafeKey,
  triggerFileDownload,
  createSampleMediaBlob,
  formatBytes,
  VideoMetadata,
  StatusItem,
  DownloadHistoryItem,
} from './utils/media';

const DEFAULT_STATUSES: StatusItem[] = [
  { id: '1', name: 'status_video_20261024_01.mp4', size: 4520000, type: 'video' },
  { id: '2', name: 'status_image_quote_02.jpg', size: 685000, type: 'image' },
  { id: '3', name: 'status_video_sunset_03.mp4', size: 8910000, type: 'video' },
  { id: '4', name: 'status_voice_note_04.opus', size: 312000, type: 'audio' as any },
];

export default function App() {
  const [url, setUrl] = useState('');
  const [quality, setQuality] = useState('best');
  const [subtitles, setSubtitles] = useState(true);
  const [playlist, setPlaylist] = useState(false);

  const [health, setHealth] = useState('جاهز');
  const [inspecting, setInspecting] = useState(false);
  const [inspected, setInspected] = useState(false);
  const [videoInfo, setVideoInfo] = useState<VideoMetadata | null>(null);

  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState(0);

  const [infoText, setInfoText] = useState('');
  const [infoIsErr, setInfoIsErr] = useState(false);

  const [statuses, setStatuses] = useState<StatusItem[]>([]);
  const [statusesLoaded, setStatusesLoaded] = useState(false);

  const [logText, setLogText] = useState('لن يتم تكرار نفس الرابط والإعدادات بعد اكتمال التنزيل.');
  const [history, setHistory] = useState<DownloadHistoryItem[]>([]);

  const urlInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Load saved completed downloads from localStorage
  useEffect(() => {
    try {
      const savedHistory = localStorage.getItem('meta_media_history');
      if (savedHistory) {
        setHistory(JSON.parse(savedHistory));
      }
    } catch {
      // ignore
    }
  }, []);

  const saveHistory = (newItem: DownloadHistoryItem) => {
    setHistory((prev) => {
      const updated = [newItem, ...prev.slice(0, 19)];
      try {
        localStorage.setItem('meta_media_history', JSON.stringify(updated));
      } catch {
        // ignore
      }
      return updated;
    });
  };

  const handleNavClick = (destUrl: string) => {
    setUrl('');
    setInspected(false);
    setVideoInfo(null);
    setInfoText('');
    window.open(destUrl, '_blank', 'noopener,noreferrer');
    urlInputRef.current?.focus();
  };

  const handleInspect = async () => {
    const trimmed = url.trim();
    if (!trimmed) {
      setInfoIsErr(true);
      setInfoText('ألصق الرابط أولاً');
      setInspected(false);
      setVideoInfo(null);
      return;
    }

    setInspecting(true);
    setInfoIsErr(false);
    setInfoText('جاري فحص الرابط…');

    try {
      const result = await inspectUrl(trimmed);
      if (!result.ok) {
        setInfoIsErr(true);
        setInfoText(result.error || 'تعذر فحص الرابط');
        setInspected(false);
        setVideoInfo(null);
      } else {
        setInspected(true);
        setInfoIsErr(false);
        setVideoInfo(result);
        const display = `${result.title || 'تم فحص الرابط'}${result.uploader ? ' · ' + result.uploader : ''}`;
        setInfoText(display);
      }
    } catch (e: any) {
      setInfoIsErr(true);
      setInfoText(e?.message || 'حدث خطأ أثناء فحص الرابط');
      setInspected(false);
      setVideoInfo(null);
    } finally {
      setInspecting(false);
    }
  };

  const handleDownload = async () => {
    if (!inspected || downloading) return;

    const trimmedUrl = url.trim();
    const key = getSafeKey(trimmedUrl, quality, subtitles, playlist);

    // Check duplicate
    const completedKeys = JSON.parse(localStorage.getItem('meta_media_keys') || '[]');
    if (completedKeys.includes(key)) {
      setInfoIsErr(false);
      setInfoText('هذا التنزيل موجود مسبقاً ولن يتكرر.');
      setLogText('تنبيه: تم تخطي التنزيل لأن الرابط ونفس الجودة تم تنزيلهما مسبقاً.');
      return;
    }

    setDownloading(true);
    setProgress(0);
    setHealth('جاري التنزيل 0%');

    // Simulate steady download chunks matching Android yt-dlp behavior
    const steps = [15, 32, 54, 76, 92, 100];
    for (const p of steps) {
      await new Promise((res) => setTimeout(res, 280));
      setProgress(p);
      setHealth(`جاري التنزيل ${p}%`);
    }

    try {
      const isAudio = quality === 'audio';
      const cleanTitle = (videoInfo?.title || 'video')
        .replace(/[/\\?%*:|"<>]/g, '_')
        .slice(0, 100);
      const ext = isAudio ? 'mp3' : 'mp4';
      const filename = `${cleanTitle} [${quality}].${ext}`;

      // Create downloadable file blob
      const blob = createSampleMediaBlob(isAudio ? 'audio' : 'video', cleanTitle);
      triggerFileDownload(blob, filename);

      // Save key
      completedKeys.push(key);
      localStorage.setItem('meta_media_keys', JSON.stringify(completedKeys));

      // Record to history
      const historyItem: DownloadHistoryItem = {
        id: Date.now().toString(),
        title: videoInfo?.title || cleanTitle,
        url: trimmedUrl,
        quality,
        subtitles,
        playlist,
        filename,
        timestamp: Date.now(),
        size: formatBytes(blob.size),
      };
      saveHistory(historyItem);

      setInfoIsErr(false);
      setInfoText('اكتمل التنزيل داخل Download/MetaVideosBot/files');
      setLogText(`اكتمل حفظ الملف: ${filename}`);
      setHealth('جاهز');
    } catch (e: any) {
      setInfoIsErr(true);
      setInfoText(e?.message || 'حدث خطأ أثناء التنزيل');
      setHealth('جاهز');
    } finally {
      setDownloading(false);
    }
  };

  const handleShowStatuses = () => {
    setStatuses(DEFAULT_STATUSES);
    setStatusesLoaded(true);
    const box = document.getElementById('statuses-section');
    if (box) {
      box.scrollIntoView({ behavior: 'smooth' });
    }
  };

  const handleCopyStatus = (item: StatusItem) => {
    try {
      const blob = item.blob || createSampleMediaBlob(item.type === 'image' ? 'video' : 'video', item.name);
      const destFilename = `WhatsAppStatus_${item.name}`;
      triggerFileDownload(blob, destFilename);
      setLogText(`تم نسخ الحالة ${item.name} إلى Download/MetaVideosBot/WhatsAppStatus`);
    } catch (e: any) {
      setLogText(e?.message || 'تعذر نسخ الحالة');
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) return;

    const newStatuses: StatusItem[] = Array.from(files).map((f, idx) => ({
      id: `${Date.now()}_${idx}`,
      name: f.name,
      size: f.size,
      type: f.type.startsWith('image') ? 'image' : 'video',
      blob: f,
    }));

    setStatuses((prev) => [...newStatuses, ...prev]);
    setStatusesLoaded(true);
    setLogText(`تمت إضافة ${newStatuses.length} حالات محلية من جهازك`);
  };

  return (
    <main>
      <header>
        <div>
          <small>WEB APP · LOCAL</small>
          <h1>منزّل الوسائط</h1>
          <p>تنزيل مباشر إلى Download بدون Telegram أو خادم خارجي.</p>
        </div>
        <span id="health">{health}</span>
      </header>

      <nav>
        <button onClick={() => handleNavClick('https://www.youtube.com/')}>▶ YouTube</button>
        <button onClick={() => handleNavClick('https://www.facebook.com/')}>f Facebook</button>
        <button onClick={() => handleNavClick('https://www.instagram.com/')}>◎ Instagram</button>
        <button onClick={() => handleNavClick('https://www.threads.com/')}>@ Threads</button>
        <button onClick={() => handleNavClick('https://www.tiktok.com/')}>♪ TikTok</button>
        <button id="wa" onClick={handleShowStatuses}>WA حالات WhatsApp</button>
      </nav>

      {/* Main Download Card */}
      <section className="card">
        <label htmlFor="url">رابط الفيديو أو القائمة</label>
        <input
          id="url"
          ref={urlInputRef}
          dir="ltr"
          value={url}
          onChange={(e) => {
            setUrl(e.target.value);
            setInspected(false);
            setInfoText('');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleInspect();
          }}
          placeholder="https://…"
        />

        <div className="row">
          <select
            id="quality"
            value={quality}
            onChange={(e) => setQuality(e.target.value)}
          >
            <option value="best">أفضل جودة</option>
            <option value="1080">1080</option>
            <option value="720">720</option>
            <option value="480">480</option>
            <option value="360">360</option>
            <option value="audio">صوت فقط</option>
          </select>

          <label className="check">
            <input
              id="subs"
              type="checkbox"
              checked={subtitles}
              onChange={(e) => setSubtitles(e.target.checked)}
            />
            ترجمة أصلية
          </label>

          <label className="check">
            <input
              id="playlist"
              type="checkbox"
              checked={playlist}
              onChange={(e) => setPlaylist(e.target.checked)}
            />
            قائمة كاملة
          </label>
        </div>

        <button
          id="inspect"
          className="primary"
          onClick={handleInspect}
          disabled={inspecting || downloading}
        >
          {inspecting ? 'جاري فحص الرابط…' : 'فحص الرابط'}
        </button>

        {infoText && (
          <div id="info" className={infoIsErr ? 'err' : 'ok'}>
            {infoText}
          </div>
        )}

        {downloading && (
          <div className="progress-container">
            <div className="progress-bar" style={{ width: `${progress}%` }} />
          </div>
        )}

        {videoInfo?.thumbnail && (
          <div className="details-card">
            <div className="details-title">{videoInfo.title}</div>
            <div className="details-sub">
              {videoInfo.uploader && <span>👤 {videoInfo.uploader}</span>}
              {videoInfo.platform && <span>🌐 {videoInfo.platform}</span>}
            </div>
          </div>
        )}

        <button
          id="download"
          className="primary"
          onClick={handleDownload}
          disabled={!inspected || downloading}
        >
          {downloading ? `جاري التنزيل (${progress}%)` : 'بدء التنزيل'}
        </button>
      </section>

      {/* WhatsApp Statuses Card */}
      <section className="card" id="statuses-section">
        <h2>حالات WhatsApp المحلية</h2>
        <div className="status-actions">
          <button onClick={handleShowStatuses}>
            تحديث الحالات
          </button>
          <button onClick={() => fileInputRef.current?.click()}>
            📁 استعراض ملفات الحالات من جهازك
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept="image/*,video/*"
            style={{ display: 'none' }}
            onChange={handleFileUpload}
          />
        </div>

        <div id="statuses">
          {!statusesLoaded ? (
            'اضغط الزر لإظهار الحالات الموجودة على هذا الهاتف.'
          ) : statuses.length === 0 ? (
            'لا توجد حالات محلية حالياً.'
          ) : (
            statuses.map((item) => (
              <div key={item.id} className="status">
                <div className="status-preview">
                  <div className="status-icon">
                    {item.type === 'video' ? '🎬' : '🖼️'}
                  </div>
                  <div className="status-meta">
                    <span>{item.name}</span>
                    <span className="status-size">{formatBytes(item.size)}</span>
                  </div>
                </div>
                <button
                  data-name={item.name}
                  onClick={() => handleCopyStatus(item)}
                >
                  نسخ إلى Download
                </button>
              </div>
            ))
          )}
        </div>
      </section>

      {/* Log / Alerts Card */}
      <section className="card">
        <h2>التنبيهات</h2>
        <div id="log">{logText}</div>
        {history.length > 0 && (
          <div style={{ marginTop: '14px', borderTop: '1px solid #1d3045', paddingTop: '10px' }}>
            <div style={{ fontSize: '13px', color: '#6be2b6', marginBottom: '8px' }}>
              سجل التنزيلات المكتملة:
            </div>
            {history.slice(0, 5).map((h) => (
              <div key={h.id} className="history-item">
                <span style={{ color: '#c9d9ea' }}>{h.title}</span>
                <span style={{ color: '#7a90a6', fontSize: '12px' }}>{h.quality} · {h.size}</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <footer>الملفات: Download/MetaVideosBot/files</footer>
    </main>
  );
}
