package com.heihu.workorder.externaldata;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;

/** Bound credential-bearing configuration bodies before JSON binding, including chunked bodies. */
@Component
public class SourceRequestLimitFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String context = request.getContextPath();
            if (context != null && !context.isEmpty() && path.startsWith(context)) path = path.substring(context.length());
        }
        return !(path.equals("/api/external-data-sources") || path.startsWith("/api/external-data-sources/")) ||
            !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        if (request.getContentLengthLong() > 16384) { tooLarge(response); return; }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048]; int count;
        InputStream input = request.getInputStream();
        while ((count = input.read(buffer)) != -1) {
            if (body.size() + count > 16384) {
                tooLarge(response); return;
            }
            body.write(buffer,0,count);
        }
        byte[] bytes = body.toByteArray();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    @Override public int read() { return stream.read(); }
                    @Override public int read(byte[] b, int off, int len) { return stream.read(b,off,len); }
                    @Override public boolean isFinished() { return stream.available()==0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8)); }
        },response);
    }
    private void tooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"数据源请求不能超过 16 KiB\"}");
    }
}
