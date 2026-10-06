const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=h5.inmoviebox.com", res => {
  console.log("Set-Cookie:", res.headers["set-cookie"]);
  const cookie = res.headers["set-cookie"] ? res.headers["set-cookie"].join("; ") : "";
  const data = JSON.stringify({ keyword: "Spider", page: 1, perPage: 10, subjectType: 0 });
  const req = https.request("https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/search", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Cookie": cookie
    }
  }, res2 => {
    let raw = "";
    res2.on("data", d => raw += d);
    res2.on("end", () => console.log("Search with cookie:", raw));
  });
  req.write(data);
  req.end();
});
