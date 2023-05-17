"use strict";

const bgReqHeader = {};
browser.webRequest.onBeforeSendHeaders.addListener(
    function (request) {
        bgReqHeader[request.requestId] = {};
        for (let header of request.requestHeaders)
            bgReqHeader[request.requestId][header.name.toLowerCase()] =
                header.value;
        return { requestHeaders: request.requestHeaders };
    },
    { urls: ["<all_urls>"] },
    ["blocking", "requestHeaders"]
);
browser.webRequest.onHeadersReceived.addListener(
    function (response) {
        const requestHeaders = bgReqHeader[response.requestId] || {};
        delete bgReqHeader[response.requestId];
        const replaceHeaders = {
            "access-control-allow-origin": requestHeaders["origin"] || "*",
            "access-control-allow-methods":
                requestHeaders["access-control-request-method"] || false,
            "access-control-allow-headers":
                requestHeaders["access-control-request-headers"] || false,
            "access-control-expose-headers": "*",
            "access-control-allow-credentials": "true"
        };
        const responseHeaders = [];
        for (let header of response.responseHeaders) {
            let lowerName = header.name.toLowerCase();
            if (lowerName in replaceHeaders) {
                if (replaceHeaders[lowerName])
                    responseHeaders.push({
                        name: header.name,
                        value: replaceHeaders[lowerName]
                    });
                replaceHeaders[lowerName] = false;
            } else responseHeaders.push(header);
        }
        for (let lowerName in replaceHeaders) {
            if (replaceHeaders[lowerName])
                responseHeaders.push({
                    name: lowerName,
                    value: replaceHeaders[lowerName]
                });
        }
        return { responseHeaders: responseHeaders };
    },
    { urls: ["<all_urls>"] },
    ["blocking", "responseHeaders"]
);
