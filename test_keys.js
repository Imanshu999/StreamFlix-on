const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId=3380137095141441488", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    console.log("resource:", JSON.stringify(json.data.resource, null, 2));
    console.log("stars:", JSON.stringify(json.data.stars, null, 2));
  });
});
