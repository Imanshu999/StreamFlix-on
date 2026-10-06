const https = require("https");
https.get("https://h5-static.aoneroom.com/ssrStatic/mbOfficial/public/_nuxt/BErA93vP.js", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const chunkMatches = raw.match(/_nuxt\/[a-zA-Z0-9]+\.js/g) || [];
    console.log("Found chunk references:", Array.from(new Set(chunkMatches)).slice(0, 20));
  });
});
