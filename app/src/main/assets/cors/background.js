"use strict";
/**
 * Force Access-Control-Allow-Origin
 */
var LAMPA_URL = "http://lampa.mx";

function originWithId(header) {
  return (
    header.name.toLowerCase() === "origin" &&
    (header.value.indexOf("moz-extension://") === 0 ||
      header.value.indexOf("chrome-extension://") === 0)
  );
}

function origin(header) {
  return header.name.toLowerCase() === "origin";
}

function sec(header) {
  return header.name.toLowerCase().startsWith("sec-");
}

function onHeadersReceived(resp) {
  let len = resp.responseHeaders.length;
  let cred = false;
  console.log(
    `***** onHeadersReceived url: [${resp.url}] id[${
      resp.requestId
    }] headers[${len}] ${JSON.stringify(resp.responseHeaders)}`
  );
  while (--len) {
    if (
      resp.responseHeaders[len].name.toLowerCase() ===
      "access-control-allow-credentials"
    ) {
      cred = true;
      break;
    }
    if (
      resp.responseHeaders[len].name.toLowerCase() ===
      "access-control-allow-origin"
    ) {
      resp.responseHeaders[len].value = LAMPA_URL;
      break;
    }
  }
  if (len === 0) {
    // if we didn't find it len will be zero
    resp.responseHeaders.push({
      name: "Access-Control-Allow-Origin",
      value: LAMPA_URL,
    });
  } else if (cred) {
    resp.responseHeaders.push({
      name: "Access-Control-Allow-Origin",
      value: LAMPA_URL,
    });
  }
  return {
    responseHeaders: resp.responseHeaders,
  };
}

browser.webRequest.onHeadersReceived.addListener(
  onHeadersReceived,
  {
    urls: ["<all_urls>"],
    /* TYPES: "main_frame", "sub_frame", "stylesheet", "script", "image", "object", "xmlhttprequest", "other" */
    types: ["xmlhttprequest"],
  },
  ["blocking", "responseHeaders"]
);

browser.webRequest.onBeforeSendHeaders.addListener(
  (details) => {
    let headers = details.requestHeaders.filter(
      (x) => !originWithId(x) && !sec(x)
    );
    console.log(
      `***** onBeforeSendHeaders id[${details.requestId}] orig headers[${
        details.requestHeaders.length
      }] ${JSON.stringify(headers)}`
    );
    return {
      requestHeaders: headers,
    };
  },
  {
    urls: ["<all_urls>"],
    /* TYPES: "main_frame", "sub_frame", "stylesheet", "script", "image", "object", "xmlhttprequest", "other" */
    types: ["xmlhttprequest"],
  },
  ["blocking", "requestHeaders"]
);
