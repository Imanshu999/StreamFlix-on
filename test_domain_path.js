const https = require("https");
https.get("https://mzfi.me/", res => {
  console.log("mzfi.me status:", res.statusCode, res.headers);
});
