package top.rootu.lampa.net;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.Buffer;

public class HttpBodyTest {
    @Test
    public void decodesBinaryBytesWithoutUtf8Conversion() throws Exception {
        MediaType type = MediaType.parse("image/png");
        RequestBody body = Http.createRequestBody(type, "AID/DQo=", "base64");
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        assertArrayEquals(new byte[] {0, (byte) 128, (byte) 255, 13, 10}, buffer.readByteArray());
        assertEquals(type, body.contentType());
        assertEquals(5, body.contentLength());
    }

    @Test
    public void preservesTextRequestsAndEmptyBinaryBodies() throws Exception {
        RequestBody text = Http.createRequestBody(MediaType.parse("application/json"), "{\"name\":\"Sintel\"}", null);
        Buffer buffer = new Buffer();
        text.writeTo(buffer);
        assertEquals("{\"name\":\"Sintel\"}", buffer.readString(StandardCharsets.UTF_8));
        assertEquals(0, Http.createRequestBody(null, "", "base64").contentLength());
    }

    @Test
    public void rejectsInvalidOrUnknownEncodings() {
        assertThrows(IllegalArgumentException.class, () -> Http.createRequestBody(null, "!", "base64"));
        assertThrows(IllegalArgumentException.class, () -> Http.createRequestBody(null, "data", "hex"));
    }

    @Test
    public void rejectsEncodedNonStringBodiesBeforeRequestDispatch() {
        for (Object body : new Object[] {null, 123, false, new Object()}) {
            assertThrows(IllegalArgumentException.class, () -> Http.prepareRequestData(body, "base64"));
        }
        assertEquals("", Http.prepareRequestData("", "base64"));
        assertEquals("AID/DQo=", Http.prepareRequestData("AID/DQo=", "BASE64"));
    }

    @Test
    public void rejectsUnknownEncodingsEvenWhenTheBodyIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> Http.prepareRequestData("", "hex"));
        assertEquals("", Http.prepareRequestData("", null));
        assertEquals("plain text", Http.prepareRequestData("plain text", ""));
    }

    @Test
    public void preservesContentTypesRegardlessOfHeaderCase() {
        Headers headers = new Headers.Builder().add("cOnTeNt-TyPe", "image/png").build();
        assertEquals(MediaType.parse("image/png"), Http.requestMediaType(headers, true, "base64"));
        Headers empty = new Headers.Builder().build();
        assertEquals(MediaType.parse("application/octet-stream"), Http.requestMediaType(empty, true, "base64"));
        assertEquals(MediaType.parse("application/x-www-form-urlencoded"), Http.requestMediaType(empty, true, null));
        assertNull(Http.requestMediaType(headers, false, "base64"));
    }
}
