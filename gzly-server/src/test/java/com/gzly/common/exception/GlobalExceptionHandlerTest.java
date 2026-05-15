package com.gzly.common.exception;

import com.gzly.common.Result;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNoResourceFound_returns404_andDoesNotThrow() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, ".env");

        ResponseEntity<Result<?>> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Result<?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(404);
        assertThat(body.getMessage()).isEqualTo("资源不存在");
    }

    @Test
    void handleClientAbort_swallowsAsyncRequestNotUsable() {
        AsyncRequestNotUsableException ex =
                new AsyncRequestNotUsableException("broken pipe");

        assertThatCode(() -> handler.handleClientAbort(ex)).doesNotThrowAnyException();
    }

    @Test
    void handleClientAbort_swallowsTomcatClientAbort() {
        ClientAbortException ex = new ClientAbortException("broken pipe");

        assertThatCode(() -> handler.handleClientAbort(ex)).doesNotThrowAnyException();
    }

    @Test
    void handleGeneral_returns500_forUnknownErrors() {
        Exception ex = new IllegalStateException("boom");

        Result<?> body = handler.handleGeneral(ex);

        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(500);
        assertThat(body.getMessage()).isEqualTo("服务器内部错误");
    }

    @Test
    void handleBiz_returnsHttp200_forDefaultBizErrorCode() {
        BizException ex = new BizException(1001, "业务错误");

        ResponseEntity<Result<?>> response = handler.handleBiz(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Result<?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(1001);
        assertThat(body.getMessage()).isEqualTo("业务错误");
    }

    @Test
    void handleBiz_returnsHttp410_forGoneCode() {
        BizException ex = new BizException(410, "资源已下线");

        ResponseEntity<Result<?>> response = handler.handleBiz(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        Result<?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo(410);
    }
}
