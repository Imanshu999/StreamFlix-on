const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=h5.inmoviebox.com", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    for (const op of json.data.operatingList) {
      if (op.subjects && op.subjects.length > 0) {
        for (const sub of op.subjects) {
          if (sub.trailer?.videoAddress?.url) {
            console.log("Found subject with trailer:", sub.title, "=>", sub.trailer.videoAddress.url);
            return;
          }
        }
      }
    }
  });
});
