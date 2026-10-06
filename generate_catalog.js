const https = require("https");
const fs = require("fs");

function fetchJson(url, options = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, options, res => {
      let raw = "";
      res.on("data", chunk => raw += chunk);
      res.on("end", () => {
        try {
          resolve(JSON.parse(raw));
        } catch (e) {
          resolve(null);
        }
      });
    }).on("error", () => resolve(null));
  });
}

async function run() {
  console.log("Fetching Home API...");
  const homeRes = await fetchJson("https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=h5.inmoviebox.com");
  console.log("Fetching Trending API...");
  const trendingRes = await fetchJson("https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/trending?page=1&perPage=50");
  console.log("Fetching Tab Operating...");
  const tabRes = await fetchJson("https://h5-api.aoneroom.com/wefeed-h5api-bff/tab-operating");

  const mediaMap = new Map();
  const bannerList = [];

  if (homeRes?.data?.operatingList) {
    for (const op of homeRes.data.operatingList) {
      if (op.type === "BANNER" && op.banner?.items) {
        for (const item of op.banner.items) {
          bannerList.push({
            id: item.subjectId || item.id,
            title: item.title,
            imageUrl: item.image?.url || "",
            subject: item.subject
          });
          if (item.subject && item.subject.subjectId) {
            mediaMap.set(String(item.subject.subjectId), {
              ...item.subject,
              bannerUrl: item.image?.url || "",
              isFeatured: true,
              category: "Featured Banner"
            });
          }
        }
      }
      if (op.subjects && Array.isArray(op.subjects)) {
        for (const sub of op.subjects) {
          if (!sub.subjectId) continue;
          const key = String(sub.subjectId);
          const existing = mediaMap.get(key);
          mediaMap.set(key, {
            ...sub,
            bannerUrl: existing?.bannerUrl || sub.trailer?.cover?.url || sub.cover?.url || "",
            isFeatured: existing?.isFeatured || false,
            category: op.title || "Popular"
          });
        }
      }
    }
  }

  if (trendingRes?.data?.subjectList) {
    for (const sub of trendingRes.data.subjectList) {
      if (!sub.subjectId) continue;
      const key = String(sub.subjectId);
      const existing = mediaMap.get(key);
      mediaMap.set(key, {
        ...sub,
        bannerUrl: existing?.bannerUrl || sub.trailer?.cover?.url || sub.cover?.url || "",
        isFeatured: existing?.isFeatured || false,
        category: existing?.category || "Trending Now"
      });
    }
  }

  console.log(`Aggregated ${mediaMap.size} unique media items!`);
  console.log(`Banner items count: ${bannerList.length}`);
}

run();
