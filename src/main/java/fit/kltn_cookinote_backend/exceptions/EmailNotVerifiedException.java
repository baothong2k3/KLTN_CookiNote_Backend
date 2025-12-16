/*
 * @ (#) EmailNotVerifiedException.java    1.0    16/12/2025
 * Copyright (c) 2025 IUH. All rights reserved.
 */
package fit.kltn_cookinote_backend.exceptions;/*
 * @description:
 * @author: Bao Thong
 * @date: 16/12/2025
 * @version: 1.0
 */

import lombok.Getter;

@Getter
public class EmailNotVerifiedException extends RuntimeException {
    private final String email;

    public EmailNotVerifiedException(String message, String email) {
        super(message);
        this.email = email;
    }
}
