"use strict";
/**
 * Force Access-Control-Allow-Origin
 */
function onHeadersReceived(resp) {
  var len = resp.responseHeaders.length;
  //console.log(`***** Headers len: [${len}] Response: ${resp.toString()}`);
  while(--len) {
    //console.log(`***** ${resp.responseHeaders[len].name} ${resp.responseHeaders[len].value}`);
    if(resp.responseHeaders[len].name.toLowerCase() === "access-control-allow-credentials") {
      break;
    }
    if(resp.responseHeaders[len].name.toLowerCase() === "access-control-allow-origin") {
      resp.responseHeaders[len].value = "*";
      break;
    }
  }
  if (len === 0) { // if we didn't find it len will be zero
    resp.responseHeaders.push({
      'name': 'Access-Control-Allow-Origin',
      'value': '*'
    });
  }
  return {responseHeaders: resp.responseHeaders};
}

browser.webRequest.onHeadersReceived.addListener(
  onHeadersReceived,
  {urls: ['*://*/*'],
  /* TYPES: "main_frame", "sub_frame", "stylesheet", "script", "image", "object", "xmlhttprequest", "other" */
  types: ['xmlhttprequest']},
  ['blocking', 'responseHeaders']
);
