const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId=3380137095141441488", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    console.log("Detail keys:", Object.keys(json.data));
    console.log("Subject:", JSON.stringify(json.data.subject, null, 2).substring(0, 500));
    console.log("Seasons:", json.data.seasons ? json.data.seasons.length : "none");
    if (json.data.seasons) {
      console.log("Season 0:", JSON.stringify(json.data.seasons[0], null, 2).substring(0, 500));
    }
  });
});
