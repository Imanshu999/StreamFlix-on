const https = require("https");
const data = JSON.stringify({ keyword: "Spider", page: 1, perPage: 10, subjectType: 0 });
const req = https.request("https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "callerSource": "node-frontend",
    "Origin": "https://h5.inmoviebox.com",
    "Referer": "https://h5.inmoviebox.com/",
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  }
}, res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => console.log("Search with headers:", raw));
});
req.write(data);
req.end();
