const { createDiStreamHttpHandler } = require("../server");

const { handler } = createDiStreamHttpHandler({ enableExternalGatewayCalls: true });

module.exports = async (req, res) => handler(req, res);
