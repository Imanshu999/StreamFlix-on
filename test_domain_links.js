const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/media-player/get-domain", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    console.log("Domain response:", raw);
  });
});
