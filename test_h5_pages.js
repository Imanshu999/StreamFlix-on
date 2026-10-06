const https = require("https");
https.get("https://h5.inmoviebox.com/", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const scripts = raw.match(/src="([^"]+\.js)"/g) || [];
    console.log("Found scripts in HTML:", scripts);
  });
});
