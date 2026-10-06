const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/detail?subjectId=3380137095141441488", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    console.log("Detail dubs:", json.data.subject?.dubs);
    console.log("Detail subtitles:", json.data.subject?.subtitles);
    console.log("Detail countryName:", json.data.subject?.countryName);
  });
});
