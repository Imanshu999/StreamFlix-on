const https = require("https");
const url = "https://h5-api.aoneroom.com/wefeed-h5api-bff/media-player/get-domain";
https.get(url, res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    console.log("Domain response:", raw);
  });
});
