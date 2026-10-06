const https = require("https");
const url = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/play?subjectId=2561731431760381424";
https.get(url, res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    console.log("Movie play response:", raw);
  });
});
