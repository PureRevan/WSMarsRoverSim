// Exercise the real JS/Wasm bridge against the production worker in browser tests.
const workerAsset = require('path').resolve(config.basePath,
    '../../../../workerApp/build/kotlin-webpack/js/productionExecutable/rover-worker.js');
config.files.push({pattern: workerAsset, included: false, served: true, watched: false});
config.proxies['/rover-worker.js'] = '/absolute' + workerAsset;
config.client.mocha = Object.assign({}, config.client.mocha, {timeout: 120000});
