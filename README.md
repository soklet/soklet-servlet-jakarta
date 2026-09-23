<a href="https://www.soklet.com">
    <picture>
        <source media="(prefers-color-scheme: dark)" srcset="https://cdn.soklet.com/soklet-gh-logo-dark-v2.png">
        <img alt="Soklet" src="https://cdn.soklet.com/soklet-gh-logo-light-v2.png" width="300" height="101">
    </picture>
</a>

## Soklet Servlet Integration (jakarta) 

[Soklet](https://www.soklet.com) is not a [Servlet Container](https://en.wikipedia.org/wiki/Jakarta_Servlet) - it has its own in-process HTTP server, its own approach to request and response constructs, and so forth.  Soklet applications are intended to be "vanilla" Java applications, as opposed to a [WAR file](https://en.wikipedia.org/wiki/WAR_(file_format)) deployed onto a Java EE App Server.

However, there is a large body of existing code that relies on the Servlet API. To support it, Soklet provides its own implementations of the following Servlet interfaces, which enable interoperability for many common use cases:

* [`HttpServletRequest`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletRequest.html)
* [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html)
* [`HttpSession`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpSession.html)
* [`ServletContext`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletServletContext.html)
* [`ServletInputStream`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletServletInputStream.html)
* [`ServletOutputStream`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletServletOutputStream.html)
* [`ServletPrintWriter`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletServletPrintWriter.html)

This library is for the `jakarta.servlet` API. If you need to integrate with the legacy `javax.servlet` API, use [`soklet-servlet-javax`](https://github.com/soklet/soklet-servlet-javax).

This library has no implementation dependencies beyond Soklet and the chosen Servlet API. Applications must supply both: they are provided dependencies of the adapter.

**Note: this README provides a high-level overview of Soklet's Servlet Integration.**<br/>
**For details, please refer to the official documentation at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).**

## Installation

Like Soklet, this library assumes Java 17+.

Version 2.0.0 requires Soklet 4.0.0 or later and is not compatible with Soklet
3.x. Soklet and the Servlet API remain provided dependencies: standalone
applications must declare both explicitly, as shown below. When upgrading, update both
the adapter and core coordinates. The four nested adapter builder classes are
now final. The internal `SokletHttpSession.setSessionId(...)` and
`SokletHttpServletResponse.setPrintWriter(...)` hooks are no longer public;
use `HttpServletRequest.changeSessionId()` and `HttpServletResponse.getWriter()`
for the supported servlet operations.

For upgrade details, see the [servlet migration guide](https://github.com/soklet/soklet/blob/master/MIGRATING_TO_4_0.md#servlet-adapters).
Annotated Soklet routes also require the Soklet annotation processor and
`-parameters`; see [Building and Running](https://www.soklet.com/#building-and-running).

Both response conversions preserve empty `204 No Content` and `304 Not Modified`
responses as bodyless. Nonempty bodies on these statuses remain invalid and
are rejected by Soklet; ordinary empty `200 OK` responses retain their existing
byte-array representation.

Request URLs and redirect authorities always use ASCII port digits, independent
of the JVM's formatting locale. This does not change the locale-sensitive
formatting methods applications use on `ServletPrintWriter`.

### Maven

```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet-servlet-jakarta</artifactId>
  <version>2.0.0</version>
</dependency>
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet</artifactId>
  <version>4.0.0</version>
</dependency>
<dependency>
  <groupId>jakarta.servlet</groupId>
  <artifactId>jakarta.servlet-api</artifactId>
  <version>6.1.0</version>
</dependency>
```

### Gradle

```js
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.soklet:soklet-servlet-jakarta:2.0.0'
  implementation 'com.soklet:soklet:4.0.0'
  implementation 'jakarta.servlet:jakarta.servlet-api:6.1.0'
}
```

## Correctness and compatibility notes

- Request parameter methods share one snapshot, retain every repeated value,
  and put query values before form values. Automatic form-body parsing applies
  only to POST with `application/x-www-form-urlencoded`; other bodies remain
  available through the reader or input stream. The first parameter-family call
  consumes an eligible form body, including when looking up a query parameter.
- Session-cookie lookup uses the exact, case-sensitive `JSESSIONID` name.
  Client cookie pairs that the chosen Servlet API cannot represent are ignored
  individually; valid neighboring cookies are retained.
  Session invalidation detaches attributes even if a binding listener throws;
  listener failures can still propagate after cleanup.
- Generated error responses use safe plain text and replace stale representation
  metadata. Redirects that clear the body also remove the previous representation's
  metadata. Cookies and unrelated application headers are preserved.
- [`resetBuffer()`](<https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html#resetBuffer()>) clears the writer's pending characters and encoding state as
  well as its bytes, while retaining the acquired writer and its locked charset.

This adapter implements the Jakarta Servlet 6.1 API. In particular,
`setHeader(name, null)` removes that header, and clearing Content-Type before
acquiring the writer also clears an explicitly selected charset. An unsupported
charset specified in Content-Type fails at writer acquisition.

Jakarta cookie extensions such as `SameSite`, `Partitioned`, `Priority`, and
custom attributes are preserved. Cookies using extensions are emitted as
validated raw `Set-Cookie` headers in both response conversions; ordinary cookies
remain in Soklet's typed cookie collection. When inspecting converted cookies,
consider both the headers and the typed collection.

These are interoperability adapters, not full servlet containers; unsupported
container features are not implicitly enabled by these corrections.

Query parameters always use UTF-8, independently of the request-body charset.
Context request/response encodings are unspecified (`null`) until configured;
body readers and response writers still fall back to ISO-8859-1. This preserves
the standard `getCharacterEncoding() == null` guard for selecting UTF-8 before
reading request parameters or the body.

`println`, `printf`, and `format` do not commit a response merely by being
called. Explicit flush/close and filling the configured buffer still commit.
`sendError` never generates a body for a bodyless HTTP status.

New sessions snapshot the context timeout from minutes into seconds; nonpositive
values mean no timeout, and values above the seconds range saturate at
`Integer.MAX_VALUE`. This is session metadata, not an eviction service:
applications remain responsible for session persistence, expiration, and
invalidation. Updating the context timeout does not change existing sessions.

## Usage

A normal Servlet API integration looks like the following:

1. Given a Soklet [`Request`](https://javadoc.soklet.com/com/soklet/Request.html), create both an [`HttpServletRequest`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletRequest.html) and an [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html).
2. Write whatever is needed to [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html)
3. Convert the [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html) to a Soklet [`MarshaledResponse`](https://javadoc.soklet.com/com/soklet/MarshaledResponse.html)

```java
@GET("/servlet-example")
public MarshaledResponse servletExample(Request request) throws java.io.IOException {
  // Create an HttpServletRequest from the Soklet Request
  HttpServletRequest httpServletRequest =
    SokletHttpServletRequest.fromRequest(request);

  // Create an HttpServletResponse from the HttpServletRequest
  SokletHttpServletResponse httpServletResponse = 
    SokletHttpServletResponse.fromHttpServletRequest(httpServletRequest);

  // Write some data to the response using Servlet APIs
  Cookie cookie = new Cookie("name", "value");
  cookie.setDomain("soklet.com");
  cookie.setMaxAge(60);
  cookie.setPath("/");

  httpServletResponse.setStatus(200);
  httpServletResponse.addHeader("test", "one");
  httpServletResponse.addHeader("test", "two");
  httpServletResponse.addCookie(cookie);
  httpServletResponse.setCharacterEncoding("ISO-8859-1");
  httpServletResponse.getWriter().print("test");    
  
  // Convert HttpServletResponse into a Soklet MarshaledResponse and return it
  return httpServletResponse.toMarshaledResponse();
}

```

Additional documentation is available at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).
