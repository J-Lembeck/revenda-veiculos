package com.revenda.common.error;

public interface ErrorCode {

    String name();

    ErrorKind getKind();

    String getMessage();
}
