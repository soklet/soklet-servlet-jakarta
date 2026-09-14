/*
 * Copyright 2024-2026 Revetware LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soklet.servlet.jakarta;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.soklet.servlet.jakarta.MarshaledResponseTestSupport.bodyBytesOrEmpty;
import static org.junit.jupiter.api.Assertions.*;

public class JakartaResponseContractTests {
	private static SokletHttpServletResponse response() {
		return SokletHttpServletResponse.fromRawPath("/x", SokletServletContext.fromDefaults());
	}

	@Test
	public void redirectWithoutClearingPreservesRepresentation() throws Exception {
		var response = response();
		response.setContentType("application/octet-stream");
		response.setHeader("Content-Length", "3");
		response.setHeader("Content-Encoding", "gzip");
		response.getOutputStream().write(new byte[]{1, 2, 3});
		response.sendRedirect("/next", 307, false);
		assertEquals("3", response.getHeader("Content-Length"));
		assertEquals("gzip", response.getHeader("Content-Encoding"));
		assertEquals("application/octet-stream", response.getContentType());
		assertArrayEquals(new byte[]{1, 2, 3}, bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertEquals(307, response.getStatus());
	}

	@Test
	public void nullSetHeaderRemovesAllValuesAndTypedCookieState() {
		var response = response();
		response.addHeader("X-Test", "first");
		response.addHeader("X-Test", "second");
		response.setHeader("x-test", null);
		assertFalse(response.containsHeader("X-Test"));
		assertTrue(response.getHeaders("X-Test").isEmpty());
		response.addCookie(new Cookie("typed", "value"));
		response.addHeader("Set-Cookie", "raw=value");
		response.setHeader("sEt-CoOkIe", null);
		assertFalse(response.containsHeader("Set-Cookie"));
		assertFalse(response.getHeaderNames().contains("Set-Cookie"));
		assertTrue(response.toMarshaledResponse().getCookies().isEmpty());
		assertTrue(response.toMarshaledResponse().getHeaders().isEmpty());
	}

	@Test
	public void nullContentTypeAndHeaderClearExplicitEncodingBeforeWriter() throws Exception {
		for (boolean viaHeader : List.of(false, true)) {
			var context = SokletServletContext.fromDefaults();
			context.setResponseCharacterEncoding("UTF-16");
			var response = SokletHttpServletResponse.fromRawPath("/x", context);
			response.setContentType("text/plain; charset=UTF-8");
			if (viaHeader)
				response.setHeader("content-type", null);
			else
				response.setContentType(null);
			assertNull(response.getContentType());
			assertEquals("UTF-16", response.getCharacterEncoding());
			response.getWriter().write("é");
			assertArrayEquals("é".getBytes(StandardCharsets.UTF_16), bodyBytesOrEmpty(response.toMarshaledResponse()));
		}
	}

	@Test
	public void nullContentTypeDoesNotUnlockWriterEncoding() throws Exception {
		var response = response();
		response.setContentType("text/plain; charset=UTF-8");
		var writer = response.getWriter();
		response.setHeader("Content-Type", null);
		assertNull(response.getContentType());
		assertSame(writer, response.getWriter());
		assertEquals("UTF-8", response.getCharacterEncoding());
		writer.write("é");
		assertArrayEquals("é".getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void unknownAndInvalidCharsetNamesFailWriterWithoutLockingIt() throws Exception {
		for (String encoding : List.of("no-such-charset", "bad charset", "")) {
			var response = response();
			response.setContentType("text/plain");
			response.setCharacterEncoding(encoding);
			response.setLocale(java.util.Locale.US);
			assertEquals(encoding, response.getCharacterEncoding());
			assertThrows(UnsupportedEncodingException.class, response::getWriter);
			response.setCharacterEncoding("UTF-8");
			response.getWriter().write("é");
			assertArrayEquals("é".getBytes(StandardCharsets.UTF_8), bodyBytesOrEmpty(response.toMarshaledResponse()));
		}
	}

	@Test
	public void unknownContentTypeEncodingMayStillUseBinaryStream() throws Exception {
		var response = response();
		response.setContentType("application/octet-stream; charset=no-such-charset");
		assertEquals("no-such-charset", response.getCharacterEncoding());
		assertThrows(UnsupportedEncodingException.class, response::getWriter);
		response.getOutputStream().write(42);
		assertArrayEquals(new byte[]{42}, bodyBytesOrEmpty(response.toMarshaledResponse()));
		assertEquals("application/octet-stream; charset=no-such-charset", response.getContentType());
	}

	@Test
	public void invalidEncodingCanBeClearedOrReset() throws Exception {
		var response = response();
		response.setContentType("text/plain; charset=no-such-charset");
		response.setCharacterEncoding((String) null);
		assertEquals("ISO-8859-1", response.getCharacterEncoding());
		response.setCharacterEncoding("no-such-charset");
		response.reset();
		response.getWriter().write("ok");
		assertArrayEquals("ok".getBytes(StandardCharsets.ISO_8859_1), bodyBytesOrEmpty(response.toMarshaledResponse()));
	}

	@Test
	public void genericCookieAttributesSurviveAllResponseRepresentationsExactlyOnce() {
		for (String sameSite : List.of("Strict", "Lax", "None")) {
			var response = response();
			Cookie cookie = new Cookie("session", "value");
			cookie.setSecure(true);
			cookie.setHttpOnly(true);
			cookie.setPath("/");
			cookie.setMaxAge(60);
			cookie.setAttribute("SameSite", sameSite);
			cookie.setAttribute("Priority", "High");
			cookie.setAttribute("Partitioned", "");
			cookie.setAttribute("Custom-Flag", "");
			cookie.setAttribute("Custom-Value", "two words");
			cookie.setAttribute("Custom-Octets", "\u0080\u00FF");
			response.addHeader("sEt-CoOkIe", "existing=value");
			response.addCookie(cookie);
			response.addCookie(new Cookie("legacy", "value"));

			String serialized = response.getHeaders("Set-Cookie").stream()
					.filter(value -> value.startsWith("session=")).findFirst().orElseThrow();
			for (String expected : List.of("SameSite=" + sameSite, "Priority=High", "Partitioned",
					"Custom-Flag", "Custom-Value=two words", "Custom-Octets=\u0080\u00FF",
					"Path=/", "Max-Age=60", "Secure", "HttpOnly"))
				assertTrue(serialized.contains("; " + expected), serialized);
			var marshaled = response.toMarshaledResponse();
			var application = response.toResponse();
			assertEquals(marshaled.getHeaders(), application.getHeaders());
			assertEquals(marshaled.getCookies(), application.getCookies());
			assertEquals(1, marshaled.getCookies().size());
			assertEquals("legacy", marshaled.getCookies().iterator().next().getName());
			var values = marshaled.getHeaders().entrySet().stream()
					.filter(entry -> entry.getKey().equalsIgnoreCase("Set-Cookie"))
					.flatMap(entry -> entry.getValue().stream()).toList();
			assertEquals(List.of("existing=value", serialized), new ArrayList<>(values));
		}
	}

	@Test
	public void genericCookieAttributeNamesAreCaseInsensitiveWithoutDuplicatingKnownFields() {
		var response = response();
		Cookie cookie = new Cookie("session", "value");
		cookie.setAttribute("pAtH", "/");
		cookie.setAttribute("sEcUrE", "");
		cookie.setAttribute("sAmEsItE", "Strict");
		response.addCookie(cookie);
		String value = response.getHeader("Set-Cookie");
		assertNotNull(value);
		assertEquals(1, value.split("Path=/", -1).length - 1);
		assertEquals(1, value.split("Secure", -1).length - 1);
		assertTrue(value.toLowerCase(java.util.Locale.ROOT).contains("; samesite=strict"));
	}

	@Test
	public void genericCookieAttributeValuesCannotInjectCookiesOrHeaders() {
		for (String value : List.of("bad; Secure", "bad\r\nInjected: yes", "bad\u0000", "bad\u007F",
				"bad\u0100", "bad\tvalue", "\uD83D\uDE00")) {
			var response = response();
			Cookie cookie = new Cookie("session", "value");
			cookie.setAttribute("Custom", value);
			response.addCookie(cookie);
			assertThrows(IllegalArgumentException.class, () -> response.getHeader("Set-Cookie"), value);
			assertThrows(IllegalArgumentException.class, response::toMarshaledResponse, value);
			assertThrows(IllegalArgumentException.class, response::toResponse, value);
		}
	}

	@Test
	public void nullSetCookieHeaderRemovesExtendedCookiesToo() {
		var response = response();
		Cookie cookie = new Cookie("session", "value");
		cookie.setAttribute("SameSite", "Strict");
		response.addCookie(cookie);
		response.setHeader("Set-Cookie", null);
		assertFalse(response.containsHeader("Set-Cookie"));
		assertTrue(response.toMarshaledResponse().getHeaders().isEmpty());
		assertTrue(response.toMarshaledResponse().getCookies().isEmpty());
	}
}
