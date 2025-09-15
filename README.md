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

This library has zero dependencies (not counting Soklet). Just add the JAR to your project and you're good to go. 

**Note: this README provides a high-level overview of Soklet's Servlet Integration.**<br/>
**For details, please refer to the official documentation at [https://www.soklet.com/docs/servlet-integration](https://www.soklet.com/docs/servlet-integration).**

## Installation

Like Soklet, this library assumes Java 17+.

### Maven

```xml
<dependency>
  <groupId>com.soklet</groupId>
  <artifactId>soklet-servlet-jakarta</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle

```js
repositories {
  mavenCentral()
}

dependencies {
  implementation 'com.soklet:soklet-servlet-jakarta:1.0.0'
}
```

## Usage

A normal Servlet API integration looks like the following:

1. Given a Soklet [`Request`](https://javadoc.soklet.com/com/soklet/core/Request.html), create both an [`HttpServletRequest`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletRequest.html) and an [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html).
2. Write whatever is needed to [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html)
3. Convert the [`HttpServletResponse`](https://jakarta.javadoc.soklet.com/com/soklet/servlet/jakarta/SokletHttpServletResponse.html) to a Soklet [`MarshaledResponse`](https://javadoc.soklet.com/com/soklet/core/MarshaledResponse.html)

```java
@GET("/servlet-example")
public MarshaledResponse servletExample(Request request) {
  // Create an HttpServletRequest from the Soklet Request
  HttpServletRequest httpServletRequest = 
    SokletHttpServletRequest.withRequest(request);

  // Create an HttpServletResponse from the Soklet Request
  SokletHttpServletResponse httpServletResponse = 
    SokletHttpServletResponse.withRequest(request);

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