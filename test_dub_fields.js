const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=h5.inmoviebox.com", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    for (const op of json.data.operatingList) {
      if (op.subjects) {
        for (const s of op.subjects) {
          if (s.dubs && s.dubs.length > 0) {
            console.log(s.title, "has dubs:", s.dubs);
          }
          if (s.subtitles && s.subtitles.length > 0) {
            console.log(s.title, "has subtitles:", s.subtitles);
          }
        }
      }
    }
  });
});
