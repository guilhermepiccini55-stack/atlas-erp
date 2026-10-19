package com.atlas.erp.common.exception;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio. Por padrão responde 422; use o outro construtor para 400, 401, 409 etc. */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
