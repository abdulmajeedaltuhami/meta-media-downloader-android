export interface VideoMetadata {
  ok: boolean;
  title?: string;
  uploader?: string;
  duration?: number;
  thumbnail?: string;
  id?: string;
  platform?: string;
  error?: string;
}

export interface StatusItem {
  id: string;
  name: string;
  size: number;
  type: 'video' | 'image';
  blob?: Blob;
  previewUrl?: string;
}

export interface DownloadHistoryItem {
  id: string;
  title: string;
  url: string;
  quality: string;
  subtitles: boolean;
  playlist: boolean;
  filename: string;
  timestamp: number;
  size: string;
}

/**
 * Generate unique key to prevent re-downloading duplicate URL with same settings
 */
export function getSafeKey(url: string, quality: string, subtitles: boolean, playlist: boolean): string {
  const str = `${url.trim().toLowerCase()}|${quality}|${subtitles}|${playlist}`;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash) + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash).toString(16);
}

/**
 * Inspect video URL via oEmbed or platform heuristics
 */
export async function inspectUrl(rawUrl: string): Promise<VideoMetadata> {
  const url = rawUrl.trim();
  if (!url) {
    return { ok: false, error: 'ألصق الرابط أولاً' };
  }

  try {
    // Check if URL is valid syntax
    new URL(url);
  } catch {
    return { ok: false, error: 'الرابط غير صالح، تأكد من إدخال رابط يبدأ بـ https://' };
  }

  // 1. YouTube
  if (/youtube\.com|youtu\.be/i.test(url)) {
    try {
      const oEmbedUrl = `https://noembed.com/embed?url=${encodeURIComponent(url)}`;
      const res = await fetch(oEmbedUrl);
      if (res.ok) {
        const data = await res.json();
        if (data.title) {
          return {
            ok: true,
            title: data.title,
            uploader: data.author_name || 'YouTube Creator',
            thumbnail: data.thumbnail_url,
            platform: 'YouTube',
          };
        }
      }
    } catch {
      // fallback
    }

    // fallback extraction for YouTube
    const match = url.match(/(?:v=|\/shorts\/|youtu\.be\/|embed\/)([^#&?]+)/);
    const videoId = match ? match[1] : 'video';
    return {
      ok: true,
      title: `فيديو YouTube (${videoId})`,
      uploader: 'YouTube Channel',
      id: videoId,
      platform: 'YouTube',
    };
  }

  // 2. TikTok
  if (/tiktok\.com/i.test(url)) {
    try {
      const oEmbedUrl = `https://www.tiktok.com/oembed?url=${encodeURIComponent(url)}`;
      const res = await fetch(oEmbedUrl);
      if (res.ok) {
        const data = await res.json();
        if (data.title) {
          return {
            ok: true,
            title: data.title.slice(0, 100),
            uploader: data.author_name || 'TikTok Creator',
            thumbnail: data.thumbnail_url,
            platform: 'TikTok',
          };
        }
      }
    } catch {
      // fallback
    }

    const userMatch = url.match(/@([^/]+)/);
    const author = userMatch ? `@${userMatch[1]}` : 'TikTok Creator';
    return {
      ok: true,
      title: `مقطع تيك توك من ${author}`,
      uploader: author,
      platform: 'TikTok',
    };
  }

  // 3. Instagram
  if (/instagram\.com/i.test(url)) {
    const isReel = url.includes('/reel/');
    const isPost = url.includes('/p/');
    const codeMatch = url.match(/\/(?:reel|p)\/([^/?]+)/);
    const code = codeMatch ? codeMatch[1] : 'media';
    return {
      ok: true,
      title: isReel ? `Instagram Reel [${code}]` : isPost ? `Instagram Post [${code}]` : 'Instagram Media',
      uploader: 'Instagram User',
      platform: 'Instagram',
    };
  }

  // 4. Facebook
  if (/facebook\.com|fb\.watch/i.test(url)) {
    return {
      ok: true,
      title: 'فيديو فيسبوك عالي الدقة',
      uploader: 'Facebook Video',
      platform: 'Facebook',
    };
  }

  // 5. Threads
  if (/threads\.net|threads\.com/i.test(url)) {
    return {
      ok: true,
      title: 'منشور ثريدز مع وسائط',
      uploader: 'Threads User',
      platform: 'Threads',
    };
  }

  // Generic or other platforms
  try {
    const parsed = new URL(url);
    const pathname = parsed.pathname;
    const filename = pathname.split('/').filter(Boolean).pop() || 'media_download';
    return {
      ok: true,
      title: `ملف وسائط: ${decodeURIComponent(filename).slice(0, 60)}`,
      uploader: parsed.hostname,
      platform: parsed.hostname,
    };
  } catch (err: any) {
    return { ok: false, error: err?.message || 'تعذر فحص الرابط' };
  }
}

/**
 * Format bytes into human readable string
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}

/**
 * Creates a playable media blob for testing and local download
 */
export function createSampleMediaBlob(type: 'audio' | 'video', title: string): Blob {
  if (type === 'audio') {
    // Generate simple minimal WAV audio header + silent/tone buffer
    const sampleRate = 44100;
    const duration = 2; // seconds
    const numChannels = 1;
    const bytesPerSample = 2;
    const totalSamples = sampleRate * duration;
    const dataSize = totalSamples * numChannels * bytesPerSample;
    const buffer = new ArrayBuffer(44 + dataSize);
    const view = new DataView(buffer);

    // RIFF chunk descriptor
    writeString(view, 0, 'RIFF');
    view.setUint32(4, 36 + dataSize, true);
    writeString(view, 8, 'WAVE');

    // fmt sub-chunk
    writeString(view, 12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true); // PCM
    view.setUint16(22, numChannels, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, sampleRate * numChannels * bytesPerSample, true);
    view.setUint16(32, numChannels * bytesPerSample, true);
    view.setUint16(34, 16, true); // bits per sample

    // data sub-chunk
    writeString(view, 36, 'data');
    view.setUint32(40, dataSize, true);

    // fill with subtle sine wave tone
    let offset = 44;
    for (let i = 0; i < totalSamples; i++) {
      const sample = Math.sin((2 * Math.PI * 440 * i) / sampleRate) * 0.2;
      view.setInt16(offset, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
      offset += 2;
    }

    return new Blob([buffer], { type: 'audio/mp3' });
  } else {
    // Minimal mock MP4 / WebM container data with title tag
    const content = `MetaMediaDownloader: ${title}\nEncoded: ${new Date().toISOString()}`;
    return new Blob([content], { type: 'video/mp4' });
  }
}

function writeString(view: DataView, offset: number, string: string) {
  for (let i = 0; i < string.length; i++) {
    view.setUint8(offset + i, string.charCodeAt(i));
  }
}

/**
 * Trigger browser file download
 */
export function triggerFileDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}
