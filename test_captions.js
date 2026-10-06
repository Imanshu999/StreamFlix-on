const https = require("https");
const url = "https://h5-api.aoneroom.com/wefeed-h5api-bff/subject/caption?subjectId=3380137095141441488&se=1&ep=1";
https.get(url, res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    console.log("Caption response:", raw);
  });
});
