(function() {
  if (window.FermataSponsorBlock) return;

  const state = {
    config: {
      enabled: false,
      endpoint: 'https://sponsor.ajay.app/api/skipSegments',
      categories: [],
      actionTypes: ['skip']
    },
    videoId: null,
    loadedKey: null,
    pendingKey: null,
    segments: [],
    lastSkipKey: null
  };

  function isVideoId(id) {
    return (typeof id === 'string') && /^[A-Za-z0-9_-]{11}$/.test(id);
  }

  function videoIdFromUrl(url) {
    try {
      const parsed = new URL(url, location.href);
      const direct = parsed.searchParams.get('v');
      if (isVideoId(direct)) return direct;

      const parts = parsed.pathname.split('/').filter(Boolean);
      const idx = parts.findIndex((part) => ['shorts', 'embed', 'v'].includes(part));
      if ((idx !== -1) && isVideoId(parts[idx + 1])) return parts[idx + 1];
      if ((parsed.hostname === 'youtu.be') && isVideoId(parts[0])) return parts[0];
    } catch (err) {
      console.debug('SponsorBlock video ID parse failed', err);
    }

    return null;
  }

  function findVideoId() {
    const current = videoIdFromUrl(location.href);
    if (current) return current;

    const canonical = document.querySelector('link[rel="canonical"]');
    const canonicalId = canonical ? videoIdFromUrl(canonical.href) : null;
    if (canonicalId) return canonicalId;

    const playerResponse = window.ytInitialPlayerResponse;
    const playerId = playerResponse && playerResponse.videoDetails &&
      playerResponse.videoDetails.videoId;
    return isVideoId(playerId) ? playerId : null;
  }

  function configKey() {
    return (state.config.enabled ? '1' : '0') + ':' + state.config.categories.join(',');
  }

  function createParams() {
    const params = new URLSearchParams();
    params.set('categories', JSON.stringify(state.config.categories));
    params.set('actionTypes', JSON.stringify(state.config.actionTypes));
    params.set('service', 'YouTube');
    return params;
  }

  function segmentsForVideo(data, videoId) {
    if (!Array.isArray(data)) return [];

    for (const entry of data) {
      if (entry && (entry.videoID === videoId)) {
        return Array.isArray(entry.segments) ? entry.segments : [];
      }
    }

    return [];
  }

  function normalizeSegments(data) {
    const normalized = [];

    for (const entry of data) {
      const segment = entry && entry.segment;
      if (!Array.isArray(segment) || (segment.length < 2)) continue;

      const start = Number(segment[0]);
      const end = Number(segment[1]);
      if (!Number.isFinite(start) || !Number.isFinite(end) || (end <= start)) continue;
      if (entry.actionType && (entry.actionType !== 'skip')) continue;

      normalized.push({start, end, category: entry.category || ''});
    }

    normalized.sort((a, b) => a.start - b.start || b.end - a.end);

    const merged = [];
    for (const segment of normalized) {
      const previous = merged[merged.length - 1];
      if (previous && (segment.start <= previous.end + 0.05)) {
        previous.end = Math.max(previous.end, segment.end);
      } else {
        merged.push(segment);
      }
    }

    return merged;
  }

  async function sha256Prefix(value) {
    const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    const bytes = new Uint8Array(digest);
    const hex = '0123456789abcdef';
    let result = '';

    for (let i = 0; i < bytes.length; i++) {
      result += hex[(bytes[i] >> 4) & 15] + hex[bytes[i] & 15];
    }

    return result.substring(0, 4);
  }

  async function loadSegments(videoId) {
    const key = videoId + ':' + configKey();
    if ((state.loadedKey === key) || (state.pendingKey === key)) return;

    state.pendingKey = key;
    state.loadedKey = null;
    state.segments = [];

    if (!state.config.enabled || (state.config.categories.length === 0)) {
      state.pendingKey = null;
      state.loadedKey = key;
      return;
    }

    try {
      const prefix = await sha256Prefix(videoId);
      const response = await fetch(state.config.endpoint + '/' + prefix + '?' + createParams(), {
        credentials: 'omit'
      });

      if (state.pendingKey !== key) return;

      if (response.status === 404) {
        state.segments = [];
      } else if (response.ok) {
        state.segments = normalizeSegments(segmentsForVideo(await response.json(), videoId));
      } else {
        console.debug('SponsorBlock request failed', response.status);
      }

      state.loadedKey = key;
    } catch (err) {
      console.debug('SponsorBlock unavailable', err);
      state.loadedKey = key;
    } finally {
      if (state.pendingKey === key) state.pendingKey = null;
    }
  }

  function skipIfNeeded(video) {
    if (!state.config.enabled || !video || video.ended || (state.segments.length === 0)) return;

    const current = video.currentTime;
    if (!Number.isFinite(current)) return;

    for (const segment of state.segments) {
      if ((current + 0.05 < segment.start) || (current >= segment.end - 0.15)) continue;

      const duration = Number.isFinite(video.duration) ? video.duration : segment.end;
      const target = Math.min(segment.end, duration);
      const skipKey = state.videoId + ':' + segment.start + ':' + segment.end;
      if ((state.lastSkipKey === skipKey) && (Math.abs(current - target) < 0.5)) return;

      state.lastSkipKey = skipKey;
      video.currentTime = target;
      return;
    }
  }

  function tick() {
    const videoId = findVideoId();

    if (videoId !== state.videoId) {
      state.videoId = videoId;
      state.loadedKey = null;
      state.pendingKey = null;
      state.segments = [];
      state.lastSkipKey = null;
    }

    if (videoId) loadSegments(videoId);
    skipIfNeeded(document.querySelector('video'));
  }

  window.FermataSponsorBlock = {
    configure(config) {
      state.config = Object.assign({}, state.config, config);
      state.loadedKey = null;
      state.pendingKey = null;
      state.segments = [];
      state.lastSkipKey = null;
      tick();
    },
    tick
  };

  document.addEventListener('timeupdate', (event) => {
    if (event.target && (event.target.tagName === 'VIDEO')) skipIfNeeded(event.target);
  }, true);

  document.addEventListener('seeking', (event) => {
    if (event.target && (event.target.tagName === 'VIDEO')) setTimeout(tick, 0);
  }, true);

  setInterval(tick, 500);
  tick();
})();
