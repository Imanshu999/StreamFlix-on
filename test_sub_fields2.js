const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/trending?page=1&perPage=50", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    for (const s of json.data.subjectList) {
      if (s.dubs && s.dubs.length > 0) {
        console.log(s.title, "dubs:", s.dubs);
      }
      if (s.subtitles && s.subtitles.length > 0) {
        console.log(s.title, "subtitles:", s.subtitles);
      }
    }
  });
});
