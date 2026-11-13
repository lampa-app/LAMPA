# Binary HTTP bodies

The native `httpReq` bridge accepts `bodyEncoding: "base64"` for binary request
bodies. Set `post_data` to a string containing the Base64 representation of
the bytes, including an empty string for an empty body. Set the actual media
type in the `Content-Type` header or the `contentType` parameter. Binary bodies
without a media type use `application/octet-stream`. The bridge decodes the body
before sending it, so the HTTP server receives the original bytes. Without
`bodyEncoding`, request bodies use UTF-8 text encoding. Unsupported encodings or
invalid Base64 request bodies are rejected with status 400. Encoded bodies
that are not strings are also rejected before an HTTP request is sent.

A caller must first check that `AndroidJS.supportsHttpBodyEncoding("base64")`
exists and returns true before using an encoded request body.

```json
{
  "url": "https://example.test/upload",
  "type": "PUT",
  "bodyEncoding": "base64",
  "post_data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=",
  "headers": { "Content-Type": "image/png" }
}
```

For binary responses, use `dataType: "base64"` and `returnHeaders: true`.
The response envelope includes the Base64 `body`, `encoding: "base64"`, and
response `headers`, including the media type. Decode the body into bytes;
do not pass it through UTF-8 string encoding.

Responses whose declared body size exceeds 2 MiB return
`[Response too large, skipped]` in `body`.
