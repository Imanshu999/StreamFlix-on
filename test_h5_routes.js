const https = require("https");
const fs = require("fs");
https.get("https://h5-static.aoneroom.com/ssrStatic/mbOfficial/public/_nuxt/BErA93vP.js", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const regex = /["'](\/wefeed-[^"']+)["']/g;
    let match;
    const routes = new Set();
    while ((match = regex.exec(raw)) !== null) {
      routes.add(match[1]);
    }
    console.log("All routes in JS:");
    console.log(Array.from(routes).sort().join("\n"));
  });
});
