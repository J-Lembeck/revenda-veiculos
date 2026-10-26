package com.revenda.common.idempotency;

import com.revenda.common.error.CommonError;
import com.revenda.common.error.FieldViolation;
import com.revenda.common.error.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê a {@code Idempotency-Key} de todo {@code POST} e resume o pedido (rota, consulta e corpo) para o caso
 * de uso gravar a chave na transação dele. Roda depois da autenticação. Não decide nada: quem exige a chave
 * e devolve a resposta guardada é o {@link IdempotencyStore}.
 */
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final int BODY_MAX_BYTES = 1024 * 1024;

    static final String KEY_INVALID_MESSAGE = "Use um UUID: de 16 a 80 letras, números, hífen ou sublinhado.";
    static final String BODY_TOO_LARGE_MESSAGE = "O corpo passa de 1 MB, o limite de um pedido com Idempotency-Key.";

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,80}");
    private static final Pattern UUID_SEGMENT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int SCOPE_MAX = 80;

    private final ApiErrorWriter errorWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null) {
            proceed(IdempotencyRequest.absent(), request, response, chain);
            return;
        }
        if (!KEY.matcher(key).matches()) {
            errorWriter.write(request, response, CommonError.VALIDATION_FAILED,
                    CommonError.VALIDATION_FAILED.getMessage(),
                    List.of(new FieldViolation(HEADER, "FORMAT_INVALID", KEY_INVALID_MESSAGE)));
            return;
        }
        byte[] body = request.getInputStream().readNBytes(BODY_MAX_BYTES + 1);
        if (body.length > BODY_MAX_BYTES) {
            errorWriter.write(request, response, CommonError.VALIDATION_FAILED, BODY_TOO_LARGE_MESSAGE, List.of());
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        proceed(new IdempotencyRequest(key, scopeOf(path), hashOf(path, request.getQueryString(), body)),
                new CachedBodyRequest(request, body), response, chain);
    }

    private static void proceed(IdempotencyRequest idempotency, HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        IdempotencyContextHolder.set(idempotency);
        try {
            chain.doFilter(request, response);
        } finally {
            IdempotencyContextHolder.clear();
        }
    }

    private static String scopeOf(String path) {
        String scope = "POST " + UUID_SEGMENT.matcher(path).replaceAll("{id}");
        return scope.length() <= SCOPE_MAX ? scope : scope.substring(0, SCOPE_MAX);
    }

    private static String hashOf(String path, String query, byte[] body) {
        String head = path + "\n" + (query != null ? query : "") + "\n";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(head.getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream stream = new ByteArrayInputStream(body);
            return new ServletInputStream() {

                @Override
                public int read() {
                    return stream.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return stream.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return stream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
