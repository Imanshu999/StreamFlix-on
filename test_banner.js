const https = require("https");
https.get("https://h5-api.aoneroom.com/wefeed-h5api-bff/home?host=h5.inmoviebox.com", res => {
  let raw = "";
  res.on("data", d => raw += d);
  res.on("end", () => {
    const json = JSON.parse(raw);
    const bannerItems = json.data.operatingList.find(op => op.type === "BANNER")?.banner?.items || [];
    console.log("Banner item 0:", JSON.stringify(bannerItems[0], null, 2));
    const firstMovieOp = json.data.operatingList.find(op => op.type === "SUBJECTS_MOVIE" && op.subjects?.length > 0);
    console.log("First movie sample:", JSON.stringify(firstMovieOp.subjects[0], null, 2));
  });
});
